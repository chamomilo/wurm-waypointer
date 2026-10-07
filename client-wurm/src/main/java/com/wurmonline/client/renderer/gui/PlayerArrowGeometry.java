package com.wurmonline.client.renderer.gui;

/** North-up player marker with fixed screen dimensions on every map layer. */
final class PlayerArrowGeometry {
    // Preserve the original arrow at the full map's maximum zoom (16 px/tile).
    static final float REFERENCE_TILE_SIZE_PIXELS = 16.0f;
    static final float OUTLINE_WIDTH_PIXELS = REFERENCE_TILE_SIZE_PIXELS * 0.16f;
    static final float STROKE_WIDTH_PIXELS = REFERENCE_TILE_SIZE_PIXELS * 0.085f;

    private PlayerArrowGeometry() { }

    static float[] points(float x, float y, float headingDegrees) {
        double radians = Math.toRadians(headingDegrees);
        float sin = (float) Math.sin(radians), cos = (float) Math.cos(radians);
        float[] local = {0, -0.40f, -0.30f, -0.03f, 0.30f, -0.03f, 0, 0.40f};
        float[] points = new float[local.length];
        for (int i = 0; i < local.length; i += 2) {
            points[i] = x + (local[i] * cos - local[i + 1] * sin) * REFERENCE_TILE_SIZE_PIXELS;
            points[i + 1] = y + (local[i] * sin + local[i + 1] * cos) * REFERENCE_TILE_SIZE_PIXELS;
        }
        return points;
    }
}
