package org.waypoints.next.integration;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CellRenderable;
import com.wurmonline.client.renderer.gui.CompassMarkerClusterHit;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.DeedSearchWindowBridge;
import com.wurmonline.client.renderer.gui.DeedInformationWindowBridge;
import com.wurmonline.client.renderer.gui.CustomMapMarkWindowBridge;
import com.wurmonline.client.renderer.gui.MiniMapWindowBridge;
import com.wurmonline.client.renderer.gui.ServerMapWindowBridge;
import com.wurmonline.client.renderer.gui.WaypointClusterPickerWindowBridge;
import com.wurmonline.client.renderer.gui.WaypointManagerWindowBridge;
import com.wurmonline.client.renderer.gui.SurroundingsWindowBridge;
import org.waypoints.api.MarkResult;
import org.waypoints.api.NavigationRequest;
import org.waypoints.api.ObjectMarkRequest;
import org.waypoints.api.ObjectMarkerType;
import org.waypoints.api.WaypointerApi;
import org.waypoints.api.WaypointerCapability;
import org.waypoints.api.WaypointerService;
import org.waypoints.api.WurmObjectKind;
import org.waypoints.api.WurmObjectRef;
import org.waypoints.api.WurmObjectSnapshot;
import org.waypoints.next.model.CapturedServerSelection;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.map.Deed;
import org.waypoints.next.map.SklotopolisMapService;
import org.waypoints.next.render.BeamProbeConfiguration;
import org.waypoints.next.render.CompassMarkerSnapshot;
import org.waypoints.next.render.NavigationRenderFrame;
import org.waypoints.next.render.StaticNavigationController;
import org.waypoints.next.render.WurmBeamProbeController;
import org.waypoints.next.render.WaypointRenderProfiler;
import org.waypoints.next.render.WaypointRenderRuntimeAccess;
import org.waypoints.next.render.WaypointRenderRuntimeBridge;
import com.wurmonline.client.renderer.effects.GroundNavigationRouteEffect;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;
import org.waypoints.next.navigation.HighwayTileIndex;
import org.waypoints.next.navigation.SklotopolisHighwayService;
import org.waypoints.next.service.ServerIdentityResolver;
import org.waypoints.next.service.ServerIdentitySession;
import org.waypoints.next.service.WaypointManagerQuery;
import org.waypoints.next.service.WaypointManagerSnapshot;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.source.ParsedCoordinate;
import org.waypoints.next.ui.WaypointEditData;
import org.waypoints.next.ui.WaypointManagerContext;
import org.waypoints.next.ui.WaypointManagerController;
import org.waypoints.next.ui.SurroundingsController;
import org.waypoints.next.surroundings.SurroundingKey;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.surroundings.SurroundingsSnapshot;
import org.waypoints.next.surroundings.ScannerProfiles;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.UUID;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Locale;
import com.wurmonline.shared.constants.PlayerAction;

/** Client lifecycle coordinator. All hook entry points preserve fail-open behavior. */
public final class WurmWaypointerRuntime {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Runtime");
    private static final ServerIdentitySession SERVER =
            new ServerIdentitySession(new ServerIdentityResolver());
    private static final StaticWaypointRuntime STATIC_WAYPOINTS =
            new StaticWaypointRuntime(LOGGER);
    private static final DeedProviderRuntime DEEDS =
            new DeedProviderRuntime(LOGGER, STATIC_WAYPOINTS);
    private static final SklotopolisHighwayService HIGHWAYS =
            new SklotopolisHighwayService(LOGGER);
    private static final SklotopolisMapService SERVER_MAPS =
            new SklotopolisMapService(LOGGER);
    private static final StaticNavigationController STATIC_NAVIGATION =
            new StaticNavigationController(LOGGER, HIGHWAYS);
    private static final NavigationRouteVisualStyleSettings NAVIGATION_SETTINGS =
            NavigationRouteVisualStyleSettings.installed();
    private static final VanillaLandmarkRuntime VANILLA_LANDMARKS =
            new VanillaLandmarkRuntime(LOGGER);
    private static final LootMapRuntime LOOT_MAPS = new LootMapRuntime(LOGGER);
    private static final ArchaeologyRuntime ARCHAEOLOGY =
            new ArchaeologyRuntime(LOGGER);
    private static final SurroundingsRuntime SURROUNDINGS =
            new SurroundingsRuntime(LOGGER);
    private static final ConcurrentLinkedQueue<UUID> EXTERNAL_NAVIGATION_REQUESTS =
            new ConcurrentLinkedQueue<UUID>();
    private static final List<DynamicWaypointProvider> DYNAMIC_WAYPOINTS =
            Collections.unmodifiableList(Arrays.<DynamicWaypointProvider>asList(
                    LOOT_MAPS, ARCHAEOLOGY, SURROUNDINGS));
    private static final ArchaeologyChimePlayer ARCHAEOLOGY_CHIMES =
            new ArchaeologyChimePlayer(LOGGER);
    private static final WaypointRenderRuntimeAccess RENDER_ACCESS =
            new WaypointRenderRuntimeAccess() {
                @Override public NavigationRenderFrame currentNavigationFrame() {
                    return WurmWaypointerRuntime.currentNavigationFrame();
                }

                @Override public CompassMarkerSnapshot currentCompassMarker() {
                    return WurmWaypointerRuntime.currentCompassMarker();
                }

                @Override public void chooseCompassWaypoint(
                        NavigationTargetKey key) {
                    WurmWaypointerRuntime.compassWaypointMarkerClicked(key);
                }
            };

