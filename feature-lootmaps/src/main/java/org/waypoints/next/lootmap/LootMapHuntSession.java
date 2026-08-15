package org.waypoints.next.lootmap;

import org.waypoints.next.source.MapBounds;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Owns one active hunt, its locked planner mode, and exactly one JSONL file. */
public final class LootMapHuntSession {
    private final UUID id;
    private final MapBounds bounds;
    private final LootMapPlanner planner = new LootMapPlanner();
    private final List<LootMapObservation> observations =
            new ArrayList<LootMapObservation>();
    private final LootMapHuntLog log;
    private double actualPathTiles;
    private LootMapObservation previous;
    private LootMapDecision decision;
    private boolean closed;

    public LootMapHuntSession(Path logDirectory, MapBounds bounds,
                              LootMapObservation first) throws IOException {
        if (first == null) throw new IllegalArgumentException("first observation is required");
        this.id = UUID.randomUUID();
        this.bounds = bounds;
        this.log = new LootMapHuntLog(logDirectory, id, first.getObservedAt(),
                first.getOriginX(), first.getOriginY());
    }

    public synchronized LootMapDecision observe(LootMapObservation observation)
            throws IOException {
        return observe(observation, null);
    }

    public synchronized LootMapDecision observe(LootMapObservation observation,
                                                 LootMapTerrain terrain)
            throws IOException {
        if (closed) throw new IllegalStateException("hunt session is closed");
        if (observation == null) throw new IllegalArgumentException("observation is required");
        if (previous != null) {
            actualPathTiles += Math.hypot(observation.getOriginX() - previous.getOriginX(),
                    observation.getOriginY() - previous.getOriginY());
        }
        observations.add(observation);
        decision = planner.plan(observations, bounds);
        decision = LootMapWaypointPlacement.adjust(decision, observation,
                bounds, terrain);
        log.reading(observations.size(), observation, decision, actualPathTiles);
        previous = observation;
        return decision;
    }

    public synchronized void close(String event, Instant at) throws IOException {
        if (closed) return;
        try {
            log.event(event, at == null ? Instant.now() : at,
                    observations.size(), actualPathTiles);
        } finally {
            closed = true;
            log.close();
        }
    }

    /** Records a non-terminal hunt phase without closing the JSONL session. */
    public synchronized void event(String event, Instant at) throws IOException {
        if (closed) throw new IllegalStateException("hunt session is closed");
        log.event(event, at == null ? Instant.now() : at,
                observations.size(), actualPathTiles);
    }

    public UUID getId() { return id; }
    public synchronized LootMapDecision getDecision() { return decision; }
    public synchronized int getReadingCount() { return observations.size(); }
    public synchronized double getActualPathTiles() { return actualPathTiles; }
    public synchronized Path getLogFile() { return log.getFile(); }
    public synchronized boolean isClosed() { return closed; }
    public synchronized List<LootMapObservation> getObservations() {
        return Collections.unmodifiableList(
                new ArrayList<LootMapObservation>(observations));
    }
}
