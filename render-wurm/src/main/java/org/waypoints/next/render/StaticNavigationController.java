package org.waypoints.next.render;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.effects.Effect;
import com.wurmonline.client.renderer.effects.EffectRender;
import com.wurmonline.client.renderer.effects.GroundNavigationRouteEffect;
import com.wurmonline.client.renderer.effects.WaypointBeamEffect;
import com.wurmonline.client.renderer.effects.WaypointSymbolEffect;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.NavigationRouteStatisticsWindowBridge;
import com.wurmonline.client.renderer.gui.WaypointLabelComponent;
import com.wurmonline.client.renderer.gui.WaypointLabelLayoutCoordinator;
import com.wurmonline.client.sound.SoundEngine;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.navigation.NavigationContext;
import org.waypoints.next.navigation.NavigationDraftOverlay;
import org.waypoints.next.navigation.NavigationEffectSelector;
import org.waypoints.next.navigation.NavigationLabelSelector;
import org.waypoints.next.navigation.NavigationSnapshot;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.navigation.NavigationViewReconcileState;
import org.waypoints.next.navigation.StaticNavigationRegistry;
import org.waypoints.next.navigation.WaypointArrivalTracker;
import org.waypoints.next.service.WaypointRevisionSnapshot;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns every Phase 2 marker, the exclusive ground route, and waypoint labels. */
public final class StaticNavigationController {
    // The pinned client can finish rebuilding custom VBO state several seconds
    // after server information arrives. Reattach after that late startup phase;
    // the former three-second pass occurred too early on the live client.
    private static final long RESOURCE_REFRESH_NANOS = 10_000_000_000L;
    private static final float BEAM_HEIGHT = 400.0f;
    private static final float THROUGH_WALL_WIDTH = 0.15f;
    private static final ManagedResourceRegistry.ResourceCondition<OwnedEffect>
            DELETED_CUSTOM_EFFECT =
            new ManagedResourceRegistry.ResourceCondition<OwnedEffect>() {
                @Override public boolean matches(OwnedEffect resource) {
                    return resource != null && resource.customEffectWasDeleted();
                }
            };
    private final Logger logger;
    private final StaticNavigationRegistry navigation = new StaticNavigationRegistry();
    private final ManagedResourceRegistry<NavigationTargetKey, NavigationTarget, OwnedEffect>
            effects;
    private final Map<NavigationTargetKey, OwnedLabel> labels =
            new LinkedHashMap<NavigationTargetKey, OwnedLabel>();
    private final WaypointLabelLayoutCoordinator labelLayout =
            new WaypointLabelLayoutCoordinator();
    private final NavigationViewReconcileState labelReconcileState =
            new NavigationViewReconcileState();
    private final WaypointArrivalTracker arrivalTracker =
            new WaypointArrivalTracker();
    private final WaypointArrivalTracker.Listener arrivalListener =
            new WaypointArrivalTracker.Listener() {
                @Override public void arrived(NavigationTarget target,
                                              int distanceMetres) {
                    notifyArrival(target, distanceMetres);
                }

                @Override public void exited(NavigationTarget target,
                                             int distanceMetres) {
                    setArrivalWorldEffect(target, true,
                            "Arrival world-effect restore failed open");
                }
            };
    private final UUID managerDraftId = UUID.randomUUID();
    private final NavigationHighwaySource highwaySource;

    private WaypointRenderConfiguration configuration =
            WaypointRenderConfiguration.defaults();
    private World world;
    private HeadsUpDisplay hud;
    private String serverKey = "";
    private String user = "";
    private EffectRender renderer;
    private EffectRender navigatorEffectOwner;
    private GroundNavigationRouteEffect navigatorEffect;
    private NavigationTarget navigatorEffectTarget;
    private NavigationRouteDiagnosticLog navigatorDiagnosticLog;
    private NavigationRenderFrame frame;
    private NavigationContext navigationContext;
    private long appliedGeneration = Long.MIN_VALUE;
    private int appliedPlayerTileX = Integer.MIN_VALUE;
    private int appliedPlayerTileY = Integer.MIN_VALUE;
    private int appliedPlayerLayer = Integer.MIN_VALUE;
    private long refreshAtNanos;
    private boolean forceReconcile = true;
    private boolean navigationPulseEnabled = true;
    private PreviewDraft managerDraft;
    private long managerDraftRevision;

