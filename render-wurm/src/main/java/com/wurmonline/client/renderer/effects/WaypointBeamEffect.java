package com.wurmonline.client.renderer.effects;

import com.wurmonline.client.game.World;
import com.wurmonline.client.options.Options;
import com.wurmonline.client.renderer.Material;
import com.wurmonline.client.renderer.MaterialInstance;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.RenderState;
import com.wurmonline.client.renderer.backend.VertexBuffer;
import com.wurmonline.client.util.GLHelper;
import org.waypoints.next.render.BeamDistanceScaling;
import org.waypoints.next.render.BeamMarkerScale;
import org.waypoints.next.render.CircleBeamAnimation;
import org.waypoints.next.render.WaypointGroundHeight;
import org.waypoints.next.render.WaypointWorldBlend;
import org.waypoints.next.render.WaypointRenderProfiler;
import org.waypoints.next.render.WaypointLatePassBridge;
import org.waypoints.next.render.WaypointLatePassParticipant;

import java.nio.FloatBuffer;
import java.util.logging.Logger;

/** Thin Wurm renderer bridge; waypoint policy and lifecycle live outside this package. */
public final class WaypointBeamEffect extends Effect
        implements WaypointLatePassParticipant {
    public enum VisualMode { ADDITIVE, INVERT, CIRCLE }

    private static final int VERTICES_PER_CROSS = 10;
    private static final int CIRCLE_SEGMENTS = 32;
    private static final int CIRCLE_VERTEX_COUNT = CIRCLE_SEGMENTS * 6 - 2;
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.BeamEffect");

    private final float x;
    private final float y;
    private final float h;
    private final int targetLayer;
    private final boolean groundAnchored;
    private final float height;
    private final VertexBuffer vbo;
    private final MaterialInstance material;
    private final boolean throughWalls;
    private final float throughWallWidth;
    private final VisualMode visualMode;
    private final float markerSize;
    private final int statePrimerOffset;
    private final int extraGeometryOffset;
    private final long animationStartedNanos = System.nanoTime();
    private float width;
    private float red;
    private float green;
    private float blue;
    private float alpha;
    private volatile boolean alive = true;
    private boolean firstRenderLogged;
    private volatile long renderCount;
    private volatile long lastRenderNanos;

    public WaypointBeamEffect(World world, float x, float y, float h, float height,
                              float width, float red, float green, float blue, float alpha,
                              boolean throughWalls, float throughWallWidth) {
        this(world, x, y, h, height, width, red, green, blue, alpha,
                VisualMode.ADDITIVE, BeamMarkerScale.DEFAULT, throughWalls,
                throughWallWidth, 0, false);
    }

    public WaypointBeamEffect(World world, float x, float y, float h, float height,
                              float width, float red, float green, float blue, float alpha,
                              VisualMode visualMode, boolean throughWalls,
                              float throughWallWidth) {
        this(world, x, y, h, height, width, red, green, blue, alpha,
                visualMode, BeamMarkerScale.DEFAULT, throughWalls,
                throughWallWidth, 0, false);
    }

    public WaypointBeamEffect(World world, float x, float y, float h, float height,
                              float width, float red, float green, float blue, float alpha,
                              VisualMode visualMode, float markerSize,
                              boolean throughWalls, float throughWallWidth) {
        this(world, x, y, h, height, width, red, green, blue, alpha,
                visualMode, markerSize, throughWalls, throughWallWidth,
                0, false);
    }

    public WaypointBeamEffect(World world, float x, float y, float h, float height,
                              float width, float red, float green, float blue, float alpha,
                              VisualMode visualMode, float markerSize,
                              boolean throughWalls, float throughWallWidth,
                              int targetLayer, boolean groundAnchored) {
        super(world);
        this.x = x;
        this.y = y;
        this.h = h;
        this.targetLayer = targetLayer;
        this.groundAnchored = groundAnchored;
        this.height = positiveFinite(height, "height");
        this.width = positiveFinite(width, "width");
        this.throughWalls = throughWalls;
        this.throughWallWidth = positiveFinite(throughWallWidth, "through-wall width");
        if (visualMode == null) throw new IllegalArgumentException(
                "visual mode is required");
        this.visualMode = visualMode;
        this.markerSize = positiveFinite(markerSize, "marker size");
        setColor(red, green, blue, alpha);
        int crossVertexCount = VERTICES_PER_CROSS * (throughWalls ? 2 : 1);
        this.statePrimerOffset = crossVertexCount;
        int primerVertexCount = throughWalls ? VERTICES_PER_CROSS : 0;
        this.extraGeometryOffset = crossVertexCount + primerVertexCount;
        int extraVertexCount = visualMode == VisualMode.CIRCLE
                ? CIRCLE_VERTEX_COUNT : 0;
        this.vbo = VertexBuffer.create(VertexBuffer.Usage.EFFECT,
                crossVertexCount + primerVertexCount + extraVertexCount,
                true, false, false, true, false, 0, 0, true, false);
        this.material = GLHelper.useDeferredShading()
                ? Material.load("material.simple").instance() : null;
        WaypointLatePassBridge.register(this);
    }

    public void setWidth(float value) {
        width = positiveFinite(value, "width");
    }

    public void setColor(float red, float green, float blue, float alpha) {
        this.red = unit(red, "red");
        this.green = unit(green, "green");
        this.blue = unit(blue, "blue");
        this.alpha = unit(alpha, "alpha");
    }

    @Override
    public void render(Queue queue, float tickFraction) {
        // Waypointer geometry has one owner in WorldRender's stable late pass.
    }

    @Override public boolean isLatePassAlive() {
        return alive;
    }

    @Override public void renderInLateWorldPass(Queue queue) {
        if (alive) renderWorld(queue, world.getTickFraction());
    }

    private void renderWorld(Queue queue, float tickFraction) {
        if (!alive || queue == null) return;
        long profileStartedNanos = System.nanoTime();
        renderCount++;
        long frameNanos = profileStartedNanos;
        lastRenderNanos = frameNanos;
        float playerX = world.getPlayerPosX();
        float playerY = world.getPlayerPosY();
        float playerH = world.getPlayerPosH();
        float deltaX = x - playerX;
        float deltaY = y - playerY;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        float geometryDistance = BeamDistanceScaling.geometryDistance(distance);
        float projection = distance > 0.0f ? geometryDistance / distance : 1.0f;
        float geometryX = playerX + deltaX * projection;
        float geometryY = playerY + deltaY * projection;
        float targetHeight = groundAnchored
                ? WaypointGroundHeight.resolve(world, x, y, targetLayer, h) : h;
        float geometryH = playerH + (targetHeight - playerH) * projection;
        float renderX = geometryX - world.getRenderOriginX();
        float renderY = geometryY - world.getRenderOriginY();
        int screenWidth = world.getWorldRenderer().getScreenWidth();
        boolean lineOnly = BeamDistanceScaling.beamRequiresLineOnlyFallback(distance);
        float adaptiveDefaultHalfHeight = BeamDistanceScaling.halfHeight(
                height, geometryDistance);
        float renderedHalfHeight = BeamMarkerScale.height(
                adaptiveDefaultHalfHeight, markerSize);
        float adaptiveThroughWallWidth = BeamDistanceScaling.throughWallWidth(
                throughWallWidth, geometryDistance, screenWidth,
                Options.fovHorizontal.value());
        float lineAbove = lineOnly
                ? BeamDistanceScaling.farLineAboveTarget(renderedHalfHeight)
                : renderedHalfHeight;
        float lineBelow = lineOnly
                ? BeamDistanceScaling.farLineBelowTarget(renderedHalfHeight)
                : renderedHalfHeight;
        float top = geometryH + lineAbove;
        float configuredFieldScale = BeamMarkerScale.fieldScale(markerSize);
        float fieldScale = lineOnly ? 0.0f : configuredFieldScale;
        float renderWidth = width * fieldScale;
        float renderRed = red;
        float renderGreen = green;
        float renderBlue = blue;
        float seconds = elapsedSeconds(animationStartedNanos, frameNanos);
        float animationPhase = animationPhase(x, y);
        float renderAlpha = alpha * slowPulse(seconds, animationPhase);
        if (visualMode == VisualMode.INVERT) {
            renderRed = WaypointWorldBlend.blackLightChannel(red);
            renderGreen = WaypointWorldBlend.blackLightChannel(green);
            renderBlue = WaypointWorldBlend.blackLightChannel(blue);
        }
        float rotation = seconds * 0.12f + animationPhase;
        FloatBuffer vertices = vbo.lock();
        putCross(vertices, renderX, renderY, geometryH, top,
                renderWidth * 0.5f, rotation,
                renderRed, renderGreen, renderBlue, renderAlpha);
        if (throughWalls) {
            // Marker size never changes this navigation line's thickness.
            putCross(vertices, renderX, renderY,
                    geometryH - lineBelow, top,
                    adaptiveThroughWallWidth * 0.5f, 0.0f,
                    renderRed, renderGreen, renderBlue, alpha);
            putStatePrimer(vertices, renderX, renderY,
                    geometryH - lineBelow, top);
        }
        if (visualMode == VisualMode.CIRCLE) {
            putCircleWall(vertices, renderX, renderY, geometryH,
                    Math.min(renderedHalfHeight, 96.0f * fieldScale),
                    BeamMarkerScale.circleRadius(markerSize),
                    renderRed, renderGreen, renderBlue, renderAlpha,
                    seconds, animationPhase);
        }
        vbo.unlock();

        if (fieldScale > 0.0f) queuePrimitive(queue, 0, false);
        if (throughWalls) {
            queueStatePrimer(queue);
            queuePrimitive(queue, VERTICES_PER_CROSS, true);
        }
        if (fieldScale > 0.0f && visualMode == VisualMode.CIRCLE) {
            queueExtraGeometry(queue, CIRCLE_VERTEX_COUNT, false);
        }
        if (!firstRenderLogged) {
            firstRenderLogged = true;
            LOGGER.info("Beam probe rendered its first frame: deferredMaterial="
                    + (material != null) + ", wideVertices=" + VERTICES_PER_CROSS
                    + ", throughWallVertices=" + (throughWalls ? VERTICES_PER_CROSS : 0)
                    + ", configuredThroughWallWidth=" + throughWallWidth
                    + ", adaptiveThroughWallWidth=" + adaptiveThroughWallWidth
                    + ", playerDistance=" + distance
                    + ", geometryDistance=" + geometryDistance
                    + ", markerSize=" + markerSize
                    + ", lineOnlyFallback=" + lineOnly
                    + ", statePrimer=" + throughWalls
                    + ", fieldScale=" + fieldScale
                    + ", renderedHalfHeight=" + renderedHalfHeight
                    + ", throughWallBottom=" + (geometryH - lineBelow)
                    + ", throughWallTop=" + top
                    + ", worldX=" + x + ", worldY=" + y
                    + ", baseHeight=" + targetHeight
                    + ", groundAnchored=" + groundAnchored
                    + ", geometryHeight=" + geometryH);
        }
        WaypointRenderProfiler.recordBeam(System.nanoTime() - profileStartedNanos);
    }

    private void putCross(FloatBuffer vertices, float renderX, float renderY,
                          float bottom, float top, float halfWidth,
                          float rotation, float red, float green, float blue,
                          float alpha) {
        float axisX = (float) Math.cos(rotation);
        float axisY = (float) Math.sin(rotation);
        float sideX = -axisY;
        float sideY = axisX;
        putVertex(vertices, renderX - axisX * halfWidth, bottom,
                renderY - axisY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX - axisX * halfWidth, top,
                renderY - axisY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX + axisX * halfWidth, bottom,
                renderY + axisY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX + axisX * halfWidth, top,
                renderY + axisY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX + axisX * halfWidth, top,
                renderY + axisY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX - sideX * halfWidth, bottom,
                renderY - sideY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX - sideX * halfWidth, bottom,
                renderY - sideY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX - sideX * halfWidth, top,
                renderY - sideY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX + sideX * halfWidth, bottom,
                renderY + sideY * halfWidth, red, green, blue, alpha);
        putVertex(vertices, renderX + sideX * halfWidth, top,
                renderY + sideY * halfWidth, red, green, blue, alpha);
    }

    private void queuePrimitive(Queue queue, int offset, boolean depthIndependent) {
        Primitive primitive = queue.reservePrimitive();
        primitive.copyStateFrom(RenderState.RENDERSTATE_ALPHABLEND);
        primitive.blendmode = visualMode == VisualMode.INVERT
                ? WaypointWorldBlend.blackLight() : WaypointWorldBlend.luminous();
        primitive.clearTextures();
        if (material != null) {
            primitive.materialInstance = material;
            primitive.program = material.getProgram();
            primitive.bindings = material.getProgramBindings();
        }
        primitive.type = Primitive.Type.TRIANGLESTRIP;
        primitive.twosided = true;
        primitive.nolight = true;
        primitive.vertex = vbo;
        primitive.index = null;
        primitive.offset = offset;
        primitive.num = VERTICES_PER_CROSS - 2;
        if (depthIndependent) {
            primitive.depthtest = Primitive.TestFunc.ALWAYS;
            primitive.depthwrite = false;
            primitive.nofog = true;
        }
        queue.queue(primitive, null);
    }

    private void putStatePrimer(FloatBuffer vertices, float renderX,
                                float renderY, float bottom, float top) {
        // Degenerate, fully transparent geometry: it reaches the renderer's
        // state machine but cannot contribute a pixel.
        putCross(vertices, renderX, renderY, bottom, top,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    private void queueStatePrimer(Queue queue) {
        Primitive primitive = queue.reservePrimitive();
        primitive.copyStateFrom(RenderState.RENDERSTATE_ALPHABLEND);
        // Wurm caches these states globally across queues. Using a state that
        // differs from the real through-wall line guarantees that the line's
        // ALPHAADD/ALPHABLEND + ALWAYS state is reapplied even when that cache
        // was left out of sync by a server or render-pipeline transition.
        primitive.blendmode = Primitive.BlendMode.ADD;
        primitive.depthtest = Primitive.TestFunc.LESSEQUAL;
        primitive.depthwrite = false;
        primitive.nofog = false;
        primitive.clearTextures();
        if (material != null) {
            primitive.materialInstance = material;
            primitive.program = material.getProgram();
            primitive.bindings = material.getProgramBindings();
        }
        primitive.type = Primitive.Type.TRIANGLESTRIP;
        primitive.twosided = true;
        primitive.nolight = true;
        primitive.vertex = vbo;
        primitive.index = null;
        primitive.offset = statePrimerOffset;
        primitive.num = VERTICES_PER_CROSS - 2;
        queue.queue(primitive, null);
    }

    private void putCircleWall(FloatBuffer vertices, float renderX, float renderY,
                               float bottom, float wallHeight, float radius,
                               float red, float green, float blue, float alpha,
                               float seconds, float animationPhase) {
        float top = bottom + Math.max(0.0f, wallHeight);
        float lastX = 0.0f;
        float lastY = 0.0f;
        float rotation = CircleBeamAnimation.rotationRadians(
                seconds, animationPhase);
        for (int segment = 0; segment < CIRCLE_SEGMENTS; segment++) {
            double first = Math.PI * 2.0d * segment / CIRCLE_SEGMENTS + rotation;
            double second = Math.PI * 2.0d * (segment + 1) / CIRCLE_SEGMENTS
                    + rotation;
            double middle = (first + second) * 0.5d;
            float glow = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(
                    middle * 3.0d - seconds * 0.45f + animationPhase));
            float wallAlpha = alpha * glow * 0.58f;
            float firstX = renderX + (float) Math.cos(first) * radius;
            float firstY = renderY + (float) Math.sin(first) * radius;
            float secondX = renderX + (float) Math.cos(second) * radius;
            float secondY = renderY + (float) Math.sin(second) * radius;
            if (segment > 0) {
                putVertex(vertices, lastX, top, lastY,
                        red, green, blue, wallAlpha);
                putVertex(vertices, firstX, bottom, firstY,
                        red, green, blue, wallAlpha);
            }
            putVertex(vertices, firstX, bottom, firstY,
                    red, green, blue, wallAlpha);
            putVertex(vertices, firstX, top, firstY,
                    red, green, blue, wallAlpha);
            putVertex(vertices, secondX, bottom, secondY,
                    red, green, blue, wallAlpha);
            putVertex(vertices, secondX, top, secondY,
                    red, green, blue, wallAlpha);
            lastX = secondX;
            lastY = secondY;
        }
    }

    private void queueExtraGeometry(Queue queue, int vertexCount,
                                    boolean depthIndependent) {
        Primitive primitive = queue.reservePrimitive();
        primitive.copyStateFrom(RenderState.RENDERSTATE_ALPHABLEND);
        primitive.blendmode = WaypointWorldBlend.luminous();
        primitive.clearTextures();
        if (material != null) {
            primitive.materialInstance = material;
            primitive.program = material.getProgram();
            primitive.bindings = material.getProgramBindings();
        }
        primitive.type = Primitive.Type.TRIANGLESTRIP;
        primitive.twosided = true;
        primitive.nolight = true;
        primitive.nofog = depthIndependent;
        if (depthIndependent) {
            primitive.depthtest = Primitive.TestFunc.ALWAYS;
            primitive.depthwrite = false;
        }
        primitive.vertex = vbo;
        primitive.index = null;
        primitive.offset = extraGeometryOffset;
        primitive.num = vertexCount - 2;
        queue.queue(primitive, null);
    }

    private void putVertex(FloatBuffer target, float vertexX, float vertexH, float vertexY,
                           float red, float green, float blue, float alpha) {
        target.put(vertexX).put(vertexH).put(vertexY);
        target.put(red).put(green).put(blue).put(alpha);
    }

    @Override
    public boolean gameTick() {
        return alive;
    }

    /** Diagnostic-only probe used by the optional manual beam verifier. */
    public boolean hasLiveRenderResources() {
        return alive && vbo.canRender();
    }

    public boolean isAlive() {
        return alive;
    }

    public long getRenderCount() {
        return renderCount;
    }

    public long getLastRenderNanos() {
        return lastRenderNanos;
    }

    @Override
    public void delete() {
        if (!alive) return;
        alive = false;
        WaypointLatePassBridge.unregister(this);
        vbo.delete();
        if (material != null) material.destroy();
    }

    private static float slowPulse(float seconds, float phase) {
        return 0.55f + 0.45f * (0.5f
                + 0.5f * (float) Math.sin(seconds * 0.38f + phase));
    }

    private static float animationPhase(float worldX, float worldY) {
        int hash = Float.floatToIntBits(worldX) * 31 + Float.floatToIntBits(worldY);
        return (hash & 1023) * ((float) Math.PI * 2.0f / 1024.0f);
    }

    private static float elapsedSeconds(long startNanos, long frameNanos) {
        return (float) ((frameNanos - startNanos) * 0.000000001d);
    }

    private static float positiveFinite(float value, String label) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
        return value;
    }

    private static float unit(float value, String label) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(label + " must be in 0..1");
        }
        return value;
    }
}
