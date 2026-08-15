package org.waypoints.next.integration;

import org.junit.Test;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;

import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WaypointClientConfigurationTest {
    @Test public void legacyConfigurationKeepsMovingDashes() {
        WaypointClientConfiguration value =
                WaypointClientConfiguration.from(new Properties());
        assertEquals(NavigationRouteVisualStyle.MOVING_DASHES,
                value.getNavigationRouteVisualStyle());
        assertEquals(240, value.getNavigationPulseMaximumDistanceMetres());
        assertFalse(value.isNavigationRouteDiagnosticsEnabled());
    }

    @Test public void parsesLocalPathsAndMapBounds() {
        Properties properties = new Properties();
        properties.setProperty("waypointDataFile", "mods/test/data.wpt");
        properties.setProperty("waypointTransferFile", "mods/test/export.wpt");
        properties.setProperty("waypointMapWidth", "8192");
        properties.setProperty("waypointMapHeight", "4096");
        properties.setProperty("lootMapLogDirectory", "mods/test/loot-hunts");
        properties.setProperty("lootMapEnabled", "false");
        properties.setProperty("archaeologySessionFile",
                "mods/test/archaeology-sessions.properties");
        properties.setProperty("archaeologyKnownLocationsFile",
                "mods/test/archaeology-known.properties");
        properties.setProperty("archaeologyEnabled", "false");
        properties.setProperty("archaeologyHistoryLimit", "23");
        properties.setProperty("navigationRouteLogDirectory",
                "mods/test/navigation-routes");
        properties.setProperty("navigationRouteDiagnostics", "false");
        properties.setProperty("navigationCartMaximumSlopeDirt", "35.5");
        properties.setProperty("navigationCartMaximumWaterDepthMetres", "0.6");
        properties.setProperty("navigationRouteLogTileInterval", "4");
        properties.setProperty("navigationRouteVisualStyle", "pulse");
        properties.setProperty("navigationPulseMaximumDistanceMetres", "320");
        properties.setProperty("navigationHighwaysEnabled", "false");
        properties.setProperty("navigationHighwaysCacheDirectory",
                "mods/test/maps");
        properties.setProperty("navigationHighwaysSyncMinutes", "7");
        properties.setProperty("serverMapEnabled", "false");
        properties.setProperty("serverMapCacheDirectory", "mods/test/server-maps");
        properties.setProperty("serverMapSyncMinutes", "25");
        properties.setProperty("serverMapShowDeeds", "false");
        properties.setProperty("serverMapShowHighways", "false");
        properties.setProperty("phase2MaxCompassMarkers", "32");
        properties.setProperty("phase2MaxWorldEffects", "8");
        properties.setProperty("phase2WorldEffectDistanceMetres", "6000");
        properties.setProperty("phase2MaxWorldLabels", "7");
        properties.setProperty("phase2WorldLabelDistanceMetres", "5000");
        WaypointClientConfiguration value = WaypointClientConfiguration.from(properties);
        assertEquals("mods\\test\\data.wpt", value.getDataFile().toString());
        assertEquals(8192, value.getMapBounds().getWidth());
        assertEquals(4096, value.getMapBounds().getHeight());
        assertEquals("mods\\test\\loot-hunts", value.getLootMapLogDirectory().toString());
        assertFalse(value.isLootMapEnabled());
        assertEquals("mods\\test\\archaeology-sessions.properties",
                value.getArchaeologySessionFile().toString());
        assertEquals("mods\\test\\archaeology-known.properties",
                value.getArchaeologyKnownLocationsFile().toString());
        assertFalse(value.isArchaeologyEnabled());
        assertEquals(23, value.getArchaeologyHistoryLimit());
        assertEquals("mods\\test\\navigation-routes",
                value.getNavigationRouteLogDirectory().toString());
        assertFalse(value.isNavigationRouteDiagnosticsEnabled());
        assertEquals(35.5f, value.getNavigationCartMaximumSlopeDirt(), 0.0001f);
        assertEquals(0.6f, value.getNavigationCartMaximumWaterDepthMetres(),
                0.0001f);
        assertEquals(4, value.getNavigationRouteLogTileInterval());
        assertEquals(NavigationRouteVisualStyle.PULSE,
                value.getNavigationRouteVisualStyle());
        assertEquals(320, value.getNavigationPulseMaximumDistanceMetres());
        assertFalse(value.isNavigationHighwaysEnabled());
        assertEquals("mods\\test\\maps",
                value.getNavigationHighwaysCacheDirectory().toString());
        assertEquals(7, value.getNavigationHighwaysSyncMinutes());
        assertFalse(value.isServerMapEnabled());
        assertEquals("mods\\test\\server-maps",
                value.getServerMapCacheDirectory().toString());
        assertEquals(25, value.getServerMapSyncMinutes());
        assertFalse(value.isServerMapShowDeeds());
        assertFalse(value.isServerMapShowHighways());
        assertEquals(32, value.getMaximumCompassMarkers());
        assertEquals(8, value.getMaximumWorldEffects());
        assertEquals(6000, value.getWorldEffectDistanceMetres());
        assertEquals(7, value.getMaximumWorldLabels());
        assertEquals(5000, value.getWorldLabelDistanceMetres());
    }

    @Test public void strictParsingRejectsInvalidBooleanInsteadOfSilentlyDisablingFeature() {
        Properties properties = new Properties();
        properties.setProperty("lootMapEnabled", "tru");
        try {
            WaypointClientConfiguration.from(properties);
            fail("Expected invalid boolean to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("lootMapEnabled"));
        }
    }

    @Test public void recoverableParsingKeepsValidSettingsAndDefaultsOnlyInvalidOne() {
        Properties properties = new Properties();
        properties.setProperty("waypointMapWidth", "8192");
        properties.setProperty("phase2MaxWorldEffects", "too-many");
        List<String> warnings = new ArrayList<String>();

        WaypointClientConfiguration value =
                WaypointClientConfiguration.from(properties, warnings::add);

        assertEquals(8192, value.getMapWidth());
        assertEquals(16, value.getMaximumWorldEffects());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("phase2MaxWorldEffects"));
    }
}
