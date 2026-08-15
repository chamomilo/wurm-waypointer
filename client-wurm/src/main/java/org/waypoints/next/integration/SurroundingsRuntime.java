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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Live catalog fed only by Wurm's loaded-renderable event stream. */
final class SurroundingsRuntime implements DynamicWaypointProvider {
    private final Logger logger;
    private final SurroundingsCatalog catalog = new SurroundingsCatalog();
    private final Map<Long, SurroundingKind> renderableKinds =
            new HashMap<Long, SurroundingKind>();

    private List<DeedArea> deedAreas = Collections.emptyList();
    private boolean deedDataAvailable;
    private String deedContext = "";

    SurroundingsRuntime(Logger logger) { this.logger = logger; }

    @Override public void configure(WaypointClientConfiguration configuration) { }

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

    synchronized void upsertRenderable(Object renderable) {
        try {
            SurroundingEntry entry = classified(SurroundingsRenderableAdapter.project(
                    renderable, Instant.now()));
            if (entry == null) return;
            upsert(entry);
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Surroundings renderable projection failed open", failure);
        }
    }

    synchronized void creatureMoved(Object renderable, double worldX,
                                    double worldY, double height) {
        try {
            SurroundingEntry entry = classified(
                    SurroundingsRenderableAdapter.projectCreature(
                            renderable, worldX, worldY, height, Instant.now()));
            if (entry != null) upsert(entry);
        } catch (Throwable failure) {
            logger.log(Level.FINE,
                    "Surroundings creature movement projection failed open", failure);
        }
    }

    synchronized void removeRenderable(Object renderable) {
        try {
            Long renderableId = SurroundingsRenderableAdapter.renderableId(renderable);
            if (renderableId != null) {
                SurroundingKind previous = renderableKinds.remove(renderableId);
                if (previous != null) {
                    catalog.remove(new SurroundingKey(previous, renderableId));
                    return;
                }
            }
            SurroundingEntry entry = SurroundingsRenderableAdapter.project(
                    renderable, Instant.now());
            if (entry != null) catalog.remove(entry.getKey());
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Surroundings renderable removal failed open", failure);
        }
    }

    synchronized void clearRenderables() {
        renderableKinds.clear();
        catalog.clearEntries();
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

    long revision() { return catalog.revision(); }

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

    private void upsert(SurroundingEntry entry) {
        SurroundingKind previous = renderableKinds.put(
                entry.getWurmId(), entry.getKind());
        if (previous != null && previous != entry.getKind()) {
            SurroundingKey previousKey = new SurroundingKey(
                    previous, entry.getWurmId());
            boolean wasMarked = catalog.isWaypointEnabled(previousKey);
            catalog.remove(previousKey);
            if (wasMarked) {
                catalog.setWaypoint(previousKey, false);
                catalog.setWaypoint(entry.getKey(), true);
            }
        }
        catalog.upsert(entry);
    }

    @Override public NavigationTargetKey pollNavigationRequest() { return null; }
    @Override public String pollMessage() { return null; }
    @Override public void observeAction(long[] targets, String actionName) { }

    @Override public synchronized void connectionEnded() {
        deedAreas = Collections.emptyList();
        deedDataAvailable = false;
        deedContext = "";
        renderableKinds.clear();
        catalog.clearSession();
    }

    @Override public String navigationReason() { return "surroundings catalog"; }
}