    private static volatile BeamProbeConfiguration configuration =
            BeamProbeConfiguration.disabled();
    private static volatile HeadsUpDisplay hud;
    private static volatile ServerIdentity identity;
    private static volatile WurmBeamProbeController beam;
    private static volatile World confirmedWorld;
    private static volatile String confirmedWorldName = "";
    private static volatile boolean awaitingServerInformation = true;
    private static volatile WaypointClientConfiguration waypointConfiguration =
            WaypointClientConfiguration.defaults();
    private static final PickableUnit[] NO_SCANNER_OUTLINES = new PickableUnit[0];
    private static volatile PickableUnit[] scannerOutlineTargets =
            NO_SCANNER_OUTLINES;
    private static volatile long scannerOutlineRevision = Long.MIN_VALUE;
    private static volatile int scannerOutlinePlayerTileX = Integer.MIN_VALUE;
    private static volatile int scannerOutlinePlayerTileY = Integer.MIN_VALUE;
    private static volatile int scannerOutlinePlayerLayer = Integer.MIN_VALUE;
    private static final WaypointManagerController MANAGER_CONTROLLER =
            new WaypointManagerController() {
                @Override public WaypointManagerContext context() {
                    return STATIC_WAYPOINTS.managerContext(hud, identity);
                }
                @Override public WaypointManagerSnapshot snapshot(WaypointManagerQuery query) {
                    return STATIC_WAYPOINTS.managerSnapshot(
                            query, managerSupplementalRecords());
                }
                @Override public WaypointEditData editData(UUID id) {
                    return STATIC_WAYPOINTS.editData(id);
                }
                @Override public ParsedCoordinate preview(String input) {
                    return STATIC_WAYPOINTS.previewCoordinate(input);
                }
                @Override public void livePreview(UUID editingId, String name,
                                                  org.waypoints.next.model.WaypointCoordinate coordinate,
                                                  MarkerStyle markerStyle) {
                    STATIC_NAVIGATION.previewManagerDraft(
                            editingId, name, coordinate,
                            STATIC_WAYPOINTS.managerPreviewStyle(
                                    editingId, markerStyle));
                }
                @Override public void clearLivePreview() {
                    STATIC_NAVIGATION.clearManagerDraft();
                }
                @Override public String clipboardText() {
                    return STATIC_WAYPOINTS.clipboardText();
                }
                @Override public void addHere(String name, MarkerStyle markerStyle,
                                              int arrivalRadiusMetres,
                                              int lifetimeMinutes) {
                    STATIC_WAYPOINTS.addHereFromManager(name, markerStyle,
                            arrivalRadiusMetres, lifetimeMinutes, hud, identity);
                }
                @Override public void addCoordinates(String name, String input,
                                                     MarkerStyle markerStyle,
                                                     int arrivalRadiusMetres,
                                                     int lifetimeMinutes) {
                    STATIC_WAYPOINTS.addCoordinatesFromManager(
                        name, input, markerStyle, arrivalRadiusMetres,
                            lifetimeMinutes, hud, identity);
                }
                @Override public void editStatic(UUID id, String name, String input,
                                                 MarkerStyle markerStyle,
                                                 int arrivalRadiusMetres,
                                                 int lifetimeMinutes) {
                    STATIC_WAYPOINTS.editStaticFromManager(
                            id, name, input, markerStyle,
                            arrivalRadiusMetres, lifetimeMinutes, hud);
                }
                @Override public void duplicate(UUID id) {
                    STATIC_WAYPOINTS.duplicateFromManager(id, hud);
                }
                @Override public void share(UUID id) {
                    STATIC_WAYPOINTS.shareFromManager(id, hud);
                }
                @Override public void importSharedClipboard() {
                    STATIC_WAYPOINTS.importSharedClipboardFromManager(hud, identity);
                }
                @Override public boolean isNavigatorActive(UUID id) {
                    return STATIC_NAVIGATION.isNavigatorActive(id);
                }
                @Override public boolean toggleNavigator(UUID id) {
                    NavigationTarget target = STATIC_NAVIGATION.toggleNavigator(id);
                    if (target == null) {
                        event("Navigator requires an enabled waypoint on the current server.");
                        return false;
                    }
                    boolean active = target.isNavigatorActive();
                    event((active ? "Navigator started: " : "Navigator stopped: ")
                            + oneLine(target.getName()) + ".");
                    return active;
                }
                @Override public void setEnabled(UUID id, boolean enabled) {
                    if (LOOT_MAPS.setEnabled(id, enabled)) {
                        STATIC_NAVIGATION.managerEnabledChanged(id, enabled);
                        event((enabled ? "Enabled: " : "Disabled: ")
                                + "active Loot Map waypoint. Hunt progress was kept.");
                    } else if (VANILLA_LANDMARKS.setEnabled(id, enabled)) {
                        event((enabled ? "Enabled: " : "Disabled: ")
                                + VANILLA_LANDMARKS.displayName(id)
                                + ". Vanilla landmark state saved for this server.");
                    } else {
                        STATIC_WAYPOINTS.setEnabledFromManager(id, enabled, hud);
                        STATIC_NAVIGATION.managerEnabledChanged(id, enabled);
                    }
                }
                @Override public void setEnabled(List<UUID> ids, boolean enabled) {
                    List<UUID> ordinary = new ArrayList<UUID>();
                    int vanilla = 0;
                    int lootMaps = 0;
                    for (UUID id : ids) {
                        if (LOOT_MAPS.setEnabled(id, enabled)) {
                            lootMaps++;
                            STATIC_NAVIGATION.managerEnabledChanged(id, enabled);
                        } else if (VANILLA_LANDMARKS.setEnabled(id, enabled)) vanilla++;
                        else ordinary.add(id);
                    }
                    if (!ordinary.isEmpty()) {
                        STATIC_WAYPOINTS.setEnabledFromManager(
                                ordinary, enabled, hud);
                        for (UUID id : ordinary) {
                            STATIC_NAVIGATION.managerEnabledChanged(id, enabled);
                        }
                    }
                    if (vanilla > 0) {
                        event((enabled ? "Enabled " : "Disabled ") + vanilla
                                + " vanilla landmark(s) for this server.");
                    }
                    if (lootMaps > 0) {
                        event((enabled ? "Enabled " : "Disabled ") + lootMaps
                                + " Loot Map waypoint(s). Hunt progress was kept.");
                    }
                }
                @Override public void delete(UUID id) {
                    STATIC_WAYPOINTS.deleteFromManager(id, hud);
                }
                @Override public void exportAll() {
                    STATIC_WAYPOINTS.exportFromManager(hud);
                }
                @Override public void importAll() {
                    STATIC_WAYPOINTS.importFromManager(hud);
                }
                @Override public void openSurroundings() {
                    WurmWaypointerRuntime.openSurroundings();
                }
                @Override public long revision() {
                    return (STATIC_WAYPOINTS.revision() * 31L
                            + VANILLA_LANDMARKS.revision()) * 31L
                            + LOOT_MAPS.revision();
                }
                @Override public void reportFailure(String operation, Throwable failure) {
                    STATIC_WAYPOINTS.reportManagerFailure(operation, failure, hud);
                }
            };

    private static List<org.waypoints.next.model.WaypointRecord>
    managerSupplementalRecords() {
        List<org.waypoints.next.model.WaypointRecord> records =
                new ArrayList<org.waypoints.next.model.WaypointRecord>();
        records.addAll(VANILLA_LANDMARKS.records());
        records.addAll(LOOT_MAPS.records());
        return records;
    }
    private static final SurroundingsController SURROUNDINGS_CONTROLLER =
            new SurroundingsController() {
                @Override public SurroundingsSnapshot snapshot(SurroundingsQuery query) {
                    SURROUNDINGS.reconcileWaypoints(
                            STATIC_WAYPOINTS.surroundingsWaypointKeys());
                    World world = hud == null ? null : hud.getWorld();
                    double x = world == null ? 0.0d : world.getPlayerPosX();
                    double y = world == null ? 0.0d : world.getPlayerPosY();
                    return SURROUNDINGS.snapshot(query, x, y);
                }
                @Override public void setWaypoint(SurroundingKey key, boolean enabled) {
                    org.waypoints.next.surroundings.SurroundingEntry entry =
                            SURROUNDINGS.find(key);
                    int changed = STATIC_WAYPOINTS.setSurroundingsWaypoints(
                            entry == null ? Collections.<org.waypoints.next.surroundings.SurroundingEntry>emptyList()
                                    : Collections.singletonList(entry),
                            Collections.singletonList(key), enabled, hud, identity);
                    SURROUNDINGS.reconcileWaypoints(
                            STATIC_WAYPOINTS.surroundingsWaypointKeys());
                    event((enabled ? "Created " : "Cleared ") + changed
                            + " 15-minute surroundings waypoint(s).");
                }
                @Override public void setWaypoints(
                        java.util.Collection<SurroundingKey> keys, boolean enabled) {
                    int changed = STATIC_WAYPOINTS.setSurroundingsWaypoints(
                            SURROUNDINGS.findAll(keys), keys, enabled, hud, identity);
                    SURROUNDINGS.reconcileWaypoints(
                            STATIC_WAYPOINTS.surroundingsWaypointKeys());
                    event((enabled ? "Created " : "Cleared ") + changed
                            + " 15-minute surroundings waypoint(s).");
                }
                @Override public void clearAllWaypoints() {
                    int changed = STATIC_WAYPOINTS.clearSurroundingsWaypoints();
                    SURROUNDINGS.reconcileWaypoints(
                            STATIC_WAYPOINTS.surroundingsWaypointKeys());
                    event("Cleared " + changed + " surroundings waypoint(s).");
                }
                @Override public void openWaypointManager() {
                    WurmWaypointerRuntime.openWaypointManager();
                }
                @Override public long revision() {
                    SURROUNDINGS.reconcileWaypoints(
                            STATIC_WAYPOINTS.surroundingsWaypointKeys());
                    return SURROUNDINGS.revision();
                }
                @Override public void reportFailure(String operation, Throwable failure) {
                    LOGGER.log(Level.WARNING, "Surroundings " + oneLine(operation)
                            + " failed", failure);
                    event("Surroundings " + oneLine(operation)
                            + " failed; see client.log.");
                }
            };

    private static final WaypointerService EXTERNAL_API = new WaypointerService() {
        @Override public int apiVersion() { return WaypointerApi.API_VERSION; }

        @Override public Set<WaypointerCapability> capabilities() {
            return EnumSet.allOf(WaypointerCapability.class);
        }

        @Override public MarkResult markObject(ObjectMarkRequest request) {
            HeadsUpDisplay currentHud = hud;
            World world = currentHud == null ? null : currentHud.getWorld();
            if (world == null || identity == null) return MarkResult.failure(
                    MarkResult.Status.WAYPOINTER_NOT_READY,
                    "Waypointer has no confirmed world yet");
            SurroundingEntry entry = findExternalSubject(request.getSubject());
            WurmObjectSnapshot snapshot = request.getSnapshot();
            if (snapshot == null) snapshot = liveHudSnapshot(
                    currentHud, request.getSubject());
            if (entry == null && snapshot != null) {
                entry = externalSnapshotEntry(request.getSubject(),
                        snapshot, java.time.Instant.now());
            }
            if (entry == null) return MarkResult.failure(
                    MarkResult.Status.SUBJECT_NOT_FOUND,
                    "object is not present in Waypointer's live catalog and "
                            + "the caller supplied no position snapshot");
            UUID id = STATIC_WAYPOINTS.upsertExternalObjectWaypoint(entry,
                    request.getOwnerId(), request.getMarkerKey(),
                    externalMarkerStyle(request.getMarkerType(), entry.getKind()),
                    request.getMaximumLifetimeSeconds(), currentHud, identity,
                    java.time.Instant.now());
            SURROUNDINGS.reconcileWaypoints(
                    STATIC_WAYPOINTS.surroundingsWaypointKeys());
            if (request.getNavigation() == NavigationRequest.ACTIVATE) {
                EXTERNAL_NAVIGATION_REQUESTS.add(id);
            }
            return MarkResult.success(id);
        }

        @Override public int subjectVanished(WurmObjectRef subject) {
            return removeExternalSubjectMarks(subject);
        }

        @Override public boolean removeOwnedMarker(String ownerId, UUID markerId) {
            boolean navigatorStopped = STATIC_NAVIGATION.isNavigatorActive(markerId);
            boolean removed = STATIC_WAYPOINTS.deleteOwnedExternalMarker(
                    ownerId, markerId);
            if (!removed) return false;
            STATIC_NAVIGATION.managerEnabledChanged(markerId, false);
            SURROUNDINGS.reconcileWaypoints(
                    STATIC_WAYPOINTS.surroundingsWaypointKeys());
            if (navigatorStopped) event("Navigator stopped: external marker removed.");
            return true;
        }

        @Override public boolean setNavigation(String ownerId, UUID markerId,
                                               boolean active) {
            if (!STATIC_WAYPOINTS.isOwnedExternalMarker(ownerId, markerId)) {
                return false;
            }
            if (active) EXTERNAL_NAVIGATION_REQUESTS.add(markerId);
            else STATIC_NAVIGATION.stopNavigator(markerId);
            return true;
        }
    };

