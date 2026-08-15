package org.waypoints.next.render;

import org.waypoints.next.model.MarkerStyle;

/** Pure sizing/count policy shared by the Wurm symbol renderer and tests. */
public final class WaypointSymbolGeometry {
    public static final int RING_SEGMENTS = 32;
    private static final float DEFAULT_SCREEN_RADIUS_PIXELS = 12.0f;
    private static final float MINIMUM_MARKER_SIZE = 1.0f;
    private static final float DEFAULT_MARKER_SIZE = 9.0f;
    private static final float MAXIMUM_MARKER_SIZE = 30.0f;
    private static final float MINIMUM_SCREEN_STROKE_PIXELS = 1.0f;
    private static final float PLAYER_HEAD_HEIGHT_METRES = 1.7f;
    private static final float GUIDE_PLAYER_MARGIN_METRES = 64.0f;
    private static final float LOOT_MAP_OUTLINE_RADIUS_METRES = 20.0f;

    private WaypointSymbolGeometry() { }

    public static boolean isSymbol(MarkerStyle.WorldStyle style) {
        return style == MarkerStyle.WorldStyle.TARGET_CROSSHAIR
                || style == MarkerStyle.WorldStyle.HOLLOW_CIRCLE
                || style == MarkerStyle.WorldStyle.PLUS
                || style == MarkerStyle.WorldStyle.EXCLAMATION
                || style == MarkerStyle.WorldStyle.HOUSE
                || style == MarkerStyle.WorldStyle.DIAMOND
                || style == MarkerStyle.WorldStyle.PICKAXE
                || style == MarkerStyle.WorldStyle.SHOVEL
                || style == MarkerStyle.WorldStyle.PICKAXE_AND_SHOVEL
                || style == MarkerStyle.WorldStyle.MONEY_SIGN
                || style == MarkerStyle.WorldStyle.CROSSED_SWORDS
                || style == MarkerStyle.WorldStyle.LIGHTHOUSE
                || style == MarkerStyle.WorldStyle.LOOT_MAP_SCROLL
                || style == MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL;
    }

    public static int quadCount(MarkerStyle.WorldStyle style) {
        if (style == MarkerStyle.WorldStyle.TARGET_CROSSHAIR) {
            return RING_SEGMENTS + 2;
        }
        if (style == MarkerStyle.WorldStyle.HOLLOW_CIRCLE) return RING_SEGMENTS;
        if (style == MarkerStyle.WorldStyle.PLUS) return 2;
        if (style == MarkerStyle.WorldStyle.EXCLAMATION) return 2;
        if (style == MarkerStyle.WorldStyle.HOUSE) return 17;
        if (style == MarkerStyle.WorldStyle.DIAMOND) return 10;
        if (style == MarkerStyle.WorldStyle.PICKAXE) return 9;
        if (style == MarkerStyle.WorldStyle.SHOVEL) return 10;
        if (style == MarkerStyle.WorldStyle.PICKAXE_AND_SHOVEL) return 19;
        if (style == MarkerStyle.WorldStyle.MONEY_SIGN) return RING_SEGMENTS + 6;
        if (style == MarkerStyle.WorldStyle.CROSSED_SWORDS) return 8;
        if (style == MarkerStyle.WorldStyle.LIGHTHOUSE) return 19;
        // Scroll (21) and four terrain-conforming full-tile outline edges.
        if (style == MarkerStyle.WorldStyle.LOOT_MAP_SCROLL) return 25;
        // Report scroll (24), vertical guide (1), and four tile-outline edges.
        if (style == MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL) return 29;
        throw new IllegalArgumentException("world style is not a symbol: " + style);
    }

    public static int stripVertexCount(MarkerStyle.WorldStyle style) {
        int quads = quadCount(style);
        return quads * 6 - 2;
    }

    /** Adds four outline edges to a non-scroll Loot Map phase pictogram. */
    public static int stripVertexCount(MarkerStyle.WorldStyle style,
                                       boolean lootMapGroundOutline) {
        int quads = quadCount(style);
        if (lootMapGroundOutline
                && style != MarkerStyle.WorldStyle.LOOT_MAP_SCROLL) {
            quads += 4;
        }
        return quads * 6 - 2;
    }

    public static float radius(float markerSize) {
        return Math.max(0.05f, markerSize * 0.05f);
    }

    public static float stroke(float radius, float beamWidth) {
        return Math.max(0.04f, Math.min(radius * 0.45f, beamWidth * 0.02f));
    }

    /** Centers every pictogram at head height, independent of symbol radius. */
    public static float centerHeight(float projectedGroundHeight,
                                     float verticalDrift) {
        return projectedGroundHeight + PLAYER_HEAD_HEIGHT_METRES + verticalDrift;
    }

