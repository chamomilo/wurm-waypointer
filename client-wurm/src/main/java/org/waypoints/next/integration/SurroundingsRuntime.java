package org.waypoints.next.integration;

import org.waypoints.next.map.Deed;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.surroundings.DeedArea;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKey;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsCatalog;
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.surroundings.SurroundingsSnapshot;
import org.waypoints.next.surroundings.ScannerColor;
import org.waypoints.next.surroundings.ScannerEvent;
import org.waypoints.next.surroundings.ScannerProfile;
import org.waypoints.next.surroundings.ScannerProfiles;
import org.waypoints.next.surroundings.ScannerSession;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Live catalog fed only by Wurm's loaded-renderable event stream. */
final class SurroundingsRuntime implements DynamicWaypointProvider {
    private final Logger logger;
    private final SurroundingsCatalog catalog = new SurroundingsCatalog();
    private final Map<Long, SurroundingKind> renderableKinds =
            new HashMap<Long, SurroundingKind>();
    private final Map<SurroundingKey, Object> renderables =
            new HashMap<SurroundingKey, Object>();
    private final Map<SurroundingKey, Long> pendingRemovals =
            new HashMap<SurroundingKey, Long>();
    private final ScannerSession scanner = new ScannerSession();
    private final Deque<String> messages = new ArrayDeque<String>();

    private static final int MAX_PENDING_MESSAGES = 64;
    private static final long TECHNICAL_REMOVE_GRACE_MILLIS = 750L;
    private int maximumOutlines = 8;
    private int outlineDistanceMetres = 80;
    private int suppressedMessages;
    private long scannerRevision;

    private List<DeedArea> deedAreas = Collections.emptyList();
    private boolean deedDataAvailable;
    private String deedContext = "";

    SurroundingsRuntime(Logger logger) { this.logger = logger; }

    @Override public synchronized void configure(
            WaypointClientConfiguration configuration) {
        WaypointClientConfiguration value = configuration == null
                ? WaypointClientConfiguration.defaults() : configuration;
        scanner.setNotificationsEnabled(value.isScannerNotifications());
        scanner.setOutlinesEnabled(value.isScannerOutlines());
        scanner.clearExcludedNames();
        for (String excluded : value.getScannerExcludedNames()) {
            scanner.addExcludedName(excluded);
        }
        maximumOutlines = value.getScannerMaximumOutlines();
        outlineDistanceMetres = value.getScannerOutlineDistanceMetres();
        scanner.reseed(catalog.entries());
        scannerRevision++;
    }

    synchronized void bind(ServerIdentity nextServer, String nextUser) {
        // The catalog itself is session-local. Server teardown owns its reset.
    }

    synchronized void updateDeeds(ServerMapSnapshot snapshot) {
        String profile = snapshot == null || snapshot.getProfile() == null
                ? "" : snapshot.getProfile().getId();
        long revision = snapshot == null ? 0L : snapshot.getDeedsRevision();
        String context = profile + "|" + revision + "|"
                + (snapshot == null ? 0L : snapshot.getRevision());
        if (context.equals(deedContext)) return;
        deedContext = context;
        boolean available = !profile.isEmpty() && revision > 0L;
        List<DeedArea> areas = new ArrayList<DeedArea>();
        if (available) for (Deed deed : snapshot.getDeeds()) {
            areas.add(new DeedArea(deed.getMinimumX(), deed.getMaximumX(),
                    deed.getMinimumY(), deed.getMaximumY()));
        }
        deedAreas = Collections.unmodifiableList(areas);
        deedDataAvailable = available;
        catalog.updateDeedAreas(deedAreas, deedDataAvailable);
    }

    synchronized SurroundingEntry upsertRenderable(Object renderable) {
        try {
            SurroundingEntry entry = classified(SurroundingsRenderableAdapter.project(
                    renderable, Instant.now()));
            if (entry == null) return null;
            upsert(entry, renderable);
            return entry;
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Surroundings renderable projection failed open", failure);
            return null;
        }
    }

