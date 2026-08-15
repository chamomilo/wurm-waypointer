package org.waypoints.next.render;

import org.junit.Test;
import org.waypoints.next.model.MarkerStyle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WaypointSymbolGeometryTest {
    @Test public void symbolStylesUseExactSharedStripBudgets() {
        assertEquals(202, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.TARGET_CROSSHAIR));
        assertEquals(190, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.HOLLOW_CIRCLE));
        assertEquals(10, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.PLUS));
        assertEquals(10, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.EXCLAMATION));
        assertEquals(100, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.HOUSE));
        assertEquals(58, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.DIAMOND));
        assertEquals(52, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.PICKAXE));
        assertEquals(58, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.SHOVEL));
        assertEquals(112, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.PICKAXE_AND_SHOVEL));
        assertEquals(226, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.MONEY_SIGN));
        assertEquals(46, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.CROSSED_SWORDS));
        assertEquals(112, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.LIGHTHOUSE));
        assertEquals(148, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.LOOT_MAP_SCROLL));
        assertEquals(82, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.SHOVEL, true));
        assertEquals(148, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.LOOT_MAP_SCROLL, true));
        assertEquals(172, WaypointSymbolGeometry.stripVertexCount(
                MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL));
    }

    @Test public void beamAndCompassStylesAreNotWorldSymbols() {
        assertTrue(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.TARGET_CROSSHAIR));
        assertTrue(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.SHOVEL));
        assertTrue(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.PICKAXE_AND_SHOVEL));
        assertTrue(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.EXCLAMATION));
        assertTrue(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.LOOT_MAP_SCROLL));
        assertTrue(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL));
        assertFalse(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.COLORED_BEAM));
        assertFalse(WaypointSymbolGeometry.isSymbol(
                MarkerStyle.WorldStyle.COMPASS_ONLY));
    }

    @Test public void persistedSizeAndWidthMapToBoundedWorldGeometry() {
        float radius = WaypointSymbolGeometry.radius(9.0f);
        assertEquals(0.45f, radius, 0.0001f);
        assertEquals(0.04f, WaypointSymbolGeometry.stroke(radius, 2.0f), 0.0001f);
        assertEquals(0.2025f, WaypointSymbolGeometry.stroke(radius, 100.0f), 0.0001f);
        assertEquals(0.05f, WaypointSymbolGeometry.radius(1.0f), 0.0001f);
        assertEquals(1.5f, WaypointSymbolGeometry.radius(30.0f), 0.0001f);
        assertEquals(101.7f, WaypointSymbolGeometry.centerHeight(100.0f, 0.0f),
                0.0001f);
        assertEquals(102.2f, WaypointSymbolGeometry.centerHeight(100.0f, 0.5f),
                0.0001f);
    }

    @Test public void distantSymbolUsesLargerSliderAwarePixelRadiusAndStroke() {
        float radius = WaypointSymbolGeometry.adaptiveRadius(
                0.45f, 1000.0f, 256.0f, 1920, 80.0f);
        float stroke = WaypointSymbolGeometry.adaptiveStroke(
                0.04f, radius, 1000.0f, 256.0f, 1920, 80.0f);
        double unitsPerPixel = 2.0d * 256.0d * Math.tan(Math.toRadians(40.0d))
                / 1920.0d;
        assertEquals(12.0d, radius / unitsPerPixel, 0.0001d);
        assertEquals(1.0d, stroke / unitsPerPixel, 0.0001d);
    }

    @Test public void distantPixelFloorFollowsTheCompleteSizeSlider() {
        assertEquals(6.0f, WaypointSymbolGeometry.minimumScreenRadiusPixels(
                WaypointSymbolGeometry.radius(1.0f)), 0.0001f);
        assertEquals(12.0f, WaypointSymbolGeometry.minimumScreenRadiusPixels(
                WaypointSymbolGeometry.radius(9.0f)), 0.0001f);
        assertEquals(24.0f, WaypointSymbolGeometry.minimumScreenRadiusPixels(
                WaypointSymbolGeometry.radius(30.0f)), 0.0001f);

        float small = WaypointSymbolGeometry.adaptiveRadius(
                WaypointSymbolGeometry.radius(1.0f), 10000.0f, 256.0f,
                1920, 80.0f);
        float standard = WaypointSymbolGeometry.adaptiveRadius(
                WaypointSymbolGeometry.radius(9.0f), 10000.0f, 256.0f,
                1920, 80.0f);
        float large = WaypointSymbolGeometry.adaptiveRadius(
                WaypointSymbolGeometry.radius(30.0f), 10000.0f, 256.0f,
                1920, 80.0f);
        assertEquals(2.0f, standard / small, 0.0001f);
        assertEquals(2.0f, large / standard, 0.0001f);
    }

    @Test public void nearbySymbolKeepsItsConfiguredSmallSize() {
        assertEquals(0.45f, WaypointSymbolGeometry.adaptiveRadius(
                0.45f, 10.0f, 10.0f, 1920, 80.0f), 0.0001f);
        assertEquals(0.04f, WaypointSymbolGeometry.adaptiveStroke(
                0.04f, 0.45f, 10.0f, 10.0f, 1920, 80.0f), 0.0001f);
    }

    @Test public void targetGuideAlwaysSpansThePlayersVisibleHeight() {
        assertEquals(-27.0f, WaypointSymbolGeometry.guideBottom(
                900.0f, 37.0f, 400.0f), 0.0f);
        assertEquals(1300.0f, WaypointSymbolGeometry.guideTop(
                900.0f, 37.0f, 400.0f), 0.0f);
        assertEquals(-500.0f, WaypointSymbolGeometry.guideBottom(
                -100.0f, 37.0f, 400.0f), 0.0f);
        assertEquals(300.0f, WaypointSymbolGeometry.guideTop(
                -100.0f, 37.0f, 400.0f), 0.0f);
    }

    @Test public void lootMapOutlineUsesContainingTileWorldBoundaries() {
        assertEquals(6260.0f, WaypointSymbolGeometry.tileMinimumWorld(
                6262.0f), 0.0f);
        assertEquals(0.0f, WaypointSymbolGeometry.tileMinimumWorld(
                0.5f), 0.0f);
        assertEquals(4.0f, WaypointSymbolGeometry.tileMinimumWorld(
                7.999f), 0.0f);
    }

    @Test public void lootMapGroundOutlineAppearsOnlyWithinFiveTiles() {
        assertTrue(WaypointSymbolGeometry.showLootMapGroundOutline(0.0f));
        assertTrue(WaypointSymbolGeometry.showLootMapGroundOutline(20.0f));
        assertFalse(WaypointSymbolGeometry.showLootMapGroundOutline(20.001f));
        assertFalse(WaypointSymbolGeometry.showLootMapGroundOutline(-0.1f));
        assertFalse(WaypointSymbolGeometry.showLootMapGroundOutline(Float.NaN));
        assertFalse(WaypointSymbolGeometry.showLootMapGroundOutline(
                Float.POSITIVE_INFINITY));
    }

    @Test public void pulledCaveGeometryPreservesPerspectiveShrink() {
        float radius = WaypointSymbolGeometry.adaptiveRadius(
                1.5f, 40.0f, 24.0f, 1920, 80.0f);
        double unitsPerPixelAtGeometry = 2.0d * 24.0d
                * Math.tan(Math.toRadians(40.0d)) / 1920.0d;
        double unitsPerPixelAtTarget = 2.0d * 40.0d
                * Math.tan(Math.toRadians(40.0d)) / 1920.0d;
        assertEquals(1.5d / unitsPerPixelAtTarget,
                radius / unitsPerPixelAtGeometry, 0.0001d);
        assertTrue(radius < 1.5f);
    }
}