    private WurmWaypointerRuntime() {
    }

    public static void configure(BeamProbeConfiguration value) {
        WaypointRenderRuntimeBridge.bind(RENDER_ACCESS);
        WaypointerApi.installRuntime(EXTERNAL_API);
        configuration = value == null ? BeamProbeConfiguration.disabled() : value;
        LOGGER.info("Runtime configuration: " + configuration.diagnosticSummary());
    }

    public static void configure(BeamProbeConfiguration beamValue,
                                 WaypointClientConfiguration waypointValue) {
        configure(beamValue);
        waypointConfiguration = waypointValue == null
                ? WaypointClientConfiguration.defaults() : waypointValue;
        STATIC_WAYPOINTS.configureAndLoad(waypointConfiguration);
        DEEDS.configure(waypointConfiguration);
        STATIC_NAVIGATION.configure(waypointConfiguration);
        STATIC_NAVIGATION.setNavigationRouteVisualStyleSink(
                new java.util.function.Consumer<NavigationRouteVisualStyle>() {
                    @Override public void accept(NavigationRouteVisualStyle style) {
                        try {
                            NAVIGATION_SETTINGS.save(style);
                            LOGGER.info("Navigation route visual style saved: " + style);
                        } catch (java.io.IOException | RuntimeException failure) {
                            LOGGER.log(Level.WARNING,
                                    "Unable to save navigation route visual style",
                                    failure);
                            event("Navigation signal changed, but the setting could not be saved; see client.log.");
                        }
                    }
                });
        SERVER_MAPS.configure(waypointConfiguration.isServerMapEnabled(),
                waypointConfiguration.getServerMapCacheDirectory(),
                waypointConfiguration.getServerMapSyncMinutes(), false);
        VANILLA_LANDMARKS.configure(waypointConfiguration);
        for (DynamicWaypointProvider provider : DYNAMIC_WAYPOINTS) {
            provider.configure(waypointConfiguration);
        }
    }

    public static void capture(CapturedServerSelection selection) {
        try {
            SERVER.capture(selection);
            identity = null;
            LOGGER.info("Server endpoint captured: " + describe(selection));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Server selection capture failed open", failure);
        }
    }

