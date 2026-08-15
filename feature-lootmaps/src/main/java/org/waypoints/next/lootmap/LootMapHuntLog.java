package org.waypoints.next.lootmap;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** One privacy-preserving JSONL file per hunt, flushed after every event. */
public final class LootMapHuntLog implements Closeable {
    public static final int SCHEMA_VERSION = 2;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final UUID sessionId;
    private final double anchorX;
    private final double anchorY;
    private final Path file;
    private final BufferedWriter writer;
    private boolean closed;

    public LootMapHuntLog(Path directory, UUID sessionId, Instant startedAt,
                          double anchorX, double anchorY) throws IOException {
        if (directory == null || sessionId == null || startedAt == null) {
            throw new IllegalArgumentException("directory, session id and time are required");
        }
        this.sessionId = sessionId;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        Files.createDirectories(directory);
        file = directory.resolve("loot-hunt-" + FILE_TIME.format(startedAt)
                + "-" + sessionId.toString() + ".jsonl");
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        writeLine("{\"schemaVersion\":" + SCHEMA_VERSION
                + ",\"event\":\"hunt_started\",\"sessionId\":\"" + sessionId
                + "\",\"at\":\"" + startedAt + "\",\"algorithmVersion\":\""
                + LootMapPlanner.ALGORITHM_VERSION + "\",\"coordinateFrame\":\"relative_to_first_reading\"}");
    }

    public synchronized void reading(int sequence, LootMapObservation observation,
                                     LootMapDecision decision,
                                     double actualPathTiles) throws IOException {
        requireOpen();
        StringBuilder json = new StringBuilder(1024);
        json.append("{\"schemaVersion\":").append(SCHEMA_VERSION)
                .append(",\"event\":\"reading_decision\",\"sessionId\":\"")
                .append(sessionId).append("\",\"sequence\":").append(sequence)
                .append(",\"at\":\"").append(observation.getObservedAt()).append("\"")
                .append(",\"observation\":{")
                .append("\"originDx\":").append(number(observation.getOriginX() - anchorX))
                .append(",\"originDy\":").append(number(observation.getOriginY() - anchorY))
                .append(",\"facingDegrees\":").append(number(observation.getPlayerFacingDegrees()))
                .append(",\"relativeDirection\":\"").append(observation.getRelativeDirection().name())
                .append("\",\"absoluteSectorDegrees\":").append(number(observation.getAbsoluteSectorDegrees()))
                .append(",\"distanceBand\":\"").append(observation.getBand().name())
                .append("\",\"minimumTiles\":").append(observation.getBand().getMinimum())
                .append(",\"maximumTiles\":");
        if (observation.getBand().isFinite()) json.append(observation.getBand().getMaximum());
        else json.append("null");
        json.append("},\"posterior\":{")
                .append("\"feasibleTileCount\":").append(decision.getFeasibleTileCount())
                .append(",\"sampleCount\":").append(decision.getPosteriorSampleCount())
                .append(",\"probabilityDistanceAtLeast100\":")
                .append(nullable(decision.getProbabilityAtLeast100()))
                .append(",\"q75DistanceTiles\":")
                .append(nullable(decision.getPosteriorQ75Distance())).append("}")
                .append(",\"decision\":{")
                .append("\"mode\":\"").append(decision.getMode().name())
                .append("\",\"waypointDx\":").append(number(decision.getWaypointX() - anchorX))
                .append(",\"waypointDy\":").append(number(decision.getWaypointY() - anchorY))
                .append(",\"plannedWaypointDx\":").append(number(
                        decision.getPlannedWaypointX() - anchorX))
                .append(",\"plannedWaypointDy\":").append(number(
                        decision.getPlannedWaypointY() - anchorY))
                .append(",\"landAdjusted\":").append(decision.isLandAdjusted())
                .append(",\"landAdjustmentTiles\":").append(number(
                        decision.getLandAdjustmentTiles()))
                .append(",\"walkTiles\":").append(number(decision.getWalkTiles()))
                .append(",\"informationScore\":").append(nullable(decision.getInformationScore()))
                .append(",\"directInformationScore\":")
                .append(nullable(decision.getDirectInformationScore()))
                .append("},\"alternatives\":[");
        for (int i = 0; i < decision.getAlternatives().size(); i++) {
            if (i > 0) json.append(',');
            LootMapDecision.Alternative alternative = decision.getAlternatives().get(i);
            json.append("{\"waypointDx\":").append(number(alternative.getX() - anchorX))
                    .append(",\"waypointDy\":").append(number(alternative.getY() - anchorY))
                    .append(",\"informationScore\":").append(number(alternative.getInformationScore()))
                    .append(",\"walkTiles\":").append(number(alternative.getWalkTiles()))
                    .append(",\"direct\":").append(alternative.isDirect()).append('}');
        }
        json.append("],\"actualPathTiles\":").append(number(actualPathTiles)).append('}');
        writeLine(json.toString());
    }

    public synchronized void event(String event, Instant at, int readingCount,
                                   double actualPathTiles) throws IOException {
        requireOpen();
        writeLine("{\"schemaVersion\":" + SCHEMA_VERSION + ",\"event\":\""
                + json(event) + "\",\"sessionId\":\"" + sessionId
                + "\",\"at\":\"" + at + "\",\"readingCount\":" + readingCount
                + ",\"actualPathTiles\":" + number(actualPathTiles) + "}");
    }

    public Path getFile() { return file; }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        writer.close();
    }

    private void writeLine(String value) throws IOException {
        writer.write(value);
        writer.newLine();
        writer.flush();
    }

    private void requireOpen() throws IOException {
        if (closed) throw new IOException("hunt log is closed");
    }

    private static String nullable(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? "null" : number(value);
    }

    private static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "null";
        return String.format(Locale.ENGLISH, "%.6f", value);
    }

    private static String json(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') result.append('\\');
            if (c == '\r' || c == '\n') result.append(' '); else result.append(c);
        }
        return result.toString();
    }
}