    public StaticNavigationController(Logger logger,
                                      NavigationHighwaySource highwaySource) {
        if (logger == null) throw new IllegalArgumentException("logger is required");
        if (highwaySource == null) throw new IllegalArgumentException(
                "highway source is required");
        this.logger = logger;
        this.highwaySource = highwaySource;
        effects = new ManagedResourceRegistry<NavigationTargetKey, NavigationTarget, OwnedEffect>(
                new ManagedResourceRegistry.Adapter<NavigationTargetKey, NavigationTarget, OwnedEffect>() {
                    @Override public NavigationTargetKey key(NavigationTarget source) {
                        return source.getKey();
                    }

                    @Override public boolean sameResource(NavigationTarget previous,
                                                          NavigationTarget next) {
                        return previous.getCoordinate().equals(next.getCoordinate())
                                && previous.getMarkerStyle().equals(next.getMarkerStyle());
                    }

                    @Override public OwnedEffect create(NavigationTarget source) {
                        World ownerWorld = world;
                        EffectRender ownerRenderer = renderer;
                        if (ownerWorld == null || ownerRenderer == null) {
                            throw new IllegalStateException("effect renderer is not ready");
                        }
                        WaypointCoordinate coordinate = source.getCoordinate();
                        float baseHeight = coordinate.getHeight() == null
                                ? ownerWorld.getPlayerPosH()
                                : coordinate.getHeight().floatValue();
                        int targetLayer = coordinate.getLayer()
                                == org.waypoints.next.model.WaypointLayer.CAVE
                                ? -1 : 0;
                        boolean groundAnchored = coordinate.getHeight() == null;
                        MarkerStyle marker = source.getMarkerStyle();
                        Effect effect;
                        CleanupKind cleanup = CleanupKind.ORDINARY;
                        if (source.isVanillaSystem()
                                && marker.getWorldStyle()
                                == MarkerStyle.WorldStyle.RIFT) {
                            effect = vanillaRift(ownerWorld,
                                    (float) coordinate.worldX(),
                                    (float) coordinate.worldY(), baseHeight);
                            cleanup = CleanupKind.RIFT;
                        } else if (source.isVanillaSystem()
                                && (marker.getWorldStyle()
                                == MarkerStyle.WorldStyle.WHITE_LIGHT
                                || marker.getWorldStyle()
                                == MarkerStyle.WorldStyle.BLACK_LIGHT)) {
                            effect = vanillaLight(ownerWorld,
                                    (float) coordinate.worldX(),
                                    (float) coordinate.worldY(), baseHeight,
                                    marker.getWorldStyle()
                                            == MarkerStyle.WorldStyle.WHITE_LIGHT);
                            cleanup = CleanupKind.LIGHT;
                        } else if (WaypointSymbolGeometry.isSymbol(
                                marker.getWorldStyle())) {
                            effect = new WaypointSymbolEffect(ownerWorld,
                                    (float) coordinate.worldX(),
                                    (float) coordinate.worldY(), baseHeight,
                                    targetLayer,
                                    marker.getWorldStyle(), marker.getMarkerSize(),
                                    marker.getBeamWidth(), marker.getRed(),
                                    marker.getGreen(), marker.getBlue(),
                                    marker.getAlpha(), groundAnchored,
                                    source.getSourceType()
                                    == org.waypoints.next.model.WaypointSourceType.LOOT_MAP);
                        } else {
                            effect = new WaypointBeamEffect(ownerWorld,
                                    (float) coordinate.worldX(),
                                    (float) coordinate.worldY(), baseHeight,
                                    BEAM_HEIGHT, marker.getBeamWidth(),
                                    marker.getRed(), marker.getGreen(),
                                    marker.getBlue(), marker.getAlpha(),
                                    beamMode(marker.getWorldStyle()),
                                    marker.getMarkerSize(), true,
                                    THROUGH_WALL_WIDTH, targetLayer,
                                    groundAnchored);
                        }
                        int effectLayer = source.isVanillaSystem()
                                ? (coordinate.getLayer()
                                == org.waypoints.next.model.WaypointLayer.CAVE ? -1 : 0)
                                : NavigationRenderLayer.forPlayer(
                                ownerWorld.getPlayerLayer());
                        effect.setLayer(effectLayer);
                        return new OwnedEffect(ownerWorld, ownerRenderer, effect,
                                cleanup);
                    }

                    @Override public void add(OwnedEffect resource) {
                        resource.owner.addEffect(resource.effect);
                        if (resource.cleanup == CleanupKind.LIGHT) {
                            addVanillaLight(resource.world, resource.effect);
                        }
                    }

                    @Override public void remove(OwnedEffect resource) {
                        if (resource.cleanup == CleanupKind.RIFT) {
                            resource.effect.removed();
                        }
                        resource.owner.removeEffect(resource.effect);
                    }

                    @Override public void delete(OwnedEffect resource) {
                        if (resource.cleanup == CleanupKind.RIFT) {
                            resource.effect.removed();
                        }
                        resource.effect.delete();
                    }
                });
    }

