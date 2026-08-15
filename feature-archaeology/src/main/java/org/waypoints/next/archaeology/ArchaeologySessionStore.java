package org.waypoints.next.archaeology;

import org.waypoints.next.model.WaypointLayer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Versioned active-session and bounded-history persistence. */
public final class ArchaeologySessionStore {
    private static final String VERSION = "2";
    private static final String LEGACY_VERSION = "1";
    private final AtomicArchaeologyProperties file;

    public ArchaeologySessionStore(Path path) { file = new AtomicArchaeologyProperties(path); }
    public Path getFile() { return file.getFile(); }
    public boolean wasRecoveredFromBackup() { return file.wasRecoveredFromBackup(); }

    public List<ArchaeologyReportSession> load() throws IOException {
        Properties values = file.load();
        if (values.isEmpty()) return Collections.emptyList();
        boolean migrateV1 = requireVersion(values);
        String ids = values.getProperty("session.ids", "").trim();
        if (ids.isEmpty()) return Collections.emptyList();
        List<ArchaeologyReportSession> result = new ArrayList<ArchaeologyReportSession>();
        for (String idText : ids.split(",")) {
            String cleanId = idText.trim();
            if (!cleanId.isEmpty()) result.add(read(values, cleanId, migrateV1));
        }
        return result;
    }

    public void save(Collection<ArchaeologyReportSession> sessions,
                     int historyLimit) throws IOException {
        List<ArchaeologyReportSession> active = new ArrayList<ArchaeologyReportSession>();
        List<ArchaeologyReportSession> history = new ArrayList<ArchaeologyReportSession>();
        if (sessions != null) for (ArchaeologyReportSession session : sessions) {
            (session.isActive() ? active : history).add(session);
        }
        Collections.sort(history, new Comparator<ArchaeologyReportSession>() {
            @Override public int compare(ArchaeologyReportSession left,
                                         ArchaeologyReportSession right) {
                return right.getUpdatedAt().compareTo(left.getUpdatedAt());
            }
        });
        if (history.size() > historyLimit) {
            history = new ArrayList<ArchaeologyReportSession>(
                    history.subList(0, historyLimit));
        }
        List<ArchaeologyReportSession> kept = new ArrayList<ArchaeologyReportSession>(active);
        kept.addAll(history);
        Properties values = new Properties();
        values.setProperty("format.version", VERSION);
        StringBuilder ids = new StringBuilder();
        for (ArchaeologyReportSession session : kept) {
            if (ids.length() > 0) ids.append(',');
            ids.append(session.getId());
            write(values, session);
        }
        values.setProperty("session.ids", ids.toString());
        file.save(values, "Wurm Waypointer archaeology report sessions");
    }

    private static void write(Properties values, ArchaeologyReportSession session) {
        String p = "session." + session.getId() + ".";
        put(values, p + "reportKey", session.getReportKey());
        put(values, p + "serverFingerprint", session.getServerFingerprint());
        put(values, p + "user", session.getUser());
        put(values, p + "deedName", session.getDeedName());
        put(values, p + "normalizedDeedName", session.getNormalizedDeedName());
        put(values, p + "reportItemId", session.getReportItemId() == null
                ? "" : session.getReportItemId().toString());
        put(values, p + "status", session.getStatus().name());
        put(values, p + "lastPlayerX", Double.toString(session.getLastPlayerTileX()));
        put(values, p + "lastPlayerY", Double.toString(session.getLastPlayerTileY()));
        put(values, p + "lastPlayerLayer", session.getLastPlayerLayer().name());
        put(values, p + "waypointX", Double.toString(session.getWaypointTileX()));
        put(values, p + "waypointY", Double.toString(session.getWaypointTileY()));
        put(values, p + "waypointLayer", session.getWaypointLayer().name());
        put(values, p + "distanceBand", session.getDistanceBand() == null
                ? "" : session.getDistanceBand().name());
        put(values, p + "direction", session.getDirection() == null
                ? "" : session.getDirection().name());
        put(values, p + "terminalStep", Integer.toString(session.getTerminalStep()));
        put(values, p + "createdAt", session.getCreatedAt().toString());
        put(values, p + "updatedAt", session.getUpdatedAt().toString());
        put(values, p + "lastEventFingerprint", session.getLastEventFingerprint());
        put(values, p + "readyChimed", Boolean.toString(session.isReadyChimed()));
    }

    private static ArchaeologyReportSession read(Properties values, String idText,
                                                   boolean migrateV1) {
        String p = "session." + idText + ".";
        String item = required(values, p + "reportItemId", false);
        String band = required(values, p + "distanceBand", false);
        String direction = required(values, p + "direction", false);
        ArchaeologyReportSession restored = ArchaeologyReportSession.restore(
                UUID.fromString(idText),
                required(values, p + "reportKey", true),
                required(values, p + "serverFingerprint", true),
                required(values, p + "user", true),
                required(values, p + "deedName", true),
                required(values, p + "normalizedDeedName", true),
                item.isEmpty() ? null : Long.valueOf(item),
                ArchaeologyReportStatus.valueOf(required(values, p + "status", true)),
                number(values, p + "lastPlayerX"),
                number(values, p + "lastPlayerY"),
                WaypointLayer.valueOf(required(values, p + "lastPlayerLayer", true)),
                number(values, p + "waypointX"), number(values, p + "waypointY"),
                WaypointLayer.valueOf(required(values, p + "waypointLayer", true)),
                band.isEmpty() ? null : ArchaeologyDistanceBand.valueOf(band),
                direction.isEmpty() ? null : ArchaeologyDirection.valueOf(direction),
                Integer.parseInt(required(values, p + "terminalStep", true)),
                Instant.parse(required(values, p + "createdAt", true)),
                Instant.parse(required(values, p + "updatedAt", true)),
                required(values, p + "lastEventFingerprint", false),
                Boolean.parseBoolean(required(values, p + "readyChimed", true)));
        return migrateV1 ? restored.migrateV1TileIndexes() : restored;
    }

    private static boolean requireVersion(Properties values) {
        String version = values.getProperty("format.version");
        if (!VERSION.equals(version) && !LEGACY_VERSION.equals(version)) {
            throw new IllegalArgumentException("unsupported archaeology session format");
        }
        return LEGACY_VERSION.equals(version);
    }

    private static String required(Properties values, String key, boolean nonEmpty) {
        String value = values.getProperty(key);
        if (value == null || (nonEmpty && value.trim().isEmpty())) {
            throw new IllegalArgumentException("missing archaeology session field " + key);
        }
        return value.trim();
    }

    private static double number(Properties values, String key) {
        return Double.parseDouble(required(values, key, true));
    }

    private static void put(Properties values, String key, String value) {
        values.setProperty(key, value == null ? "" : value);
    }
}