    /** World coordinate of the near/left edge of the containing Wurm tile. */
    public static float tileMinimumWorld(float worldCoordinate) {
        return (float) Math.floor(worldCoordinate / 4.0f) * 4.0f;
    }

    /** Shows the search-tile outline only within five four-metre Wurm tiles. */
    public static boolean showLootMapGroundOutline(float distanceMetres) {
        return !Float.isNaN(distanceMetres) && distanceMetres >= 0.0f
                && distanceMetres <= LOOT_MAP_OUTLINE_RADIUS_METRES;
    }

    /** Keeps the target-ground guide visible even across a tall mountain. */
    public static float guideBottom(float targetHeight, float playerHeight,
                                    float lineBelowTarget) {
        return Math.min(targetHeight - Math.max(0.0f, lineBelowTarget),
                playerHeight - GUIDE_PLAYER_MARGIN_METRES);
    }

    /** Keeps the target-ground guide visible even from above a deep valley. */
    public static float guideTop(float targetHeight, float playerHeight,
                                 float lineAboveTarget) {
        return Math.max(targetHeight + Math.max(0.0f, lineAboveTarget),
                playerHeight + GUIDE_PLAYER_MARGIN_METRES);
    }

    public static float adaptiveRadius(float configuredRadius,
                                       float actualDistance,
                                       float geometryDistance,
                                       int screenWidth, float horizontalFovDegrees) {
        float unitsPerPixel = worldUnitsPerPixel(
                geometryDistance, screenWidth, horizontalFovDegrees);
        float projectedConfigured = configuredRadius * projectionScale(
                actualDistance, geometryDistance);
        return unitsPerPixel <= 0.0f ? configuredRadius
                : Math.max(projectedConfigured,
                unitsPerPixel * minimumScreenRadiusPixels(configuredRadius));
    }

    public static float adaptiveStroke(float configuredStroke, float radius,
                                       float actualDistance,
                                       float geometryDistance, int screenWidth,
                                       float horizontalFovDegrees) {
        float unitsPerPixel = worldUnitsPerPixel(
                geometryDistance, screenWidth, horizontalFovDegrees);
        float projectedConfigured = configuredStroke * projectionScale(
                actualDistance, geometryDistance);
        float minimum = unitsPerPixel <= 0.0f ? projectedConfigured
                : unitsPerPixel * MINIMUM_SCREEN_STROKE_PIXELS;
        return Math.min(radius * 0.45f, Math.max(projectedConfigured, minimum));
    }

    private static float projectionScale(float actualDistance,
                                         float geometryDistance) {
        if (Float.isNaN(actualDistance) || Float.isInfinite(actualDistance)
                || actualDistance <= 0.0f || Float.isNaN(geometryDistance)
                || Float.isInfinite(geometryDistance)
                || geometryDistance <= 0.0f) return 1.0f;
        return Math.min(1.0f, geometryDistance / actualDistance);
    }

    /**
     * The far-distance floor is 12 px at the standard size instead of the old
     * fixed 4 px. It scales from 6 px at slider minimum, through 12 px at the
     * default, to 24 px at maximum, so a distant pictogram still follows the
     * same size control after perspective projection reaches its floor.
     */
    static float minimumScreenRadiusPixels(float configuredRadius) {
        float markerSize = Math.max(MINIMUM_MARKER_SIZE,
                configuredRadius / 0.05f);
        if (markerSize <= DEFAULT_MARKER_SIZE) {
            float progress = (markerSize - MINIMUM_MARKER_SIZE)
                    / (DEFAULT_MARKER_SIZE - MINIMUM_MARKER_SIZE);
            return DEFAULT_SCREEN_RADIUS_PIXELS * (0.5f + progress * 0.5f);
        }
        float progress = (markerSize - DEFAULT_MARKER_SIZE)
                / (MAXIMUM_MARKER_SIZE - DEFAULT_MARKER_SIZE);
        return DEFAULT_SCREEN_RADIUS_PIXELS * (1.0f + progress);
    }

    private static float worldUnitsPerPixel(float distance, int screenWidth,
                                            float horizontalFovDegrees) {
        if (Float.isNaN(distance) || Float.isInfinite(distance)
                || distance <= 0.0f || screenWidth <= 0) return 0.0f;
        float safeFov = Math.max(10.0f, Math.min(170.0f, horizontalFovDegrees));
        return (float) (2.0d * distance
                * Math.tan(Math.toRadians(safeFov * 0.5f)) / screenWidth);
    }
}
