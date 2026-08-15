package org.waypoints.next.navigation;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointSourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable render-safe projection; it contains no store or client objects. */
public final class NavigationTarget {
    private final NavigationTargetKey key;
    private final String name;
    private final WaypointCoordinate coordinate;
    private final MarkerStyle markerStyle;
    private final WaypointSourceType sourceType;
    private final boolean selected;
    private final boolean worldBeamVisible;
    private final boolean navigatorActive;
    private final int arrivalRadiusMetres;
    private final long expiresAtEpochMillis;
    private final Map<String, List<String>> extensions;

    NavigationTarget(NavigationTargetKey key, String name,
                     WaypointCoordinate coordinate, MarkerStyle markerStyle,
                     WaypointSourceType sourceType, boolean selected,
                     boolean worldBeamVisible, int arrivalRadiusMetres) {
        this(key, name, coordinate, markerStyle, sourceType, selected,
                worldBeamVisible, arrivalRadiusMetres, 0L, false,
                Collections.<String, List<String>>emptyMap());
    }

    NavigationTarget(NavigationTargetKey key, String name,
                     WaypointCoordinate coordinate, MarkerStyle markerStyle,
                     WaypointSourceType sourceType, boolean selected,
                     boolean worldBeamVisible, int arrivalRadiusMetres,
                     long expiresAtEpochMillis) {
        this(key, name, coordinate, markerStyle, sourceType, selected,
                worldBeamVisible, arrivalRadiusMetres, expiresAtEpochMillis,
                false, Collections.<String, List<String>>emptyMap());
    }

    NavigationTarget(NavigationTargetKey key, String name,
                     WaypointCoordinate coordinate, MarkerStyle markerStyle,
                     WaypointSourceType sourceType, boolean selected,
                     boolean worldBeamVisible, int arrivalRadiusMetres,
                     long expiresAtEpochMillis, boolean navigatorActive) {
        this(key, name, coordinate, markerStyle, sourceType, selected,
                worldBeamVisible, arrivalRadiusMetres, expiresAtEpochMillis,
                navigatorActive, Collections.<String, List<String>>emptyMap());
    }

    NavigationTarget(NavigationTargetKey key, String name,
                     WaypointCoordinate coordinate, MarkerStyle markerStyle,
                     WaypointSourceType sourceType, boolean selected,
                     boolean worldBeamVisible, int arrivalRadiusMetres,
                     long expiresAtEpochMillis, boolean navigatorActive,
                     Map<String, List<String>> extensions) {
        this.key = key;
        this.name = name;
        this.coordinate = coordinate;
        this.markerStyle = markerStyle;
        this.sourceType = sourceType;
        this.selected = selected;
        this.worldBeamVisible = worldBeamVisible;
        this.navigatorActive = navigatorActive;
        this.arrivalRadiusMetres = arrivalRadiusMetres;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
        LinkedHashMap<String, List<String>> copy =
                new LinkedHashMap<String, List<String>>();
        if (extensions != null) {
            for (Map.Entry<String, List<String>> entry
                    : extensions.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(
                        new ArrayList<String>(entry.getValue())));
            }
        }
        this.extensions = Collections.unmodifiableMap(copy);
    }

    public NavigationTargetKey getKey() { return key; }
    public String getName() { return name; }
    public WaypointCoordinate getCoordinate() { return coordinate; }
    public MarkerStyle getMarkerStyle() { return markerStyle; }
    public WaypointSourceType getSourceType() { return sourceType; }
    public boolean isVanillaSystem() {
        return sourceType == WaypointSourceType.VANILLA_SYSTEM;
    }
    public boolean isCompassVisible() { return !isVanillaSystem(); }
    public boolean isSelected() { return selected; }
    public boolean isWorldBeamVisible() { return worldBeamVisible; }
    public boolean isNavigatorActive() { return navigatorActive; }
    public int getArrivalRadiusMetres() { return arrivalRadiusMetres; }
    public long getExpiresAtEpochMillis() { return expiresAtEpochMillis; }
    public boolean isTemporary() { return expiresAtEpochMillis > 0L; }
    public Map<String, List<String>> getExtensions() { return extensions; }
    public String getExtension(String key) {
        List<String> values = extensions.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