    synchronized SurroundingEntry creatureMoved(Object renderable, double worldX,
                                                double worldY, double height) {
        try {
            SurroundingEntry entry = classified(
                    SurroundingsRenderableAdapter.projectCreature(
                            renderable, worldX, worldY, height, Instant.now()));
            if (entry != null) upsert(entry, renderable);
            return entry;
        } catch (Throwable failure) {
            logger.log(Level.FINE,
                    "Surroundings creature movement projection failed open", failure);
            return null;
        }
    }

    synchronized SurroundingKey removeRenderable(Object renderable,
                                                  boolean authoritative) {
        try {
            Long renderableId = SurroundingsRenderableAdapter.renderableId(renderable);
            if (renderableId != null) {
                SurroundingKind previous = renderableKinds.remove(renderableId);
                if (previous != null) {
                    SurroundingKey key = new SurroundingKey(previous, renderableId);
                    SurroundingEntry removed = catalog.find(key);
                    renderables.remove(key);
                    catalog.remove(key);
                    removed(key, removed, authoritative);
                    return key;
                }
            }
            SurroundingEntry entry = SurroundingsRenderableAdapter.project(
                    renderable, Instant.now());
            if (entry != null) {
                SurroundingEntry removed = catalog.find(entry.getKey());
                renderables.remove(entry.getKey());
                catalog.remove(entry.getKey());
                removed(entry.getKey(), removed, authoritative);
                return entry.getKey();
            }
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Surroundings renderable removal failed open", failure);
        }
        return null;
    }

    synchronized void clearRenderables() {
        renderableKinds.clear();
        renderables.clear();
        pendingRemovals.clear();
        catalog.clearEntries();
        scanner.clearObserved();
    }

    synchronized void removeAuthoritatively(SurroundingKey key) {
        SurroundingEntry removed = catalog.find(key);
        renderableKinds.remove(Long.valueOf(key.getWurmId()));
        renderables.remove(key);
        pendingRemovals.remove(key);
        catalog.remove(key);
        enqueue(removed == null ? scanner.observeRemoved(key)
                : scanner.observeRemoved(removed));
    }

    SurroundingsSnapshot snapshot(SurroundingsQuery query,
                                  double playerWorldX, double playerWorldY) {
        return catalog.snapshot(query, playerWorldX, playerWorldY);
    }

    SurroundingEntry find(SurroundingKey key) { return catalog.find(key); }

    List<SurroundingEntry> findAll(Collection<SurroundingKey> keys) {
        return catalog.findAll(keys);
    }

    void reconcileWaypoints(Collection<SurroundingKey> keys) {
        catalog.reconcileWaypoints(keys);
    }

    synchronized long revision() {
        return catalog.revision() * 31L + scannerRevision;
    }

    synchronized int activateScanner(String profileId) {
        ScannerProfile profile = ScannerProfiles.find(profileId);
        if (profile == null) throw new IllegalArgumentException(
                "unknown scan profile '" + profileId + "'; use "
                        + ScannerProfiles.ids());
        messages.clear();
        suppressedMessages = 0;
        int count = scanner.activate(profile, catalog.entries());
        scannerRevision++;
        return count;
    }

    synchronized void deactivateScanner() {
        scanner.deactivate();
        messages.clear();
        suppressedMessages = 0;
        scannerRevision++;
    }