    public synchronized void configure(WaypointRenderConfiguration value) {
        if (navigatorEffect != null) clearNavigatorEffect();
        configuration = value == null ? WaypointRenderConfiguration.defaults() : value;
        navigationPulseEnabled = true;
        highwaySource.configure(configuration);
        forceReconcile = true;
        labelReconcileState.reset();
    }

    public synchronized void tick(World nextWorld, HeadsUpDisplay nextHud,
                                  ServerIdentity currentServer, String currentUser,
                                  WaypointRevisionSnapshot source) {
        if (nextWorld == null || nextHud == null || currentServer == null
                || !currentServer.isSafeForAutomaticRendering()
                || clean(currentUser).isEmpty() || source == null) {
            if (world != null || frame != null) detach("unresolved navigation context");
            return;
        }
        try {
            activate(nextWorld, nextHud, currentServer, currentUser);
            NavigationSnapshot next = withManagerDraft(
                    navigation.reconcile(source, navigationContext));
            if (frame == null) frame = new NavigationRenderFrame(nextWorld, next);
            else frame.update(next);

            EffectRender nextRenderer = effectRenderer(nextWorld);
            if (renderer != nextRenderer) {
                clearEffects("effect renderer replacement");
                renderer = nextRenderer;
                forceReconcile = true;
            }
            // EffectRender.clear() deletes VBOs before emptying its list. A
            // competing mod or a cave-transition ordering can prevent our
            // clear hook from invalidating ownership. Detect the authoritative
            // custom-effect alive flag here so stale registry entries cannot
            // suppress recreation forever.
            if (effects.anyResourceMatches(DELETED_CUSTOM_EFFECT)) {
                int invalidated = effects.size();
                effects.invalidateAfterExternalClear();
                forceReconcile = true;
                logger.info("Phase 2 self-healed externally deleted effects: count="
                        + invalidated + ", serverKey=\"" + serverKey + "\"");
            }
            long now = System.nanoTime();
            if (refreshAtNanos > 0L && now >= refreshAtNanos) {
                clearEffects("post-activation resource stabilization");
                refreshAtNanos = 0L;
                forceReconcile = true;
            }

            int playerTileX = (int) Math.floor(nextWorld.getPlayerPosX() / 4.0f);
            int playerTileY = (int) Math.floor(nextWorld.getPlayerPosY() / 4.0f);
            int playerLayer = NavigationRenderLayer.forPlayer(
                    nextWorld.getPlayerLayer());
            if (appliedPlayerLayer != Integer.MIN_VALUE
                    && appliedPlayerLayer != playerLayer) {
                clearEffects("player render layer changed");
                forceReconcile = true;
            }
            if (forceReconcile || appliedGeneration != next.getGeneration()
                    || appliedPlayerTileX != playerTileX
                    || appliedPlayerTileY != playerTileY
                    || appliedPlayerLayer != playerLayer) {
                List<NavigationTarget> desired = renderer == null
                        ? Collections.<NavigationTarget>emptyList()
                        : NavigationEffectSelector.select(next,
                        nextWorld.getPlayerPosX() / 4.0d,
                        nextWorld.getPlayerPosY() / 4.0d,
                        configuration.getWorldEffectDistanceMetres(),
                        configuration.getMaximumWorldEffects());
                effects.reconcile(desired);
                appliedGeneration = next.getGeneration();
                appliedPlayerTileX = playerTileX;
                appliedPlayerTileY = playerTileY;
                appliedPlayerLayer = playerLayer;
                forceReconcile = false;
            }
            reconcileNavigator(next.getActiveNavigator());
            NavigationRouteStatisticsWindowBridge.reconcile(nextHud,
                    next.getActiveNavigator(), navigatorEffect == null
                            ? null : navigatorEffect.getRouteStatistics());
            // Labels remain independent from beam visibility. Rebuild their
            // ownership only when culling inputs change; components continue
            // projection every render frame without per-tick collection churn.
            int viewportWidth = nextHud.getWidth();
            if (labelReconcileState.requires(next.getGeneration(), playerTileX,
                    playerTileY, viewportWidth)) {
                reconcileLabels(next, nextWorld.getPlayerPosX() / 4.0d,
                        nextWorld.getPlayerPosY() / 4.0d);
                labelReconcileState.applied(next.getGeneration(), playerTileX,
                        playerTileY, viewportWidth);
            }
            WaypointRenderProfiler.activeResources(next.getTargets().size(),
                    effects.size(), labels.size());
            arrivalTracker.update(next,
                    nextWorld.getPlayerPosX() / 4.0d,
                    nextWorld.getPlayerPosY() / 4.0d,
                    nextWorld.getPlayerLayer() < 0
                            ? WaypointLayer.CAVE : WaypointLayer.SURFACE,
                    arrivalListener);
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Phase 2 static navigation failed open", failure);
            forceReconcile = true;
        }
    }

