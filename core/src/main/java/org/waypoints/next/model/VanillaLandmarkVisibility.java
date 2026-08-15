package org.waypoints.next.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable per-endpoint On/Off choices; missing entries deliberately default On. */
public final class VanillaLandmarkVisibility {
    private final Map<String, Boolean> values;

    public VanillaLandmarkVisibility() {
        this(Collections.<String, Boolean>emptyMap());
    }

    public VanillaLandmarkVisibility(Map<String, Boolean> source) {
        LinkedHashMap<String, Boolean> copy = new LinkedHashMap<String, Boolean>();
        if (source != null) {
            for (Map.Entry<String, Boolean> entry : source.entrySet()) {
                String key = clean(entry.getKey());
                if (!key.isEmpty() && entry.getValue() != null) {
                    copy.put(key, entry.getValue());
                }
            }
        }
        values = Collections.unmodifiableMap(copy);
    }

    public boolean isEnabled(String endpoint, VanillaLandmarkKind kind) {
        Boolean value = values.get(key(endpoint, kind));
        return value == null || value.booleanValue();
    }

    public VanillaLandmarkVisibility withEnabled(String endpoint,
                                                  VanillaLandmarkKind kind,
                                                  boolean enabled) {
        LinkedHashMap<String, Boolean> changed =
                new LinkedHashMap<String, Boolean>(values);
        changed.put(key(endpoint, kind), Boolean.valueOf(enabled));
        return new VanillaLandmarkVisibility(changed);
    }

    public Map<String, Boolean> entries() { return values; }

    public static String key(String endpoint, VanillaLandmarkKind kind) {
        String cleanEndpoint = clean(endpoint);
        if (cleanEndpoint.isEmpty() || kind == null) {
            throw new IllegalArgumentException("endpoint and vanilla landmark are required");
        }
        return cleanEndpoint.toLowerCase(Locale.ENGLISH) + "|" + kind.name();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
