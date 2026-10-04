package org.waypoints.next.integration;

import org.waypoints.next.source.MapBounds;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;
import org.waypoints.next.render.WaypointRenderConfiguration;

import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/** Local paths, map validation bounds, and bounded Phase 2 render budgets. */
public final class WaypointClientConfiguration
        implements WaypointRenderConfiguration {
    private final Path dataFile;
    private final Path transferFile;
    private final Path vanillaLandmarkStateFile;
    private final Path lootMapLogDirectory;
    private final Path archaeologySessionFile;
    private final Path archaeologyKnownLocationsFile;
    private final Path navigationRouteLogDirectory;
    private final boolean lootMapEnabled;
    private final boolean archaeologyEnabled;
    private final int archaeologyHistoryLimit;
    private final boolean navigationRouteDiagnosticsEnabled;
    private final float navigationCartMaximumSlopeDirt;
    private final float navigationCartMaximumWaterDepthMetres;
    private final int navigationRouteLogTileInterval;
    private final NavigationRouteVisualStyle navigationRouteVisualStyle;
    private final int navigationPulseMaximumDistanceMetres;
    private final boolean navigationHighwaysEnabled;
    private final Path navigationHighwaysCacheDirectory;
    private final int navigationHighwaysSyncMinutes;
    private final boolean serverMapEnabled;
    private final Path serverMapCacheDirectory;
    private final int serverMapSyncMinutes;
    private final boolean serverMapShowDeeds;
    private final boolean serverMapShowHighways;
    private final boolean deedProviderEnabled;
    private final Path deedProviderCacheDirectory;
    private final String deedProviderMappings;
    private final MapBounds mapBounds;
    private final int maximumCompassMarkers;
    private final int maximumWorldEffects;
    private final int worldEffectDistanceMetres;
    private final int maximumWorldLabels;
    private final int worldLabelDistanceMetres;
    private final boolean scannerNotifications;
    private final boolean scannerOutlines;
    private final int scannerMaximumOutlines;
    private final int scannerOutlineDistanceMetres;
    private final Set<String> scannerExcludedNames;

    private WaypointClientConfiguration(Path dataFile, Path transferFile,
                                        Path vanillaLandmarkStateFile,
                                        Path lootMapLogDirectory,
                                        Path archaeologySessionFile,
                                        Path archaeologyKnownLocationsFile,
                                        Path navigationRouteLogDirectory,
                                        boolean lootMapEnabled,
                                        boolean archaeologyEnabled,
                                        int archaeologyHistoryLimit,
                                        boolean navigationRouteDiagnosticsEnabled,
                                        float navigationCartMaximumSlopeDirt,
                                        float navigationCartMaximumWaterDepthMetres,
                                        int navigationRouteLogTileInterval,
                                        NavigationRouteVisualStyle navigationRouteVisualStyle,
                                        int navigationPulseMaximumDistanceMetres,
                                        boolean navigationHighwaysEnabled,
                                        Path navigationHighwaysCacheDirectory,
                                        int navigationHighwaysSyncMinutes,
                                        boolean serverMapEnabled,
                                        Path serverMapCacheDirectory,
                                        int serverMapSyncMinutes,
                                        boolean serverMapShowDeeds,
                                        boolean serverMapShowHighways,
                                        boolean deedProviderEnabled,
                                        Path deedProviderCacheDirectory,
                                        String deedProviderMappings,
                                        MapBounds mapBounds,
                                        int maximumCompassMarkers,
                                        int maximumWorldEffects,
                                        int worldEffectDistanceMetres,
                                        int maximumWorldLabels,
                                        int worldLabelDistanceMetres,
                                        boolean scannerNotifications,
                                        boolean scannerOutlines,
                                        int scannerMaximumOutlines,
                                        int scannerOutlineDistanceMetres,
                                        Set<String> scannerExcludedNames) {
        this.dataFile = dataFile;
        this.transferFile = transferFile;
        this.vanillaLandmarkStateFile = vanillaLandmarkStateFile;
        this.lootMapLogDirectory = lootMapLogDirectory;
        this.archaeologySessionFile = archaeologySessionFile;
        this.archaeologyKnownLocationsFile = archaeologyKnownLocationsFile;
        this.navigationRouteLogDirectory = navigationRouteLogDirectory;
        this.lootMapEnabled = lootMapEnabled;
        this.archaeologyEnabled = archaeologyEnabled;
        this.archaeologyHistoryLimit = archaeologyHistoryLimit;
        this.navigationRouteDiagnosticsEnabled =
                navigationRouteDiagnosticsEnabled;
        this.navigationCartMaximumSlopeDirt = navigationCartMaximumSlopeDirt;
        this.navigationCartMaximumWaterDepthMetres =
                navigationCartMaximumWaterDepthMetres;
        this.navigationRouteLogTileInterval = navigationRouteLogTileInterval;
        this.navigationRouteVisualStyle = navigationRouteVisualStyle;
        this.navigationPulseMaximumDistanceMetres =
                navigationPulseMaximumDistanceMetres;
        this.navigationHighwaysEnabled = navigationHighwaysEnabled;
        this.navigationHighwaysCacheDirectory = navigationHighwaysCacheDirectory;
        this.navigationHighwaysSyncMinutes = navigationHighwaysSyncMinutes;
        this.serverMapEnabled = serverMapEnabled;
        this.serverMapCacheDirectory = serverMapCacheDirectory;
        this.serverMapSyncMinutes = serverMapSyncMinutes;
        this.serverMapShowDeeds = serverMapShowDeeds;
        this.serverMapShowHighways = serverMapShowHighways;
        this.deedProviderEnabled = deedProviderEnabled;
        this.deedProviderCacheDirectory = deedProviderCacheDirectory;
        this.deedProviderMappings = deedProviderMappings;
        this.mapBounds = mapBounds;
        this.maximumCompassMarkers = maximumCompassMarkers;
        this.maximumWorldEffects = maximumWorldEffects;
        this.worldEffectDistanceMetres = worldEffectDistanceMetres;
        this.maximumWorldLabels = maximumWorldLabels;
        this.worldLabelDistanceMetres = worldLabelDistanceMetres;
        this.scannerNotifications = scannerNotifications;
        this.scannerOutlines = scannerOutlines;
        this.scannerMaximumOutlines = scannerMaximumOutlines;
        this.scannerOutlineDistanceMetres = scannerOutlineDistanceMetres;
        this.scannerExcludedNames = scannerExcludedNames;
    }

    public static WaypointClientConfiguration defaults() {
        return from(new Properties());
    }

    public static WaypointClientConfiguration from(Properties properties) {
        return from(properties, null);
    }

    public static WaypointClientConfiguration from(Properties properties,
            Consumer<String> warningSink) {
        ConfigurationProperties source =
                new ConfigurationProperties(properties, warningSink);
        return new WaypointClientConfiguration(
                source.path("waypointDataFile", "wurm-waypointer-data/waypoints.wpt"),
                source.path("waypointTransferFile", "wurm-waypointer-data/waypoints-transfer.wpt"),
                source.path("vanillaLandmarkStateFile", "wurm-waypointer-data/vanilla-landmarks.state"),
                source.path("lootMapLogDirectory", "mods/wurm-waypointer/lootmap-hunts"),
                source.path("archaeologySessionFile", "mods/wurm-waypointer/archaeology-sessions.properties"),
                source.path("archaeologyKnownLocationsFile", "mods/wurm-waypointer/archaeology-known-locations.properties"),
                source.path("navigationRouteLogDirectory", "mods/wurm-waypointer/navigation-routes"),
                source.bool("lootMapEnabled", true),
                source.bool("archaeologyEnabled", true),
                source.integer("archaeologyHistoryLimit", 64, 8, 1024),
                source.bool("navigationRouteDiagnostics", false),
                source.decimal("navigationCartMaximumSlopeDirt", 40.0f, 0.1f, 1000.0f),
                source.decimal("navigationCartMaximumWaterDepthMetres", 0.7f, 0.0f, 100.0f),
                source.integer("navigationRouteLogTileInterval", 8, 1, 512),
                source.enumeration("navigationRouteVisualStyle",
                        NavigationRouteVisualStyle.MOVING_DASHES,
                        NavigationRouteVisualStyle.class),
                source.integer("navigationPulseMaximumDistanceMetres", 240, 16, 2000),
                source.bool("navigationHighwaysEnabled", true),
                source.path("navigationHighwaysCacheDirectory", "mods/wurm-waypointer/maps"),
                source.integer("navigationHighwaysSyncMinutes", 15, 1, 1440),
                source.bool("serverMapEnabled", true),
                source.path("serverMapCacheDirectory", "mods/wurm-waypointer/maps"),
                source.integer("serverMapSyncMinutes", 60, 5, 1440),
                source.bool("serverMapShowDeeds", true),
                source.bool("serverMapShowHighways", true),
                source.bool("deedProviderEnabled", true),
                source.path("deedProviderCacheDirectory", "mods/wurm-waypointer/deeds"),
                source.textValue("deedProviderMappings", "", 16_384),
                new MapBounds(source.integer("waypointMapWidth", 4096, 1, 65536),
                        source.integer("waypointMapHeight", 4096, 1, 65536)),
                source.integer("maximumCompassMarkers",
                        "phase2MaxCompassMarkers", 64, 1, 1024),
                source.integer("maximumWorldEffects",
                        "phase2MaxWorldEffects", 16, 0, 1024),
                source.integer("worldEffectDistanceMetres",
                        "phase2WorldEffectDistanceMetres", 12000, 1, 100000),
                source.integer("maximumWorldLabels",
                        "phase2MaxWorldLabels", 16, 0, 1024),
                source.integer("worldLabelDistanceMetres",
                        "phase2WorldLabelDistanceMetres", 12000, 1, 100000),
                source.bool("scannerNotifications", true),
                source.bool("scannerOutlines", true),
                source.integer("scannerMaximumOutlines", 8, 0, 32),
                source.integer("scannerOutlineDistanceMetres", 80, 4, 512),
                source.commaSeparated("scannerExcludedNames", "", 32, 80));
    }

    public Path getDataFile() { return dataFile; }
    public Path getTransferFile() { return transferFile; }
    public Path getVanillaLandmarkStateFile() { return vanillaLandmarkStateFile; }
    public Path getLootMapLogDirectory() { return lootMapLogDirectory; }
    public Path getArchaeologySessionFile() { return archaeologySessionFile; }
    public Path getArchaeologyKnownLocationsFile() {
        return archaeologyKnownLocationsFile;
    }
    public Path getNavigationRouteLogDirectory() {
        return navigationRouteLogDirectory;
    }
    public boolean isLootMapEnabled() { return lootMapEnabled; }
    public boolean isArchaeologyEnabled() { return archaeologyEnabled; }
    public int getArchaeologyHistoryLimit() { return archaeologyHistoryLimit; }
    public boolean isNavigationRouteDiagnosticsEnabled() {
        return navigationRouteDiagnosticsEnabled;
    }
    public float getNavigationCartMaximumSlopeDirt() {
        return navigationCartMaximumSlopeDirt;
    }
    public float getNavigationCartMaximumWaterDepthMetres() {
        return navigationCartMaximumWaterDepthMetres;
    }
    public int getNavigationRouteLogTileInterval() {
        return navigationRouteLogTileInterval;
    }
    public NavigationRouteVisualStyle getNavigationRouteVisualStyle() {
        return navigationRouteVisualStyle;
    }
    public int getNavigationPulseMaximumDistanceMetres() {
        return navigationPulseMaximumDistanceMetres;
    }
    public boolean isNavigationHighwaysEnabled() {
        return navigationHighwaysEnabled;
    }
    public Path getNavigationHighwaysCacheDirectory() {
        return navigationHighwaysCacheDirectory;
    }
    public int getNavigationHighwaysSyncMinutes() {
        return navigationHighwaysSyncMinutes;
    }
    public boolean isServerMapEnabled() { return serverMapEnabled; }
    public Path getServerMapCacheDirectory() { return serverMapCacheDirectory; }
    public int getServerMapSyncMinutes() { return serverMapSyncMinutes; }
    public boolean isServerMapShowDeeds() { return serverMapShowDeeds; }
    public boolean isServerMapShowHighways() { return serverMapShowHighways; }
    public boolean isDeedProviderEnabled() { return deedProviderEnabled; }
    public Path getDeedProviderCacheDirectory() { return deedProviderCacheDirectory; }
    public String getDeedProviderMappings() { return deedProviderMappings; }
    public MapBounds getMapBounds() { return mapBounds; }
    @Override public int getMapWidth() { return mapBounds.getWidth(); }
    @Override public int getMapHeight() { return mapBounds.getHeight(); }
    public int getMaximumCompassMarkers() { return maximumCompassMarkers; }
    public int getMaximumWorldEffects() { return maximumWorldEffects; }
    public int getWorldEffectDistanceMetres() { return worldEffectDistanceMetres; }
    public int getMaximumWorldLabels() { return maximumWorldLabels; }
    public int getWorldLabelDistanceMetres() { return worldLabelDistanceMetres; }
    public boolean isScannerNotifications() { return scannerNotifications; }
    public boolean isScannerOutlines() { return scannerOutlines; }
    public int getScannerMaximumOutlines() { return scannerMaximumOutlines; }
    public int getScannerOutlineDistanceMetres() {
        return scannerOutlineDistanceMetres;
    }
    public Set<String> getScannerExcludedNames() { return scannerExcludedNames; }

    public String diagnosticSummary() {
        return "dataFile=\"" + dataFile + "\", transferFile=\"" + transferFile
                + "\", vanillaLandmarkStateFile=\"" + vanillaLandmarkStateFile
                + "\", lootMapLogDirectory=\"" + lootMapLogDirectory
                + "\", lootMapEnabled=" + lootMapEnabled
                + ", archaeologySessionFile=\"" + archaeologySessionFile
                + "\", archaeologyKnownLocationsFile=\""
                + archaeologyKnownLocationsFile
                + "\", archaeologyEnabled=" + archaeologyEnabled
                + ", archaeologyHistoryLimit=" + archaeologyHistoryLimit
                + ", navigationRouteLogDirectory=\""
                + navigationRouteLogDirectory
                + "\", navigationRouteDiagnostics="
                + navigationRouteDiagnosticsEnabled
                + ", navigationCartMaximumSlopeDirt="
                + navigationCartMaximumSlopeDirt
                + ", navigationCartMaximumWaterDepthMetres="
                + navigationCartMaximumWaterDepthMetres
                + ", navigationRouteLogTileInterval="
                + navigationRouteLogTileInterval
                + ", navigationRouteVisualStyle="
                + navigationRouteVisualStyle
                + ", navigationPulseMaximumDistanceMetres="
                + navigationPulseMaximumDistanceMetres
                + ", navigationHighwaysEnabled=" + navigationHighwaysEnabled
                + ", navigationHighwaysCacheDirectory=\""
                + navigationHighwaysCacheDirectory
                + "\", navigationHighwaysSyncMinutes="
                + navigationHighwaysSyncMinutes
                + ", serverMapEnabled=" + serverMapEnabled
                + ", serverMapCacheDirectory=\"" + serverMapCacheDirectory
                + "\", serverMapSyncMinutes=" + serverMapSyncMinutes
                + ", serverMapShowDeeds=" + serverMapShowDeeds
                + ", serverMapShowHighways=" + serverMapShowHighways
                + ", deedProviderEnabled=" + deedProviderEnabled
                + ", deedProviderCacheDirectory=\"" + deedProviderCacheDirectory
                + "\", deedProviderMappings="
                + (deedProviderMappings.isEmpty() ? "none" : "configured")
                + ", mapBounds=" + mapBounds.getWidth() + "x" + mapBounds.getHeight()
                + ", maximumCompassMarkers=" + maximumCompassMarkers
                + ", maximumWorldEffects=" + maximumWorldEffects
                + ", worldEffectDistanceMetres=" + worldEffectDistanceMetres
                + ", maximumWorldLabels=" + maximumWorldLabels
                + ", worldLabelDistanceMetres=" + worldLabelDistanceMetres
                + ", scannerNotifications=" + scannerNotifications
                + ", scannerOutlines=" + scannerOutlines
                + ", scannerMaximumOutlines=" + scannerMaximumOutlines
                + ", scannerOutlineDistanceMetres="
                + scannerOutlineDistanceMetres
                + ", scannerExcludedNames=" + scannerExcludedNames;
    }

}
