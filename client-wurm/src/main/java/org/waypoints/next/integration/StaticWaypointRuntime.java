package org.waypoints.next.integration;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.util.Computer;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointLifetime;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.persistence.OpaqueWaypointRecord;
import org.waypoints.next.persistence.WaypointDocument;
import org.waypoints.next.persistence.WaypointFormatCodec;
import org.waypoints.next.persistence.WaypointStore;
import org.waypoints.next.service.WaypointFilter;
import org.waypoints.next.service.WaypointManagerQuery;
import org.waypoints.next.service.WaypointManagerSnapshot;
import org.waypoints.next.service.WaypointManagerViewService;
import org.waypoints.next.service.WaypointManager;
import org.waypoints.next.service.WaypointTransferService;
import org.waypoints.next.service.WaypointShareCodec;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.service.ServerIdentityRepair;
import org.waypoints.next.source.CoordinateInputParser;
import org.waypoints.next.source.ParsedCoordinate;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKey;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.validation.WaypointLimits;
import org.waypoints.next.validation.WaypointRecordValidator;
import org.waypoints.next.ui.WaypointEditData;
import org.waypoints.next.ui.WaypointManagerContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/** First Phase 1 vertical slice: local static CRUD, parsing and durable transfer. */
final class StaticWaypointRuntime {
    private static final String PREFIX = "[Wurm Waypointer] ";
    private static final long SURROUNDINGS_LIFETIME_SECONDS = 15L * 60L;

