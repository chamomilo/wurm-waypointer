package org.waypoints.next.integration;

import org.waypoints.next.archaeology.ArchaeologyDistanceBand;
import org.waypoints.next.archaeology.ArchaeologyMessage;
import org.waypoints.next.archaeology.ArchaeologyMessageParser;
import org.waypoints.next.archaeology.ArchaeologyPlanner;
import org.waypoints.next.archaeology.ArchaeologyReportSession;
import org.waypoints.next.archaeology.ArchaeologyReportStatus;
import org.waypoints.next.archaeology.ArchaeologySessionStore;
import org.waypoints.next.archaeology.KnownArchaeologyLocation;
import org.waypoints.next.archaeology.KnownArchaeologyLocationStore;
import org.waypoints.next.archaeology.KnownArchaeologyRegistry;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.source.MapBounds;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Client-only completed archaeology report lifecycle and exact-location registry. */
final class ArchaeologyRuntime implements DynamicWaypointProvider {
    enum SoundCue { REPORT_READY, CACHE_FOUND }

    private final Logger logger;
    private final ArchaeologyMessageParser parser = new ArchaeologyMessageParser();
    private final ArchaeologyPlanner planner = new ArchaeologyPlanner();
    private final ConcurrentLinkedQueue<String> messages =
            new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<NavigationTargetKey> navigationRequests =
            new ConcurrentLinkedQueue<NavigationTargetKey>();
    private final ConcurrentLinkedQueue<SoundCue> sounds =
            new ConcurrentLinkedQueue<SoundCue>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable,
                            "WurmWaypointer-Archaeology");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private final Map<UUID, ArchaeologyReportSession> sessions =
            new LinkedHashMap<UUID, ArchaeologyReportSession>();
    private final Map<String, UUID> activeKeys = new LinkedHashMap<String, UUID>();
    private final Map<UUID, WaypointRecord> records =
            new LinkedHashMap<UUID, WaypointRecord>();

    private boolean enabled = true;
    private boolean storageReady = true;
    private int historyLimit = 64;
    private MapBounds bounds = new MapBounds(4096, 4096);
    private ArchaeologySessionStore sessionStore;
    private KnownArchaeologyLocationStore locationStore;
    private KnownArchaeologyRegistry registry = new KnownArchaeologyRegistry();
    private long revision;
    private long combinedRevision;
    private long combinedBaseRevision = Long.MIN_VALUE;
    private long combinedArchaeologyRevision = Long.MIN_VALUE;
    private String boundServerFingerprint = "";
    private String boundUser = "";
    private ServerIdentity boundServer;
    private Long pendingReportItemId;
    private long pendingReportActionAtNanos;

    ArchaeologyRuntime(Logger logger) { this.logger = logger; }

    @Override public synchronized void configure(
            WaypointClientConfiguration configuration) {
        enabled = configuration.isArchaeologyEnabled();
        historyLimit = configuration.getArchaeologyHistoryLimit();
        bounds = configuration.getMapBounds();
        sessionStore = new ArchaeologySessionStore(
                configuration.getArchaeologySessionFile());
        locationStore = new KnownArchaeologyLocationStore(
                configuration.getArchaeologyKnownLocationsFile());
        sessions.clear();
        activeKeys.clear();
        records.clear();
        storageReady = true;
        try {
            for (ArchaeologyReportSession session : sessionStore.load()) {
                sessions.put(session.getId(), session);
            }
            rebuildActiveKeys();
            registry = new KnownArchaeologyRegistry(locationStore.load());
            if (sessionStore.wasRecoveredFromBackup()
                    || locationStore.wasRecoveredFromBackup()) {
                messages.add("Archaeology data was recovered from a backup file.");
            }
        } catch (Throwable failure) {
            storageReady = false;
            logger.log(Level.WARNING,
                    "Unable to load archaeology tracker state; persistence disabled",
                    failure);
            messages.add("Archaeology storage is unavailable; tracking remains fail-open but will not be saved.");
        }
        revision++;
        logger.info("Archaeology report tracker configured: enabled=" + enabled
                + ", sessionFile=\"" + sessionStore.getFile().toAbsolutePath().normalize()
                + "\", knownLocationFile=\""
                + locationStore.getFile().toAbsolutePath().normalize()
                + "\", historyLimit=" + historyLimit
                + ", loadedSessions=" + sessions.size()
                + ", knownLocations=" + registry.snapshot().size());
    }

    boolean observe(String tab, String text, EventContext context) {
        if (!enabled) return false;
        final ArchaeologyMessage message = parser.parse(tab, text);
        if (message == null) return false;
        logger.info("Archaeology Event recognized: kind=" + message.getKind()
                + ", deed=\"" + message.getDeedName() + "\"");
        if (context == null || context.server == null
                || !context.server.isSafeForAutomaticRendering()) {
            logger.fine("Archaeology event ignored until server identity is resolved");
            return true;
        }
        worker.execute(new Runnable() {
            @Override public void run() { process(message, context); }
        });
        return true;
    }

    synchronized void bind(EventContext context) {
        String nextServer = context == null || context.server == null ? ""
                : context.server.getEndpointFingerprint();
        String nextUser = context == null ? "" : normalizeUser(context.user);
        if (nextServer.equals(boundServerFingerprint)
                && nextUser.equalsIgnoreCase(boundUser)
                && (context == null || context.server == boundServer)) return;
        boundServerFingerprint = nextServer;
        boundUser = nextUser;
        boundServer = context == null ? null : context.server;
        rebuildRecords(context);
    }

    @Override public synchronized WaypointRevisionSnapshot combine(
            WaypointRevisionSnapshot base) {
        if (combinedBaseRevision != base.getRevision()
                || combinedArchaeologyRevision != revision) {
            combinedBaseRevision = base.getRevision();
            combinedArchaeologyRevision = revision;
            combinedRevision++;
        }
        List<WaypointRecord> values = new ArrayList<WaypointRecord>(
                base.getRecords().size() + records.size());
        values.addAll(base.getRecords());
        values.addAll(records.values());
        return new WaypointRevisionSnapshot(combinedRevision, values);
    }

    synchronized List<WaypointRecord> records() {
        return Collections.unmodifiableList(
                new ArrayList<WaypointRecord>(records.values()));
    }

    synchronized List<ArchaeologyReportSession> sessions() {
        return Collections.unmodifiableList(
                new ArrayList<ArchaeologyReportSession>(sessions.values()));
    }

    synchronized List<KnownArchaeologyLocation> knownLocations() {
        return registry.snapshot();
    }

    @Override public String pollMessage() { return messages.poll(); }
    @Override public NavigationTargetKey pollNavigationRequest() {
        return navigationRequests.poll();
    }
    SoundCue pollSoundCue() { return sounds.poll(); }

    @Override public synchronized void observeAction(long[] targets,
                                                      String actionName) {
        if (!enabled || actionName == null
                || !"get direction".equalsIgnoreCase(actionName.trim())
                || targets == null || targets.length == 0) return;
        pendingReportItemId = Long.valueOf(targets[0]);
        pendingReportActionAtNanos = System.nanoTime();
    }

    boolean handleConsoleCommand(String command, String[] arguments) {
        String name = command == null ? "" : command.trim();
        if (name.startsWith("/")) name = name.substring(1);
        if (!"wp".equalsIgnoreCase(name) && !"waypoint".equalsIgnoreCase(name)) {
            return false;
        }
        String[] args = WaypointCommandArguments.withoutRepeatedCommand(name, arguments);
        if (args.length == 0 || !("archaeology".equalsIgnoreCase(args[0])
                || "arch".equalsIgnoreCase(args[0]))) return false;
        final String operation = args.length < 2 ? "status" : args[1].trim();
        final String value = args.length < 3 ? ""
                : WaypointCommandArguments.join(args, 2, args.length);
        worker.execute(new Runnable() {
            @Override public void run() { command(operation, value); }
        });
        return true;
    }

    @Override public synchronized void connectionEnded() {
        boundServerFingerprint = "";
        boundUser = "";
        boundServer = null;
        pendingReportItemId = null;
        navigationRequests.clear();
        if (!records.isEmpty()) {
            records.clear();
            revision++;
        }
    }

    @Override public String navigationReason() { return "archaeology report"; }

    private void process(ArchaeologyMessage message, EventContext context) {
        try {
            switch (message.getKind()) {
                case REPORT_READY: ready(message, context); break;
                case DIRECTION: direction(message, context); break;
                case CACHE_FOUND: cacheFound(message, context); break;
                default: break;
            }
        } catch (Throwable failure) {
            logger.log(Level.WARNING,
                    "Archaeology report event processing failed open", failure);
            messages.add("Archaeology tracker could not process this Event message; the game action was not affected.");
        }
    }

    private void ready(ArchaeologyMessage message, EventContext context)
            throws IOException {
        ArchaeologyReportSession session;
        boolean playReady;
        boolean repairedKnownLocation = false;
        KnownArchaeologyLocation known;
        String eventFingerprint = eventFingerprint(message, context);
        synchronized (this) {
            session = active(context, message.getDeedName());
            if (session != null && eventFingerprint.equals(
                    session.getLastEventFingerprint())) return;
            if (session == null) {
                session = ArchaeologyReportSession.create(
                        context.server.getEndpointFingerprint(), context.user,
                        message.getDeedName(), context.tileX, context.tileY,
                        context.layer, context.at);
            }
            playReady = !session.isReadyChimed();
            known = registry.find(context.server.getEndpointFingerprint(),
                    message.getDeedName());
            if (known != null && known.isNeedsConfirmation()) {
                known = registry.trustSavedLocation(known);
                repairedKnownLocation = true;
            }
            WaypointCoordinate coordinate = known != null ? known.coordinate()
                    : new WaypointCoordinate(context.tileX, context.tileY,
                    null, context.layer);
            ArchaeologyReportStatus status = known != null
                    ? ArchaeologyReportStatus.KNOWN_LOCATION
                    : ArchaeologyReportStatus.REPORT_READY;
            session = session.transition(status, context.tileX, context.tileY,
                    context.layer, coordinate, null, null, 0,
                    eventFingerprint, context.at, playReady);
            putActive(session);
            updateRecord(session, context);
            if (status == ArchaeologyReportStatus.KNOWN_LOCATION) {
                navigationRequests.add(key(session));
            }
        }
        if (repairedKnownLocation) persistLocations();
        persistSessions();
        if (playReady) sounds.add(SoundCue.REPORT_READY);
        if (known != null) {
            messages.add("The location of " + message.getDeedName()
                    + " is already known. The marker is on the exact saved tile; this report may create another cache there.");
        } else {
            messages.add("Complete archaeology report for " + message.getDeedName()
                    + " is ready. Open the report and choose Get direction to begin the cache search.");
        }
    }

    private void direction(ArchaeologyMessage message, EventContext context)
            throws IOException {
        ArchaeologyReportSession session;
        KnownArchaeologyLocation known;
        boolean reusedKnown = false;
        boolean repairedKnownLocation = false;
        String eventFingerprint = eventFingerprint(message, context);
        synchronized (this) {
            session = active(context, message.getDeedName());
            if (session != null && eventFingerprint.equals(
                    session.getLastEventFingerprint())) return;
            if (session == null) {
                session = ArchaeologyReportSession.create(
                        context.server.getEndpointFingerprint(), context.user,
                        message.getDeedName(), context.tileX, context.tileY,
                        context.layer, context.at);
            }
            Long itemId = correlatedReportItemId();
            if (itemId != null) session = session.withReportItemId(itemId, context.at);
            known = registry.find(context.server.getEndpointFingerprint(),
                    message.getDeedName());
            if (known != null) {
                if (known.isNeedsConfirmation()) {
                    known = registry.trustSavedLocation(known);
                    repairedKnownLocation = true;
                }
                session = session.transition(
                        ArchaeologyReportStatus.KNOWN_LOCATION,
                        context.tileX, context.tileY, context.layer,
                        known.coordinate(), message.getDistanceBand(),
                        message.getDirection(), 0,
                        eventFingerprint, context.at, false);
                putActive(session);
                updateRecord(session, context);
                navigationRequests.add(key(session));
                reusedKnown = true;
            }
            if (!reusedKnown) {
                int terminalStep = message.getDistanceBand().isVeryClose()
                        ? session.getTerminalStep() : 0;
                WaypointCoordinate next = planner.next(context.tileX, context.tileY,
                        context.layer, message.getDistanceBand(),
                        message.getDirection(), terminalStep, bounds);
                ArchaeologyReportStatus status = message.getDistanceBand().isVeryClose()
                        ? ArchaeologyReportStatus.VERY_CLOSE
                        : ArchaeologyReportStatus.TRACKING;
                session = session.transition(status, context.tileX, context.tileY,
                        context.layer, next, message.getDistanceBand(),
                        message.getDirection(), ArchaeologyPlanner.nextTerminalStep(
                        message.getDistanceBand(), terminalStep),
                        eventFingerprint, context.at, false);
                putActive(session);
                updateRecord(session, context);
                navigationRequests.add(key(session));
            }
        }
        if (reusedKnown) {
            if (repairedKnownLocation) persistLocations();
            persistSessions();
            messages.add("The saved exact location for " + message.getDeedName()
                    + " overrides the approximate report reading; the marker remains on that tile.");
            return;
        }
        persistSessions();
        messages.add("Archaeology report for " + message.getDeedName()
                + ": marker moved to the approximate next reading point ("
                + message.getDistanceBand().getPhrase() + ", "
                + message.getDirection().name().toLowerCase(Locale.ENGLISH)
                .replace('_', ' ') + ").");
    }

    private void cacheFound(ArchaeologyMessage message, EventContext context)
            throws IOException {
        ArchaeologyReportSession session;
        String eventFingerprint = eventFingerprint(message, context);
        synchronized (this) {
            session = active(context, message.getDeedName());
            if (session != null && eventFingerprint.equals(
                    session.getLastEventFingerprint())) return;
            if (session == null) {
                session = ArchaeologyReportSession.create(
                        context.server.getEndpointFingerprint(), context.user,
                        message.getDeedName(), context.tileX, context.tileY,
                        context.layer, context.at);
            }
            WaypointCoordinate exact = new WaypointCoordinate(context.tileX,
                    context.tileY, null, context.layer);
            registry.confirm(context.server.getEndpointFingerprint(),
                    message.getDeedName(), context.tileX, context.tileY,
                    context.layer, context.at, session.getId());
            session = session.transition(ArchaeologyReportStatus.CACHE_FOUND,
                    context.tileX, context.tileY, context.layer, exact,
                    session.getDistanceBand(), session.getDirection(),
                    session.getTerminalStep(), eventFingerprint,
                    context.at, false);
            sessions.put(session.getId(), session);
            activeKeys.remove(session.getSessionKey());
            if (records.remove(session.getId()) != null) revision++;
            pruneHistory();
        }
        persistLocations();
        persistSessions();
        sounds.add(SoundCue.CACHE_FOUND);
        messages.add("Exact settlement location for " + message.getDeedName()
                + " was saved. Future reports will lead directly to this tile.");
    }

    private void command(String operation, String value) {
        try {
            if ("status".equalsIgnoreCase(operation)) {
                statusMessage();
            } else if ("known".equalsIgnoreCase(operation)) {
                knownMessage();
            } else if ("dismiss".equalsIgnoreCase(operation)) {
                dismiss(value);
            } else if ("clear-known".equalsIgnoreCase(operation)) {
                clearKnown(value);
            } else {
                messages.add("Usage: /wp archaeology status | known | dismiss <deed|all> | clear-known <server|all>");
            }
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Archaeology command failed open", failure);
            messages.add("Archaeology command failed; see client.log.");
        }
    }

    private synchronized void statusMessage() {
        int active = 0;
        for (ArchaeologyReportSession session : sessions.values()) {
            if (visible(session)) active++;
        }
        int known = 0;
        for (KnownArchaeologyLocation location : registry.snapshot()) {
            if (location.getServerFingerprint().equals(boundServerFingerprint)) known++;
        }
        messages.add("Archaeology tracker: active reports=" + active
                + ", exact known locations on this server=" + known
                + ", persistence=" + (storageReady ? "ready" : "unavailable") + ".");
    }

    private synchronized void knownMessage() {
        int shown = 0;
        for (KnownArchaeologyLocation location : registry.snapshot()) {
            if (!location.getServerFingerprint().equals(boundServerFingerprint)) continue;
            messages.add(location.getDeedName() + ": X="
                    + (int) Math.floor(location.getTileX()) + " Y="
                    + (int) Math.floor(location.getTileY()) + " "
                    + location.getLayer().name().toLowerCase(Locale.ENGLISH)
                    + (location.isNeedsConfirmation() ? " (needs reconfirmation)" : ""));
            if (++shown >= 8) break;
        }
        if (shown == 0) messages.add("No exact archaeology locations are saved for this server.");
    }

    private void dismiss(String requested) throws IOException {
        String value = requested == null ? "" : requested.trim();
        if (value.isEmpty()) {
            messages.add("Usage: /wp archaeology dismiss <deed|all>");
            return;
        }
        int count = 0;
        synchronized (this) {
            List<ArchaeologyReportSession> matches = new ArrayList<ArchaeologyReportSession>();
            for (ArchaeologyReportSession session : sessions.values()) {
                if (!visible(session)) continue;
                if ("all".equalsIgnoreCase(value)
                        || session.getDeedName().equalsIgnoreCase(value)) {
                    matches.add(session);
                }
            }
            Instant now = Instant.now();
            for (ArchaeologyReportSession session : matches) {
                ArchaeologyReportSession dismissed = session.transition(
                        ArchaeologyReportStatus.DISMISSED,
                        session.getLastPlayerTileX(), session.getLastPlayerTileY(),
                        session.getLastPlayerLayer(), session.getWaypointCoordinate(),
                        session.getDistanceBand(), session.getDirection(),
                        session.getTerminalStep(), "manual-dismiss", now, false);
                sessions.put(dismissed.getId(), dismissed);
                activeKeys.remove(dismissed.getSessionKey());
                records.remove(dismissed.getId());
                count++;
            }
            if (count > 0) revision++;
            pruneHistory();
        }
        persistSessions();
        messages.add(count == 0 ? "No matching active archaeology report."
                : "Dismissed " + count + " archaeology report marker(s); exact known locations were kept.");
    }

    private void clearKnown(String requested) throws IOException {
        String scope = requested == null ? "" : requested.trim();
        if (!("server".equalsIgnoreCase(scope) || "all".equalsIgnoreCase(scope))) {
            messages.add("Usage: /wp archaeology clear-known <server|all>");
            return;
        }
        int removed;
        synchronized (this) {
            removed = "all".equalsIgnoreCase(scope) ? registry.clearAll()
                    : registry.clearServer(boundServerFingerprint);
            Instant now = Instant.now();
            for (ArchaeologyReportSession session : new ArrayList<ArchaeologyReportSession>(sessions.values())) {
                if (session.getStatus() != ArchaeologyReportStatus.KNOWN_LOCATION) continue;
                if (!"all".equalsIgnoreCase(scope)
                        && !session.getServerFingerprint().equals(boundServerFingerprint)) continue;
                WaypointCoordinate readyAt = new WaypointCoordinate(
                        session.getLastPlayerTileX(), session.getLastPlayerTileY(),
                        null, session.getLastPlayerLayer());
                ArchaeologyReportSession changed = session.transition(
                        ArchaeologyReportStatus.REPORT_READY,
                        session.getLastPlayerTileX(), session.getLastPlayerTileY(),
                        session.getLastPlayerLayer(), readyAt, null, null, 0,
                        "known-location-cleared", now, false);
                sessions.put(changed.getId(), changed);
            }
            rebuildRecords(boundContext(now));
        }
        persistLocations();
        persistSessions();
        messages.add("Cleared " + removed + " exact archaeology location(s)"
                + ("all".equalsIgnoreCase(scope) ? " across all servers." : " for this server."));
    }

    private synchronized ArchaeologyReportSession active(EventContext context,
                                                          String deedName) {
        String key = sessionKey(context.server.getEndpointFingerprint(),
                context.user, deedName);
        UUID id = activeKeys.get(key);
        return id == null ? null : sessions.get(id);
    }

    private synchronized void putActive(ArchaeologyReportSession session) {
        sessions.put(session.getId(), session);
        activeKeys.put(session.getSessionKey(), session.getId());
    }

    private synchronized void rebuildActiveKeys() {
        activeKeys.clear();
        List<ArchaeologyReportSession> ordered =
                new ArrayList<ArchaeologyReportSession>(sessions.values());
        Collections.sort(ordered, new Comparator<ArchaeologyReportSession>() {
            @Override public int compare(ArchaeologyReportSession left,
                                         ArchaeologyReportSession right) {
                return left.getUpdatedAt().compareTo(right.getUpdatedAt());
            }
        });
        for (ArchaeologyReportSession session : ordered) {
            if (session.isActive()) activeKeys.put(session.getSessionKey(), session.getId());
        }
    }

    private synchronized void rebuildRecords(EventContext context) {
        records.clear();
        if (context != null && context.server != null
                && context.server.isSafeForAutomaticRendering()) {
            for (ArchaeologyReportSession session : sessions.values()) {
                if (visible(session)) records.put(session.getId(), record(session, context));
            }
        }
        revision++;
    }

    private synchronized void updateRecord(ArchaeologyReportSession session,
                                           EventContext context) {
        if (visible(session, context)) {
            records.put(session.getId(), record(session, context));
        } else {
            records.remove(session.getId());
        }
        revision++;
    }

    private boolean visible(ArchaeologyReportSession session) {
        return session.isActive()
                && session.getServerFingerprint().equals(boundServerFingerprint)
                && session.getUser().equalsIgnoreCase(boundUser);
    }

    private static boolean visible(ArchaeologyReportSession session,
                                   EventContext context) {
        return context != null && context.server != null && session.isActive()
                && session.getServerFingerprint().equals(
                context.server.getEndpointFingerprint())
                && session.getUser().equalsIgnoreCase(context.user);
    }

    private static WaypointRecord record(ArchaeologyReportSession session,
                                         EventContext context) {
        ArchaeologyReportStatus status = session.getStatus();
        String name;
        String description;
        WaypointResolution resolution;
        int arrival;
        if (status == ArchaeologyReportStatus.KNOWN_LOCATION) {
            name = session.getDeedName()
                    + " - known settlement location (exact saved tile)";
            description = "Exact tile confirmed by a previous hidden-cache discovery.";
            resolution = WaypointResolution.EXACT_SAVED;
            arrival = WaypointArrival.DISABLED;
        } else if (status == ArchaeologyReportStatus.REPORT_READY) {
            name = session.getDeedName() + " - archaeology report ready";
            description = "Open the completed report and choose Get direction.";
            resolution = WaypointResolution.PENDING;
            arrival = WaypointArrival.DISABLED;
        } else {
            name = session.getDeedName() + " - archaeology next reading";
            description = "Approximate next reading point; this is not the hidden cache location.";
            resolution = WaypointResolution.SEARCH_STEP;
            arrival = WaypointArrival.DISABLED;
        }
        Map<String, List<String>> extensions =
                new LinkedHashMap<String, List<String>>();
        extensions.put("archaeology.reportKey",
                Collections.singletonList(session.getReportKey()));
        extensions.put("archaeology.status",
                Collections.singletonList(status.name()));
        extensions.put("archaeology.deed",
                Collections.singletonList(session.getDeedName()));
        if (session.getReportItemId() != null) {
            extensions.put("archaeology.reportItemId",
                    Collections.singletonList(session.getReportItemId().toString()));
        }
        return WaypointRecord.builder().id(session.getId()).name(name)
                .description(description).createdByUser(session.getUser())
                .serverIdentity(context.server)
                .sourceType(WaypointSourceType.ARCHAEOLOGY_REPORT)
                .sourceKey(session.getReportKey())
                .coordinate(session.getWaypointCoordinate())
                .resolution(resolution).enabled(true)
                .markerStyle(new MarkerStyle(
                        MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL,
                        1.0f, 0.96f, 0.78f, 0.96f,
                        22.0f, 4.8f, true, true))
                .arrivalRadiusMetres(arrival).group("Archaeology Reports")
                .createdAt(session.getCreatedAt()).updatedAt(session.getUpdatedAt())
                .lastResolvedAt(session.getUpdatedAt()).extensions(extensions).build();
    }

    private synchronized Long correlatedReportItemId() {
        if (pendingReportItemId == null) return null;
        if (System.nanoTime() - pendingReportActionAtNanos > 60_000_000_000L) {
            pendingReportItemId = null;
            return null;
        }
        Long result = pendingReportItemId;
        pendingReportItemId = null;
        return result;
    }

    private synchronized NavigationTargetKey key(ArchaeologyReportSession session) {
        return new NavigationTargetKey(session.getServerFingerprint(), session.getId());
    }

    private synchronized void pruneHistory() {
        List<ArchaeologyReportSession> history = new ArrayList<ArchaeologyReportSession>();
        for (ArchaeologyReportSession session : sessions.values()) {
            if (!session.isActive()) history.add(session);
        }
        Collections.sort(history, new Comparator<ArchaeologyReportSession>() {
            @Override public int compare(ArchaeologyReportSession left,
                                         ArchaeologyReportSession right) {
                return right.getUpdatedAt().compareTo(left.getUpdatedAt());
            }
        });
        for (int index = historyLimit; index < history.size(); index++) {
            sessions.remove(history.get(index).getId());
        }
    }

    private void persistSessions() throws IOException {
        Collection<ArchaeologyReportSession> snapshot;
        synchronized (this) {
            if (!storageReady) return;
            snapshot = new ArrayList<ArchaeologyReportSession>(sessions.values());
        }
        sessionStore.save(snapshot, historyLimit);
    }

    private void persistLocations() throws IOException {
        List<KnownArchaeologyLocation> snapshot;
        synchronized (this) {
            if (!storageReady) return;
            snapshot = registry.snapshot();
        }
        locationStore.save(snapshot);
    }

    private synchronized EventContext boundContext(Instant at) {
        return boundServer == null || boundServerFingerprint.isEmpty()
                ? null : new EventContext(0.0d, 0.0d, 0.0d,
                WaypointLayer.SURFACE, boundServer, boundUser, at, bounds);
    }

    private static String sessionKey(String serverFingerprint, String user,
                                     String deedName) {
        return normalizeUser(user).toLowerCase(Locale.ENGLISH) + "|"
                + ArchaeologyReportSession.reportKey(serverFingerprint,
                ArchaeologyMessageParser.normalizedDeedKey(deedName));
    }

    private static String eventFingerprint(ArchaeologyMessage message,
                                           EventContext context) {
        return message.getFingerprint() + "|x=" + context.tileX
                + "|y=" + context.tileY + "|layer=" + context.layer.name();
    }

    private static String normalizeUser(String value) {
        return value == null || value.trim().isEmpty() ? "Wurm" : value.trim();
    }

    static final class EventContext {
        private final double tileX;
        private final double tileY;
        private final double height;
        private final WaypointLayer layer;
        private final ServerIdentity server;
        private final String user;
        private final Instant at;

        EventContext(double tileX, double tileY, double height,
                     WaypointLayer layer, ServerIdentity server, String user,
                     Instant at, MapBounds bounds) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.height = height;
            this.layer = layer == null ? WaypointLayer.SURFACE : layer;
            this.server = server;
            this.user = normalizeUser(user);
            this.at = at == null ? Instant.now() : at;
            if (bounds != null) bounds.requireContains(tileX, tileY);
        }
    }
}
