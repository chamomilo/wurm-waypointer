package org.waypoints.next.deeds;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One validated provider-owned deed location and its display metadata. */
public final class DeedRecord {
    private final String stableKey;
    private final String name;
    private final int tileX;
    private final int tileY;
    private final Map<String, String> metadata;

    public DeedRecord(String stableKey, String name, int tileX, int tileY,
                      Map<String, String> metadata) {
        this.stableKey = required(stableKey, 240, "stable key");
        this.name = required(name, 120, "name");
        if (tileX < 0 || tileY < 0) throw new IllegalArgumentException(
                "deed coordinates must be non-negative");
        this.tileX = tileX;
        this.tileY = tileY;
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        if (metadata != null) for (Map.Entry<String, String> field
                : metadata.entrySet()) {
            String key = clean(field.getKey());
            String value = clean(field.getValue());
            if (!key.isEmpty() && key.length() <= 80 && value.length() <= 1000) {
                copy.put(key, value);
            }
        }
        this.metadata = Collections.unmodifiableMap(copy);
    }

    public String getStableKey() { return stableKey; }
    public String getName() { return name; }
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }
    public Map<String, String> getMetadata() { return metadata; }
    public String metadata(String key) {
        if (key == null) return "";
        String direct = metadata.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, String> field : metadata.entrySet()) {
            if (field.getKey().equalsIgnoreCase(key)) return field.getValue();
        }
        return "";
    }

    /** Stable fallback for feeds that expose names but no durable numeric id. */
    public static String nameKey(String name) {
        return clean(name).toLowerCase(Locale.ENGLISH)
                .replaceAll("\\s+", " ");
    }

    private static String required(String value, int maximum, String label) {
        String clean = clean(value);
        if (clean.isEmpty() || clean.length() > maximum) {
            throw new IllegalArgumentException("deed " + label
                    + " is required and must not exceed " + maximum + " characters");
        }
        return clean;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeedRecord)) return false;
        DeedRecord that = (DeedRecord) other;
        return tileX == that.tileX && tileY == that.tileY
                && stableKey.equals(that.stableKey) && name.equals(that.name)
                && metadata.equals(that.metadata);
    }

    @Override public int hashCode() {
        return Objects.hash(stableKey, name, tileX, tileY, metadata);
    }
}