    synchronized boolean isScannerActive() { return scanner.isActive(); }
    synchronized String scannerProfileId() {
        ScannerProfile profile = scanner.getProfile();
        return profile == null ? "off" : profile.getId();
    }
    synchronized int scannerMatchCount() { return scanner.getMatchCount(); }
    synchronized boolean isScannerNotificationsEnabled() {
        return scanner.isNotificationsEnabled();
    }
    synchronized void setScannerNotificationsEnabled(boolean enabled) {
        scanner.setNotificationsEnabled(enabled);
        scannerRevision++;
    }
    synchronized boolean isScannerOutlinesEnabled() {
        return scanner.isOutlinesEnabled();
    }
    synchronized void setScannerOutlinesEnabled(boolean enabled) {
        scanner.setOutlinesEnabled(enabled);
        scannerRevision++;
    }
    synchronized Collection<String> scannerExcludedNames() {
        return scanner.getExcludedNames();
    }
    synchronized boolean addScannerExcludedName(String value) {
        boolean changed = scanner.addExcludedName(value);
        if (changed) {
            scanner.reseed(catalog.entries());
            scannerRevision++;
        }
        return changed;
    }
    synchronized boolean removeScannerExcludedName(String value) {
        boolean changed = scanner.removeExcludedName(value);
        if (changed) {
            scanner.reseed(catalog.entries());
            scannerRevision++;
        }
        return changed;
    }
    synchronized int clearScannerExcludedNames() {
        int count = scanner.getExcludedNames().size();
        scanner.clearExcludedNames();
        if (count > 0) {
            scanner.reseed(catalog.entries());
            scannerRevision++;
        }
        return count;
    }
    synchronized int maximumOutlines() { return maximumOutlines; }
    synchronized int outlineDistanceMetres() { return outlineDistanceMetres; }

