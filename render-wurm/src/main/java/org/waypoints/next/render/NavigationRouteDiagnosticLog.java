package org.waypoints.next.render;

import org.waypoints.next.navigation.GroundRouteTrace;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One asynchronously written JSONL file per navigator activation. */
public final class NavigationRouteDiagnosticLog implements Closeable {
    public static final int SCHEMA_VERSION = 2;
    public static final long DEFAULT_MAXIMUM_FILE_BYTES = 16L * 1024L * 1024L;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final UUID sessionId = UUID.randomUUID();
    private final Path file;
    private final BufferedWriter writer;
    private final ExecutorService worker;
    private final Logger logger;
    private final long maximumFileBytes;
    private int sequence;
    private long bytesWritten;
    private boolean closed;
    private volatile boolean acceptingRouteRecords = true;

    public NavigationRouteDiagnosticLog(Path directory, Instant startedAt,
                                        String serverFingerprint,
                                        UUID waypointId, String waypointName,
                                        int targetTileX, int targetTileY,
                                        int targetLayer,
                                        float maximumSlopeDirt,
                                        float maximumWaterDepthMetres,
                                        Logger logger) throws IOException {
        this(directory, startedAt, serverFingerprint, waypointId, waypointName,
                targetTileX, targetTileY, targetLayer, maximumSlopeDirt,
                maximumWaterDepthMetres, logger,
                DEFAULT_MAXIMUM_FILE_BYTES);
    }

