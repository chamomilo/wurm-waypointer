package org.waypoints.next.archaeology;

import org.waypoints.next.model.WaypointLayer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Separate exact-location registry; approximate observations never call save mutations. */
public final class KnownArchaeologyLocationStore {
    private static final String VERSION = "2";
    private static final String LEGACY_VERSION = "1";
    private final AtomicArchaeologyProperties file;

    public KnownArchaeologyLocationStore(Path path) {
        file = new AtomicArchaeologyProperties(path);
    }
    public Path getFile() { return file.getFile(); }
    public boolean wasRecoveredFromBackup() { return file.wasRecoveredFromBackup(); }

    public List<KnownArchaeologyLocation> load() throws IOException {
        Properties values = file.load();
        if (values.isEmpty()) return Collections.emptyList();
        String version = values.getProperty("format.version");
        if (!VERSION.equals(version) && !LEGACY_VERSION.equals(version)) {
            throw new IllegalArgumentException("unsupported known archaeology format");
        }
        boolean migrateV1 = LEGACY_VERSION.equals(version);
        String ids = values.getProperty("location.ids", "").trim();
        if (ids.isEmpty()) return Collections.emptyList();
        List<KnownArchaeologyLocation> result = new ArrayList<KnownArchaeologyLocation>();
        for (String idText : ids.split(",")) {
            String id = idText.trim();
            if (id.isEmpty()) continue;
            String p = "location." + id + ".";
            String source = required(values, p + "sourceSessionId", false);
            KnownArchaeologyLocation location = new KnownArchaeologyLocation(
                    required(values, p + "serverFingerprint", true),
                    required(values, p + "deedName", true),
                    Double.parseDouble(required(values, p + "tileX", true)),
                    Double.parseDouble(required(values, p + "tileY", true)),
                    WaypointLayer.valueOf(required(values, p + "layer", true)),
                    Instant.parse(required(values, p + "discoveredAt", true)),
                    Instant.parse(required(values, p + "lastConfirmedAt", true)),
                    source.isEmpty() ? null : UUID.fromString(source),
                    Boolean.parseBoolean(required(values, p + "needsConfirmation", true)));
            result.add(migrateV1 ? location.migrateV1TileIndexes() : location);
        }
        return result;
    }

    public void save(Collection<KnownArchaeologyLocation> locations)
            throws IOException {
        Properties values = new Properties();
        values.setProperty("format.version", VERSION);
        StringBuilder ids = new StringBuilder();
        int index = 0;
        if (locations != null) for (KnownArchaeologyLocation location : locations) {
            String id = Integer.toString(index++);
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
            String p = "location." + id + ".";
            put(values, p + "serverFingerprint", location.getServerFingerprint());
            put(values, p + "deedName", location.getDeedName());
            put(values, p + "normalizedDeedName", location.getNormalizedDeedName());
            put(values, p + "tileX", Double.toString(location.getTileX()));
            put(values, p + "tileY", Double.toString(location.getTileY()));
            put(values, p + "layer", location.getLayer().name());
            put(values, p + "discoveredAt", location.getDiscoveredAt().toString());
            put(values, p + "lastConfirmedAt", location.getLastConfirmedAt().toString());
            put(values, p + "sourceSessionId", location.getSourceSessionId() == null
                    ? "" : location.getSourceSessionId().toString());
            put(values, p + "needsConfirmation",
                    Boolean.toString(location.isNeedsConfirmation()));
        }
        values.setProperty("location.ids", ids.toString());
        file.save(values, "Wurm Waypointer exact archaeology locations");
    }

    private static String required(Properties values, String key, boolean nonEmpty) {
        String value = values.getProperty(key);
        if (value == null || (nonEmpty && value.trim().isEmpty())) {
            throw new IllegalArgumentException("missing known archaeology field " + key);
        }
        return value.trim();
    }

    private static void put(Properties values, String key, String value) {
        values.setProperty(key, value == null ? "" : value);
    }
}