    public synchronized NavigationTarget selectAndToggle(NavigationTargetKey key) {
        NavigationSnapshot next = withManagerDraft(navigation.selectAndToggleBeam(key));
        if (frame != null) frame.update(next);
        forceReconcile = true;
        return next.find(key);
    }

    public synchronized NavigationTarget toggleNavigator(UUID waypointId) {
        if (waypointId == null || clean(serverKey).isEmpty()) return null;
        return toggleNavigator(new NavigationTargetKey(serverKey, waypointId));
    }

    /** Also permits dynamic targets such as the Loot Map waypoint. */
    public synchronized NavigationTarget toggleNavigator(NavigationTargetKey key) {
        NavigationSnapshot next = withManagerDraft(navigation.toggleNavigator(key));
        if (frame != null) frame.update(next);
        forceReconcile = true;
        return next.find(key);
    }

    /** Starts or retargets navigation without turning off an already active owner. */
    public synchronized NavigationTarget startNavigator(NavigationTargetKey key) {
        NavigationSnapshot next = withManagerDraft(navigation.activateNavigator(key));
        if (frame != null) frame.update(next);
        forceReconcile = true;
        return next.find(key);
    }

    public synchronized boolean isNavigatorActive(UUID waypointId) {
        NavigationTargetKey active = navigation.navigatorKey();
        return waypointId != null && active != null
                && waypointId.equals(active.getWaypointId())
                && serverKey.equalsIgnoreCase(active.getServerFingerprint());
    }

    /** Session-only switch used by /wp nav pulse; navigation ownership remains. */
    public synchronized void setNavigationPulseEnabled(boolean enabled) {
        if (navigationPulseEnabled == enabled) return;
        navigationPulseEnabled = enabled;
        if (navigatorEffect != null && navigatorEffect.isAlive()) {
            navigatorEffect.setVisualStyle(effectiveNavigationRouteVisualStyle(
                    configuration.getNavigationRouteVisualStyle(), enabled));
        } else {
            forceReconcile = true;
        }
    }

    public synchronized boolean isNavigationPulseEnabled() {
        return navigationPulseEnabled;
    }

    public synchronized void managerEnabledChanged(UUID waypointId,
                                                    boolean enabled) {
        NavigationSnapshot changed = navigation.setWorldEffectVisible(
                waypointId, enabled);
        if (!enabled) changed = navigation.deactivateNavigator(waypointId);
        NavigationSnapshot next = withManagerDraft(changed);
        if (frame != null) frame.update(next);
        forceReconcile = true;
    }

    public synchronized void previewManagerDraft(UUID editingId, String name,
                                                 WaypointCoordinate coordinate,
                                                 MarkerStyle markerStyle) {
        if (coordinate == null) throw new IllegalArgumentException(
                "preview coordinate is required");
        if (markerStyle == null) throw new IllegalArgumentException(
                "preview marker style is required");
        if (managerDraft != null && managerDraft.matches(
                editingId, name, coordinate, markerStyle)) return;
        managerDraft = new PreviewDraft(editingId, name, coordinate, markerStyle);
        managerDraftRevision++;
        forceReconcile = true;
        if (frame != null) frame.update(withManagerDraft(navigation.snapshot()));
    }

    public synchronized void clearManagerDraft() {
        if (managerDraft == null) return;
        managerDraft = null;
        managerDraftRevision++;
        forceReconcile = true;
        if (frame != null) frame.update(navigation.snapshot());
    }

    public synchronized NavigationRenderFrame currentFrame() {
        return frame;
    }

    public synchronized void rendererCleared(Object clearedRenderer) {
        if (clearedRenderer == null || renderer != clearedRenderer) return;
        int invalidated = effects.size();
        effects.invalidateAfterExternalClear();
        invalidateNavigatorAfterExternalClear();
        forceReconcile = true;
        if (invalidated > 0) {
            logger.info("Phase 2 renderer clear invalidated managed effects: count="
                    + invalidated + ", serverKey=\"" + serverKey + "\"");
        }
    }

    public synchronized void detach(String reason) {
        clearEffects(reason);
        clearLabels(reason);
        navigation.clearView();
        world = null;
        hud = null;
        renderer = null;
        frame = null;
        navigationContext = null;
        highwaySource.deactivate();
        serverKey = "";
        user = "";
        refreshAtNanos = 0L;
        appliedGeneration = Long.MIN_VALUE;
        appliedPlayerTileX = Integer.MIN_VALUE;
        appliedPlayerTileY = Integer.MIN_VALUE;
        appliedPlayerLayer = Integer.MIN_VALUE;
        labelReconcileState.reset();
        arrivalTracker.reset();
        forceReconcile = true;
        managerDraft = null;
        managerDraftRevision++;
        WaypointRenderProfiler.activeResources(0, 0, 0);
    }

