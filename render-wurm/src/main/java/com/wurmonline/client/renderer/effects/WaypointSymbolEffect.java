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
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.render.BeamDistanceScaling;
import org.waypoints.next.render.WaypointSymbolGeometry;
import org.waypoints.next.render.WaypointGroundHeight;
import org.waypoints.next.render.WaypointWorldBlend;
import org.waypoints.next.render.WaypointRenderProfiler;
import org.waypoints.next.render.WaypointLatePassBridge;
import org.waypoints.next.render.WaypointLatePassParticipant;

import java.nio.FloatBuffer;
import java.util.logging.Logger;

/** Camera-facing world symbol with one VBO and no per-frame object allocation. */
public final class WaypointSymbolEffect extends Effect
        implements WaypointLatePassParticipant {
    private static final Logger LOGGER = Logger.getLogger(
            "WurmWaypointer.SymbolEffect");
    private final float x;
    private final float y;
    private final float h;
    private final int targetLayer;
    private final boolean groundAnchored;
    private final boolean lootMapGroundOutline;
    private final MarkerStyle.WorldStyle shape;
    private final float radius;
    private final float stroke;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;
    private final int vertexCount;
    private final VertexBuffer vbo;
    private final MaterialInstance material;
    private final long animationStartedNanos = System.nanoTime();
    private volatile boolean alive = true;
    private boolean firstRenderDiagnosticWritten;

    private FloatBuffer writing;
    private float centerX;
    private float centerY;
    private float centerH;
    private float rightX;
    private float rightY;
    private float frameRadius;
    private float frameStroke;
    private float frameAlpha;
    private float frameGroundHover;
    private boolean hasQuad;
    private float lastX;
    private float lastY;
    private float lastH;

    public WaypointSymbolEffect(World world, float x, float y, float h,
                                int targetLayer,
                                MarkerStyle.WorldStyle shape, float markerSize,
                                float beamWidth, float red, float green,
                                float blue, float alpha) {
        this(world, x, y, h, targetLayer, shape, markerSize, beamWidth,
                red, green, blue, alpha, false, false);
    }

    public WaypointSymbolEffect(World world, float x, float y, float h,
                                int targetLayer,
                                MarkerStyle.WorldStyle shape, float markerSize,
                                float beamWidth, float red, float green,
                                float blue, float alpha,
                                boolean groundAnchored) {
        this(world, x, y, h, targetLayer, shape, markerSize, beamWidth,
                red, green, blue, alpha, groundAnchored, false);
    }

    public WaypointSymbolEffect(World world, float x, float y, float h,
                                int targetLayer,
                                MarkerStyle.WorldStyle shape, float markerSize,
                                float beamWidth, float red, float green,
                                float blue, float alpha,
                                boolean groundAnchored,
                                boolean lootMapGroundOutline) {
        super(world);
        if (!WaypointSymbolGeometry.isSymbol(shape)) {
            throw new IllegalArgumentException("world style is not a symbol: " + shape);
        }
        this.x = x;
        this.y = y;
        this.h = h;
        this.targetLayer = targetLayer;
        this.groundAnchored = groundAnchored;
        this.lootMapGroundOutline = lootMapGroundOutline;
        this.shape = shape;
        this.radius = WaypointSymbolGeometry.radius(markerSize);
        this.stroke = WaypointSymbolGeometry.stroke(radius, beamWidth);
        this.red = unit(red, "red");
        this.green = unit(green, "green");
        this.blue = unit(blue, "blue");
        this.alpha = unit(alpha, "alpha");
        this.vertexCount = WaypointSymbolGeometry.stripVertexCount(
                shape, lootMapGroundOutline);
        this.vbo = VertexBuffer.create(VertexBuffer.Usage.EFFECT, vertexCount,
                true, false, false, true, false, 0, 0, true, false);
        this.material = GLHelper.useDeferredShading()
                ? Material.load("material.simple").instance() : null;
        WaypointLatePassBridge.register(this);
    }

    @Override public void render(Queue queue, float tickFraction) {
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
        float playerX = world.getPlayerPosX();
        float playerY = world.getPlayerPosY();
        float playerH = world.getPlayerPosH();
        float deltaX = x - playerX;
        float deltaY = y - playerY;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        float geometryDistance = BeamDistanceScaling.symbolGeometryDistance(
                distance, targetLayer);
        float projection = distance > 0.0f ? geometryDistance / distance : 1.0f;
        centerX = playerX + deltaX * projection - world.getRenderOriginX();
        centerY = playerY + deltaY * projection - world.getRenderOriginY();
        float targetHeight = groundAnchored
                ? WaypointGroundHeight.resolve(world, x, y, targetLayer, h) : h;
        float geometryH = playerH + (targetHeight - playerH) * projection;
        int screenWidth = world.getWorldRenderer().getScreenWidth();
        float horizontalFov = Options.fovHorizontal.value();
        frameRadius = WaypointSymbolGeometry.adaptiveRadius(
                radius, distance, geometryDistance, screenWidth, horizontalFov);
        frameStroke = WaypointSymbolGeometry.adaptiveStroke(
                stroke, frameRadius, distance, geometryDistance,
                screenWidth, horizontalFov);
        float seconds = (float) ((profileStartedNanos - animationStartedNanos)
                * 0.000000001d);
        float animationPhase = animationPhase(x, y);
        float verticalDrift = (float) Math.sin(seconds * 0.55f + animationPhase)
                * frameRadius * 0.42f;
        centerH = WaypointSymbolGeometry.centerHeight(geometryH, verticalDrift);
        frameGroundHover = (0.5f + 0.5f
                * (float) Math.sin(seconds * 0.55f + animationPhase)) * 0.15f;
        frameAlpha = alpha * (0.55f + 0.45f * (0.5f
                + 0.5f * (float) Math.sin(seconds * 0.38f + animationPhase)));
        if (distance > 0.001f) {
            rightX = -deltaY / distance;
            rightY = deltaX / distance;
        } else {
            rightX = 1.0f;
            rightY = 0.0f;
        }

        writing = vbo.lock();
        hasQuad = false;
        if (shape == MarkerStyle.WorldStyle.TARGET_CROSSHAIR
                || shape == MarkerStyle.WorldStyle.HOLLOW_CIRCLE) writeRing();
        if (shape == MarkerStyle.WorldStyle.TARGET_CROSSHAIR
                || shape == MarkerStyle.WorldStyle.PLUS) writePlus();
        if (shape == MarkerStyle.WorldStyle.EXCLAMATION) writeExclamation();
        if (shape == MarkerStyle.WorldStyle.HOUSE) writeHouse();
        if (shape == MarkerStyle.WorldStyle.DIAMOND) writeDiamond();
        if (shape == MarkerStyle.WorldStyle.PICKAXE) writePickaxe();
        if (shape == MarkerStyle.WorldStyle.SHOVEL) writeShovel();
        if (shape == MarkerStyle.WorldStyle.PICKAXE_AND_SHOVEL) {
            writePickaxeAndShovel();
        }
        if (shape == MarkerStyle.WorldStyle.MONEY_SIGN) writeMoneySign();
        if (shape == MarkerStyle.WorldStyle.CROSSED_SWORDS) writeCrossedSwords();
        if (shape == MarkerStyle.WorldStyle.LIGHTHOUSE) writeLighthouse();
        if (shape == MarkerStyle.WorldStyle.LOOT_MAP_SCROLL) {
            writeLootMapScroll(geometryH, playerH, projection, distance,
                    geometryDistance, screenWidth, horizontalFov);
        }
        if (lootMapGroundOutline
                && shape != MarkerStyle.WorldStyle.LOOT_MAP_SCROLL) {
            writeLootMapGroundOutlineOrHidden(playerH, projection, distance,
                    geometryDistance, screenWidth, horizontalFov);
        }
        if (shape == MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL) {
            writeArchaeologyReportScroll(geometryH, playerH, projection, distance,
                    geometryDistance, screenWidth, horizontalFov);
        }
        vbo.unlock();
        writing = null;

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
        primitive.nofog = true;
        primitive.depthtest = Primitive.TestFunc.ALWAYS;
        primitive.depthwrite = false;
        primitive.vertex = vbo;
        primitive.index = null;
        primitive.offset = 0;
        primitive.num = vertexCount - 2;
        queue.queue(primitive, null);
        if (!firstRenderDiagnosticWritten) {
            firstRenderDiagnosticWritten = true;
            LOGGER.info("Waypoint symbol rendered its first frame: shape=" + shape
                    + ", playerDistance=" + distance
                    + ", geometryDistance=" + geometryDistance
                    + ", worldX=" + x + ", worldY=" + y
                    + ", playerHeight=" + playerH
                    + ", targetHeight=" + targetHeight
                    + ", geometryHeight=" + geometryH
                    + ", vboReady=" + vbo.canRender());
        }
        WaypointRenderProfiler.recordSymbol(System.nanoTime() - profileStartedNanos);
    }

    private void writeRing() {
        writeRing(frameRadius);
    }

    private void writeRing(float ringRadius) {
        float inner = Math.max(0.05f, ringRadius - frameStroke);
        for (int i = 0; i < WaypointSymbolGeometry.RING_SEGMENTS; i++) {
            double first = Math.PI * 2.0d * i / WaypointSymbolGeometry.RING_SEGMENTS;
            double second = Math.PI * 2.0d * (i + 1)
                    / WaypointSymbolGeometry.RING_SEGMENTS;
            float cosFirst = (float) Math.cos(first);
            float sinFirst = (float) Math.sin(first);
            float cosSecond = (float) Math.cos(second);
            float sinSecond = (float) Math.sin(second);
            putQuad(cosFirst * ringRadius, sinFirst * ringRadius,
                    cosFirst * inner, sinFirst * inner,
                    cosSecond * ringRadius, sinSecond * ringRadius,
                    cosSecond * inner, sinSecond * inner);
        }
    }

    private void writePlus() {
        float halfStroke = frameStroke * 0.5f;
        float reach = shape == MarkerStyle.WorldStyle.TARGET_CROSSHAIR
                ? frameRadius * 1.18f : frameRadius;
        putQuad(-reach, -halfStroke, -reach, halfStroke,
                reach, -halfStroke, reach, halfStroke);
        putQuad(-halfStroke, -reach, -halfStroke, reach,
                halfStroke, -reach, halfStroke, reach);
    }

    private void writeExclamation() {
        float r = frameRadius;
        float halfWidth = Math.max(frameStroke, r * 0.13f);
        putQuad(-halfWidth, -r * 0.08f, -halfWidth, r,
                halfWidth, -r * 0.08f, halfWidth, r);
        putQuad(-halfWidth, -r, -halfWidth, -r * 0.66f,
                halfWidth, -r, halfWidth, -r * 0.66f);
    }

    private void writeHouse() {
        float r = frameRadius;
        writeSegment(-r, r * 0.12f, 0.0f, r);
        writeSegment(0.0f, r, r, r * 0.12f);
        writeSegment(-r * 0.72f, r * 0.36f, -r * 0.72f, -r);
        writeSegment(r * 0.72f, r * 0.36f, r * 0.72f, -r);
        writeSegment(-r * 0.72f, -r, -r * 0.18f, -r);
        writeSegment(r * 0.18f, -r, r * 0.72f, -r);
        writeSegment(r * 0.42f, r * 0.62f, r * 0.42f, r * 0.98f);
        writeSegment(r * 0.42f, r * 0.98f, r * 0.68f, r * 0.98f);
        writeSegment(r * 0.68f, r * 0.98f, r * 0.68f, r * 0.38f);
        writeSegment(-r * 0.28f, r * 0.28f, r * 0.18f, r * 0.28f);
        writeSegment(r * 0.18f, r * 0.28f, r * 0.18f, -r * 0.1f);
        writeSegment(r * 0.18f, -r * 0.1f, -r * 0.28f, -r * 0.1f);
        writeSegment(-r * 0.28f, -r * 0.1f, -r * 0.28f, r * 0.28f);
        writeSegment(-r * 0.18f, -r, -r * 0.18f, -r * 0.28f);
        writeSegment(r * 0.18f, -r, r * 0.18f, -r * 0.28f);
        writeSegment(-r * 0.18f, -r * 0.28f,
                r * 0.18f, -r * 0.28f);
        writeSegment(r * 0.08f, -r * 0.62f, r * 0.13f, -r * 0.62f);
    }

    private void writeDiamond() {
        float r = frameRadius;
        float top = r * 0.82f;
        writeSegment(-r * 0.55f, top, r * 0.55f, top);
        writeSegment(r * 0.55f, top, r, r * 0.28f);
        writeSegment(r, r * 0.28f, 0.0f, -r);
        writeSegment(0.0f, -r, -r, r * 0.28f);
        writeSegment(-r, r * 0.28f, -r * 0.55f, top);
        writeSegment(-r, r * 0.28f, r, r * 0.28f);
        writeSegment(-r * 0.55f, top, -r * 0.28f, r * 0.28f);
        writeSegment(r * 0.55f, top, r * 0.28f, r * 0.28f);
        writeSegment(-r * 0.28f, r * 0.28f, 0.0f, -r);
        writeSegment(r * 0.28f, r * 0.28f, 0.0f, -r);
    }

    private void writePickaxe() {
        writePickaxeTool(1.0f, 0.0f, 1.0f);
    }

    private void writeShovel() {
        writeShovelTool(1.0f, 0.0f, 1.0f);
    }

    private void writePickaxeAndShovel() {
        // Match the reference silhouette: shovel blade at upper left and
        // D-grip at lower right, crossed by a pickaxe rising to the right.
        float diagonal = 0.70710677f;
        writeShovelTool(0.78f, -diagonal, -diagonal);
        writePickaxeTool(0.82f, -diagonal, diagonal);
    }

    private void writePickaxeTool(float scale, float sin, float cos) {
        toolSegment(0.0f, -0.96f, 0.0f, 0.52f, scale, sin, cos);
        toolSegment(-0.13f, 0.48f, 0.13f, 0.48f, scale, sin, cos);
        toolSegment(-0.13f, 0.48f, -0.13f, 0.65f, scale, sin, cos);
        toolSegment(-0.13f, 0.65f, 0.13f, 0.65f, scale, sin, cos);
        toolSegment(0.13f, 0.65f, 0.13f, 0.48f, scale, sin, cos);
        toolSegment(-1.0f, 0.45f, -0.58f, 0.68f, scale, sin, cos);
        toolSegment(-0.58f, 0.68f, 0.0f, 0.76f, scale, sin, cos);
        toolSegment(0.0f, 0.76f, 0.58f, 0.68f, scale, sin, cos);
        toolSegment(0.58f, 0.68f, 1.0f, 0.45f, scale, sin, cos);
    }

    private void writeShovelTool(float scale, float sin, float cos) {
        toolSegment(-0.18f, 0.94f, 0.18f, 0.94f, scale, sin, cos);
        toolSegment(0.18f, 0.94f, 0.28f, 0.72f, scale, sin, cos);
        toolSegment(0.28f, 0.72f, -0.28f, 0.72f, scale, sin, cos);
        toolSegment(-0.28f, 0.72f, -0.18f, 0.94f, scale, sin, cos);
        toolSegment(0.0f, 0.72f, 0.0f, -0.38f, scale, sin, cos);
        toolSegment(-0.34f, -0.38f, 0.34f, -0.38f, scale, sin, cos);
        toolSegment(-0.34f, -0.38f, -0.4f, -0.76f, scale, sin, cos);
        toolSegment(-0.4f, -0.76f, 0.0f, -1.0f, scale, sin, cos);
        toolSegment(0.0f, -1.0f, 0.4f, -0.76f, scale, sin, cos);
        toolSegment(0.4f, -0.76f, 0.34f, -0.38f, scale, sin, cos);
    }

    private void toolSegment(float startU, float startV,
                             float endU, float endV, float scale,
                             float sin, float cos) {
        float r = frameRadius * scale;
        writeSegment(r * (startU * cos - startV * sin),
                r * (startU * sin + startV * cos),
                r * (endU * cos - endV * sin),
                r * (endU * sin + endV * cos));
    }

    private void writeMoneySign() {
        writeRing(frameRadius);
        float r = frameRadius * 0.62f;
        writeSegment(0.0f, r, 0.0f, -r);
        writeSegment(r * 0.58f, r * 0.72f, -r * 0.42f, r * 0.72f);
        writeSegment(-r * 0.42f, r * 0.72f, -r * 0.62f, r * 0.2f);
        writeSegment(-r * 0.62f, r * 0.2f, r * 0.55f, -r * 0.18f);
        writeSegment(r * 0.55f, -r * 0.18f, r * 0.38f, -r * 0.72f);
        writeSegment(r * 0.38f, -r * 0.72f, -r * 0.58f, -r * 0.72f);
    }

    private void writeCrossedSwords() {
        float r = frameRadius;
        // Each blade, grip, guard, and pommel stays on one coherent sword
        // axis; the two axes are exact mirrors rather than a bent W shape.
        writeSegment(-r * 0.82f, r * 0.95f, r * 0.08f, -r * 0.15f);
        writeSegment(r * 0.82f, r * 0.95f, -r * 0.08f, -r * 0.15f);
        writeSegment(r * 0.08f, -r * 0.15f, r * 0.62f, -r * 0.82f);
        writeSegment(-r * 0.08f, -r * 0.15f, -r * 0.62f, -r * 0.82f);
        writeSegment(-r * 0.08f, -r * 0.28f, r * 0.24f, -r * 0.02f);
        writeSegment(-r * 0.24f, -r * 0.02f, r * 0.08f, -r * 0.28f);
        writeSegment(r * 0.52f, -r * 0.9f, r * 0.72f, -r * 0.74f);
        writeSegment(-r * 0.72f, -r * 0.74f, -r * 0.52f, -r * 0.9f);
    }

    private void writeLighthouse() {
        float r = frameRadius;
        writeSegment(-r * 0.35f, -r * 0.65f, -r * 0.22f, r * 0.22f);
        writeSegment(r * 0.35f, -r * 0.65f, r * 0.22f, r * 0.22f);
        writeSegment(-r * 0.35f, -r * 0.65f, r * 0.35f, -r * 0.65f);
        writeSegment(-r * 0.27f, -r * 0.28f, r * 0.29f, r * 0.08f);
        writeSegment(-r * 0.38f, r * 0.22f, r * 0.38f, r * 0.22f);
        writeSegment(-r * 0.38f, r * 0.22f, -r * 0.38f, r * 0.52f);
        writeSegment(-r * 0.38f, r * 0.52f, r * 0.38f, r * 0.52f);
        writeSegment(r * 0.38f, r * 0.52f, r * 0.38f, r * 0.22f);
        writeSegment(-r * 0.46f, r * 0.52f, 0.0f, r * 0.78f);
        writeSegment(0.0f, r * 0.78f, r * 0.46f, r * 0.52f);
        writeSegment(0.0f, r * 0.78f, 0.0f, r);
        writeSegment(-r * 0.48f, r * 0.62f, -r * 0.9f, r * 0.78f);
        writeSegment(-r * 0.5f, r * 0.4f, -r, r * 0.38f);
        writeSegment(r * 0.48f, r * 0.62f, r * 0.9f, r * 0.78f);
        writeSegment(r * 0.5f, r * 0.4f, r, r * 0.38f);
        writeSegment(-r, -r * 0.82f, -r * 0.5f, -r * 0.92f);
        writeSegment(-r * 0.5f, -r * 0.92f, 0.0f, -r * 0.82f);
        writeSegment(0.0f, -r * 0.82f, r * 0.5f, -r * 0.92f);
        writeSegment(r * 0.5f, -r * 0.92f, r, -r * 0.82f);
    }

    /** Readable low-poly interpretation of the supplied rolled treasure-map reference. */
    private void writeLootMapScroll(float geometryH, float playerH,
                                    float projection,
                                    float distance, float geometryDistance,
                                    int screenWidth, float horizontalFov) {
        float r = frameRadius;
        // Parchment body.
        writeSegment(-r * 0.65f, r * 0.7f, r * 0.65f, r * 0.7f);
        writeSegment(-r * 0.65f, -r * 0.7f, r * 0.65f, -r * 0.7f);
        writeSegment(-r * 0.65f, r * 0.7f, -r * 0.65f, -r * 0.7f);
        writeSegment(r * 0.65f, r * 0.7f, r * 0.65f, -r * 0.7f);
        // Four rolled corners.
        writeScrollCurl(-1.0f, 1.0f, r);
        writeScrollCurl(1.0f, 1.0f, r);
        writeScrollCurl(-1.0f, -1.0f, r);
        writeScrollCurl(1.0f, -1.0f, r);
        // Route and treasure X inside the parchment.
        writeSegment(-r * 0.38f, r * 0.28f, -r * 0.12f, r * 0.05f);
        writeSegment(-r * 0.12f, r * 0.05f, r * 0.18f, r * 0.30f);
        writeSegment(r * 0.18f, r * 0.30f, r * 0.35f, r * 0.05f);
        writeSegment(r * 0.23f, -r * 0.20f, r * 0.53f, -r * 0.50f);
        writeSegment(r * 0.53f, -r * 0.20f, r * 0.23f, -r * 0.50f);

        writeLootMapGroundOutlineOrHidden(playerH, projection, distance,
                geometryDistance, screenWidth, horizontalFov);
    }

    private void writeLootMapGroundOutlineOrHidden(
            float playerH, float projection, float distance,
            float geometryDistance, int screenWidth, float horizontalFov) {
        float guideWidth = BeamDistanceScaling.throughWallWidth(
                0.15f, geometryDistance, screenWidth, horizontalFov);
        if (WaypointSymbolGeometry.showLootMapGroundOutline(distance)) {
            writeLootMapGroundOutline(playerH, projection,
                    Math.max(0.08f * projection, guideWidth));
        } else {
            writeHiddenLootMapGroundOutline();
        }
    }

    /** Distinct rolled report silhouette based on the archaeology scroll asset. */
    private void writeArchaeologyReportScroll(float geometryH, float playerH,
                                              float projection,
                                              float distance,
                                              float geometryDistance,
                                              int screenWidth,
                                              float horizontalFov) {
        float r = frameRadius;
        // Tall parchment body.
        writeSegment(-r * 0.58f, r * 0.72f, r * 0.58f, r * 0.72f);
        writeSegment(-r * 0.58f, -r * 0.72f, r * 0.58f, -r * 0.72f);
        writeSegment(-r * 0.58f, r * 0.72f, -r * 0.58f, -r * 0.72f);
        writeSegment(r * 0.58f, r * 0.72f, r * 0.58f, -r * 0.72f);
        // Wooden rollers at top and bottom.
        writeSegment(-r * 0.78f, r * 0.82f, r * 0.78f, r * 0.82f);
        writeSegment(-r * 0.78f, r * 0.92f, -r * 0.78f, r * 0.70f);
        writeSegment(r * 0.78f, r * 0.92f, r * 0.78f, r * 0.70f);
        writeSegment(-r * 0.78f, -r * 0.82f, r * 0.78f, -r * 0.82f);
        writeSegment(-r * 0.78f, -r * 0.92f, -r * 0.78f, -r * 0.70f);
        writeSegment(r * 0.78f, -r * 0.92f, r * 0.78f, -r * 0.70f);
        // Uneven torn sides.
        writeSegment(-r * 0.58f, r * 0.32f, -r * 0.70f, r * 0.18f);
        writeSegment(-r * 0.70f, -r * 0.16f, -r * 0.58f, -r * 0.31f);
        writeSegment(r * 0.58f, r * 0.17f, r * 0.69f, r * 0.02f);
        writeSegment(r * 0.69f, -r * 0.28f, r * 0.58f, -r * 0.43f);
        // Four-axis archaeology compass rose near the top.
        float starY = r * 0.25f;
        float starR = r * 0.22f;
        writeSegment(0.0f, starY + starR, 0.0f, starY - starR);
        writeSegment(-starR, starY, starR, starY);
        writeSegment(-starR * 0.72f, starY + starR * 0.72f,
                starR * 0.72f, starY - starR * 0.72f);
        writeSegment(starR * 0.72f, starY + starR * 0.72f,
                -starR * 0.72f, starY - starR * 0.72f);
        // Small diamond wax seal and stamped cross at lower right.
        float sealX = r * 0.34f;
        float sealY = -r * 0.42f;
        float sealR = r * 0.15f;
        writeSegment(sealX, sealY + sealR, sealX + sealR, sealY);
        writeSegment(sealX + sealR, sealY, sealX, sealY - sealR);
        writeSegment(sealX, sealY - sealR, sealX - sealR, sealY);
        writeSegment(sealX - sealR, sealY, sealX, sealY + sealR);
        writeSegment(sealX - sealR * 0.65f, sealY,
                sealX + sealR * 0.65f, sealY);
        writeSegment(sealX, sealY - sealR * 0.65f,
                sealX, sealY + sealR * 0.65f);

        writeNavigationGuide(geometryH, playerH, projection, distance,
                geometryDistance, screenWidth, horizontalFov);
    }

    /** Shared Rift-independent ground guide for every dynamic report marker. */
    private void writeNavigationGuide(float geometryH, float playerH,
                                      float projection, float distance,
                                      float geometryDistance, int screenWidth,
                                      float horizontalFov) {
        float halfHeight = BeamDistanceScaling.halfHeight(400.0f,
                geometryDistance);
        boolean lineOnly = BeamDistanceScaling.beamRequiresLineOnlyFallback(distance);
        float lineAbove = lineOnly
                ? BeamDistanceScaling.farLineAboveTarget(halfHeight) : halfHeight;
        float lineBelow = lineOnly
                ? BeamDistanceScaling.farLineBelowTarget(halfHeight) : halfHeight;
        float guideWidth = BeamDistanceScaling.throughWallWidth(
                0.15f, geometryDistance, screenWidth, horizontalFov);
        writeVerticalGuide(WaypointSymbolGeometry.guideBottom(
                        geometryH, playerH, lineBelow),
                WaypointSymbolGeometry.guideTop(
                        geometryH, playerH, lineAbove),
                guideWidth);
        writeTileOutline(geometryH + 0.04f, 2.0f * projection,
                Math.max(0.08f * projection, guideWidth));
    }

    private void writeVerticalGuide(float bottom, float top, float width) {
        float half = width * 0.5f;
        putWorldQuad(centerX - rightX * half, bottom, centerY - rightY * half,
                centerX + rightX * half, bottom, centerY + rightY * half,
                centerX - rightX * half, top, centerY - rightY * half,
                centerX + rightX * half, top, centerY + rightY * half);
    }

    /**
     * Matches Wurm's selected-tile outline: exact world tile boundaries and
     * one independently sampled terrain height at every corner. The small
     * non-negative hover reaches its minimum once per symbol cycle.
     */
    private void writeLootMapGroundOutline(float playerH, float projection,
                                           float width) {
        float minimumWorldX = WaypointSymbolGeometry.tileMinimumWorld(x);
        float minimumWorldY = WaypointSymbolGeometry.tileMinimumWorld(y);
        float maximumWorldX = minimumWorldX + 4.0f;
        float maximumWorldY = minimumWorldY + 4.0f;
        float originX = world.getRenderOriginX();
        float originY = world.getRenderOriginY();
        float playerX = world.getPlayerPosX();
        float playerY = world.getPlayerPosY();
        float left = playerX + (minimumWorldX - playerX) * projection - originX;
        float right = playerX + (maximumWorldX - playerX) * projection - originX;
        float near = playerY + (minimumWorldY - playerY) * projection - originY;
        float far = playerY + (maximumWorldY - playerY) * projection - originY;
        float fallback = groundAnchored ? h : centerH;
        float nearLeftH = projectedCornerHeight(minimumWorldX, minimumWorldY,
                playerH, projection, fallback) + frameGroundHover;
        float nearRightH = projectedCornerHeight(maximumWorldX, minimumWorldY,
                playerH, projection, fallback) + frameGroundHover;
        float farLeftH = projectedCornerHeight(minimumWorldX, maximumWorldY,
                playerH, projection, fallback) + frameGroundHover;
        float farRightH = projectedCornerHeight(maximumWorldX, maximumWorldY,
                playerH, projection, fallback) + frameGroundHover;
        writeTerrainTileOutline(left, right, near, far, nearLeftH,
                nearRightH, farLeftH, farRightH, width);
    }

    /** Preserves the fixed VBO budget while the distant outline is hidden. */
    private void writeHiddenLootMapGroundOutline() {
        for (int edge = 0; edge < 4; edge++) {
            putWorldQuad(centerX, centerH, centerY,
                    centerX, centerH, centerY,
                    centerX, centerH, centerY,
                    centerX, centerH, centerY);
        }
    }

    private float projectedCornerHeight(float worldX, float worldY,
                                        float playerH, float projection,
                                        float fallback) {
        float terrain = WaypointGroundHeight.resolve(world, worldX, worldY,
                targetLayer, fallback);
        return playerH + (terrain - playerH) * projection;
    }

    private void writeTerrainTileOutline(
            float left, float right, float near, float far,
            float nearLeftH, float nearRightH,
            float farLeftH, float farRightH, float width) {
        float halfStroke = width * 0.5f;
        putWorldQuad(left, nearLeftH, near - halfStroke,
                left, nearLeftH, near + halfStroke,
                right, nearRightH, near - halfStroke,
                right, nearRightH, near + halfStroke);
        putWorldQuad(left, farLeftH, far - halfStroke,
                left, farLeftH, far + halfStroke,
                right, farRightH, far - halfStroke,
                right, farRightH, far + halfStroke);
        putWorldQuad(left - halfStroke, nearLeftH, near,
                left + halfStroke, nearLeftH, near,
                left - halfStroke, farLeftH, far,
                left + halfStroke, farLeftH, far);
        putWorldQuad(right - halfStroke, nearRightH, near,
                right + halfStroke, nearRightH, near,
                right - halfStroke, farRightH, far,
                right + halfStroke, farRightH, far);
    }

    /** Draws the complete 4x4-metre Wurm tile selected by the route planner. */
    private void writeTileOutline(float groundH, float halfTile, float width) {
        float halfStroke = width * 0.5f;
        float left = centerX - halfTile;
        float right = centerX + halfTile;
        float near = centerY - halfTile;
        float far = centerY + halfTile;
        putWorldQuad(left, groundH, near - halfStroke,
                left, groundH, near + halfStroke,
                right, groundH, near - halfStroke,
                right, groundH, near + halfStroke);
        putWorldQuad(left, groundH, far - halfStroke,
                left, groundH, far + halfStroke,
                right, groundH, far - halfStroke,
                right, groundH, far + halfStroke);
        putWorldQuad(left - halfStroke, groundH, near,
                left + halfStroke, groundH, near,
                left - halfStroke, groundH, far,
                left + halfStroke, groundH, far);
        putWorldQuad(right - halfStroke, groundH, near,
                right + halfStroke, groundH, near,
                right - halfStroke, groundH, far,
                right + halfStroke, groundH, far);
    }

    private void writeScrollCurl(float horizontal, float vertical, float r) {
        writeSegment(horizontal * r * 0.65f, vertical * r * 0.70f,
                horizontal * r * 0.90f, vertical * r * 0.82f);
        writeSegment(horizontal * r * 0.90f, vertical * r * 0.82f,
                horizontal * r * 0.98f, vertical * r * 0.65f);
        writeSegment(horizontal * r * 0.98f, vertical * r * 0.65f,
                horizontal * r * 0.80f, vertical * r * 0.55f);
    }

    private void writeSegment(float startU, float startV,
                              float endU, float endV) {
        float deltaU = endU - startU;
        float deltaV = endV - startV;
        float length = (float) Math.sqrt(deltaU * deltaU + deltaV * deltaV);
        if (length <= 0.0001f) return;
        float half = frameStroke * 0.5f;
        float offsetU = -deltaV / length * half;
        float offsetV = deltaU / length * half;
        putQuad(startU - offsetU, startV - offsetV,
                startU + offsetU, startV + offsetV,
                endU - offsetU, endV - offsetV,
                endU + offsetU, endV + offsetV);
    }

    private void putQuad(float au, float av, float bu, float bv,
                         float cu, float cv, float du, float dv) {
        float ax = centerX + rightX * au;
        float ay = centerY + rightY * au;
        float ah = centerH + av;
        putWorldQuad(ax, ah, ay,
                centerX + rightX * bu, centerH + bv, centerY + rightY * bu,
                centerX + rightX * cu, centerH + cv, centerY + rightY * cu,
                centerX + rightX * du, centerH + dv, centerY + rightY * du);
    }

    private void putWorldQuad(float ax, float ah, float ay,
                              float bx, float bh, float by,
                              float cx, float ch, float cy,
                              float dx, float dh, float dy) {
        if (hasQuad) {
            putWorld(lastX, lastH, lastY);
            putWorld(ax, ah, ay);
        }
        putWorld(ax, ah, ay);
        putWorld(bx, bh, by);
        putWorld(cx, ch, cy);
        putWorld(dx, dh, dy);
        lastX = dx;
        lastY = dy;
        lastH = dh;
        hasQuad = true;
    }

    private void putWorld(float vertexX, float vertexH, float vertexY) {
        writing.put(vertexX).put(vertexH).put(vertexY);
        writing.put(red).put(green).put(blue).put(frameAlpha);
    }

    @Override public boolean gameTick() { return alive; }

    public boolean isAlive() { return alive; }

    @Override public void delete() {
        if (!alive) return;
        alive = false;
        WaypointLatePassBridge.unregister(this);
        vbo.delete();
        if (material != null) material.destroy();
    }

    private static float animationPhase(float worldX, float worldY) {
        int hash = Float.floatToIntBits(worldX) * 31 + Float.floatToIntBits(worldY);
        return (hash & 1023) * ((float) Math.PI * 2.0f / 1024.0f);
    }

    private static float unit(float value, String label) {
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(label + " must be in 0..1");
        }
        return value;
    }
}