    public static void hudReady(HeadsUpDisplay nextHud) {
        try {
            if (hud != nextHud) {
                detachBeam("HUD replacement/init");
                STATIC_NAVIGATION.detach("HUD replacement/init");
                WaypointClusterPickerWindowBridge.detach(hud, "HUD replacement/init");
                WaypointManagerWindowBridge.detach(hud, "HUD replacement/init");
                SurroundingsWindowBridge.detach(hud, "HUD replacement/init");
                DeedSearchWindowBridge.detach(hud, "HUD replacement/init");
                DeedInformationWindowBridge.detach(hud, "HUD replacement/init");
                CustomMapMarkWindowBridge.detach(hud, "HUD replacement/init");
                MiniMapWindowBridge.detach(hud, "HUD replacement/init");
                ServerMapWindowBridge.resetAll();
            }
            hud = nextHud;
            identity = null;
            SERVER.reconnecting();
            if (nextHud == null || confirmedWorld != nextHud.getWorld()) {
                awaitingServerInformation = true;
            }
            LOGGER.info("HUD ready: instance=" + identityOf(nextHud)
                    + ", beamEnabled=" + configuration.isEnabled());
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "HUD initialization probe failed open", failure);
        }
    }

    public static void hudTick(HeadsUpDisplay currentHud) {
        try {
            if (currentHud == null) return;
            if (hud != currentHud) hudReady(currentHud);
            World world = currentHud.getWorld();
            if (world == null || awaitingServerInformation || confirmedWorld != world) return;
            if (identity == null && !confirmedWorldName.isEmpty()) {
                identity = SERVER.resolve(confirmedWorldName);
                LOGGER.info("Server identity resolved: " + describe(identity));
                STATIC_WAYPOINTS.confirmCurrentServer(identity);
            }
            VANILLA_LANDMARKS.bind(identity);
            SERVER_MAPS.activate(identity);
            MiniMapWindowBridge.tick(currentHud);
            DEEDS.bind(identity);
            SURROUNDINGS.updateDeeds(serverMapSnapshot());
            DeedSearchWindowBridge.refresh(currentHud, serverMapSnapshot());
            if (configuration.isEnabled()) {
                String serverKey = phase0ServerKey(identity, confirmedWorldName);
                if (!serverKey.isEmpty()) {
                    beam().attachIfEnabled(world, currentHud, configuration, serverKey);
                }
            }
            STATIC_WAYPOINTS.expireDue(System.currentTimeMillis());
            ArchaeologyRuntime.EventContext archaeologyContext =
                    new ArchaeologyRuntime.EventContext(
                    org.waypoints.next.archaeology.ArchaeologyTileCoordinates.centerOf(
                            world.getPlayerCurrentTileX()),
                    org.waypoints.next.archaeology.ArchaeologyTileCoordinates.centerOf(
                            world.getPlayerCurrentTileY()),
                    world.getPlayerPosH(), world.getPlayerLayer() < 0
                    ? org.waypoints.next.model.WaypointLayer.CAVE
                    : org.waypoints.next.model.WaypointLayer.SURFACE,
                    identity, world.getUsername(), java.time.Instant.now(),
                    waypointConfiguration.getMapBounds());
            ARCHAEOLOGY.bind(archaeologyContext);
            SURROUNDINGS.bind(identity, world.getUsername());
            SURROUNDINGS.tick(System.currentTimeMillis());
            refreshScannerOutlineTargets(world);
            STATIC_NAVIGATION.tick(world, currentHud, identity,
                    world.getUsername(), combineDynamicWaypoints(
                            VANILLA_LANDMARKS.combine(
                                    STATIC_WAYPOINTS.revisionSnapshot())));
            startRequestedNavigation();
            STATIC_WAYPOINTS.flushEvents(currentHud);
            flushDynamicMessages();
            ArchaeologyRuntime.SoundCue archaeologySound;
            while ((archaeologySound = ARCHAEOLOGY.pollSoundCue()) != null) {
                ARCHAEOLOGY_CHIMES.enqueue(archaeologySound);
            }
            while (LOOT_MAPS.pollDigChime()) {
                ARCHAEOLOGY_CHIMES.enqueueLootMapDig();
            }
            ARCHAEOLOGY_CHIMES.tick(world);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "HUD tick probe failed open", failure);
        }
    }

    public static void compassClicked() {
        try {
            HeadsUpDisplay current = hud;
            if (current == null) throw new IllegalStateException("HUD is not ready yet");
            WaypointClusterPickerWindowBridge.detach(current, "ordinary compass click");
            SurroundingsWindowBridge.detach(current, "ordinary compass click");
            boolean visible = WaypointManagerWindowBridge.toggle(
                    current, MANAGER_CONTROLLER);
            LOGGER.info("Compass click " + (visible ? "opened" : "closed")
                    + " the Phase 1 Waypoint Manager");
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass click callback failed open", failure);
        }
    }

    public static boolean handleConsoleCommand(String command, String[] arguments) {
        try {
            if (handleDeedCommand(command, arguments)) return true;
            if (handleSurroundingsCommand(command, arguments)) return true;
            if (handleNavigatorCommand(command, arguments)) return true;
            if (ARCHAEOLOGY.handleConsoleCommand(command, arguments)) return true;
            if (LOOT_MAPS.handleConsoleCommand(command, arguments)) return true;
            return STATIC_WAYPOINTS.handleCommand(command, arguments, hud, identity);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Waypoint console hook failed open", failure);
            return false;
        }
    }

    private static boolean handleDeedCommand(String command,
                                             String[] arguments) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (!"wp".equalsIgnoreCase(normalized)
                && !"waypoint".equalsIgnoreCase(normalized)) return false;
        String[] values = WaypointCommandArguments.withoutRepeatedCommand(
                command, arguments);
        if (values.length == 0 || !"deeds".equalsIgnoreCase(values[0])) return false;
        String operation = values.length < 2 ? "open" : values[1];
        if ("open".equalsIgnoreCase(operation)) {
            HeadsUpDisplay current = hud;
            if (current == null) event("HUD is not ready yet.");
            else DeedSearchWindowBridge.open(current, serverMapSnapshot());
        } else if ("refresh".equalsIgnoreCase(operation)) {
            DEEDS.refreshNow();
            event("Deed provider refresh queued in the background.");
        } else if ("status".equalsIgnoreCase(operation)) {
            event("Deed provider: " + DEEDS.status() + ".");
        } else {
            event("Usage: /wp deeds [open | refresh | status]");
        }
        return true;
    }

    private static boolean handleSurroundingsCommand(String command,
                                                      String[] arguments) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if ("surroundings".equalsIgnoreCase(normalized)
                || "nearby".equalsIgnoreCase(normalized)) {
            openSurroundings();
            return true;
        }
        if (!"wp".equalsIgnoreCase(normalized)
                && !"waypoint".equalsIgnoreCase(normalized)) return false;
        String[] values = WaypointCommandArguments.withoutRepeatedCommand(
                command, arguments);
        if (values.length > 0 && "scan".equalsIgnoreCase(values[0])) {
            handleScannerCommand(values);
            return true;
        }
        if (values.length == 0 || (!"surroundings".equalsIgnoreCase(values[0])
                && !"nearby".equalsIgnoreCase(values[0]))) return false;
        openSurroundings();
        return true;
    }

    private static void handleScannerCommand(String[] values) {
        String operation = values.length < 2 ? "status"
                : values[1].trim().toLowerCase(Locale.ENGLISH);
        if (ScannerProfiles.find(operation) != null) {
            int matches = SURROUNDINGS.activateScanner(operation);
            invalidateScannerOutlineTargets();
            event("Scanner profile " + operation + " enabled: " + matches
                    + " loaded match(es). " + scannerPresentationSummary());
            return;
        }
        if ("off".equals(operation) || "stop".equals(operation)) {
            SURROUNDINGS.deactivateScanner();
            invalidateScannerOutlineTargets();
            event("Scanner is off.");
            return;
        }
        if ("status".equals(operation)) {
            event("Scanner profile: " + SURROUNDINGS.scannerProfileId() + "; "
                    + SURROUNDINGS.scannerMatchCount() + " loaded match(es); "
                    + scannerPresentationSummary());
            return;
        }
        if ("profiles".equals(operation) || "list".equals(operation)) {
            event("Scanner profiles: " + ScannerProfiles.ids() + ".");
            return;
        }
        if ("window".equals(operation) || "show".equals(operation)) {
            openSurroundings();
            return;
        }
        if ("notify".equals(operation) || "notifications".equals(operation)) {
            Boolean enabled = booleanCommand(values, 2);
            if (enabled != null) {
                SURROUNDINGS.setScannerNotificationsEnabled(enabled.booleanValue());
            }
            event("Scanner notifications are "
                    + (SURROUNDINGS.isScannerNotificationsEnabled()
                    ? "on." : "off."));
            return;
        }
        if ("outline".equals(operation) || "outlines".equals(operation)) {
            Boolean enabled = booleanCommand(values, 2);
            if (enabled != null) {
                SURROUNDINGS.setScannerOutlinesEnabled(enabled.booleanValue());
                invalidateScannerOutlineTargets();
            }
            event("Scanner outlines are "
                    + (SURROUNDINGS.isScannerOutlinesEnabled() ? "on" : "off")
                    + "; at most " + SURROUNDINGS.maximumOutlines()
                    + " nearest objects within "
                    + SURROUNDINGS.outlineDistanceMetres() + "m.");
            return;
        }
        if ("exclude".equals(operation) || "unexclude".equals(operation)) {
            List<String> fragments = scannerNameFragments(values, 2);
            if (fragments.isEmpty()) {
                event("Usage: /wp scan " + operation
                        + " <name fragment>[, <name fragment>...]");
                return;
            }
            int changed = 0;
            for (String fragment : fragments) {
                boolean one = "exclude".equals(operation)
                        ? SURROUNDINGS.addScannerExcludedName(fragment)
                        : SURROUNDINGS.removeScannerExcludedName(fragment);
                if (one) changed++;
            }
            invalidateScannerOutlineTargets();
            event("Scanner minus-name rules changed: " + changed + "; active="
                    + scannerExcludedNamesLabel() + ".");
            return;
        }
        if ("excludes".equals(operation)) {
            event("Scanner minus-name rules: " + scannerExcludedNamesLabel() + ".");
            return;
        }
        if ("clear-excludes".equals(operation)) {
            int changed = SURROUNDINGS.clearScannerExcludedNames();
            invalidateScannerOutlineTargets();
            event("Cleared " + changed + " Scanner minus-name rule(s).");
            return;
        }
        event("Usage: /wp scan uniques|treasure|animals|off|status|profiles; "
                + "/wp scan exclude|unexclude <name[, name...]>; "
                + "/wp scan excludes|clear-excludes; "
                + "/wp scan notify|outline on|off|status; /wp scan window");
    }

    private static Boolean booleanCommand(String[] values, int index) {
        if (values.length <= index || "status".equalsIgnoreCase(values[index])) {
            return null;
        }
        if ("on".equalsIgnoreCase(values[index])) return Boolean.TRUE;
        if ("off".equalsIgnoreCase(values[index])) return Boolean.FALSE;
        return null;
    }

    static List<String> scannerNameFragments(String[] values, int start) {
        String joined = WaypointCommandArguments.join(values, start, values.length);
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for (String part : joined.split("[,;]+")) {
            String clean = part.trim();
            if (!clean.isEmpty()) result.add(clean);
        }
        return new ArrayList<String>(result);
    }

    private static String scannerExcludedNamesLabel() {
        Collection<String> excluded = SURROUNDINGS.scannerExcludedNames();
        return excluded.isEmpty() ? "none" : excluded.toString();
    }

    private static String scannerPresentationSummary() {
        return "notifications " + (SURROUNDINGS.isScannerNotificationsEnabled()
                ? "on" : "off") + "; outlines "
                + (SURROUNDINGS.isScannerOutlinesEnabled() ? "on" : "off")
                + " (" + SURROUNDINGS.maximumOutlines() + " nearest / "
                + SURROUNDINGS.outlineDistanceMetres() + "m); minus names "
                + scannerExcludedNamesLabel() + ".";
    }

    private static boolean handleNavigatorCommand(String command,
                                                   String[] arguments) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (!"wp".equalsIgnoreCase(normalized)
                && !"waypoint".equalsIgnoreCase(normalized)) return false;
        String[] values = WaypointCommandArguments.withoutRepeatedCommand(
                command, arguments);
        if (values.length == 0 || !"nav".equalsIgnoreCase(values[0])) return false;
        if (values.length >= 2 && "pulse".equalsIgnoreCase(values[1])) {
            String operation = values.length < 3 ? "status" : values[2];
            if ("off".equalsIgnoreCase(operation)) {
                STATIC_NAVIGATION.setNavigationPulseEnabled(false);
                event("Navigation signal selected: Solid.");
            } else if ("on".equalsIgnoreCase(operation)) {
                STATIC_NAVIGATION.setNavigationPulseEnabled(true);
                event("Navigation signal selected: Pulse.");
            } else if ("status".equalsIgnoreCase(operation)) {
                event("Navigation pulse is "
                        + (STATIC_NAVIGATION.isNavigationPulseEnabled()
                        ? "on" : "off") + ".");
            } else {
                event("Usage: /wp nav pulse on | off | status");
            }
            return true;
        }
        if (values.length >= 2 && "style".equalsIgnoreCase(values[1])) {
            String requested = values.length < 3 ? "status" : values[2];
            NavigationRouteVisualStyle style = navigationRouteVisualStyle(requested);
            if ("status".equalsIgnoreCase(requested)) {
                event("Navigation signal is " + navigationRouteVisualStyleLabel(
                        STATIC_NAVIGATION.getNavigationRouteVisualStyle()) + ".");
            } else if (style != null) {
                STATIC_NAVIGATION.selectNavigationRouteVisualStyle(style);
                event("Navigation signal selected: "
                        + navigationRouteVisualStyleLabel(style) + ".");
            } else {
                event("Usage: /wp nav style pulse | solid | moving | status");
            }
            return true;
        }
        NavigationRenderFrame current = currentNavigationFrame();
        if (current == null || current.getSnapshot() == null) {
            event("Navigator is not ready yet.");
            return true;
        }
        NavigationTarget active = current.getSnapshot().getActiveNavigator();
        if (values.length == 1) {
            event(active == null ? "Navigator is stopped."
                    : "Navigator target: " + active.getName() + " ["
                    + active.getKey().getWaypointId() + "].");
            return true;
        }
        String requested = WaypointCommandArguments.join(values, 1, values.length);
        if ("off".equalsIgnoreCase(requested)
                || "stop".equalsIgnoreCase(requested)) {
            if (active == null) event("Navigator is already stopped.");
            else {
                STATIC_NAVIGATION.toggleNavigator(active.getKey());
                event("Navigator stopped: " + active.getName() + ".");
            }
            return true;
        }
        NavigationTarget target = findNavigationTarget(
                current.getSnapshot().getTargets(), requested);
        if (target == null) {
            event("No unique current waypoint matches '" + requested
                    + "'. Use its full UUID.");
            return true;
        }
        NavigationTarget changed = STATIC_NAVIGATION.toggleNavigator(target.getKey());
        event(changed != null && changed.isNavigatorActive()
                ? "Navigator started: " + target.getName() + "."
                : "Navigator stopped: " + target.getName() + ".");
        return true;
    }

    private static NavigationRouteVisualStyle navigationRouteVisualStyle(
            String value) {
        if ("pulse".equalsIgnoreCase(value)) return NavigationRouteVisualStyle.PULSE;
        if ("solid".equalsIgnoreCase(value)) return NavigationRouteVisualStyle.SOLID;
        if ("moving".equalsIgnoreCase(value)
                || "moving_dashes".equalsIgnoreCase(value)) {
            return NavigationRouteVisualStyle.MOVING_DASHES;
        }
        return null;
    }

    private static String navigationRouteVisualStyleLabel(
            NavigationRouteVisualStyle style) {
        return style == NavigationRouteVisualStyle.PULSE ? "Pulse"
                : style == NavigationRouteVisualStyle.SOLID ? "Solid" : "Moving";
    }

    private static NavigationTarget findNavigationTarget(
            List<NavigationTarget> targets, String requested) {
        UUID id = null;
        try { id = UUID.fromString(requested); }
        catch (IllegalArgumentException ignored) { }
        NavigationTarget match = null;
        for (NavigationTarget target : targets) {
            boolean matches = id == null
                    ? target.getName().equalsIgnoreCase(requested)
                    : target.getKey().getWaypointId().equals(id);
            if (!matches) continue;
            if (match != null) return null;
            match = target;
        }
        return match;
    }

    /** Called before Event text is displayed; never suppresses the original message. */
    public static void observeEvent(String tab, String text) {
        try {
            HeadsUpDisplay currentHud = hud;
            World world = currentHud == null ? null : currentHud.getWorld();
            if (world == null) return;
            WaypointLayer playerLayer = world.getPlayerLayer() < 0
                    ? WaypointLayer.CAVE : WaypointLayer.SURFACE;
            LOOT_MAPS.observe(tab, text, new LootMapRuntime.EventContext(
                    world.getPlayerCurrentTileX(), world.getPlayerCurrentTileY(),
                    world.getPlayerRotX(), world.getPlayerPosH(),
                    playerLayer,
                    identity, world.getUsername(), java.time.Instant.now(),
                    waypointConfiguration.getMapBounds(),
                    new WurmLootMapTerrain(world, playerLayer)));
            ARCHAEOLOGY.observe(tab, text, new ArchaeologyRuntime.EventContext(
                    org.waypoints.next.archaeology.ArchaeologyTileCoordinates.centerOf(
                            world.getPlayerCurrentTileX()),
                    org.waypoints.next.archaeology.ArchaeologyTileCoordinates.centerOf(
                            world.getPlayerCurrentTileY()),
                    world.getPlayerPosH(), playerLayer,
                    identity, world.getUsername(), java.time.Instant.now(),
                    waypointConfiguration.getMapBounds()));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Dynamic report Event hooks failed open", failure);
        }
    }

    /** Called by the multicolor ChatPanel overload used by some Event lines. */
    public static void observeEventSegments(String tab,
            java.util.List<com.wurmonline.shared.util.MulticolorLineSegment> segments) {
        observeEvent(tab, EventSegmentText.join(segments));
    }

    public static void observeAction(long[] targets, PlayerAction action) {
        try {
            String actionName = WurmPlayerActionName.resolve(action);
            for (DynamicWaypointProvider provider : DYNAMIC_WAYPOINTS) {
                provider.observeAction(targets, actionName);
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Dynamic report action correlation failed open", failure);
        }
    }

    /** Completes a dug-up Loot Map only when its real container opens. */
    public static void inventoryWindowOpened(long itemId, String windowName) {
        try {
            LOOT_MAPS.inventoryWindowOpened(itemId, windowName);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Loot Map chest-window correlation failed open", failure);
        }
    }

    public static CompassMarkerSnapshot currentCompassMarker() {
        try {
            WurmBeamProbeController current = beam;
            return current == null ? null : current.compassMarker();
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass marker snapshot failed open", failure);
            return null;
        }
    }

    public static void compassWaypointMarkerClicked() {
        try {
            WurmBeamProbeController current = beam;
            if (current == null) return;
            boolean visible = current.toggleWorldBeam();
            HeadsUpDisplay currentHud = hud;
            CompassMarkerSnapshot marker = current.compassMarker();
            if (currentHud != null && marker != null) {
                currentHud.textMessage(":Event", 0.35f, 0.85f, 1.0f,
                        "[Wurm Waypointer] World beam for " + marker.getName()
                                + (visible ? " shown." : " hidden."));
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass marker toggle failed open", failure);
        }
    }

    public static void compassWaypointMarkerClicked(Object target) {
        try {
            if (WaypointClusterPickerWindowBridge.closeIfOpen(
                    hud, "compass marker click")) return;
            if (target instanceof CompassMarkerClusterHit) {
                openClusterPicker((CompassMarkerClusterHit) target);
                return;
            }
            if (target instanceof NavigationTargetKey) {
                WaypointClusterPickerWindowBridge.detach(hud, "single marker click");
                NavigationTarget changed = STATIC_NAVIGATION.selectAndToggle(
                        (NavigationTargetKey) target);
                HeadsUpDisplay currentHud = hud;
                if (changed != null && currentHud != null) {
                    String state = changed.getMarkerStyle().getWorldStyle()
                            == MarkerStyle.WorldStyle.COMPASS_ONLY
                            ? "; compass-only style."
                            : "; world marker " + (changed.isWorldBeamVisible()
                            ? "shown." : "hidden.");
                    currentHud.textMessage(":Event", 0.35f, 0.85f, 1.0f,
                            "[Wurm Waypointer] Selected " + changed.getName() + state);
                }
                return;
            }
            compassWaypointMarkerClicked();
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass static marker toggle failed open", failure);
        }
    }

    public static void compassWaypointMarkerRightClicked(NavigationTargetKey target) {
        try {
            if (target == null || hud == null) return;
            WaypointClusterPickerWindowBridge.detach(hud, "single marker right click");
            NavigationRenderFrame frame = currentNavigationFrame();
            NavigationTarget selected = frame == null || frame.getSnapshot() == null
                    ? null : frame.getSnapshot().find(target);
            if (selected != null && (selected.getSourceType()
                    == WaypointSourceType.MANAGED_ANIMAL
                    || selected.getSourceType() == WaypointSourceType.MANAGED_ITEM)) {
                openSurroundings();
                LOGGER.info("Compass surroundings marker right click opened catalog: id="
                        + target.getWaypointId());
                return;
            }
            SurroundingsWindowBridge.detach(hud, "static marker edit");
            WaypointManagerWindowBridge.openEdit(
                    hud, MANAGER_CONTROLLER, target.getWaypointId());
            LOGGER.info("Compass waypoint marker right click opened Edit: id="
                    + target.getWaypointId());
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Compass waypoint marker right-click edit failed open", failure);
        }
    }

    public static NavigationRenderFrame currentNavigationFrame() {
        try { return STATIC_NAVIGATION.currentFrame(); }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Static navigation frame failed open", failure);
            return null;
        }
    }

    /** Immutable cached array consumed by the injected world-render pass. */
    public static PickableUnit[] currentScannerOutlineTargets() {
        return scannerOutlineTargets;
    }

    private static void refreshScannerOutlineTargets(World world) {
        if (world == null) {
            invalidateScannerOutlineTargets();
            return;
        }
        long revision = SURROUNDINGS.revision();
        int tileX = (int) Math.floor(world.getPlayerPosX() / 4.0d);
        int tileY = (int) Math.floor(world.getPlayerPosY() / 4.0d);
        int layer = world.getPlayerLayer();
        if (revision == scannerOutlineRevision
                && tileX == scannerOutlinePlayerTileX
                && tileY == scannerOutlinePlayerTileY
                && layer == scannerOutlinePlayerLayer) return;
        List<SurroundingsRuntime.ScannerOutlineSubject> subjects =
                SURROUNDINGS.scannerOutlineSubjects(world.getPlayerPosX(),
                        world.getPlayerPosY(), layer);
        List<PickableUnit> targets = new ArrayList<PickableUnit>(subjects.size());
        for (SurroundingsRuntime.ScannerOutlineSubject subject : subjects) {
            Object renderable = subject.getRenderable();
            if (renderable instanceof PickableUnit) {
                targets.add(new ScannerOutlinePickable(
                        (PickableUnit) renderable, subject.getColor()));
            }
        }
        scannerOutlineTargets = targets.isEmpty() ? NO_SCANNER_OUTLINES
                : targets.toArray(new PickableUnit[targets.size()]);
        scannerOutlineRevision = revision;
        scannerOutlinePlayerTileX = tileX;
        scannerOutlinePlayerTileY = tileY;
        scannerOutlinePlayerLayer = layer;
    }

    private static void invalidateScannerOutlineTargets() {
        scannerOutlineTargets = NO_SCANNER_OUTLINES;
        scannerOutlineRevision = Long.MIN_VALUE;
        scannerOutlinePlayerTileX = Integer.MIN_VALUE;
        scannerOutlinePlayerTileY = Integer.MIN_VALUE;
        scannerOutlinePlayerLayer = Integer.MIN_VALUE;
    }

    static String performanceSummary(boolean resetSamples) {
        return WaypointRenderProfiler.summary(resetSamples);
    }

    public static void componentVisibilityChanged(Object component, boolean visible,
                                                  String operation) {
        try {
            MiniMapWindowBridge.visibilityChanged(hud, component);
            if (component != null && "com.wurmonline.client.renderer.gui.CompassComponent"
                    .equals(component.getClass().getName())) {
                LOGGER.info("Compass visibility changed: visible=" + visible
                        + ", operation=" + oneLine(operation));
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass visibility diagnostic failed open", failure);
        }
    }

    public static void connectionEnded() {
        try {
            endDynamicSessions();
            invalidateScannerOutlineTargets();
            ARCHAEOLOGY_CHIMES.clear();
            detachBeam("disconnect");
            STATIC_NAVIGATION.detach("disconnect");
            WaypointClusterPickerWindowBridge.detach(hud, "disconnect");
            WaypointManagerWindowBridge.detach(hud, "disconnect");
            SurroundingsWindowBridge.detach(hud, "disconnect");
            DeedSearchWindowBridge.detach(hud, "disconnect");
            DeedInformationWindowBridge.detach(hud, "disconnect");
            CustomMapMarkWindowBridge.detach(hud, "disconnect");
            MiniMapWindowBridge.detach(hud, "disconnect");
            SERVER_MAPS.deactivate();
            DEEDS.deactivate();
            ServerMapWindowBridge.resetAll();
            hud = null;
            identity = null;
            confirmedWorld = null;
            confirmedWorldName = "";
            awaitingServerInformation = true;
            VANILLA_LANDMARKS.clearSession();
            // Preserve the last browser endpoint for an automatic reconnect.
            // A new browser/direct selection overwrites it before the next login.
            SERVER.reconnecting();
            LOGGER.info("Connection ended; runtime HUD and resolved identity cleared");
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Connection cleanup failed open", failure);
        }
    }

    public static void connectionTransferred(String host, int gamePort) {
        try {
            endDynamicSessions();
            invalidateScannerOutlineTargets();
            ARCHAEOLOGY_CHIMES.clear();
            detachBeam("server transfer");
            STATIC_NAVIGATION.detach("server transfer");
            WaypointClusterPickerWindowBridge.detach(hud, "server transfer");
            WaypointManagerWindowBridge.detach(hud, "server transfer");
            SurroundingsWindowBridge.detach(hud, "server transfer");
            DeedSearchWindowBridge.detach(hud, "server transfer");
            DeedInformationWindowBridge.detach(hud, "server transfer");
            CustomMapMarkWindowBridge.detach(hud, "server transfer");
            MiniMapWindowBridge.detach(hud, "server transfer");
            SERVER_MAPS.deactivate();
            DEEDS.deactivate();
            ServerMapWindowBridge.resetAll();
            identity = null;
            confirmedWorld = null;
            confirmedWorldName = "";
            awaitingServerInformation = true;
            VANILLA_LANDMARKS.clearSession();
            SERVER.transfer(host, gamePort);
            LOGGER.info("Server endpoint captured: source=SERVER_TRANSFER, fullName=\"\", host=\""
                    + oneLine(host) + "\", gamePort=" + gamePort + ", queryPort=unknown");
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Server transfer cleanup failed open", failure);
        }
    }

    public static void serverInformationUpdated(World world, int cluster, String serverName) {
        try {
            confirmedWorld = world;
            confirmedWorldName = oneLine(serverName);
            identity = null;
            awaitingServerInformation = confirmedWorldName.isEmpty();
            LOGGER.info("Fresh world server information received: name=\""
                    + confirmedWorldName + "\", cluster=" + cluster
                    + ", world=" + identityOf(world)
                    + ", renderingReleased=" + !awaitingServerInformation);
        } catch (Throwable failure) {
            awaitingServerInformation = true;
            LOGGER.log(Level.FINE, "World server information capture failed open", failure);
        }
    }

    public static void effectRendererCleared(Object renderer) {
        try {
            WurmBeamProbeController current = beam;
            if (current != null) current.rendererCleared(renderer);
            STATIC_NAVIGATION.rendererCleared(renderer);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Effect renderer clear notification failed open", failure);
        }
    }

    public static boolean captureVanillaLandmark(long effectId, short effectType,
                                                  float worldX, float worldY,
                                                  float height, int layer) {
        try {
            return VANILLA_LANDMARKS.capture(effectId, effectType,
                    worldX, worldY, height, layer);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING,
                    "Vanilla landmark capture failed open; original effect retained",
                    failure);
            return false;
        }
    }

    public static void vanillaLandmarkRemoved(long effectId) {
        try {
            VANILLA_LANDMARKS.removed(effectId);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Vanilla landmark removal capture failed open",
                    failure);
        }
    }

    public static ServerIdentity currentServerIdentity() {
        return identity;
    }

    /** Immutable data consumed by the native M-map bridge. */
    public static ServerMapSnapshot serverMapSnapshot() {
        return DEEDS.overlay(SERVER_MAPS.current());
    }

    public static HighwayTileIndex serverMapHighways() {
        return HIGHWAYS.current();
    }

    public static WaypointRevisionSnapshot serverMapWaypoints() {
        return combineDynamicWaypoints(VANILLA_LANDMARKS.combine(
                STATIC_WAYPOINTS.revisionSnapshot()));
    }

    public static boolean serverMapWaypointEditable(UUID id) {
        if (id == null) return false;
        try {
            WaypointRevisionSnapshot snapshot = STATIC_WAYPOINTS.revisionSnapshot();
            if (snapshot == null) return false;
            for (org.waypoints.next.model.WaypointRecord record
                    : snapshot.getRecords()) {
                if (id.equals(record.getId())) return record.getSourceType()
                        != WaypointSourceType.DEED;
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Server map editability check failed open",
                    failure);
        }
        return false;
    }

    public static boolean serverMapShowsDeeds() {
        return waypointConfiguration.isServerMapShowDeeds();
    }

    public static boolean serverMapShowsHighways() {
        return waypointConfiguration.isServerMapShowHighways();
    }

    public static int currentPlayerTileX() {
        World world = hud == null ? null : hud.getWorld();
        return world == null ? 0 : world.getPlayerCurrentTileX();
    }

    public static int currentPlayerTileY() {
        World world = hud == null ? null : hud.getWorld();
        return world == null ? 0 : world.getPlayerCurrentTileY();
    }

    public static String currentPlayerName() {
        World world = hud == null ? null : hud.getWorld();
        return world == null ? "" : oneLine(world.getUsername());
    }

    /** Exact terrain name when the hovered tile is in Wurm's live buffer. */
    public static String serverMapLiveTileDescription(int tileX, int tileY) {
        World world = hud == null ? null : hud.getWorld();
        return WurmSurfaceTileDescription.describe(world, tileX, tileY);
    }

    public static void serverMapWaypointRequested(int tileX, int tileY,
                                                   WaypointLayer layer) {
        try {
            HeadsUpDisplay current = hud;
            ServerMapSnapshot map = SERVER_MAPS.current();
            if (current == null || map == null || map.getProfile() == null) return;
            if (tileX < 0 || tileY < 0
                    || tileX >= map.getProfile().getMapWidth()
                    || tileY >= map.getProfile().getMapHeight()) return;
            WaypointClusterPickerWindowBridge.detach(current, "map waypoint create");
            SurroundingsWindowBridge.detach(current, "map waypoint create");
            String coordinates = "x=" + tileX + " y=" + tileY
                    + (layer == WaypointLayer.CAVE ? " cave" : "");
            WaypointManagerWindowBridge.openCreateCoordinates(current,
                    MANAGER_CONTROLLER, "Map " + tileX + ", " + tileY,
                    coordinates);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Server map waypoint request failed open", failure);
        }
    }

    public static GroundNavigationRouteEffect.RouteSnapshot
    currentNavigationRoute() {
        try { return STATIC_NAVIGATION.currentNavigatorMapRoute(); }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Navigator route snapshot failed open",
                    failure);
            return null;
        }
    }

    public static void serverMapCustomMarkSaved(int tileX, int tileY,
                                                String text) {
        try {
            if (hud == null || identity == null) return;
            STATIC_WAYPOINTS.addCustomMapMark(text, tileX, tileY, hud, identity);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Unable to save custom map mark", failure);
            event("Could not save custom map mark: "
                    + oneLine(failure.getMessage()) + ".");
            throw failure instanceof RuntimeException
                    ? (RuntimeException) failure
                    : new IllegalStateException(failure);
        }
    }

    /** A deed picker selection tracks a provider-owned DEED waypoint. */
    public static void serverMapDeedWaypointRequested(Deed deed) {
        try {
            if (deed == null || hud == null || identity == null) return;
            DEEDS.track(deed, hud, identity);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Unable to track selected deed", failure);
            event("Could not track deed: " + oneLine(failure.getMessage()) + ".");
        }
    }

    /** Tracks a deed if necessary and queues it as the active NAV target. */
    public static void serverMapDeedNavigationRequested(Deed deed) {
        try {
            if (deed == null || hud == null || identity == null) return;
            WaypointRecord record = DEEDS.track(deed, hud, identity);
            EXTERNAL_NAVIGATION_REQUESTS.add(record.getId());
            event("Navigation to deed queued: " + oneLine(record.getName()) + ".");
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Unable to navigate to selected deed",
                    failure);
            event("Could not navigate to deed: "
                    + oneLine(failure.getMessage()) + ".");
        }
    }

    public static void serverMapDeedProviderRefreshRequested() {
        try {
            DEEDS.refreshNow();
            event("Deed provider refresh queued in the background.");
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Manual deed refresh failed open", failure);
            event("Could not queue deed provider refresh.");
        }
    }

    public static void serverMapWaypointEditRequested(UUID id) {
        try {
            HeadsUpDisplay current = hud;
            if (current == null || !serverMapWaypointEditable(id)) return;
            WaypointClusterPickerWindowBridge.detach(current,
                    "map waypoint edit");
            SurroundingsWindowBridge.detach(current, "map waypoint edit");
            WaypointManagerWindowBridge.openEdit(current,
                    MANAGER_CONTROLLER, id);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Server map waypoint edit request failed open", failure);
        }
    }

    public static void openWaypointManager() {
        HeadsUpDisplay current = hud;
        if (current == null) throw new IllegalStateException("HUD is not ready yet");
        WaypointClusterPickerWindowBridge.detach(current, "manager open");
        SurroundingsWindowBridge.detach(current, "manager open");
        WaypointManagerWindowBridge.open(current, MANAGER_CONTROLLER);
    }

    public static void openSurroundings() {
        HeadsUpDisplay current = hud;
        if (current == null) throw new IllegalStateException("HUD is not ready yet");
        WaypointClusterPickerWindowBridge.detach(current, "surroundings open");
        WaypointManagerWindowBridge.detach(current, "surroundings open");
        SurroundingsWindowBridge.open(current, SURROUNDINGS_CONTROLLER);
    }

    /** Called after a creature or ground item enters or changes in the client. */
    public static void surroundingsRenderableUpserted(Object renderable) {
        try {
            SurroundingEntry entry = SURROUNDINGS.upsertRenderable(renderable);
            STATIC_WAYPOINTS.refreshExternalObjectWaypoints(
                    entry, java.time.Instant.now());
        }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings upsert hook failed open", failure);
        }
    }

    /** Called with the latest server target for a moving creature or item. */
    public static void surroundingsCreatureMoved(Object renderable, float worldX,
                                                  float worldY, float height) {
        try {
            SurroundingEntry entry = SURROUNDINGS.creatureMoved(
                    renderable, worldX, worldY, height);
            STATIC_WAYPOINTS.refreshExternalObjectWaypoints(
                    entry, java.time.Instant.now());
        }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings movement hook failed open", failure);
        }
    }

    /** Called after a creature or ground item leaves the client stream. */
    public static void surroundingsRenderableRemoved(Object renderable,
                                                      boolean removedFromWorld) {
        try {
            SurroundingKey removed = SURROUNDINGS.removeRenderable(
                    renderable, removedFromWorld);
            // In the pinned client true is used by authoritative server removals
            // (picked up, buried, destroyed, dead-animation completion, etc.).
            // false is also emitted by addRenderable's technical remove-before-add.
            if (!removedFromWorld || removed == null) return;
            removeVanishedMarks(removed);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings remove hook failed open", failure);
        }
    }

    /** Exact lifecycle boundary: the creature is gone and the corpse is a new object. */
    public static void surroundingsCreatureReplacedByCorpse(long creatureId,
                                                             long corpseId) {
        try {
            SurroundingKey removed = new SurroundingKey(
                    SurroundingKind.ANIMAL, creatureId);
            SURROUNDINGS.removeAuthoritatively(removed);
            removeVanishedMarks(removed);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Creature-to-corpse waypoint lifecycle failed open", failure);
        }
    }

    /** Called when Wurm clears the active cell renderer. */
    public static void surroundingsRenderablesCleared() {
        try { SURROUNDINGS.clearRenderables(); }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings clear hook failed open", failure);
        }
    }

    private static void openClusterPicker(CompassMarkerClusterHit cluster) {
        HeadsUpDisplay currentHud = hud;
        NavigationRenderFrame frame = currentNavigationFrame();
        if (currentHud == null || frame == null || frame.getWorld() == null
                || frame.getSnapshot() == null) return;
        List<NavigationTarget> members = new ArrayList<NavigationTarget>(cluster.size());
        for (int i = 0; i < cluster.size(); i++) {
            NavigationTarget target = frame.getSnapshot().find(cluster.get(i));
            if (target != null) members.add(target);
        }
        if (members.size() == 1) {
            compassWaypointMarkerClicked(members.get(0).getKey());
            return;
        }
        if (members.size() < 2) return;
        WaypointClusterPickerWindowBridge.open(currentHud, members,
                frame.getWorld(), cluster.getScreenX(), cluster.getScreenY());
    }

    private static synchronized WurmBeamProbeController beam() {
        if (beam == null) {
            beam = new WurmBeamProbeController(LOGGER);
            LOGGER.info("Beam renderer initialized lazily after HUD readiness");
        }
        return beam;
    }

    private static void detachBeam(String reason) {
        WurmBeamProbeController current = beam;
        if (current != null) current.detach(reason);
    }

    private static void event(String text) {
        HeadsUpDisplay current = hud;
        if (current != null) {
            current.textMessage(":Event", 0.35f, 0.85f, 1.0f,
                    "[Wurm Waypointer] " + oneLine(text));
        }
    }

    private static WaypointRevisionSnapshot combineDynamicWaypoints(
            WaypointRevisionSnapshot base) {
        WaypointRevisionSnapshot combined = base;
        for (DynamicWaypointProvider provider : DYNAMIC_WAYPOINTS) {
            combined = provider.combine(combined);
        }
        return combined;
    }

    private static void startRequestedNavigation() {
        for (DynamicWaypointProvider provider : DYNAMIC_WAYPOINTS) {
            NavigationTargetKey request;
            while ((request = provider.pollNavigationRequest()) != null) {
                NavigationTarget started = STATIC_NAVIGATION.startNavigator(request);
                if (started != null && started.isNavigatorActive()) {
                    event("Navigator started: " + oneLine(started.getName())
                            + " (" + provider.navigationReason() + ").");
                }
            }
        }
        UUID externalId;
        while ((externalId = EXTERNAL_NAVIGATION_REQUESTS.poll()) != null) {
            NavigationRenderFrame current = currentNavigationFrame();
            NavigationTarget target = current == null ? null
                    : findNavigationTarget(current.getSnapshot().getTargets(),
                    externalId.toString());
            NavigationTarget started = target == null ? null
                    : STATIC_NAVIGATION.startNavigator(target.getKey());
            if (started != null && started.isNavigatorActive()) {
                event("Navigator started: " + oneLine(started.getName())
                        + " (external API request).");
            }
        }
    }

    private static SurroundingEntry findExternalSubject(WurmObjectRef subject) {
        if (subject == null) return null;
        long id = subject.getWurmId();
        switch (subject.getKind()) {
            case CREATURE:
                return SURROUNDINGS.find(new SurroundingKey(
                        SurroundingKind.ANIMAL, id));
            case ITEM:
                return SURROUNDINGS.find(new SurroundingKey(
                        SurroundingKind.ITEM, id));
            case CONTAINER:
                return SURROUNDINGS.find(new SurroundingKey(
                        SurroundingKind.CONTAINER, id));
            case AUTO:
            default:
                for (SurroundingKind kind : new SurroundingKind[]{
                        SurroundingKind.ANIMAL, SurroundingKind.ITEM,
                        SurroundingKind.CONTAINER}) {
                    SurroundingEntry found = SURROUNDINGS.find(
                            new SurroundingKey(kind, id));
                    if (found != null) return found;
                }
                return null;
        }
    }

    static SurroundingEntry externalSnapshotEntry(
            WurmObjectRef subject, WurmObjectSnapshot snapshot,
            java.time.Instant now) {
        if (subject == null || snapshot == null) return null;
        SurroundingKind kind = subject.getKind() == WurmObjectKind.CREATURE
                ? SurroundingKind.ANIMAL
                : subject.getKind() == WurmObjectKind.CONTAINER
                ? SurroundingKind.CONTAINER : SurroundingKind.ITEM;
        return SurroundingEntry.builder().kind(kind)
                .wurmId(subject.getWurmId()).name(snapshot.getName())
                .category("External objects").layer(snapshot.getLayer())
                .position(snapshot.getWorldX(), snapshot.getWorldY(),
                        snapshot.getHeight())
                .firstSeenAt(now).updatedAt(now).build();
    }

    /** Resolves the object currently represented by the stock Select/Target HUD. */
    private static WurmObjectSnapshot liveHudSnapshot(
            HeadsUpDisplay currentHud, WurmObjectRef subject) {
        if (currentHud == null || subject == null) return null;
        PickableUnit[] candidates = new PickableUnit[]{
                reflectedPickable(currentHud.getSelectBar(), "selectedUnit"),
                reflectedPickable(currentHud, "targetRenderable"),
                currentHud.getWorld() == null ? null
                        : currentHud.getWorld().getCurrentHoveredObject()
        };
        for (PickableUnit candidate : candidates) {
            if (candidate == null || candidate.getId() != subject.getWurmId()
                    || !(candidate instanceof CellRenderable)) continue;
            CellRenderable positioned = (CellRenderable) candidate;
            try {
                return new WurmObjectSnapshot(candidate.getHoverName(),
                        positioned.getXPos(), positioned.getYPos(),
                        positioned.getHPos(), positioned.getLayer());
            } catch (RuntimeException invalidSnapshot) {
                LOGGER.log(Level.FINE,
                        "Unable to snapshot selected API object", invalidSnapshot);
            }
        }
        return null;
    }

    private static PickableUnit reflectedPickable(Object owner,
                                                   String fieldName) {
        if (owner == null) return null;
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(owner);
                return value instanceof PickableUnit ? (PickableUnit) value : null;
            } catch (NoSuchFieldException absent) {
                type = type.getSuperclass();
            } catch (Throwable inaccessible) {
                LOGGER.log(Level.FINE,
                        "Unable to inspect HUD object field " + fieldName,
                        inaccessible);
                return null;
            }
        }
        return null;
    }

    static MarkerStyle externalMarkerStyle(ObjectMarkerType requested,
                                           SurroundingKind kind) {
        MarkerStyle base = SurroundingsRuntime.style(kind);
        MarkerStyle.WorldStyle worldStyle;
        ObjectMarkerType markerType = requested == null
                ? ObjectMarkerType.ALERT : requested;
        switch (markerType) {
            case TARGET:
                worldStyle = MarkerStyle.WorldStyle.TARGET_CROSSHAIR;
                break;
            case BEAM:
                worldStyle = MarkerStyle.WorldStyle.COLORED_BEAM;
                break;
            case COMPASS_ONLY:
                worldStyle = MarkerStyle.WorldStyle.COMPASS_ONLY;
                break;
            case ALERT:
            default:
                worldStyle = MarkerStyle.WorldStyle.EXCLAMATION;
                break;
        }
        if (markerType == ObjectMarkerType.ALERT) {
            // ALERT is a semantic warning, not a category-coloured ordinary
            // Surroundings mark. Red also remains visible on blue/green wagon
            // cloth where the inherited CONTAINER cyan was easily lost.
            return new MarkerStyle(worldStyle,
                    1.0f, 0.12f, 0.055f, 1.0f,
                    Math.max(15.0f, base.getMarkerSize()),
                    Math.max(2.6f, base.getBeamWidth()),
                    base.isShowLabel(), base.isShowDistance());
        }
        return new MarkerStyle(worldStyle, base.getRed(), base.getGreen(),
                base.getBlue(), base.getAlpha(), base.getMarkerSize(),
                base.getBeamWidth(), base.isShowLabel(), base.isShowDistance());
    }

    private static int removeExternalSubjectMarks(WurmObjectRef subject) {
        if (subject == null) return 0;
        int removed = 0;
        if (subject.getKind() == WurmObjectKind.AUTO) {
            for (SurroundingKind kind : new SurroundingKind[]{
                    SurroundingKind.ANIMAL, SurroundingKind.ITEM,
                    SurroundingKind.CONTAINER}) {
                removed += removeVanishedMarks(new SurroundingKey(
                        kind, subject.getWurmId()));
            }
            return removed;
        }
        SurroundingKind kind = subject.getKind() == WurmObjectKind.CREATURE
                ? SurroundingKind.ANIMAL
                : subject.getKind() == WurmObjectKind.CONTAINER
                ? SurroundingKind.CONTAINER : SurroundingKind.ITEM;
        return removeVanishedMarks(new SurroundingKey(kind, subject.getWurmId()));
    }

    private static int removeVanishedMarks(SurroundingKey removed) {
        List<UUID> deleted = STATIC_WAYPOINTS
                .removeVanishedSurroundingsWaypoint(removed);
        if (deleted.isEmpty()) return 0;
        boolean navigatorStopped = false;
        for (UUID id : deleted) {
            if (STATIC_NAVIGATION.isNavigatorActive(id)) navigatorStopped = true;
            STATIC_NAVIGATION.managerEnabledChanged(id, false);
        }
        SURROUNDINGS.reconcileWaypoints(
                STATIC_WAYPOINTS.surroundingsWaypointKeys());
        event("Removed " + deleted.size()
                + " object mark(s): target disappeared."
                + (navigatorStopped ? " Navigator stopped." : ""));
        return deleted.size();
    }

    private static void flushDynamicMessages() {
        for (DynamicWaypointProvider provider : DYNAMIC_WAYPOINTS) {
            String message;
            while ((message = provider.pollMessage()) != null) event(message);
        }
    }

    private static void endDynamicSessions() {
        for (DynamicWaypointProvider provider : DYNAMIC_WAYPOINTS) {
            provider.connectionEnded();
        }
    }

    private static String describe(CapturedServerSelection value) {
        if (value == null) return "selection=null";
        org.waypoints.next.model.ServerEndpoint endpoint = value.getEndpoint();
        Integer queryPort = endpoint.getQueryPort();
        return "source=" + value.getSource()
                + ", fullName=\"" + oneLine(value.getFullName()) + "\""
                + ", host=\"" + oneLine(endpoint.getHost()) + "\""
                + ", gamePort=" + endpoint.getGamePort()
                + ", queryPort=" + (queryPort == null ? "unknown" : queryPort);
    }

    private static String describe(ServerIdentity value) {
        if (value == null) return "identity=null";
        org.waypoints.next.model.ServerEndpoint endpoint = value.getEndpoint();
        Integer queryPort = endpoint == null ? null : endpoint.getQueryPort();
        return "resolution=" + value.getResolution()
                + ", fullName=\"" + oneLine(value.getFullName()) + "\""
                + ", shortName=\"" + oneLine(value.getShortName()) + "\""
                + ", host=\"" + (endpoint == null ? "" : oneLine(endpoint.getHost())) + "\""
                + ", gamePort=" + (endpoint == null ? "unknown" : endpoint.getGamePort())
                + ", queryPort=" + (queryPort == null ? "unknown" : queryPort);
    }

    private static String oneLine(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String identityOf(Object value) {
        return value == null ? "null" : Integer.toHexString(System.identityHashCode(value));
    }

    private static String phase0ServerKey(ServerIdentity value, String worldName) {
        if (value == null || value.getEndpointFingerprint().isEmpty()) return "";
        String name = oneLine(worldName).toLowerCase(Locale.ENGLISH);
        return name.isEmpty() ? "" : value.getEndpointFingerprint() + "|" + name;
    }
}