    private void notifyArrival(NavigationTarget target, int distanceMetres) {
        // Arrival hides only the session-owned world effect. The waypoint,
        // compass marker, and independently managed distance label stay active.
        // A fresh HUD restores the effect because registry visibility is not
        // persisted.
        setArrivalWorldEffect(target, false,
                "Arrival world-effect cleanup failed open");
        boolean navigatorStopped = false;
        try {
            if (target.getKey().equals(navigation.navigatorKey())) {
                NavigationSnapshot stopped = navigation.deactivateNavigator(
                        target.getKey());
                if (frame != null) frame.update(withManagerDraft(stopped));
                clearNavigatorEffect();
                forceReconcile = true;
                navigatorStopped = true;
                logger.info("Navigator stopped on waypoint arrival: key="
                        + target.getKey() + ", distance=" + distanceMetres + "m");
            }
        } catch (Throwable failure) {
            logger.log(Level.FINE,
                    "Arrival navigator cleanup failed open", failure);
        }
        try {
            HeadsUpDisplay currentHud = hud;
            if (currentHud != null) {
                currentHud.textMessage(":Event", 0.35f, 0.85f, 1.0f,
                        "[Wurm Waypointer] Arrived at "
                                + oneLine(target.getName()) + " - "
                                + distanceMetres + "m."
                                + (navigatorStopped
                                ? " Navigator stopped." : ""));
            }
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Arrival Event notification failed open", failure);
        }
        try {
            World currentWorld = world;
            SoundEngine<?> sound = currentWorld == null
                    ? null : currentWorld.getSoundEngine();
            if (sound != null && SoundEngine.getPlayerPosition() != null) {
                sound.play("sound.bell.handbell", SoundEngine.getPlayerPosition(),
                        1.0f, 1.0f, 1.0f, false, false);
            }
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Arrival sound failed open", failure);
        }
    }

    private void setArrivalWorldEffect(NavigationTarget target, boolean visible,
                                       String failureMessage) {
        try {
            NavigationSnapshot changed = navigation.setWorldEffectVisible(
                    target.getKey().getWaypointId(), visible);
            if (frame != null) frame.update(withManagerDraft(changed));
            forceReconcile = true;
        } catch (Throwable failure) {
            logger.log(Level.FINE, failureMessage, failure);
        }
    }

    private void activate(World nextWorld, HeadsUpDisplay nextHud,
                          ServerIdentity currentServer, String currentUser) {
        String nextServerKey = currentServer.getEndpointFingerprint();
        String nextUser = clean(currentUser);
        if (world == nextWorld && hud == nextHud && serverKey.equals(nextServerKey)
                && user.equalsIgnoreCase(nextUser)) return;
        if (world != null || hud != null) {
            clearEffects("navigation context replacement");
            clearLabels("navigation context replacement");
            navigation.clearView();
        }
        world = nextWorld;
        hud = nextHud;
        serverKey = nextServerKey;
        user = nextUser;
        renderer = effectRenderer(nextWorld);
        frame = new NavigationRenderFrame(nextWorld, NavigationSnapshot.empty());
        navigationContext = new NavigationContext(currentServer, nextUser,
                configuration.getMaximumCompassMarkers());
        highwaySource.activate(currentServer);
        refreshAtNanos = System.nanoTime() + RESOURCE_REFRESH_NANOS;
        forceReconcile = true;
        labelReconcileState.reset();
        logger.info("Phase 2 static navigation activated: serverKey=\""
                + serverKey + "\", user=\"" + oneLine(user) + "\"");
    }