    NavigationRouteDiagnosticLog(Path directory, Instant startedAt,
                                 String serverFingerprint,
                                 UUID waypointId, String waypointName,
                                 int targetTileX, int targetTileY,
                                 int targetLayer,
                                 float maximumSlopeDirt,
                                 float maximumWaterDepthMetres,
                                 Logger logger,
                                 long maximumFileBytes) throws IOException {
        if (directory == null || startedAt == null || waypointId == null) {
            throw new IllegalArgumentException(
                    "directory, start time and waypoint id are required");
        }
        if (maximumFileBytes < 1L) {
            throw new IllegalArgumentException(
                    "maximum file bytes must be positive");
        }
        this.logger = logger == null
                ? Logger.getLogger("WurmWaypointer.RouteDiagnostics") : logger;
        this.maximumFileBytes = maximumFileBytes;
        Files.createDirectories(directory);
        file = directory.resolve("navigation-route-" + FILE_TIME.format(startedAt)
                + "-" + sessionId.toString() + ".jsonl");
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "wurm-waypointer-route-diagnostics");
                thread.setDaemon(true);
                return thread;
            }
        });
        writeControlLine("{\"schemaVersion\":" + SCHEMA_VERSION
                + ",\"event\":\"route_session_started\",\"sessionId\":\""
                + sessionId + "\",\"at\":\"" + startedAt
                + "\",\"algorithmVersion\":\""
                + GroundRouteTrace.ALGORITHM_VERSION
                + "\",\"coordinateFrame\":\"absolute_server_tiles\""
                + ",\"serverFingerprint\":\"" + json(serverFingerprint)
                + "\",\"waypointId\":\"" + waypointId
                + "\",\"waypointName\":\"" + json(waypointName)
                + "\",\"target\":{\"tileX\":" + targetTileX
                + ",\"tileY\":" + targetTileY + ",\"layer\":"
                + targetLayer + "},\"limits\":{\"maximumSlopeDirt\":"
                + number(maximumSlopeDirt)
                + ",\"maximumWaterDepthMetres\":"
                + number(maximumWaterDepthMetres) + "}}");
    }

    public synchronized void routeEvaluated(final GroundRouteTrace trace) {
        routeEvaluated(trace, "unspecified", 0, 0, 0, 0, 0, 0L);
    }

    public synchronized void routeEvaluated(final GroundRouteTrace trace,
                                            final String strategy,
                                            final int expandedNodes,
                                            final int rejectedSlopeEdges,
                                            final int rejectedWaterEdges,
                                            final int rejectedUnknownEdges,
                                            final int rejectedCornerEdges,
                                            final long highwayRevision) {
        if (closed || !acceptingRouteRecords || trace == null) return;
        worker.execute(new Runnable() {
            @Override public void run() {
                if (!acceptingRouteRecords) return;
                try {
                    writeTrace(trace, Instant.now(), strategy, expandedNodes,
                            rejectedSlopeEdges, rejectedWaterEdges,
                            rejectedUnknownEdges, rejectedCornerEdges,
                            highwayRevision);
                } catch (IOException failure) {
                    logger.log(Level.WARNING,
                            "Unable to append navigation route diagnostics to \""
                                    + file.toAbsolutePath().normalize() + "\"",
                            failure);
                }
            }
        });
    }

    public Path getFile() { return file; }

    @Override public void close() throws IOException {
        close("route_stopped");
    }

    public synchronized void close(final String reason) throws IOException {
        if (closed) return;
        closed = true;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    writeControlLine("{\"schemaVersion\":" + SCHEMA_VERSION
                            + ",\"event\":\"route_session_ended\",\"sessionId\":\""
                            + sessionId + "\",\"at\":\"" + Instant.now()
                            + "\",\"reason\":\"" + json(reason) + "\"}");
                } catch (IOException failure) {
                    logger.log(Level.WARNING,
                            "Unable to finish navigation route diagnostics", failure);
                } finally {
                    try {
                        writer.close();
                    } catch (IOException failure) {
                        logger.log(Level.WARNING,
                                "Unable to close navigation route diagnostics", failure);
                    }
                }
            }
        });
        worker.shutdown();
        try {
            worker.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeTrace(GroundRouteTrace trace, Instant at,
                            String strategy, int expandedNodes,
                            int rejectedSlopeEdges, int rejectedWaterEdges,
                            int rejectedUnknownEdges, int rejectedCornerEdges,
                            long highwayRevision)
            throws IOException {
        if (!acceptingRouteRecords) return;
        int nextSequence = sequence + 1;
        StringBuilder json = new StringBuilder(Math.max(2048,
                trace.getPoints().size() * 220));
        json.append("{\"schemaVersion\":").append(SCHEMA_VERSION)
                .append(",\"event\":\"route_evaluated\",\"sessionId\":\"")
                .append(sessionId).append("\",\"sequence\":").append(nextSequence)
                .append(",\"at\":\"").append(at).append("\"")
                .append(",\"algorithmVersion\":\"")
                .append(GroundRouteTrace.ALGORITHM_VERSION).append("\"")
                .append(",\"search\":{\"strategy\":\"")
                .append(json(strategy)).append("\",\"expandedNodes\":")
                .append(expandedNodes)
                .append(",\"rejectedSlopeEdges\":")
                .append(rejectedSlopeEdges)
                .append(",\"rejectedWaterEdges\":")
                .append(rejectedWaterEdges)
                .append(",\"rejectedUnknownEdges\":")
                .append(rejectedUnknownEdges)
                .append(",\"rejectedCornerEdges\":")
                .append(rejectedCornerEdges)
                .append(",\"highwayRevision\":")
                .append(highwayRevision).append('}')
                .append(",\"candidate\":{")
                .append("\"targetTileX\":").append(trace.getTargetTileX())
                .append(",\"targetTileY\":").append(trace.getTargetTileY())
                .append(",\"layer\":").append(trace.getLayer())
                .append(",\"candidatePointCount\":")
                .append(trace.getCandidatePointCount())
                .append(",\"measuredPointCount\":").append(trace.getPoints().size())
                .append(",\"reachedTarget\":").append(trace.isReachedTarget())
                .append(",\"result\":\"").append(trace.getResult()).append("\"")
                .append(",\"renderablePointCount\":")
                .append(trace.getRenderablePointCount()).append('}')
                .append(",\"limits\":{\"maximumSlopeDirt\":")
                .append(number(trace.getMaximumSlopeDirt()))
                .append(",\"maximumWaterDepthMetres\":")
                .append(number(trace.getMaximumWaterDepthMetres())).append('}')
                .append(",\"summary\":{\"observedMaximumSlopeDirt\":")
                .append(number(trace.getObservedMaximumSlopeDirt()))
                .append(",\"observedMaximumWaterDepthMetres\":")
                .append(number(trace.getObservedMaximumWaterDepthMetres()))
                .append(",\"blockingPointIndex\":")
                .append(nullableIndex(trace.getBlockingPointIndex()))
                .append(",\"blockingSegmentIndex\":")
                .append(nullableIndex(trace.getBlockingSegmentIndex()))
                .append(",\"unverifiedSegmentCount\":")
                .append(trace.getUnverifiedSegmentCount()).append('}')
                .append(",\"points\":[");
        for (int i = 0; i < trace.getPoints().size(); i++) {
            if (i > 0) json.append(',');
            GroundRouteTrace.Point point = trace.getPoints().get(i);
            json.append("{\"index\":").append(i)
                    .append(",\"tileX\":").append(point.getTileX())
                    .append(",\"tileY\":").append(point.getTileY())
                    .append(",\"groundHeightMetres\":")
                    .append(number(point.getGroundHeightMetres()))
                    .append(",\"heightSource\":\"")
                    .append(point.getHeightSource()).append("\"")
                    .append(",\"waterDepthMetres\":")
                    .append(number(point.getWaterDepthMetres()))
                    .append(",\"waterSource\":\"")
                    .append(point.getWaterSource()).append("\"")
                    .append(",\"road\":").append(point.isRoad())
                    .append(",\"highwayKind\":\"")
                    .append(point.getHighwayKind())
                    .append("\",\"publishedHighway\":")
                    .append(point.isPublishedHighway())
                    .append(",\"highwayPortal\":")
                    .append(point.isHighwayPortal())
                    .append(",\"tileMaximumSlopeDirt\":")
                    .append(number(point.getTileMaximumSlopeDirt())).append('}');
        }
        json.append("],\"segments\":[");
        for (int i = 0; i < trace.getSegments().size(); i++) {
            if (i > 0) json.append(',');
            GroundRouteTrace.Segment segment = trace.getSegments().get(i);
            json.append("{\"index\":").append(segment.getIndex())
                    .append(",\"fromPointIndex\":")
                    .append(segment.getFromPointIndex())
                    .append(",\"toPointIndex\":")
                    .append(segment.getToPointIndex())
                    .append(",\"horizontalMetres\":")
                    .append(number(segment.getHorizontalMetres()))
                    .append(",\"heightDeltaMetres\":")
                    .append(number(segment.getHeightDeltaMetres()))
                    .append(",\"absoluteSlopeDirtEstimate\":")
                    .append(number(segment.getAbsoluteSlopeDirtEstimate()))
                    .append(",\"maximumTraversedSlopeDirt\":")
                    .append(number(segment.getMaximumTraversedSlopeDirt()))
                    .append(",\"gradePercent\":")
                    .append(number(segment.getGradePercent()))
                    .append(",\"maximumWaterDepthMetres\":")
                    .append(number(segment.getMaximumWaterDepthMetres()))
                    .append(",\"status\":\"")
                    .append(segment.getStatus()).append("\"}");
        }
        json.append("]}");
        if (!writeRouteLine(json.toString(), at)) return;
        sequence = nextSequence;
        logger.info("Ground navigator route evaluated: result=" + trace.getResult()
                + ", points=" + trace.getPoints().size()
                + ", rendered=" + trace.getRenderablePointCount()
                + ", maxSlopeDirt=" + number(trace.getObservedMaximumSlopeDirt())
                + ", maxWaterDepthMetres="
                + number(trace.getObservedMaximumWaterDepthMetres())
                + ", blockingSegment="
                + nullableIndex(trace.getBlockingSegmentIndex())
                + ", log=\"" + file.toAbsolutePath().normalize() + "\"");
    }

    private boolean writeRouteLine(String value, Instant at)
            throws IOException {
        long recordBytes = encodedLineBytes(value);
        if (bytesWritten + recordBytes <= maximumFileBytes) {
            writeControlLine(value);
            return true;
        }
        acceptingRouteRecords = false;
        writeControlLine("{\"schemaVersion\":" + SCHEMA_VERSION
                + ",\"event\":\"route_log_limit_reached\",\"sessionId\":\""
                + sessionId + "\",\"at\":\"" + at
                + "\",\"maximumFileBytes\":" + maximumFileBytes
                + ",\"writtenBytes\":" + bytesWritten
                + ",\"afterSequence\":" + sequence + "}");
        logger.warning("Ground navigator diagnostics reached the "
                + maximumFileBytes + " byte session limit; further route "
                + "snapshots are suppressed for \""
                + file.toAbsolutePath().normalize() + "\"");
        return false;
    }

    private void writeControlLine(String value) throws IOException {
        writer.write(value);
        writer.newLine();
        writer.flush();
        bytesWritten += encodedLineBytes(value);
    }

    private static long encodedLineBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length
                + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
    }

    private static String nullableIndex(int value) {
        return value < 0 ? "null" : Integer.toString(value);
    }

    private static String number(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return "null";
        return String.format(Locale.ENGLISH, "%.4f", value);
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
