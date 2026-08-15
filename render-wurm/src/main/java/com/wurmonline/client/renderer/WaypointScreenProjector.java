package com.wurmonline.client.renderer;

import com.wurmonline.client.game.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.Project;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Thin bridge exposing exact world-to-screen projection for the pinned client. */
public final class WaypointScreenProjector {
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(4);
    private static final FloatBuffer WINDOW_POSITION = BufferUtils.createFloatBuffer(3);

    private WaypointScreenProjector() {
    }

    /**
     * Returns the horizontal viewport coordinate in 0..1. Values outside that
     * range are valid for a point beyond a side edge. A point behind the
     * camera returns signed infinity for the nearest left/right edge; NaN is
     * reserved for an unavailable renderer or invalid matrix result.
     */
    public static synchronized float projectNormalizedX(World world, float worldX,
                                                        float worldY, float worldHeight) {
        if (world == null) return Float.NaN;
        WorldRender renderer = world.getWorldRenderer();
        if (renderer == null) return Float.NaN;
        int screenWidth = renderer.getScreenWidth();
        int screenHeight = renderer.getScreenHeight();
        if (screenWidth <= 0 || screenHeight <= 0) return Float.NaN;

        VIEWPORT.clear();
        VIEWPORT.put(0).put(0).put(screenWidth).put(screenHeight).flip();
        WINDOW_POSITION.clear();
        float renderX = worldX - world.getRenderOriginX();
        float renderY = worldY - world.getRenderOriginY();
        Matrix view = renderer.viewMatrixWorldRender;
        Matrix projection = renderer.projectionMatrixWorld;
        boolean projected = Project.gluProject(
                renderX, worldHeight, renderY,
                view.getBuffer(), projection.getBuffer(),
                VIEWPORT, WINDOW_POSITION);
        if (!projected) return Float.NaN;

        float windowX = WINDOW_POSITION.get(0);
        float windowZ = WINDOW_POSITION.get(2);
        if (Float.isNaN(windowX) || Float.isInfinite(windowX)
                || Float.isNaN(windowZ) || Float.isInfinite(windowZ)) {
            return Float.NaN;
        }
        float normalizedX = windowX / screenWidth;
        if (clipW(view, projection, renderX, worldHeight, renderY) <= 0.0f) {
            // Perspective division mirrors X behind the camera, so invert the
            // projected half to keep the label on the physically nearest edge.
            return normalizedX <= 0.5f
                    ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
        return normalizedX;
    }

    private static float clipW(Matrix view, Matrix projection,
                               float x, float y, float z) {
        float viewX = view._m00() * x + view._m10() * y
                + view._m20() * z + view._m30();
        float viewY = view._m01() * x + view._m11() * y
                + view._m21() * z + view._m31();
        float viewZ = view._m02() * x + view._m12() * y
                + view._m22() * z + view._m32();
        float viewW = view._m03() * x + view._m13() * y
                + view._m23() * z + view._m33();
        return projection._m03() * viewX + projection._m13() * viewY
                + projection._m23() * viewZ + projection._m33() * viewW;
    }
}