    private void reconcileLabels(NavigationSnapshot snapshot,
                                 double playerTileX, double playerTileY) {
        List<NavigationTarget> desired = NavigationLabelSelector.select(snapshot,
                playerTileX, playerTileY,
                configuration.getWorldLabelDistanceMetres(),
                configuration.getMaximumWorldLabels());
        Map<NavigationTargetKey, NavigationTarget> desiredByKey =
                new LinkedHashMap<NavigationTargetKey, NavigationTarget>();
        for (NavigationTarget target : desired) desiredByKey.put(target.getKey(), target);

        for (NavigationTargetKey key :
                new ArrayList<NavigationTargetKey>(labels.keySet())) {
            if (!desiredByKey.containsKey(key)) removeLabel(key);
        }
        int layoutOrder = 0;
        for (NavigationTarget target : desired) {
            OwnedLabel current = labels.get(target.getKey());
            if (current != null && current.owner == hud
                    && sameLabelTarget(current.target, target)
                    && current.component.isAttachedTo(hud)) {
                current.component.updateViewport(hud.getWidth());
                current.component.updateLayoutOrder(layoutOrder++);
                continue;
            }
            if (current != null) removeLabel(target.getKey());
            WaypointCoordinate coordinate = target.getCoordinate();
            float baseHeight = coordinate.getHeight() == null
                    ? world.getPlayerPosH() : coordinate.getHeight().floatValue();
            int targetLayer = coordinate.getLayer() == WaypointLayer.CAVE ? -1 : 0;
            WaypointLabelComponent component = WaypointLabelComponent.attachManaged(
                    hud, labelLayout, layoutOrder++, world,
                    (float) coordinate.worldX(),
                    (float) coordinate.worldY(), baseHeight, targetLayer,
                    coordinate.getHeight() == null, target.getName(),
                    target.getMarkerStyle().getRed(),
                    target.getMarkerStyle().getGreen(),
                    target.getMarkerStyle().getBlue(),
                    target.getMarkerStyle().getAlpha(),
                    target.getExpiresAtEpochMillis());
            labels.put(target.getKey(), new OwnedLabel(hud, target, component));
        }
    }

    private void removeLabel(NavigationTargetKey key) {
        OwnedLabel owned = labels.remove(key);
        if (owned == null) return;
        try { owned.component.detach(owned.owner); }
        catch (Throwable failure) {
            logger.log(Level.FINE, "Phase 2 waypoint label cleanup failed open", failure);
        }
    }

    private void clearLabels(String reason) {
        int count = labels.size();
        for (NavigationTargetKey key :
                new ArrayList<NavigationTargetKey>(labels.keySet())) {
            removeLabel(key);
        }
        if (count > 0) logger.fine("Phase 2 waypoint labels detached: count="
                + count + ", reason=" + oneLine(reason));
    }

    private void clearEffects(String reason) {
        int count = effects.size() + (navigatorEffect == null ? 0 : 1);
        clearNavigatorEffect();
        try { effects.clear(); }
        catch (Throwable failure) {
            logger.log(Level.FINE, "Phase 2 effect cleanup used delete fallback", failure);
        }
        if (count > 0) logger.info("Phase 2 managed effects detached: count="
                + count + ", reason=" + oneLine(reason));
    }

