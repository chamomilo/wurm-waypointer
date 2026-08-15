package org.waypoints.next.render;

import org.waypoints.next.navigation.NavigationRouteVisualStyle;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The complete configuration surface visible to the renderer.
 *
 * Feature adapters may implement this contract, but the renderer must not
 * depend on their runtime or configuration classes.
 */
public interface WaypointRenderConfiguration {
    Path getNavigationRouteLogDirectory();
    boolean isNavigationRouteDiagnosticsEnabled();
    float getNavigationCartMaximumSlopeDirt();
    float getNavigationCartMaximumWaterDepthMetres();
    int getNavigationRouteLogTileInterval();
    NavigationRouteVisualStyle getNavigationRouteVisualStyle();
    int getNavigationPulseMaximumDistanceMetres();
    boolean isNavigationHighwaysEnabled();
    Path getNavigationHighwaysCacheDirectory();
    int getNavigationHighwaysSyncMinutes();
    int getMapWidth();
    int getMapHeight();
    int getMaximumCompassMarkers();
    int getMaximumWorldEffects();
    int getWorldEffectDistanceMetres();
    int getMaximumWorldLabels();
    int getWorldLabelDistanceMetres();

    static WaypointRenderConfiguration defaults() {
        return Defaults.INSTANCE;
    }

    final class Defaults implements WaypointRenderConfiguration {
        private static final Defaults INSTANCE = new Defaults();

        private Defaults() {
        }

        @Override public Path getNavigationRouteLogDirectory() {
            return Paths.get("mods", "wurm-waypointer", "navigation-routes");
        }
        @Override public boolean isNavigationRouteDiagnosticsEnabled() { return true; }
        @Override public float getNavigationCartMaximumSlopeDirt() { return 40.0f; }
        @Override public float getNavigationCartMaximumWaterDepthMetres() { return 0.7f; }
        @Override public int getNavigationRouteLogTileInterval() { return 8; }
        @Override public NavigationRouteVisualStyle getNavigationRouteVisualStyle() {
            return NavigationRouteVisualStyle.MOVING_DASHES;
        }
        @Override public int getNavigationPulseMaximumDistanceMetres() { return 240; }
        @Override public boolean isNavigationHighwaysEnabled() { return true; }
        @Override public Path getNavigationHighwaysCacheDirectory() {
            return Paths.get("mods", "wurm-waypointer", "maps");
        }
        @Override public int getNavigationHighwaysSyncMinutes() { return 15; }
        @Override public int getMapWidth() { return 4096; }
        @Override public int getMapHeight() { return 4096; }
        @Override public int getMaximumCompassMarkers() { return 64; }
        @Override public int getMaximumWorldEffects() { return 16; }
        @Override public int getWorldEffectDistanceMetres() { return 12000; }
        @Override public int getMaximumWorldLabels() { return 16; }
        @Override public int getWorldLabelDistanceMetres() { return 12000; }
    }
}
