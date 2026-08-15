package org.waypoints.next.render;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.effects.EffectRender;
import com.wurmonline.client.renderer.effects.WaypointBeamEffect;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.WaypointLabelComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Creates server-bound Phase 0 probes and owns their Wurm render lifecycle. */
public final class WurmBeamProbeController {
    private static final long TRANSFER_STABILIZATION_NANOS = 3_000_000_000L;
    private final Logger logger;
    private final Map<String, ProbeWaypoint> probes =
            new LinkedHashMap<String, ProbeWaypoint>();
    private BeamProbeConfiguration configuration = BeamProbeConfiguration.disabled();
    private boolean failed;
    private boolean labelFailed;
    private HeadsUpDisplay labelHud;
    private WaypointLabelComponent label;
    private World targetWorld;
    private String activeServerKey;
    private ProbeWaypoint active;
    private EffectRender attachedRenderer;
    private final RendererStabilizationGate stabilization =
            new RendererStabilizationGate();
    private final SingleEffectLifecycle<World, WaypointBeamEffect> lifecycle;

    public WurmBeamProbeController(Logger logger) {
        this.logger = logger;
        lifecycle = new SingleEffectLifecycle<World, WaypointBeamEffect>(
                new SingleEffectLifecycle.Adapter<World, WaypointBeamEffect>() {
                    @Override
                    public WaypointBeamEffect create(World world) {
                        ProbeWaypoint waypoint = active;
                        BeamProbeConfiguration value = configuration;
                        WaypointBeamEffect effect = new WaypointBeamEffect(world,
                                waypoint.x, waypoint.y, waypoint.height,
                                value.getHeight(), value.getWidth(),
                                waypoint.red, waypoint.green, waypoint.blue,
                                value.getAlpha(), value.isThroughWalls(),
                                value.getThroughWallWidth());
                        effect.setLayer(waypoint.layer);
                        return effect;
                    }

                    @Override
                    public void add(World world, WaypointBeamEffect effect) {
                        EffectRender current = renderer(world);
                        current.addEffect(effect);
                        attachedRenderer = current;
                    }

                    @Override
                    public void remove(World world, WaypointBeamEffect effect) {
                        EffectRender owner = attachedRenderer;
                        attachedRenderer = null;
                        if (owner != null) owner.removeEffect(effect);
                        else renderer(world).removeEffect(effect);
                    }

                    @Override
                    public void delete(WaypointBeamEffect effect) {
                        effect.delete();
                    }
                });
    }

    public synchronized void attachIfEnabled(World world, HeadsUpDisplay hud,
                                             BeamProbeConfiguration value,
                                             String serverKey) {
        if (value == null || !value.isEnabled() || world == null
                || world.getWorldRenderer() == null || serverKey == null
                || serverKey.trim().isEmpty()) return;
        configuration = value;
        activate(world, hud, serverKey);
        if (active == null) return;

        if (active.worldBeamVisible && !failed) {
            try {
                EffectRender currentRenderer = renderer(world);
                if (currentRenderer == null) {
                    attachLabel(hud, world);
                    return;
                }
                long nowNanos = System.nanoTime();
                boolean stabilizationDue = stabilization.takeIfDue(nowNanos);
                WaypointBeamEffect previousEffect = lifecycle.attachedEffect();
                if (stabilizationDue && previousEffect != null) {
                    long lastRender = previousEffect.getLastRenderNanos();
                    long renderAgeMillis = lastRender <= 0L ? -1L
                            : Math.max(0L, (nowNanos - lastRender) / 1_000_000L);
                    long renderCount = previousEffect.getRenderCount();
                    String resourcesLive = liveResourceState(previousEffect);
                    lifecycle.detach();
                    logger.info("Post-transfer beam render resources refreshed "
                            + "automatically: serverKey=\"" + activeServerKey
                            + "\", previousRenderCount=" + renderCount
                            + ", previousRenderAgeMs=" + renderAgeMillis
                            + ", previousResourcesLive=" + resourcesLive
                            + ", renderer=" + identity(currentRenderer));
                }
                boolean alreadyAttached = lifecycle.isAttached();
                boolean rendererChanged = alreadyAttached
                        && !lifecycle.isAttachedTo(world, currentRenderer);
                EffectRender previousRenderer = attachedRenderer;
                lifecycle.attach(world, currentRenderer);
                if (rendererChanged && lifecycle.isAttached()) {
                    logger.info("Beam renderer instance changed; active probe was "
                            + "reattached automatically: serverKey=\"" + activeServerKey
                            + "\", previousRenderer=" + identity(previousRenderer)
                            + ", currentRenderer=" + identity(currentRenderer));
                }
                if (!alreadyAttached && lifecycle.isAttached()) {
                    logger.info("Beam probe attached: serverKey=\"" + activeServerKey
                            + "\", name=\"" + active.name + "\", targetWorldX=" + active.x
                            + ", targetWorldY=" + active.y
                            + ", baseHeight=" + active.height
                            + ", layer=" + active.layer + ", color="
                            + active.red + "," + active.green + "," + active.blue + ","
                            + value.getAlpha() + ", width=" + value.getWidth()
                            + ", height=" + value.getHeight()
                            + ", throughWalls=" + value.isThroughWalls()
                            + ", throughWallWidth=" + value.getThroughWallWidth()
                            + ", renderer=" + identity(currentRenderer));
                }
            } catch (Throwable failure) {
                failed = true;
                logger.log(Level.WARNING, "Phase 0 beam probe failed open", failure);
            }
        }
        attachLabel(hud, world);
    }

