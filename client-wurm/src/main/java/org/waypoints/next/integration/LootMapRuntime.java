package org.waypoints.next.integration;

import org.waypoints.next.lootmap.LootMapDecision;
import org.waypoints.next.lootmap.LootMapDistanceBand;
import org.waypoints.next.lootmap.LootMapHuntSession;
import org.waypoints.next.lootmap.LootMapMessage;
import org.waypoints.next.lootmap.LootMapMessageParser;
import org.waypoints.next.lootmap.LootMapObservation;
import org.waypoints.next.lootmap.LootMapTerrain;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.UncertaintyObservation;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.source.MapBounds;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Client adapter for TreasureHunting Event messages and dynamic navigation. */
final class LootMapRuntime implements DynamicWaypointProvider {
    private final Logger logger;
    private final LootMapMessageParser parser = new LootMapMessageParser();
    private final ConcurrentLinkedQueue<String> messages =
            new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<NavigationTargetKey> navigationRequests =
            new ConcurrentLinkedQueue<NavigationTargetKey>();
    private final ConcurrentLinkedQueue<Boolean> digChimes =
            new ConcurrentLinkedQueue<Boolean>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "WurmWaypointer-LootMap");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private boolean enabled = true;
    private Path logDirectory;
    private MapBounds bounds;
    private LootMapHuntSession hunt;
    private WaypointRecord record;
    private long revision;
    private long combinedRevision;
    private long combinedBaseRevision = Long.MIN_VALUE;
    private long combinedLootRevision = Long.MIN_VALUE;
    private Long pendingMapItemId;
    private long pendingMapActionAtNanos;
    private Long activeMapItemId;
    private boolean awaitingChestOpen;
    private Long pendingChestOpenItemId;
    private long pendingChestOpenActionAtNanos;
    private boolean exactReadingChimed;

    LootMapRuntime(Logger logger) {
        this.logger = logger;
    }

    @Override public synchronized void configure(
            WaypointClientConfiguration configuration) {
        enabled = configuration.isLootMapEnabled();
        logDirectory = configuration.getLootMapLogDirectory();
        bounds = configuration.getMapBounds();
        revision++;
        logger.info("Loot Map assistant configured: enabled=" + enabled
                + ", logDirectory=\"" + logDirectory.toAbsolutePath().normalize()
                + "\", mapBounds=" + bounds.getWidth() + "x" + bounds.getHeight());
    }

    boolean observe(String tab, String text, EventContext context) {
        if (!enabled) return false;
        final LootMapMessage message = parser.parse(tab, text);
        if (message == null) return false;
        if (message.getKind() != LootMapMessage.Kind.CHEST_DUG_UP
                && (context == null || context.server == null
                || !context.server.isSafeForAutomaticRendering())) {
            logger.fine("Loot Map event ignored until server identity is resolved");
            return true;
        }
        final EventContext correlated = context == null ? null
                : context.withMapItemId(correlatedMapItemId());
        worker.execute(new Runnable() {
            @Override public void run() { process(message, correlated); }
        });
        return true;
    }

    private void process(LootMapMessage message, EventContext context) {
        try {
            if (message.getKind() == LootMapMessage.Kind.CHEST_DUG_UP) {
                chestDugUp(context == null ? Instant.now() : context.at);
                return;
            }
            LootMapObservation observation = new LootMapObservation(
                    context.tileX, context.tileY, context.facing,
                    message.getDirection(), message.getBand(), context.at);
            LootMapHuntSession current;
            boolean mapChanged;
            synchronized (this) {
                mapChanged = context.mapItemId != null && activeMapItemId != null
                        && !context.mapItemId.equals(activeMapItemId);
            }
            if (mapChanged) {
                finish("map_changed", context.at);
                messages.add("A different Loot Map was read; the previous hunt log was closed.");
            }
            synchronized (this) {
                if (hunt == null || hunt.isClosed()) {
                    hunt = new LootMapHuntSession(logDirectory, bounds, observation);
                    exactReadingChimed = false;
                    messages.add("Loot Map hunt started; log: "
                            + hunt.getLogFile().getFileName());
                }
                if (context.mapItemId != null) activeMapItemId = context.mapItemId;
                current = hunt;
            }
            LootMapDecision decision = current.observe(observation,
                    context.terrain);
            boolean digForLoot = message.getBand() == LootMapDistanceBand.EXACT;
            WaypointRecord next = record(context, current, decision, digForLoot);
            boolean playDigChime = false;
            synchronized (this) {
                record = next;
                revision++;
                if (digForLoot && !exactReadingChimed) {
                    exactReadingChimed = true;
                    playDigChime = true;
                }
            }
            if (playDigChime) digChimes.add(Boolean.TRUE);
            navigationRequests.add(new NavigationTargetKey(
                    next.getServerIdentity().getEndpointFingerprint(), next.getId()));
            String mode = decision.getMode().name().toLowerCase(
                    java.util.Locale.ENGLISH).replace('_', '-');
            String landAdjustment = decision.isLandAdjusted()
                    ? "; moved " + Math.round(decision.getLandAdjustmentTiles())
                    + " tiles from water to dry land" : "";
            messages.add("Loot Map reading " + current.getReadingCount()
                    + ": reported loot range "
                    + message.getBand().displayRangeTiles()
                    + "; next reading point "
                    + Math.round(decision.getWalkTiles())
                    + " tiles away (" + mode + landAdjustment + ").");
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Loot Map event processing failed open", failure);
            messages.add("Loot Map assistant could not process this reading; "
                    + "the game action was not affected.");
        }
    }

    @Override public synchronized WaypointRevisionSnapshot combine(
            WaypointRevisionSnapshot base) {
        if (combinedBaseRevision != base.getRevision()
                || combinedLootRevision != revision) {
            combinedBaseRevision = base.getRevision();
            combinedLootRevision = revision;
            combinedRevision++;
        }
        List<WaypointRecord> values = new ArrayList<WaypointRecord>(
                base.getRecords().size() + (record == null ? 0 : 1));
        values.addAll(base.getRecords());
        if (record != null) values.add(record);
        return new WaypointRevisionSnapshot(combinedRevision, values);
    }

    synchronized List<WaypointRecord> records() {
        return record == null ? Collections.<WaypointRecord>emptyList()
                : Collections.singletonList(record);
    }

    @Override public String pollMessage() { return messages.poll(); }

    @Override public NavigationTargetKey pollNavigationRequest() {
        return navigationRequests.poll();
    }

    boolean pollDigChime() { return digChimes.poll() != null; }

    @Override public synchronized void observeAction(long[] targets,
                                                      String actionName) {
        if (!enabled || actionName == null || targets == null
                || targets.length == 0) return;
        String normalized = actionName.trim();
        if ("read map".equalsIgnoreCase(normalized)) {
            pendingMapItemId = Long.valueOf(targets[0]);
            pendingMapActionAtNanos = System.nanoTime();
        } else if (awaitingChestOpen
                && "open".equalsIgnoreCase(normalized)) {
            pendingChestOpenItemId = Long.valueOf(targets[0]);
            pendingChestOpenActionAtNanos = System.nanoTime();
        }
    }

    /** Called only after Wurm has created an actually open container window. */
    void inventoryWindowOpened(final long itemId, final String windowName) {
        if (!enabled || !isTreasureChestWindow(windowName)) return;
        worker.execute(new Runnable() {
            @Override public void run() {
                boolean correlated;
                synchronized (LootMapRuntime.this) {
                    correlated = awaitingChestOpen
                            && pendingChestOpenItemId != null
                            && pendingChestOpenItemId.longValue() == itemId
                            && System.nanoTime() - pendingChestOpenActionAtNanos
                            <= 60_000_000_000L;
                }
                if (!correlated) return;
                finish("chest_opened", Instant.now());
                messages.add("Loot Map chest opened; waypoint and hunt log closed.");
            }
        });
    }

    private synchronized Long correlatedMapItemId() {
        return correlatedMapItemId(System.nanoTime());
    }

    synchronized Long correlatedMapItemId(long nowNanos) {
        if (pendingMapItemId == null) return activeMapItemId;
        long elapsed = nowNanos - pendingMapActionAtNanos;
        if (elapsed < 0L || elapsed > 60_000_000_000L) {
            pendingMapItemId = null;
            return activeMapItemId;
        }
        return pendingMapItemId;
    }

    private void chestDugUp(Instant at) throws java.io.IOException {
        LootMapHuntSession current;
        WaypointRecord currentRecord;
        synchronized (this) {
            if (awaitingChestOpen || hunt == null || record == null) return;
            awaitingChestOpen = true;
            pendingChestOpenItemId = null;
            current = hunt;
            Map<String, List<String>> extensions =
                    new LinkedHashMap<String, List<String>>(record.getExtensions());
            extensions.put("lootmap.phase",
                    Collections.singletonList("AWAITING_CHEST_OPEN"));
            currentRecord = WaypointRecord.copyOf(record)
                    .name("Clear ambush and open chest")
                    .description("The chest is here. Defeat its guardians and open it.")
                    .updatedAt(at).extensions(extensions).build();
            record = currentRecord;
            revision++;
        }
        current.event("chest_dug_up", at);
        navigationRequests.add(new NavigationTargetKey(
                currentRecord.getServerIdentity().getEndpointFingerprint(),
                currentRecord.getId()));
        messages.add("Loot Map chest found; clear the ambush and open the chest.");
    }

    private static boolean isTreasureChestWindow(String name) {
        if (name == null) return false;
        String normalized = name.trim().toLowerCase(java.util.Locale.ENGLISH);
        return normalized.contains("loot chest")
                || normalized.contains("treasure chest");
    }

    boolean handleConsoleCommand(String command, String[] arguments) {
        String name = command == null ? "" : command.trim();
        if (name.startsWith("/")) name = name.substring(1);
        if (!"wp".equalsIgnoreCase(name) && !"waypoint".equalsIgnoreCase(name)) {
            return false;
        }
        String[] args = WaypointCommandArguments.withoutRepeatedCommand(name, arguments);
        if (args.length == 0 || !"loot".equalsIgnoreCase(args[0])) return false;
        String operation = args.length < 2 ? "status" : args[1].trim();
        if ("reset".equalsIgnoreCase(operation)
                || "dismiss".equalsIgnoreCase(operation)) {
            dismiss();
            messages.add("Loot Map hunt dismissed; the current log file was closed.");
        } else if ("status".equalsIgnoreCase(operation)) {
            LootMapHuntSession current;
            synchronized (this) { current = hunt; }
            messages.add(current == null ? "No active Loot Map hunt."
                    : "Active Loot Map hunt: readings=" + current.getReadingCount()
                    + ", log=" + current.getLogFile().getFileName());
        } else {
            messages.add("Usage: /wp loot status | /wp loot reset");
        }
        return true;
    }

    @Override public void connectionEnded() {
        // The client JVM may terminate immediately after this callback. Closing
        // synchronously guarantees the final JSONL line is flushed to disk.
        finish("connection_ended", Instant.now());
    }

    @Override public String navigationReason() { return "Loot Map reading"; }

    void dismiss() {
        worker.execute(new Runnable() {
            @Override public void run() { finish("dismissed", Instant.now()); }
        });
    }

    private void finish(String event, Instant at) {
        LootMapHuntSession current;
        synchronized (this) {
            current = hunt;
            hunt = null;
            activeMapItemId = null;
            pendingMapItemId = null;
            awaitingChestOpen = false;
            pendingChestOpenItemId = null;
            exactReadingChimed = false;
            navigationRequests.clear();
            if (record != null) { record = null; revision++; }
        }
        if (current == null) return;
        try { current.close(event, at); }
        catch (Throwable failure) {
            logger.log(Level.WARNING, "Unable to close Loot Map hunt log", failure);
        }
    }

    private static WaypointRecord record(EventContext context,
                                         LootMapHuntSession hunt,
                                         LootMapDecision decision,
                                         boolean digForLoot) {
        Instant created = hunt.getObservations().get(0).getObservedAt();
        List<UncertaintyObservation> uncertainty =
                new ArrayList<UncertaintyObservation>();
        double diagonal = Math.hypot(context.mapWidth, context.mapHeight);
        for (LootMapObservation observation : hunt.getObservations()) {
            LootMapDistanceBand band = observation.getBand();
            uncertainty.add(new UncertaintyObservation(
                    observation.getAbsoluteSectorDegrees(), 22.5d,
                    band.getMinimum(), band.isFinite() ? band.getMaximum() : diagonal,
                    observation.getObservedAt()));
        }
        Map<String, List<String>> extensions =
                new LinkedHashMap<String, List<String>>();
        extensions.put("lootmap.sessionId",
                Collections.singletonList(hunt.getId().toString()));
        extensions.put("lootmap.mode",
                Collections.singletonList(decision.getMode().name()));
        extensions.put("lootmap.logFile",
                Collections.singletonList(hunt.getLogFile().getFileName().toString()));
        extensions.put("lootmap.readingCount",
                Collections.singletonList(Integer.toString(
                        hunt.getReadingCount())));
        if (decision.isLandAdjusted()) {
            extensions.put("lootmap.landAdjusted",
                    Collections.singletonList("true"));
            extensions.put("lootmap.plannedTile",
                    Collections.singletonList(Math.round(
                            decision.getPlannedWaypointX()) + ","
                            + Math.round(decision.getPlannedWaypointY())));
        }
        if (context.mapItemId != null) {
            extensions.put("lootmap.mapItemId",
                    Collections.singletonList(context.mapItemId.toString()));
        }
        return WaypointRecord.builder().id(hunt.getId())
                .name(digForLoot
                        ? "Dig for loot ! (use shovel on map)"
                        : decision.getMode() == LootMapDecision.Mode.FINAL_POINT
                        ? "Loot Map: search here" : "Loot Map: next reading")
                .description("Observation-only route minimizing expected map readings, then walking distance.")
                .createdByUser(context.user).serverIdentity(context.server)
                .sourceType(WaypointSourceType.LOOT_MAP)
                .sourceKey(hunt.getId().toString())
                .coordinate(new WaypointCoordinate(decision.getWaypointX() + 0.5d,
                        decision.getWaypointY() + 0.5d, null,
                        context.layer))
                .resolution(WaypointResolution.STATIC_EXACT)
                .uncertaintyObservations(uncertainty).enabled(true)
                .markerStyle(new MarkerStyle(digForLoot
                        ? MarkerStyle.WorldStyle.SHOVEL
                        : MarkerStyle.WorldStyle.LOOT_MAP_SCROLL,
                        1.0f, 0.62f, 0.08f, 0.92f, 22.0f, 4.8f, true, true))
                .arrivalRadiusMetres(WaypointArrival.DISABLED)
                .group("Loot Maps").createdAt(created).updatedAt(context.at)
                .lastResolvedAt(context.at).extensions(extensions).build();
    }

    static final class EventContext {
        private final double tileX;
        private final double tileY;
        private final double facing;
        private final double height;
        private final WaypointLayer layer;
        private final ServerIdentity server;
        private final String user;
        private final Instant at;
        private final int mapWidth;
        private final int mapHeight;
        private final Long mapItemId;
        private final LootMapTerrain terrain;

        EventContext(double tileX, double tileY, double facing, double height,
                     WaypointLayer layer, ServerIdentity server, String user,
                     Instant at, MapBounds bounds) {
            this(tileX, tileY, facing, height, layer, server, user, at,
                    bounds, null, null);
        }

        EventContext(double tileX, double tileY, double facing, double height,
                     WaypointLayer layer, ServerIdentity server, String user,
                     Instant at, MapBounds bounds, LootMapTerrain terrain) {
            this(tileX, tileY, facing, height, layer, server, user, at,
                    bounds, null, terrain);
        }

        private EventContext(double tileX, double tileY, double facing, double height,
                     WaypointLayer layer, ServerIdentity server, String user,
                     Instant at, MapBounds bounds, Long mapItemId,
                     LootMapTerrain terrain) {
            this.tileX = tileX; this.tileY = tileY; this.facing = facing;
            this.height = height; this.layer = layer; this.server = server;
            this.user = user == null || user.trim().isEmpty() ? "Wurm" : user.trim();
            this.at = at; this.mapWidth = bounds.getWidth();
            this.mapHeight = bounds.getHeight();
            this.mapItemId = mapItemId;
            this.terrain = terrain;
        }

        private EventContext withMapItemId(Long value) {
            return new EventContext(tileX, tileY, facing, height, layer,
                    server, user, at, new MapBounds(mapWidth, mapHeight), value,
                    terrain);
        }
    }
}