    private void reconcileNavigator(NavigationTarget target) {
        if (target == null || renderer == null || world == null) {
            clearNavigatorEffect();
            return;
        }
        if (navigatorEffect != null
                && navigatorEffect.isAlive()
                && navigatorEffectOwner == renderer
                && sameNavigatorTarget(navigatorEffectTarget, target)) return;
        clearNavigatorEffect();
        WaypointCoordinate coordinate = target.getCoordinate();
        MarkerStyle style = target.getMarkerStyle();
        NavigationRouteDiagnosticLog routeLog = createRouteDiagnosticLog(target);
        GroundNavigationRouteEffect created = null;
        try {
            created = new GroundNavigationRouteEffect(
                    world, (int) Math.floor(coordinate.getTileX()),
                    (int) Math.floor(coordinate.getTileY()),
                    coordinate.getLayer() == WaypointLayer.CAVE ? -1 : 0,
                    style.getRed(), style.getGreen(), style.getBlue(),
                    style.getAlpha(),
                    effectiveNavigationRouteVisualStyle(
                            configuration.getNavigationRouteVisualStyle(),
                            navigationPulseEnabled),
                    configuration.getNavigationPulseMaximumDistanceMetres(),
                    configuration.getNavigationCartMaximumSlopeDirt(),
                    configuration.getNavigationCartMaximumWaterDepthMetres(),
                    configuration.getNavigationRouteLogTileInterval(),
                    configuration.getMapWidth(),
                    configuration.getMapHeight(), highwaySource,
                    routeLog);
            created.setLayer(NavigationRenderLayer.forPlayer(world.getPlayerLayer()));
            renderer.addEffect(created);
            navigatorEffectOwner = renderer;
            navigatorEffect = created;
            navigatorEffectTarget = target;
            navigatorDiagnosticLog = routeLog;
        } catch (Throwable failure) {
            if (created != null) {
                try { created.delete(); }
                catch (Throwable ignored) { }
            }
            closeRouteDiagnosticLog(routeLog, "route_start_failed");
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) throw (Error) failure;
            throw new IllegalStateException("navigator start failed", failure);
        }
    }

    static org.waypoints.next.navigation.NavigationRouteVisualStyle
    effectiveNavigationRouteVisualStyle(
            org.waypoints.next.navigation.NavigationRouteVisualStyle configured,
            boolean pulseEnabled) {
        if (configured == null) {
            configured = org.waypoints.next.navigation.NavigationRouteVisualStyle
                    .MOVING_DASHES;
        }
        return pulseEnabled ? configured
                : org.waypoints.next.navigation.NavigationRouteVisualStyle.SOLID;
    }

    private void clearNavigatorEffect() {
        GroundNavigationRouteEffect effect = navigatorEffect;
        EffectRender owner = navigatorEffectOwner;
        NavigationRouteDiagnosticLog routeLog = navigatorDiagnosticLog;
        navigatorEffect = null;
        navigatorEffectOwner = null;
        navigatorEffectTarget = null;
        navigatorDiagnosticLog = null;
        NavigationRouteStatisticsWindowBridge.detach(hud,
                "navigator effect stopped");
        if (effect != null) {
            try {
                if (owner != null) owner.removeEffect(effect);
            } catch (Throwable failure) {
                logger.log(Level.FINE,
                        "Ground navigator removal failed open", failure);
            } finally {
                try { effect.delete(); }
                catch (Throwable failure) {
                    logger.log(Level.FINE,
                            "Ground navigator delete failed open", failure);
                }
            }
        }
        closeRouteDiagnosticLog(routeLog, "route_stopped");
    }

    private void invalidateNavigatorAfterExternalClear() {
        NavigationRouteDiagnosticLog routeLog = navigatorDiagnosticLog;
        navigatorEffect = null;
        navigatorEffectOwner = null;
        navigatorEffectTarget = null;
        navigatorDiagnosticLog = null;
        NavigationRouteStatisticsWindowBridge.detach(hud,
                "navigator renderer cleared");
        closeRouteDiagnosticLog(routeLog, "renderer_cleared");
    }

    private NavigationRouteDiagnosticLog createRouteDiagnosticLog(
            NavigationTarget target) {
        if (!configuration.isNavigationRouteDiagnosticsEnabled()) return null;
        WaypointCoordinate coordinate = target.getCoordinate();
        int targetX = (int) Math.floor(coordinate.getTileX());
        int targetY = (int) Math.floor(coordinate.getTileY());
        int targetLayer = coordinate.getLayer() == WaypointLayer.CAVE ? -1 : 0;
        try {
            NavigationRouteDiagnosticLog result =
                    new NavigationRouteDiagnosticLog(
                            configuration.getNavigationRouteLogDirectory(),
                            Instant.now(), target.getKey().getServerFingerprint(),
                            target.getKey().getWaypointId(), target.getName(),
                            targetX, targetY, targetLayer,
                            configuration.getNavigationCartMaximumSlopeDirt(),
                            configuration.getNavigationCartMaximumWaterDepthMetres(),
                            logger);
            logger.info("Ground navigator diagnostics started: algorithm="
                    + org.waypoints.next.navigation.GroundRouteTrace.ALGORITHM_VERSION
                    + ", log=\"" + result.getFile().toAbsolutePath().normalize()
                    + "\"");
            return result;
        } catch (IOException failure) {
            logger.log(Level.WARNING,
                    "Unable to start ground navigator diagnostics; route rendering continues",
                    failure);
            return null;
        }
    }

    private void closeRouteDiagnosticLog(NavigationRouteDiagnosticLog routeLog,
                                         String reason) {
        if (routeLog == null) return;
        try {
            routeLog.close(reason);
        } catch (IOException failure) {
            logger.log(Level.WARNING,
                    "Unable to close ground navigator diagnostics", failure);
        }
    }

    private static boolean sameNavigatorTarget(NavigationTarget left,
                                               NavigationTarget right) {
        return left != null && right != null
                && left.getKey().equals(right.getKey())
                && left.getCoordinate().equals(right.getCoordinate())
                && left.getMarkerStyle().equals(right.getMarkerStyle());
    }

    private static boolean sameLabelTarget(NavigationTarget left,
                                           NavigationTarget right) {
        return left != null && right != null && left.getKey().equals(right.getKey())
                && left.getName().equals(right.getName())
                && left.getCoordinate().equals(right.getCoordinate())
                && left.getMarkerStyle().equals(right.getMarkerStyle())
                && left.getExpiresAtEpochMillis()
                == right.getExpiresAtEpochMillis();
    }

    private NavigationSnapshot withManagerDraft(NavigationSnapshot stored) {
        PreviewDraft draft = managerDraft;
        if (draft == null || clean(serverKey).isEmpty()) return stored;
        return NavigationDraftOverlay.apply(stored, serverKey, managerDraftId,
                draft.editingId, draft.name, draft.coordinate, draft.markerStyle,
                managerDraftRevision);
    }

    private static EffectRender effectRenderer(World world) {
        return world == null || world.getWorldRenderer() == null ? null
                : world.getWorldRenderer().getEffectRenderer();
    }

    /** Keeps vanilla effect classes unresolved until the first post-login render tick. */
    private static Effect vanillaLight(World world, float x, float y, float height,
                                       boolean white) {
        return constructVanilla("com.wurmonline.client.renderer.effects.LightBeamEffect",
                new Class<?>[]{World.class, Float.TYPE, Float.TYPE, Float.TYPE,
                        Boolean.TYPE},
                new Object[]{world, Float.valueOf(x), Float.valueOf(y),
                        Float.valueOf(height), Boolean.valueOf(white)});
    }

    /** Keeps vanilla effect classes unresolved until the first post-login render tick. */
    private static Effect vanillaRift(World world, float x, float y, float height) {
        return constructVanilla("com.wurmonline.client.renderer.effects.RiftSpawnEffect",
                new Class<?>[]{World.class, Float.TYPE, Float.TYPE, Float.TYPE},
                new Object[]{world, Float.valueOf(x), Float.valueOf(y),
                        Float.valueOf(height)});
    }

    private static Effect constructVanilla(String className, Class<?>[] signature,
                                           Object[] arguments) {
        try {
            ClassLoader loader = StaticNavigationController.class.getClassLoader();
            Class<?> type = Class.forName(className, true, loader);
            return (Effect) type.getConstructor(signature).newInstance(arguments);
        } catch (Throwable failure) {
            throw new IllegalStateException("unable to construct exact vanilla effect",
                    failure);
        }
    }

    private static void addVanillaLight(World world, Effect effect) {
        try {
            ClassLoader loader = StaticNavigationController.class.getClassLoader();
            Class<?> lightSource = Class.forName(
                    "com.wurmonline.client.renderer.light.LightSource", true, loader);
            Object manager = world.getClass().getMethod(
                    "getLightManager", Integer.TYPE).invoke(
                    world, Integer.valueOf(effect.getLayer()));
            manager.getClass().getMethod("addLight", lightSource).invoke(
                    manager, effect);
        } catch (Throwable failure) {
            throw new IllegalStateException("unable to register exact vanilla light",
                    failure);
        }
    }

    private static WaypointBeamEffect.VisualMode beamMode(
            MarkerStyle.WorldStyle style) {
        if (style == MarkerStyle.WorldStyle.BLACK_LIGHT) {
            return WaypointBeamEffect.VisualMode.INVERT;
        }
        if (style == MarkerStyle.WorldStyle.CIRCLE_BEAM) {
            return WaypointBeamEffect.VisualMode.CIRCLE;
        }
        if (style == MarkerStyle.WorldStyle.WHITE_LIGHT
                || style == MarkerStyle.WorldStyle.COLORED_BEAM) {
            return WaypointBeamEffect.VisualMode.ADDITIVE;
        }
        throw new IllegalArgumentException("world style has no beam effect: " + style);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static final class OwnedEffect {
        private final World world;
        private final EffectRender owner;
        private final Effect effect;
        private final CleanupKind cleanup;

        private OwnedEffect(World world, EffectRender owner, Effect effect,
                            CleanupKind cleanup) {
            this.world = world;
            this.owner = owner;
            this.effect = effect;
            this.cleanup = cleanup;
        }

        private boolean customEffectWasDeleted() {
            if (effect instanceof WaypointBeamEffect) {
                return !((WaypointBeamEffect) effect).isAlive();
            }
            if (effect instanceof WaypointSymbolEffect) {
                return !((WaypointSymbolEffect) effect).isAlive();
            }
            return false;
        }
    }

    private enum CleanupKind { ORDINARY, LIGHT, RIFT }

    private static final class OwnedLabel {
        private final HeadsUpDisplay owner;
        private final NavigationTarget target;
        private final WaypointLabelComponent component;

        private OwnedLabel(HeadsUpDisplay owner, NavigationTarget target,
                           WaypointLabelComponent component) {
            this.owner = owner;
            this.target = target;
            this.component = component;
        }
    }

    private static final class PreviewDraft {
        private final UUID editingId;
        private final String name;
        private final WaypointCoordinate coordinate;
        private final MarkerStyle markerStyle;

        private PreviewDraft(UUID editingId, String name,
                             WaypointCoordinate coordinate,
                             MarkerStyle markerStyle) {
            this.editingId = editingId;
            this.name = name == null ? "" : name;
            this.coordinate = coordinate;
            this.markerStyle = markerStyle;
        }

        private boolean matches(UUID nextEditingId, String nextName,
                                WaypointCoordinate nextCoordinate,
                                MarkerStyle nextStyle) {
            String cleanName = nextName == null ? "" : nextName;
            if (editingId == null ? nextEditingId != null
                    : !editingId.equals(nextEditingId)) return false;
            return name.equals(cleanName)
                    && coordinate.equals(nextCoordinate)
                    && markerStyle.equals(nextStyle);
        }
    }
}
