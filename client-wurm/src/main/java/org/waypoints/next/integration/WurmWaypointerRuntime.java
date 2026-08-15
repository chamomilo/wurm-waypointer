package org.waypoints.next.integration;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.gui.CompassMarkerClusterHit;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.DeedSearchWindowBridge;
import com.wurmonline.client.renderer.gui.DeedInformationWindowBridge;
import com.wurmonline.client.renderer.gui.ServerMapWindowBridge;
import com.wurmonline.client.renderer.gui.WaypointClusterPickerWindowBridge;
import com.wurmonline.client.renderer.gui.WaypointManagerWindowBridge;
import com.wurmonline.client.renderer.gui.SurroundingsWindowBridge;
import org.waypoints.next.model.CapturedServerSelection;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.map.SklotopolisMapService;
import org.waypoints.next.render.BeamProbeConfiguration;
import org.waypoints.next.render.CompassMarkerSnapshot;
import org.waypoints.next.render.NavigationRenderFrame;
import org.waypoints.next.render.StaticNavigationController;
import org.waypoints.next.render.WurmBeamProbeController;
import org.waypoints.next.render.WaypointRenderProfiler;
import org.waypoints.next.render.WaypointRenderRuntimeAccess;
import org.waypoints.next.render.WaypointRenderRuntimeBridge;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
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
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.surroundings.SurroundingsSnapshot;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

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
    private static final SklotopolisHighwayService HIGHWAYS =
            new SklotopolisHighwayService(LOGGER);
    private static final SklotopolisMapService SERVER_MAPS =
            new SklotopolisMapService(LOGGER);
    private static final StaticNavigationController STATIC_NAVIGATION =
            new StaticNavigationController(LOGGER, HIGHWAYS);
    private static final VanillaLandmarkRuntime VANILLA_LANDMARKS =
            new VanillaLandmarkRuntime(LOGGER);
    private static final LootMapRuntime LOOT_MAPS = new LootMapRuntime(LOGGER);
    private static final ArchaeologyRuntime ARCHAEOLOGY =
            new ArchaeologyRuntime(LOGGER);
    private static final SurroundingsRuntime SURROUNDINGS =
            new SurroundingsRuntime(LOGGER);
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

    private WurmWaypointerRuntime() {
    }

    public static void configure(BeamProbeConfiguration value) {
        WaypointRenderRuntimeBridge.bind(RENDER_ACCESS);
        configuration = value == null ? BeamProbeConfiguration.disabled() : value;
        LOGGER.info("Runtime configuration: " + configuration.diagnosticSummary());
    }

    public static void configure(BeamProbeConfiguration beamValue,
                                 WaypointClientConfiguration waypointValue) {
        configure(beamValue);
        waypointConfiguration = waypointValue == null
                ? WaypointClientConfiguration.defaults() : waypointValue;
        STATIC_WAYPOINTS.configureAndLoad(waypointConfiguration);
        STATIC_NAVIGATION.configure(waypointConfiguration);
        SERVER_MAPS.configure(waypointConfiguration.isServerMapEnabled(),
                waypointConfiguration.getServerMapCacheDirectory(),
                waypointConfiguration.getServerMapSyncMinutes());
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
            SURROUNDINGS.updateDeeds(SERVER_MAPS.current());
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
        if (values.length == 0 || (!"surroundings".equalsIgnoreCase(values[0])
                && !"nearby".equalsIgnoreCase(values[0]))) return false;
        openSurroundings();
        return true;
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
                event("Navigation pulse is off for this client session. "
                        + "The active Navigator keeps a solid route.");
            } else if ("on".equalsIgnoreCase(operation)) {
                STATIC_NAVIGATION.setNavigationPulseEnabled(true);
                event("Navigation pulse is on for this client session.");
            } else if ("status".equalsIgnoreCase(operation)) {
                event("Navigation pulse is "
                        + (STATIC_NAVIGATION.isNavigationPulseEnabled()
                        ? "on" : "off") + ".");
            } else {
                event("Usage: /wp nav pulse on | off | status");
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
            String actionName = action == null ? null : action.getName();
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

    static String performanceSummary(boolean resetSamples) {
        return WaypointRenderProfiler.summary(resetSamples);
    }

    public static void componentVisibilityChanged(Object component, boolean visible,
                                                  String operation) {
        try {
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
            ARCHAEOLOGY_CHIMES.clear();
            detachBeam("disconnect");
            STATIC_NAVIGATION.detach("disconnect");
            WaypointClusterPickerWindowBridge.detach(hud, "disconnect");
            WaypointManagerWindowBridge.detach(hud, "disconnect");
            SurroundingsWindowBridge.detach(hud, "disconnect");
            DeedSearchWindowBridge.detach(hud, "disconnect");
            DeedInformationWindowBridge.detach(hud, "disconnect");
            SERVER_MAPS.deactivate();
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
            ARCHAEOLOGY_CHIMES.clear();
            detachBeam("server transfer");
            STATIC_NAVIGATION.detach("server transfer");
            WaypointClusterPickerWindowBridge.detach(hud, "server transfer");
            WaypointManagerWindowBridge.detach(hud, "server transfer");
            SurroundingsWindowBridge.detach(hud, "server transfer");
            DeedSearchWindowBridge.detach(hud, "server transfer");
            DeedInformationWindowBridge.detach(hud, "server transfer");
            SERVER_MAPS.deactivate();
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
        return SERVER_MAPS.current();
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
                if (id.equals(record.getId())) return true;
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
        try { SURROUNDINGS.upsertRenderable(renderable); }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings upsert hook failed open", failure);
        }
    }

    /** Called with the latest server target for a moving creature or item. */
    public static void surroundingsCreatureMoved(Object renderable, float worldX,
                                                  float worldY, float height) {
        try { SURROUNDINGS.creatureMoved(renderable, worldX, worldY, height); }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings movement hook failed open", failure);
        }
    }

    /** Called after a creature or ground item leaves the client stream. */
    public static void surroundingsRenderableRemoved(Object renderable) {
        try { SURROUNDINGS.removeRenderable(renderable); }
        catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings remove hook failed open", failure);
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