    private final Logger logger;
    private final WaypointRecordValidator validator = new WaypointRecordValidator();
    private final WaypointFormatCodec codec = new WaypointFormatCodec(validator);
    private final WaypointManager manager = new WaypointManager(validator);
    private final WaypointManagerViewService managerView =
            new WaypointManagerViewService();
    private final CoordinateInputParser parser = new CoordinateInputParser();
    private final WaypointTransferService transfers = new WaypointTransferService();
    private final WaypointShareCodec shares = new WaypointShareCodec();
    private final ServerIdentityRepair serverIdentityRepair = new ServerIdentityRepair();
    private final ConcurrentLinkedQueue<String> pendingEvents =
            new ConcurrentLinkedQueue<String>();
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "Wurm-Waypointer-storage");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private volatile WaypointClientConfiguration configuration =
            WaypointClientConfiguration.defaults();
    private volatile WaypointStore store;
    private volatile WaypointStore transferStore;
    private volatile List<OpaqueWaypointRecord> opaqueRecords =
            Collections.emptyList();
    private volatile boolean ready;
    private volatile WaypointRevisionSnapshot cachedRevisionSnapshot;
    private volatile long nextExpiryAtMillis = Long.MAX_VALUE;

    StaticWaypointRuntime(Logger logger) {
        this.logger = logger;
    }

    synchronized void configureAndLoad(WaypointClientConfiguration value) {
        configuration = value == null ? WaypointClientConfiguration.defaults() : value;
        store = new WaypointStore(configuration.getDataFile(), codec);
        transferStore = new WaypointStore(configuration.getTransferFile(), codec);
        ready = false;
        try {
            WaypointDocument loaded = store.load();
            manager.replaceAll(loaded.getRecords());
            refreshNextExpiry();
            opaqueRecords = immutableOpaque(loaded.getOpaqueRecords());
            ready = true;
            logger.info("Phase 1 static store loaded: path=\""
                    + absolute(configuration.getDataFile()) + "\", records="
                    + loaded.getRecords().size() + ", opaqueRecords="
                    + loaded.getOpaqueRecords().size() + ", recoveredFromBackup="
                    + store.wasRecoveredFromBackup());
            if (store.wasRecoveredFromBackup()) {
                pendingEvents.add(PREFIX + "Primary waypoint file was corrupt; data recovered from backup.");
            }
        } catch (Throwable failure) {
            manager.replaceAll(Collections.<WaypointRecord>emptyList());
            refreshNextExpiry();
            opaqueRecords = Collections.emptyList();
            logger.log(Level.WARNING,
                    "Phase 1 static store is unavailable; mutations are disabled to avoid data loss: path=\""
                            + absolute(configuration.getDataFile()) + "\"", failure);
            pendingEvents.add(PREFIX + "Local waypoint storage is unavailable; see client.log. Mutations are disabled.");
        }
    }

    boolean handleCommand(String command, String[] arguments, HeadsUpDisplay hud,
                          ServerIdentity identity) {
        String commandName = clean(command);
        if (commandName.startsWith("/")) commandName = commandName.substring(1);
        if (!"wp".equalsIgnoreCase(commandName)
                && !"waypoint".equalsIgnoreCase(commandName)) return false;
        String operation = "dispatch";
        try {
            String[] args = WaypointCommandArguments.withoutRepeatedCommand(
                    commandName, arguments);
            operation = args.length == 0 ? "help" : clean(args[0]).toLowerCase(Locale.ENGLISH);
            if ("help".equals(operation)) {
                message(hud, "Commands: /wp here <name>; /wp add <name> <x> <y|map-link>; "
                        + "/wp paste <name>; /wp list; /wp duplicate|delete|enable|disable <UUID|exact name>; "
                        + "/wp rename <UUID|name> | <new name>; /wp manager; /wp export; /wp import; "
                        + "/wp perf [reset].");
                return true;
            }
            if ("perf".equals(operation)) {
                boolean reset = args.length > 1
                        && "reset".equalsIgnoreCase(clean(args[1]));
                message(hud, WurmWaypointerRuntime.performanceSummary(reset));
                return true;
            }
            requireReady();
            World world = requireWorld(hud);
            ServerIdentity server = requireServer(identity);
            String user = requireUser(world);
            if ("here".equals(operation)) {
                String name = WaypointCommandArguments.join(args, 1, args.length);
                if (name.isEmpty()) throw new IllegalArgumentException("usage: /wp here <name>");
                WaypointLayer layer = world.getPlayerLayer() < 0
                        ? WaypointLayer.CAVE : WaypointLayer.SURFACE;
                addStatic(name, new WaypointCoordinate(world.getPlayerCurrentTileX(),
                        world.getPlayerCurrentTileY(), Double.valueOf(world.getPlayerPosH()), layer),
                        user, server, "here", "", hud);
            } else if ("add".equals(operation)) {
                WaypointCommandArguments.AddRequest request =
                        WaypointCommandArguments.parseAdd(args, parser,
                                configuration.getMapBounds());
                ParsedCoordinate parsed = request.getCoordinate();
                addStatic(request.getName(), parsed.getCoordinate(), user, server,
                        parsed.getSourceKind(), parsed.getServerHint(), hud);
            } else if ("paste".equals(operation)) {
                String name = WaypointCommandArguments.join(args, 1, args.length);
                if (name.isEmpty()) throw new IllegalArgumentException("usage: /wp paste <name>");
                ParsedCoordinate parsed = parser.parse(clipboardText(),
                        configuration.getMapBounds());
                addStatic(name, parsed.getCoordinate(), user, server, "clipboard",
                        parsed.getServerHint(), hud);
            } else if ("list".equals(operation)) {
                listCurrent(hud, world, user, server);
            } else if ("delete".equals(operation)) {
                WaypointRecord record = requireRecord(reference(args));
                manager.delete(record.getId());
                refreshNextExpiry();
                scheduleSave("delete " + record.getId());
                message(hud, "Deleted: " + record.getName() + " [" + record.getId() + "]");
            } else if ("duplicate".equals(operation)) {
                WaypointRecord record = manager.duplicate(requireRecord(reference(args)).getId(), Instant.now());
                refreshNextExpiry();
                scheduleSave("duplicate " + record.getId());
                message(hud, "Duplicate created: " + record.getName() + " [" + record.getId() + "]");
            } else if ("enable".equals(operation) || "disable".equals(operation)) {
                boolean enabled = "enable".equals(operation);
                WaypointRecord record = manager.setEnabled(requireRecord(reference(args)).getId(),
                        enabled, Instant.now());
                scheduleSave((enabled ? "enable " : "disable ") + record.getId());
                message(hud, (enabled ? "Enabled: " : "Disabled: ") + record.getName());
            } else if ("rename".equals(operation)) {
                rename(args, hud);
            } else if ("export".equals(operation)) {
                exportAsync();
                message(hud, "Export queued: "
                        + absolute(configuration.getTransferFile()));
            } else if ("import".equals(operation)) {
                importAsync();
                message(hud, "Import queued: "
                        + absolute(configuration.getTransferFile()));
            } else if ("manager".equals(operation)) {
                WurmWaypointerRuntime.openWaypointManager();
            } else {
                throw new IllegalArgumentException("unknown command: /wp " + operation);
            }
        } catch (Throwable failure) {
            String diagnostic = "Phase 1 waypoint command failed open: operation=\""
                    + oneLine(operation) + "\", failure="
                    + failure.getClass().getName() + ", reason=\""
                    + oneLine(safeMessage(failure)) + "\"";
            if (failure instanceof IllegalArgumentException) {
                logger.info(diagnostic);
            } else {
                logger.log(Level.WARNING, diagnostic, failure);
            }
            message(hud, "Error: " + safeMessage(failure));
        }
        return true;
    }

    void flushEvents(HeadsUpDisplay hud) {
        if (hud == null) return;
        for (int i = 0; i < 8; i++) {
            String event = pendingEvents.poll();
            if (event == null) break;
            try { hud.textMessage(":Event", 0.35f, 0.85f, 1.0f, event); }
            catch (Throwable failure) {
                pendingEvents.add(event);
                return;
            }
        }
    }

    /** Called from HUD tick; persistence itself remains on storageExecutor. */
    void expireDue(long nowEpochMillis) {
        if (!ready || nowEpochMillis < nextExpiryAtMillis) return;
        List<WaypointRecord> expired = manager.removeExpired(nowEpochMillis);
        refreshNextExpiry();
        if (expired.isEmpty()) return;
        scheduleSave("temporary expiry count=" + expired.size());
        if (expired.size() == 1) {
            WaypointRecord record = expired.get(0);
            pendingEvents.add(PREFIX + "Temporary waypoint expired: "
                    + oneLine(record.getName()) + " ["
                    + record.getId().toString().substring(0, 8) + "].");
        } else {
            pendingEvents.add(PREFIX + "Expired " + expired.size()
                    + " temporary waypoints.");
        }
    }

    int recordCount() { return manager.snapshot().size(); }

    void confirmCurrentServer(ServerIdentity confirmed) {
        if (!ready || confirmed == null || !confirmed.isSafeForAutomaticRendering()) return;
        List<WaypointRecord> before = manager.snapshot();
        ServerIdentityRepair.RepairResult result =
                serverIdentityRepair.repairUnresolvedTransfers(
                        before, confirmed, Instant.now());
        if (result.getRepairedCount() == 0) return;
        List<WaypointRecord> after = result.getRecords();
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i) != after.get(i)) manager.update(after.get(i));
        }
        scheduleSave("confirm transferred server identity count="
                + result.getRepairedCount());
        logger.info("Confirmed transferred server identity for saved static records: endpoint=\""
                + oneLine(confirmed.getEndpointFingerprint()) + "\", shortName=\""
                + oneLine(confirmed.getShortName()) + "\", records="
                + result.getRepairedCount());
        pendingEvents.add(PREFIX + "Confirmed server identity for "
                + result.getRepairedCount() + " saved waypoint(s).");
    }

    WaypointManagerContext managerContext(HeadsUpDisplay hud,
                                           ServerIdentity identity) {
        requireReady();
        World world = requireWorld(hud);
        ServerIdentity server = requireServer(identity);
        return new WaypointManagerContext(requireUser(world), server,
                world.getPlayerPosX() / 4.0d, world.getPlayerPosY() / 4.0d,
                world.getPlayerPosH(), world.getPlayerLayer() < 0
                ? WaypointLayer.CAVE : WaypointLayer.SURFACE);
    }

    WaypointManagerSnapshot managerSnapshot(WaypointManagerQuery query) {
        requireReady();
        return managerView.snapshot(manager.snapshot(), query);
    }

    WaypointManagerSnapshot managerSnapshot(WaypointManagerQuery query,
                                             List<WaypointRecord> systemRecords) {
        requireReady();
        List<WaypointRecord> combined = new ArrayList<WaypointRecord>(
                manager.snapshot().size() + (systemRecords == null
                        ? 0 : systemRecords.size()));
        if (systemRecords != null) combined.addAll(systemRecords);
        combined.addAll(manager.snapshot());
        return managerView.snapshot(combined, query);
    }

    WaypointEditData editData(UUID id) {
        requireReady();
        WaypointRecord record = manager.find(id);
        if (record == null) throw new IllegalArgumentException("waypoint does not exist: " + id);
        WaypointCoordinate coordinate = record.getCoordinate();
        if (coordinate == null) throw new IllegalArgumentException(
                "waypoint has no editable exact coordinates");
        return new WaypointEditData(record.getId(), record.getName(),
                coordinate.getTileX(), coordinate.getTileY(), coordinate.getLayer(),
                record.getMarkerStyle(), record.getArrivalRadiusMetres(),
                record.getExpiresAt());
    }

    ParsedCoordinate previewCoordinate(String input) {
        requireReady();
        return parser.parse(input, configuration.getMapBounds());
    }

    MarkerStyle managerPreviewStyle(UUID editingId, MarkerStyle requested) {
        MarkerStyle style = requireMarkerStyle(requested);
        WaypointRecord record = editingId == null ? null : manager.find(editingId);
        return surroundingsMarkerStyle(record, style);
    }

    void addHereFromManager(String name, MarkerStyle markerStyle,
                            int arrivalRadiusMetres,
                            int lifetimeMinutes,
                            HeadsUpDisplay hud,
                            ServerIdentity identity) {
        WaypointManagerContext context = managerContext(hud, identity);
        Instant now = Instant.now();
        addStatic(name, new WaypointCoordinate(context.getTileX(), context.getTileY(),
                Double.valueOf(context.getHeight()), context.getLayer()),
                context.getUser(), context.getServer(), "manager-here", "",
                requireMarkerStyle(markerStyle),
                WaypointArrival.requireRadius(arrivalRadiusMetres),
                WaypointLifetime.resolve(lifetimeMinutes, null, now, false),
                now, hud);
    }

    void addCoordinatesFromManager(String name, String input,
                                   MarkerStyle markerStyle,
                                   int arrivalRadiusMetres,
                                   int lifetimeMinutes,
                                   HeadsUpDisplay hud,
                                   ServerIdentity identity) {
        WaypointManagerContext context = managerContext(hud, identity);
        ParsedCoordinate parsed = previewCoordinate(input);
        Instant now = Instant.now();
        addStatic(name, parsed.getCoordinate(), context.getUser(), context.getServer(),
                "manager-" + parsed.getSourceKind(), parsed.getServerHint(),
                requireMarkerStyle(markerStyle),
                WaypointArrival.requireRadius(arrivalRadiusMetres),
                WaypointLifetime.resolve(lifetimeMinutes, null, now, false),
                now, hud);
    }

    void editStaticFromManager(UUID id, String name, String input,
                               MarkerStyle markerStyle,
                               int arrivalRadiusMetres,
                               int lifetimeMinutes,
                               HeadsUpDisplay hud) {
        requireReady();
        WaypointRecord old = manager.find(id);
        if (old == null) throw new IllegalArgumentException("waypoint does not exist: " + id);
        ParsedCoordinate parsed = previewCoordinate(input);
        Instant now = Instant.now();
        WaypointRecord changed = WaypointRecord.copyOf(old).name(name)
                .coordinate(parsed.getCoordinate()).resolution(WaypointResolution.STATIC_EXACT)
                .markerStyle(surroundingsMarkerStyle(old,
                        requireMarkerStyle(markerStyle)))
                .arrivalRadiusMetres(WaypointArrival.requireRadius(
                        arrivalRadiusMetres))
                .expiresAt(WaypointLifetime.resolve(lifetimeMinutes,
                        old.getExpiresAt(), now, true))
                .updatedAt(now).lastResolvedAt(now).build();
        manager.update(changed);
        refreshNextExpiry();
        scheduleSave("manager edit " + changed.getId());
        message(hud, "Updated: " + changed.getName() + " [" + changed.getId()
                + "] X=" + changed.getCoordinate().getTileX()
                + " Y=" + changed.getCoordinate().getTileY()
                + " arrival=" + changed.getArrivalRadiusMetres() + "m");
    }

    void duplicateFromManager(UUID id, HeadsUpDisplay hud) {
        requireReady();
        WaypointRecord record = manager.duplicate(id, Instant.now());
        refreshNextExpiry();
        scheduleSave("manager duplicate " + record.getId());
        message(hud, "Duplicate created: " + record.getName()
                + " [" + record.getId() + "]");
    }

    void shareFromManager(UUID id, HeadsUpDisplay hud) {
        requireReady();
        WaypointRecord record = manager.find(id);
        if (record == null) throw new IllegalArgumentException(
                "waypoint does not exist: " + id);
        String token = shares.encode(record);
        boolean copied = true;
        try { Computer.setClipboardContents(token); }
        catch (Throwable failure) {
            copied = false;
            logger.log(Level.FINE, "Unable to copy shared waypoint token", failure);
        }
        message(hud, (copied ? "Share: " : "Share (copy manually): ") + token);
    }

    void importSharedClipboardFromManager(HeadsUpDisplay hud,
                                          ServerIdentity identity) {
        requireReady();
        WaypointManagerContext context = managerContext(hud, identity);
        WaypointShareCodec.SharedWaypoint shared = shares.decode(clipboardText());
        Instant now = Instant.now();
        addStatic(shared.getName(), shared.getCoordinate(), context.getUser(),
                shared.getServerIdentity(), "shared-clipboard", "",
                shared.getMarkerStyle(), shared.getArrivalRadiusMetres(),
                requireLiveSharedExpiry(shared.getExpiresAt(), now), now, hud);
    }

    void setEnabledFromManager(UUID id, boolean enabled, HeadsUpDisplay hud) {
        requireReady();
        WaypointRecord record = manager.setEnabled(id, enabled, Instant.now());
        scheduleSave("manager " + (enabled ? "enable " : "disable ") + id);
        message(hud, (enabled ? "Enabled: " : "Disabled: ") + record.getName()
                + ". Navigation registry updated.");
    }

    void setEnabledFromManager(List<UUID> ids, boolean enabled,
                               HeadsUpDisplay hud) {
        requireReady();
        if (ids == null) throw new IllegalArgumentException("waypoint ids are required");
        Instant now = Instant.now();
        int changed = 0;
        for (UUID id : ids) {
            WaypointRecord old = manager.find(id);
            if (old != null && old.isEnabled() != enabled) {
                manager.setEnabled(id, enabled, now);
                changed++;
            }
        }
        if (changed > 0) {
            scheduleSave("manager bulk " + (enabled ? "enable" : "disable")
                    + " count=" + changed);
        }
        message(hud, (enabled ? "Enabled " : "Disabled ") + changed
                + " filtered waypoint(s). Navigation registry updated.");
    }

    void deleteFromManager(UUID id, HeadsUpDisplay hud) {
        requireReady();
        WaypointRecord record = manager.find(id);
        if (record == null) throw new IllegalArgumentException("waypoint does not exist: " + id);
        manager.delete(id);
        refreshNextExpiry();
        scheduleSave("manager delete " + id);
        message(hud, "Deleted: " + record.getName() + " [" + id + "]");
    }

    int setSurroundingsWaypoints(Collection<SurroundingEntry> entries,
                                 Collection<SurroundingKey> keys,
                                 boolean enabled, HeadsUpDisplay hud,
                                 ServerIdentity identity) {
        WaypointManagerContext context = enabled ? managerContext(hud, identity) : null;
        return setSurroundingsWaypoints(entries, keys, enabled, context, Instant.now());
    }

    int setSurroundingsWaypoints(Collection<SurroundingEntry> entries,
                                 Collection<SurroundingKey> keys,
                                 boolean enabled, WaypointManagerContext context,
                                 Instant now) {
        requireReady();
        if (keys == null) throw new IllegalArgumentException(
                "surroundings waypoint keys are required");
        if (now == null) throw new IllegalArgumentException("current time is required");
        Set<SurroundingKey> selected = new LinkedHashSet<SurroundingKey>(keys);
        int changed = 0;
        if (enabled) {
            if (context == null) throw new IllegalArgumentException(
                    "waypoint context is required");
            Map<SurroundingKey, WaypointRecord> existingByKey =
                    new LinkedHashMap<SurroundingKey, WaypointRecord>();
            List<WaypointRecord> currentRecords = manager.snapshot();
            int recordCount = currentRecords.size();
            Set<UUID> existingIds = new LinkedHashSet<UUID>();
            for (WaypointRecord record : currentRecords) {
                existingIds.add(record.getId());
                SurroundingKey key = surroundingsKey(record);
                if (key != null && !existingByKey.containsKey(key)) {
                    existingByKey.put(key, record);
                }
            }
            Set<SurroundingKey> additions = new LinkedHashSet<SurroundingKey>();
            if (entries != null) for (SurroundingEntry entry : entries) {
                if (entry != null && selected.contains(entry.getKey())
                        && !existingByKey.containsKey(entry.getKey())) {
                    additions.add(entry.getKey());
                }
            }
            if (recordCount + additions.size() > WaypointLimits.MAX_RECORDS) {
                throw new IllegalArgumentException(
                        "waypoint limit reached (" + WaypointLimits.MAX_RECORDS + ")");
            }
            if (entries != null) for (SurroundingEntry entry : entries) {
                if (entry == null || !selected.contains(entry.getKey())) continue;
                WaypointRecord existing = existingByKey.get(entry.getKey());
                WaypointRecord record = surroundingsRecord(
                        entry, context, existing, existingIds, now);
                if (existing == null) {
                    manager.add(record);
                    existingByKey.put(entry.getKey(), record);
                    existingIds.add(record.getId());
                } else manager.update(record);
                changed++;
            }
        } else {
            List<WaypointRecord> snapshot = manager.snapshot();
            for (WaypointRecord record : snapshot) {
                SurroundingKey key = surroundingsKey(record);
                if (key != null && selected.contains(key)
                        && manager.delete(record.getId())) {
                    changed++;
                }
            }
        }
        if (changed > 0) {
            refreshNextExpiry();
            scheduleSave("surroundings " + (enabled ? "mark" : "clear")
                    + " count=" + changed);
        }
        return changed;
    }

    int clearSurroundingsWaypoints() {
        requireReady();
        int changed = 0;
        for (WaypointRecord record : manager.snapshot()) {
            if (surroundingsKey(record) != null && manager.delete(record.getId())) {
                changed++;
            }
        }
        if (changed > 0) {
            refreshNextExpiry();
            scheduleSave("surroundings clear all count=" + changed);
        }
        return changed;
    }

    Set<SurroundingKey> surroundingsWaypointKeys() {
        LinkedHashSet<SurroundingKey> result = new LinkedHashSet<SurroundingKey>();
        for (WaypointRecord record : manager.snapshot()) {
            SurroundingKey key = surroundingsKey(record);
            if (key != null) result.add(key);
        }
        return Collections.unmodifiableSet(result);
    }

    void exportFromManager(HeadsUpDisplay hud) {
        requireReady();
        exportAsync();
        message(hud, "Export queued: " + absolute(configuration.getTransferFile()));
    }

    void importFromManager(HeadsUpDisplay hud) {
        requireReady();
        importAsync();
        message(hud, "Import queued: " + absolute(configuration.getTransferFile()));
    }

    long revision() { return manager.revision(); }

    WaypointRevisionSnapshot revisionSnapshot() {
        WaypointRevisionSnapshot cached = cachedRevisionSnapshot;
        long currentRevision = manager.revision();
        if (cached != null && cached.getRevision() == currentRevision) return cached;
        synchronized (this) {
            cached = cachedRevisionSnapshot;
            currentRevision = manager.revision();
            if (cached == null || cached.getRevision() != currentRevision) {
                cached = manager.revisionSnapshot();
                cachedRevisionSnapshot = cached;
            }
            return cached;
        }
    }

    void reportManagerFailure(String operation, Throwable failure,
                              HeadsUpDisplay hud) {
        String diagnostic = "Phase 1 Manager action failed open: operation=\""
                + oneLine(operation) + "\", failure="
                + (failure == null ? "unknown" : failure.getClass().getName())
                + ", reason=\"" + oneLine(safeMessage(failure)) + "\"";
        if (failure instanceof IllegalArgumentException) logger.info(diagnostic);
        else if (failure == null) logger.warning(diagnostic);
        else logger.log(Level.WARNING, diagnostic, failure);
        message(hud, "Manager error: " + safeMessage(failure));
    }

    private void addStatic(String name, WaypointCoordinate coordinate, String user,
                           ServerIdentity server, String source, String serverHint,
                           HeadsUpDisplay hud) {
        addStatic(name, coordinate, user, server, source, serverHint,
                MarkerStyle.defaultColoredBeam(),
                WaypointArrival.DEFAULT_RADIUS_METRES, null, Instant.now(), hud);
    }

    private void addStatic(String name, WaypointCoordinate coordinate, String user,
                           ServerIdentity server, String source, String serverHint,
                           MarkerStyle markerStyle, int arrivalRadiusMetres,
                           Instant expiresAt, Instant now, HeadsUpDisplay hud) {
        WaypointRecord record = WaypointRecord.builder().name(name).description("")
                .createdByUser(user).serverIdentity(server)
                .sourceType(WaypointSourceType.STATIC).sourceKey("")
                .coordinate(coordinate).resolution(WaypointResolution.STATIC_EXACT)
                .markerStyle(markerStyle)
                .arrivalRadiusMetres(WaypointArrival.requireRadius(
                        arrivalRadiusMetres))
                .expiresAt(expiresAt)
                .createdAt(now).updatedAt(now).lastResolvedAt(now).build();
        manager.add(record);
        refreshNextExpiry();
        scheduleSave("add " + record.getId());
        String hint = serverHint.isEmpty() ? "" : ", map/server hint=" + serverHint;
        message(hud, "Saved: " + record.getName() + " [" + record.getId()
                + "] X=" + coordinate.getTileX() + " Y=" + coordinate.getTileY()
                + " layer=" + coordinate.getLayer() + ", arrival="
                + record.getArrivalRadiusMetres() + "m, lifetime="
                + (expiresAt == null ? "Permanent" : expiresAt.toString())
                + ", source=" + source + hint);
    }

    private WaypointRecord surroundingsRecord(SurroundingEntry entry,
                                              WaypointManagerContext context,
                                              WaypointRecord existing,
                                              Set<UUID> existingIds,
                                              Instant now) {
        UUID id = existing == null
                ? SurroundingsRuntime.stableId(entry.getKey()) : existing.getId();
        while (existing == null && existingIds.contains(id)) id = UUID.randomUUID();
        Instant created = existing == null ? now : existing.getCreatedAt();
        String description = "Surroundings " + entry.getCategory() + "; "
                + entry.getDeedStatus().getLabel() + "; 15-minute snapshot";
        return WaypointRecord.builder().id(id).name(limitName(entry.getName()))
                .description(description).createdByUser(context.getUser())
                .serverIdentity(context.getServer())
                .sourceType(entry.getKind() == SurroundingKind.ANIMAL
                        ? WaypointSourceType.MANAGED_ANIMAL
                        : WaypointSourceType.MANAGED_ITEM)
                .sourceKey(entry.getKey().toString())
                .coordinate(new WaypointCoordinate(entry.getWorldX() / 4.0d,
                        entry.getWorldY() / 4.0d, entry.getHeight(),
                        entry.getLayer() < 0 ? WaypointLayer.CAVE
                                : WaypointLayer.SURFACE))
                .resolution(WaypointResolution.STATIC_EXACT).enabled(true)
                .markerStyle(SurroundingsRuntime.style(entry.getKind()))
                .arrivalRadiusMetres(WaypointArrival.DISABLED)
                .expiresAt(now.plusSeconds(SURROUNDINGS_LIFETIME_SECONDS))
                .group("Surroundings / " + entry.getKind().name())
                .createdAt(created).updatedAt(now).lastResolvedAt(now).build();
    }

    private static SurroundingKey surroundingsKey(WaypointRecord record) {
        if (record == null || (record.getSourceType()
                != WaypointSourceType.MANAGED_ANIMAL
                && record.getSourceType() != WaypointSourceType.MANAGED_ITEM)) {
            return null;
        }
        return SurroundingKey.parse(record.getSourceKey());
    }

    private static MarkerStyle surroundingsMarkerStyle(WaypointRecord record,
                                                        MarkerStyle style) {
        if (record == null || (record.getSourceType()
                != WaypointSourceType.MANAGED_ANIMAL
                && record.getSourceType() != WaypointSourceType.MANAGED_ITEM)) {
            return style;
        }
        if (style.getWorldStyle() == MarkerStyle.WorldStyle.EXCLAMATION) {
            return style;
        }
        return new MarkerStyle(MarkerStyle.WorldStyle.EXCLAMATION,
                style.getRed(), style.getGreen(), style.getBlue(),
                style.getAlpha(), style.getMarkerSize(), style.getBeamWidth(),
                style.isShowLabel(), style.isShowDistance());
    }

    private static String limitName(String value) {
        String clean = oneLine(value);
        if (clean.isEmpty()) clean = "Surroundings object";
        return clean.length() <= WaypointLimits.MAX_NAME ? clean
                : clean.substring(0, WaypointLimits.MAX_NAME);
    }

    private void listCurrent(HeadsUpDisplay hud, World world, String user,
                             ServerIdentity server) {
        List<WaypointRecord> records = manager.filtered(WaypointFilter.builder()
                .currentServer(server).user(user).build());
        message(hud, "Waypoints for the current user/server: " + records.size());
        int shown = Math.min(20, records.size());
        for (int i = 0; i < shown; i++) {
            WaypointRecord record = records.get(i);
            int metres = distanceMetres(world, record.getCoordinate());
            message(hud, record.getName() + " - " + metres + "m [" + record.getId()
                    + "] " + (record.isEnabled() ? "On" : "Off"));
        }
        if (records.size() > shown) message(hud, (records.size() - shown)
                + " more waypoints are not shown; open Waypoint Manager for the full list.");
    }

    private void rename(String[] args, HeadsUpDisplay hud) {
        String joined = WaypointCommandArguments.join(args, 1, args.length);
        int split = joined.lastIndexOf('|');
        if (split <= 0 || split + 1 >= joined.length()) {
            throw new IllegalArgumentException("usage: /wp rename <UUID|exact name> | <new name>");
        }
        WaypointRecord old = requireRecord(joined.substring(0, split).trim());
        String name = joined.substring(split + 1).trim();
        WaypointRecord changed = WaypointRecord.copyOf(old).name(name)
                .updatedAt(Instant.now()).build();
        manager.update(changed);
        scheduleSave("rename " + changed.getId());
        message(hud, "Renamed: " + changed.getName() + " [" + changed.getId() + "]");
    }

    private void scheduleSave(final String reason) {
        final WaypointStore destination = store;
        final WaypointDocument snapshot = new WaypointDocument(manager.snapshot(), opaqueRecords);
        storageExecutor.execute(new Runnable() {
            @Override public void run() {
                try {
                    destination.save(snapshot);
                    logger.info("Phase 1 static store saved: reason=\"" + oneLine(reason)
                            + "\", path=\"" + absolute(destination.getFile())
                            + "\", records=" + snapshot.getRecords().size()
                            + ", opaqueRecords=" + snapshot.getOpaqueRecords().size()
                            + ", atomicFallback=" + destination.usedAtomicFallback());
                } catch (Throwable failure) {
                    logger.log(Level.WARNING, "Unable to save Phase 1 static waypoint store", failure);
                    pendingEvents.add(PREFIX + "Unable to save waypoints; see client.log.");
                }
            }
        });
    }

    private void exportAsync() {
        final WaypointStore destination = transferStore;
        final List<WaypointRecord> records = manager.snapshot();
        storageExecutor.execute(new Runnable() {
            @Override public void run() {
                try {
                    transfers.exportTo(destination, records);
                    logger.info("Waypoint export completed: path=\""
                            + absolute(destination.getFile()) + "\", records=" + records.size());
                    pendingEvents.add(PREFIX + "Export completed: "
                            + absolute(destination.getFile()) + " (" + records.size() + ")");
                } catch (Throwable failure) {
                    logger.log(Level.WARNING, "Waypoint export failed", failure);
                    pendingEvents.add(PREFIX + "Export failed; see client.log.");
                }
            }
        });
    }

    private void importAsync() {
        final WaypointStore source = transferStore;
        storageExecutor.execute(new Runnable() {
            @Override public void run() {
                try {
                    WaypointTransferService.ImportResult result = transfers.importFrom(
                            source, manager, Instant.now());
                    refreshNextExpiry();
                    List<OpaqueWaypointRecord> mergedOpaque =
                            new ArrayList<OpaqueWaypointRecord>(opaqueRecords);
                    mergedOpaque.addAll(result.getOpaqueRecords());
                    opaqueRecords = immutableOpaque(mergedOpaque);
                    store.save(new WaypointDocument(manager.snapshot(), opaqueRecords));
                    logger.info("Waypoint import completed: path=\""
                            + absolute(source.getFile()) + "\", imported="
                            + result.getImported() + ", skippedExisting="
                            + result.getSkippedExisting() + ", preservedOpaque="
                            + result.getPreservedOpaque());
                    pendingEvents.add(PREFIX + "Import completed: imported "
                            + result.getImported() + ", already existed "
                            + result.getSkippedExisting() + ", preserved future records "
                            + result.getPreservedOpaque() + ".");
                } catch (Throwable failure) {
                    logger.log(Level.WARNING, "Waypoint import failed", failure);
                    pendingEvents.add(PREFIX + "Import failed; see client.log.");
                }
            }
        });
    }

    private WaypointRecord requireRecord(String reference) {
        WaypointRecord record = manager.findByIdOrExactName(reference);
        if (record == null) throw new IllegalArgumentException("waypoint not found: " + reference);
        return record;
    }

    private void refreshNextExpiry() {
        nextExpiryAtMillis = manager.nextExpiryEpochMilli();
    }

    private static Instant requireLiveSharedExpiry(Instant expiresAt, Instant now) {
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("shared temporary waypoint expired at "
                    + expiresAt + "; ask the sender for a new WWP1 line");
        }
        return expiresAt;
    }

    private static String reference(String[] args) {
        String value = WaypointCommandArguments.join(args, 1, args == null ? 0 : args.length);
        if (value.isEmpty()) throw new IllegalArgumentException("specify a waypoint UUID or exact name");
        return value;
    }

    private static World requireWorld(HeadsUpDisplay hud) {
        if (hud == null || hud.getWorld() == null) {
            throw new IllegalStateException("HUD/world is not ready yet");
        }
        return hud.getWorld();
    }

    private static ServerIdentity requireServer(ServerIdentity identity) {
        if (identity == null || identity.getEndpoint() == null) {
            throw new IllegalStateException("server endpoint is not confirmed yet");
        }
        return identity;
    }

    private static String requireUser(World world) {
        String user = clean(world.getUsername());
        if (user.isEmpty()) throw new IllegalStateException("player name is not ready yet");
        return user;
    }

    private void requireReady() {
        if (!ready) throw new IllegalStateException("local waypoint storage is unavailable");
    }

    private static int distanceMetres(World world, WaypointCoordinate coordinate) {
        if (coordinate == null) return 0;
        double dx = coordinate.worldX() - world.getPlayerPosX();
        double dy = coordinate.worldY() - world.getPlayerPosY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance >= Integer.MAX_VALUE ? Integer.MAX_VALUE
                : Math.max(0, (int) Math.round(distance));
    }

    String clipboardText() {
        String value = Computer.readClipboardContents();
        if (clean(value).isEmpty()) {
            throw new IllegalArgumentException("clipboard does not contain text coordinates");
        }
        logger.info("Phase 1 clipboard text received through Wurm input API: characters="
                + value.length());
        return value;
    }

    private static void message(HeadsUpDisplay hud, String text) {
        if (hud == null) return;
        try { hud.textMessage(":Event", 0.35f, 0.85f, 1.0f, PREFIX + oneLine(text)); }
        catch (Throwable ignored) { }
    }

    private static List<OpaqueWaypointRecord> immutableOpaque(
            List<OpaqueWaypointRecord> values) {
        return Collections.unmodifiableList(new ArrayList<OpaqueWaypointRecord>(values));
    }

    private static String safeMessage(Throwable failure) {
        String value = failure == null ? "unknown error" : clean(failure.getMessage());
        return value.isEmpty() ? failure.getClass().getSimpleName() : value;
    }

    private static String absolute(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static MarkerStyle requireMarkerStyle(MarkerStyle value) {
        if (value == null) throw new IllegalArgumentException("marker style is required");
        return value;
    }
    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