    private void activate(World world, HeadsUpDisplay hud, String serverKey) {
        if (world == targetWorld && serverKey.equals(activeServerKey) && active != null) return;
        if (activeServerKey != null && !serverKey.equals(activeServerKey)) {
            detachActiveRender("server-bound probe switch");
        }
        ProbeWaypoint waypoint = probes.get(serverKey);
        if (waypoint == null) {
            waypoint = createProbe(world, serverKey);
            probes.put(serverKey, waypoint);
            logger.info("Server-bound Phase 0 waypoint created: serverKey=\""
                    + serverKey + "\", name=\"" + waypoint.name + "\", color="
                    + waypoint.red + "," + waypoint.green + "," + waypoint.blue
                    + ", targetWorldX=" + waypoint.x + ", targetWorldY=" + waypoint.y);
        } else {
            logger.info("Server-bound Phase 0 waypoint restored: serverKey=\""
                    + serverKey + "\", name=\"" + waypoint.name + "\", visible="
                    + waypoint.worldBeamVisible);
        }
        targetWorld = world;
        activeServerKey = serverKey;
        active = waypoint;
        stabilization.schedule(System.nanoTime(), TRANSFER_STABILIZATION_NANOS);
        if (hud != null && labelHud != null && labelHud != hud) {
            detachLabel("HUD replacement during probe activation");
        }
    }

    private ProbeWaypoint createProbe(World world, String serverKey) {
        Phase0ProbeProfile profile = Phase0ProbeProfile.forServer(
                serverKey, configuration);
        return new ProbeWaypoint(
                profile.getName(),
                world.getPlayerPosX() + 8.0f,
                world.getPlayerPosY() + 8.0f,
                world.getPlayerPosH(), world.getPlayerLayer(),
                profile.getRed(), profile.getGreen(), profile.getBlue());
    }

    private void attachLabel(HeadsUpDisplay hud, World world) {
        if (hud == null || targetWorld != world || active == null || labelFailed) return;
        try {
            if (label == null) {
                label = WaypointLabelComponent.attach(hud, world,
                        active.x, active.y, active.height, active.name,
                        active.red, active.green, active.blue, configuration.getAlpha());
                labelHud = hud;
                logger.info("Waypoint label attached: serverKey=\"" + activeServerKey
                        + "\", name=\"" + active.name + "\", targetWorldX="
                        + active.x + ", targetWorldY=" + active.y);
            } else {
                label.updateViewport(hud.getWidth());
            }
        } catch (Throwable failure) {
            labelFailed = true;
            logger.log(Level.WARNING, "Waypoint HUD label failed open", failure);
        }
    }

    public synchronized CompassMarkerSnapshot compassMarker() {
        World world = targetWorld;
        ProbeWaypoint waypoint = active;
        if (world == null || waypoint == null || !configuration.isEnabled()) return null;
        return new CompassMarkerSnapshot(
                world.getPlayerPosX(), world.getPlayerPosY(), world.getPlayerRotX(),
                waypoint.x, waypoint.y,
                waypoint.red, waypoint.green, waypoint.blue,
                waypoint.name, waypoint.worldBeamVisible);
    }

    public synchronized boolean toggleWorldBeam() {
        ProbeWaypoint waypoint = active;
        if (targetWorld == null || waypoint == null || !configuration.isEnabled()) return false;
        waypoint.worldBeamVisible = !waypoint.worldBeamVisible;
        if (!waypoint.worldBeamVisible) {
            detachWorldBeam("compass waypoint marker toggle");
        }
        logger.info("Waypoint world beam toggled from compass marker: serverKey=\""
                + activeServerKey + "\", name=\"" + waypoint.name
                + "\", visible=" + waypoint.worldBeamVisible);
        return waypoint.worldBeamVisible;
    }

    public synchronized void rendererCleared(Object clearedRenderer) {
        if (clearedRenderer == null || attachedRenderer != clearedRenderer) return;
        boolean wasAttached = lifecycle.isAttached();
        lifecycle.invalidateAfterExternalClear();
        attachedRenderer = null;
        failed = false;
        if (wasAttached) {
            logger.info("Beam renderer clear invalidated the active probe; "
                    + "a fresh effect will attach on the next HUD tick: serverKey=\""
                    + activeServerKey + "\"");
        }
    }

    private void detachWorldBeam(String reason) {
        try {
            boolean wasAttached = lifecycle.isAttached();
            lifecycle.detach();
            if (wasAttached) logger.info("Beam probe detached and deleted: reason=" + reason);
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Phase 0 beam cleanup used delete fallback", failure);
        }
    }

    public synchronized void detach(String reason) {
        failed = false;
        labelFailed = false;
        stabilization.cancel();
        detachActiveRender(reason);
        targetWorld = null;
        activeServerKey = null;
        active = null;
    }

    private void detachActiveRender(String reason) {
        detachWorldBeam(reason);
        detachLabel(reason);
    }

    private void detachLabel(String reason) {
        if (label == null) return;
        try {
            label.detach(labelHud);
            logger.info("Waypoint label detached: reason=" + reason);
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Waypoint label cleanup failed open", failure);
        }
        label = null;
        labelHud = null;
    }

    private static EffectRender renderer(World world) {
        return world.getWorldRenderer().getEffectRenderer();
    }

    private static String identity(Object value) {
        return value == null ? "null" : value.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    private static String liveResourceState(WaypointBeamEffect effect) {
        try {
            return Boolean.toString(effect.hasLiveRenderResources());
        } catch (Throwable failure) {
            return "unavailable(" + failure.getClass().getSimpleName() + ")";
        }
    }

    private static final class ProbeWaypoint {
        private final String name;
        private final float x;
        private final float y;
        private final float height;
        private final int layer;
        private final float red;
        private final float green;
        private final float blue;
        private boolean worldBeamVisible = true;

        private ProbeWaypoint(String name, float x, float y, float height, int layer,
                              float red, float green, float blue) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.height = height;
            this.layer = layer;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }
}