    synchronized List<ScannerOutlineSubject> scannerOutlineSubjects(
            double playerWorldX, double playerWorldY, int playerLayer) {
        if (!scanner.isActive() || !scanner.isOutlinesEnabled()
                || maximumOutlines <= 0) return Collections.emptyList();
        final double originX = playerWorldX;
        final double originY = playerWorldY;
        double maximumSquared = (double) outlineDistanceMetres
                * (double) outlineDistanceMetres;
        List<ScannerOutlineSubject> result =
                new ArrayList<ScannerOutlineSubject>();
        ScannerProfile profile = scanner.getProfile();
        for (SurroundingEntry entry : catalog.entries()) {
            if (!scanner.matches(entry)
                    || (entry.getLayer() < 0) != (playerLayer < 0)) continue;
            double dx = entry.getWorldX() - originX;
            double dy = entry.getWorldY() - originY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared > maximumSquared) continue;
            Object renderable = renderables.get(entry.getKey());
            if (renderable != null) result.add(new ScannerOutlineSubject(
                    entry.getKey(), renderable, profile.getColor(), distanceSquared));
        }
        Collections.sort(result, new Comparator<ScannerOutlineSubject>() {
            @Override public int compare(ScannerOutlineSubject left,
                                         ScannerOutlineSubject right) {
                int distance = Double.compare(left.distanceSquared,
                        right.distanceSquared);
                return distance != 0 ? distance : left.key.compareTo(right.key);
            }
        });
        if (result.size() > maximumOutlines) {
            result = new ArrayList<ScannerOutlineSubject>(
                    result.subList(0, maximumOutlines));
        }
        return Collections.unmodifiableList(result);
    }

    synchronized void tick(long nowMillis) {
        if (pendingRemovals.isEmpty()) return;
        List<SurroundingKey> due = new ArrayList<SurroundingKey>();
        for (Map.Entry<SurroundingKey, Long> pending : pendingRemovals.entrySet()) {
            if (pending.getValue().longValue() <= nowMillis) due.add(pending.getKey());
        }
        for (SurroundingKey key : due) {
            pendingRemovals.remove(key);
            enqueue(scanner.observeRemoved(key));
        }
    }

    @Override public WaypointRevisionSnapshot combine(WaypointRevisionSnapshot base) {
        // Mark now creates an ordinary persisted 15-minute manager waypoint.
        return base;
    }

    static MarkerStyle style(SurroundingKind kind) {
        if (kind == SurroundingKind.ANIMAL) {
            return new MarkerStyle(MarkerStyle.WorldStyle.EXCLAMATION,
                    1.0f, 0.28f, 0.16f, 0.92f, 13.0f, 2.4f, true, true);
        }
        if (kind == SurroundingKind.CONTAINER) {
            return new MarkerStyle(MarkerStyle.WorldStyle.EXCLAMATION,
                    0.15f, 0.85f, 1.0f, 0.90f, 12.0f, 2.2f, true, true);
        }
        return new MarkerStyle(MarkerStyle.WorldStyle.EXCLAMATION,
                0.98f, 0.84f, 0.20f, 0.90f, 10.0f, 2.0f, true, true);
    }

    static UUID stableId(SurroundingKey key) {
        return UUID.nameUUIDFromBytes(("wurm-waypointer:surroundings:"
                + key.toString()).getBytes(StandardCharsets.UTF_8));
    }

    private SurroundingEntry classified(SurroundingEntry entry) {
        return entry == null ? null : entry.withDeedStatus(
                SurroundingsCatalog.deedStatus(entry, deedAreas, deedDataAvailable));
    }

    private void upsert(SurroundingEntry entry, Object renderable) {
        pendingRemovals.remove(entry.getKey());
        SurroundingKind previous = renderableKinds.put(
                entry.getWurmId(), entry.getKind());
        if (previous != null && previous != entry.getKind()) {
            SurroundingKey previousKey = new SurroundingKey(
                    previous, entry.getWurmId());
            pendingRemovals.remove(previousKey);
            boolean wasMarked = catalog.isWaypointEnabled(previousKey);
            catalog.remove(previousKey);
            renderables.remove(previousKey);
            if (wasMarked) {
                catalog.setWaypoint(previousKey, false);
                catalog.setWaypoint(entry.getKey(), true);
            }
        }
        catalog.upsert(entry);
        renderables.put(entry.getKey(), renderable);
        enqueue(scanner.observeUpsert(entry));
    }

    @Override public NavigationTargetKey pollNavigationRequest() { return null; }
    @Override public synchronized String pollMessage() {
        String message = messages.pollFirst();
        if (message != null) return message;
        if (suppressedMessages <= 0) return null;
        int count = suppressedMessages;
        suppressedMessages = 0;
        return "Scanner suppressed " + count
                + " additional notifications; refine the profile or add minus-name rules.";
    }
    @Override public void observeAction(long[] targets, String actionName) { }

    @Override public synchronized void connectionEnded() {
        deedAreas = Collections.emptyList();
        deedDataAvailable = false;
        deedContext = "";
        renderableKinds.clear();
        renderables.clear();
        pendingRemovals.clear();
        catalog.clearSession();
        scanner.deactivate();
        messages.clear();
        suppressedMessages = 0;
        scannerRevision++;
    }

    @Override public String navigationReason() { return "surroundings catalog"; }

    private void enqueue(ScannerEvent event) {
        if (event == null) return;
        if (messages.size() >= MAX_PENDING_MESSAGES) {
            suppressedMessages++;
            return;
        }
        SurroundingEntry entry = event.getEntry();
        messages.addLast("Scanner " + event.getProfile().getId() + ": "
                + (event.getType() == ScannerEvent.Type.APPEARED
                ? "appeared " : "disappeared ")
                + entry.getName() + " [" + entry.getKind().name().toLowerCase(
                        Locale.ENGLISH)
                + " #" + entry.getWurmId() + "].");
    }

    private void removed(SurroundingKey key, SurroundingEntry entry,
                         boolean authoritative) {
        if (authoritative) {
            pendingRemovals.remove(key);
            enqueue(entry == null ? scanner.observeRemoved(key)
                    : scanner.observeRemoved(entry));
        } else {
            pendingRemovals.put(key, Long.valueOf(
                    System.currentTimeMillis() + TECHNICAL_REMOVE_GRACE_MILLIS));
        }
    }

    static final class ScannerOutlineSubject {
        private final SurroundingKey key;
        private final Object renderable;
        private final ScannerColor color;
        private final double distanceSquared;

        private ScannerOutlineSubject(SurroundingKey key, Object renderable,
                                      ScannerColor color,
                                      double distanceSquared) {
            this.key = key;
            this.renderable = renderable;
            this.color = color;
            this.distanceSquared = distanceSquared;
        }

        Object getRenderable() { return renderable; }
        ScannerColor getColor() { return color; }
    }
}
