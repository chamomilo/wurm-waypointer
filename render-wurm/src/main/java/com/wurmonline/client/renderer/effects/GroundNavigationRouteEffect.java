package com.wurmonline.client.renderer.effects;

import com.wurmonline.client.game.CaveDataBuffer;
import com.wurmonline.client.game.DistantTerrainDataBuffer;
import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.Material;
import com.wurmonline.client.renderer.MaterialInstance;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.RenderState;
import com.wurmonline.client.renderer.backend.VertexBuffer;
import com.wurmonline.client.renderer.structures.BridgePartData;
import com.wurmonline.client.util.GLHelper;
import com.wurmonline.mesh.Tiles;
import org.waypoints.next.navigation.CartTerrainRoutePlanner;
import org.waypoints.next.navigation.ChainedCartTerrainRoutePlanner;
import org.waypoints.next.navigation.GroundRouteTrace;
import org.waypoints.next.navigation.HighwayIndexSource;
import org.waypoints.next.navigation.HighwayRoutePlanner;
import org.waypoints.next.navigation.HighwayTileIndex;
import org.waypoints.next.navigation.NavigationRouteStatistics;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;
import org.waypoints.next.render.WaypointWorldBlend;
import org.waypoints.next.render.NavigationRouteDiagnosticLog;
import org.waypoints.next.render.WaypointLatePassBridge;
import org.waypoints.next.render.WaypointLatePassParticipant;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One depth-tested ground ribbon following a bounded cart-safe A* route.
 * Planning runs away from the render thread and republishes as terrain loads.
 */
public final class GroundNavigationRouteEffect extends Effect
        implements WaypointLatePassParticipant {
    enum TravelLayer { SURFACE, BRIDGE, TUNNEL }

    private static final Logger LOGGER = Logger.getLogger(
            GroundNavigationRouteEffect.class.getName());
    private static final int MAX_POINTS = 8192;
    private static final int MAX_DASHES = MAX_POINTS * 2;
    private static final int VERTICES_PER_DASH = 6;
    private static final int MAX_VERTICES = MAX_DASHES * VERTICES_PER_DASH;
    private static final float TILE_SIZE = 4.0f;
    private static final float HALF_WIDTH = 0.28f;
    private static final float GROUND_LIFT = 0.50f;
    private static final float DASH_LENGTH_METRES = 2.0f;
    private static final float DASH_GAP_METRES = 1.5f;
    private static final float DASH_PERIOD_METRES =
            DASH_LENGTH_METRES + DASH_GAP_METRES;
    private static final float CART_XRAY_DISTANCE_METRES = 10.0f;
    private static final long DASH_CYCLE_NANOS = 1_000_000_000L;
    private static final long PULSE_CYCLE_NANOS = 2_000_000_000L;
    private static final long PULSE_TRAVEL_NANOS = 1_000_000_000L;
    private static final long PULSE_TAIL_LINGER_NANOS = 500_000_000L;
    private static final float PULSE_HEAD_HALF_LENGTH = 0.72f;
    private static final float PULSE_HEAD_HALF_WIDTH = 0.56f;
    private static final float PULSE_HEAD_LIFT = 0.0f;
    private static final int MAXIMUM_LEG_TILES = 192;
    private static final int DETOUR_MARGIN_TILES = 64;
    private static final int MAXIMUM_EXPANDED_NODES = 150000;
    private static final int CONNECTOR_MAXIMUM_LEG_TILES = 512;
    private static final int CONNECTOR_DETOUR_MARGIN_TILES = 96;
    private static final int CONNECTOR_MAXIMUM_EXPANDED_NODES = 500000;
    private static final long TERRAIN_RECHECK_NANOS = 1_000_000_000L;
    private static final int MAXIMUM_CHAINED_LEGS = 32;

    private final int targetTileX;
    private final int targetTileY;
    private final int targetLayer;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;
    private final NavigationRouteVisualStyle visualStyle;
    private final float pulseMaximumDistanceMetres;
    private final float maximumSlopeDirt;
    private final float maximumWaterDepthMetres;
    private final int diagnosticTileInterval;
    private final NavigationRouteDiagnosticLog diagnosticLog;
    private final CartTerrainRoutePlanner planner;
    private final CartTerrainRoutePlanner connectorPlanner;
    private final HighwayRoutePlanner highwayPlanner = new HighwayRoutePlanner();
    private final HighwayIndexSource highwaySource;
    private final ExecutorService planningWorker;
    private final VertexBuffer vbo;
    private final MaterialInstance material;
    private final float[] pulseX = new float[MAX_POINTS + 1];
    private final float[] pulseY = new float[MAX_POINTS + 1];
    private final float[] pulseHeight = new float[MAX_POINTS + 1];
    private final float[] pulseDistance = new float[MAX_POINTS + 1];
    private final float[] pulseSampleA = new float[3];
    private final float[] pulseSampleB = new float[3];
    private volatile RouteSnapshot route = RouteSnapshot.empty();
    private volatile NavigationRouteStatistics routeStatistics;
    private RouteSnapshot lastDiagnosedRoute = RouteSnapshot.empty();
    private String lastDiagnosedStrategy;
    private boolean lastDiagnosedReachedTarget;
    private TravelLayer lastDiagnosedTravelLayer;
    private long lastDiagnosedHighwayRevision = Long.MIN_VALUE;
    private volatile boolean planning;
    private int requestedStartX = Integer.MIN_VALUE;
    private int requestedStartY = Integer.MIN_VALUE;
    private TravelLayer requestedTravelLayer;
    private long nextTerrainRecheckNanos;
    private long requestedHighwayRevision = Long.MIN_VALUE;
    private long pulseEpochNanos = Long.MIN_VALUE;
    private long capturedPulseCycle = Long.MIN_VALUE;
    private int pulsePointCount;
    private boolean pulseCaptureDiagnosticWritten;
    private volatile boolean alive = true;

    public GroundNavigationRouteEffect(World world, int targetTileX,
                                       int targetTileY, int targetLayer,
                                       float red, float green, float blue,
                                       float alpha,
                                       NavigationRouteVisualStyle visualStyle,
                                       int pulseMaximumDistanceMetres,
                                       float maximumSlopeDirt,
                                       float maximumWaterDepthMetres,
                                       int diagnosticTileInterval,
                                       int mapWidth, int mapHeight,
                                       HighwayIndexSource highwaySource,
                                       NavigationRouteDiagnosticLog diagnosticLog) {
        super(world);
        this.targetTileX = targetTileX;
        this.targetTileY = targetTileY;
        this.targetLayer = targetLayer;
        this.red = unit(red, "red");
        this.green = unit(green, "green");
        this.blue = unit(blue, "blue");
        this.alpha = unit(alpha, "alpha");
        if (visualStyle == null) throw new IllegalArgumentException(
                "navigation route visual style is required");
        if (pulseMaximumDistanceMetres < 1) throw new IllegalArgumentException(
                "pulse maximum distance must be positive");
        this.visualStyle = visualStyle;
        this.pulseMaximumDistanceMetres = pulseMaximumDistanceMetres;
        this.maximumSlopeDirt = positive(maximumSlopeDirt,
                "maximum slope dirt");
        this.maximumWaterDepthMetres = nonNegative(
                maximumWaterDepthMetres, "maximum water depth");
        this.routeStatistics = NavigationRouteStatistics.empty(
                targetTileX, targetTileY);
        if (diagnosticTileInterval < 1) throw new IllegalArgumentException(
                "diagnostic tile interval must be positive");
        this.diagnosticTileInterval = diagnosticTileInterval;
        this.diagnosticLog = diagnosticLog;
        this.highwaySource = highwaySource;
        this.planner = new CartTerrainRoutePlanner(mapWidth, mapHeight,
                MAXIMUM_LEG_TILES, DETOUR_MARGIN_TILES,
                MAXIMUM_EXPANDED_NODES, MAX_POINTS, maximumSlopeDirt,
                maximumWaterDepthMetres);
        this.connectorPlanner = new CartTerrainRoutePlanner(mapWidth, mapHeight,
                CONNECTOR_MAXIMUM_LEG_TILES, CONNECTOR_DETOUR_MARGIN_TILES,
                CONNECTOR_MAXIMUM_EXPANDED_NODES, MAX_POINTS,
                maximumSlopeDirt, maximumWaterDepthMetres);
        this.planningWorker = Executors.newSingleThreadExecutor(
                new ThreadFactory() {
                    @Override public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable,
                                "wurm-waypointer-cart-route");
                        thread.setDaemon(true);
                        return thread;
                    }
                });
        this.vbo = VertexBuffer.create(VertexBuffer.Usage.EFFECT,
                MAX_VERTICES, true, false, false, true, false,
                0, 0, true, false);
        this.material = GLHelper.useDeferredShading()
                ? Material.load("material.simple").instance() : null;
        WaypointLatePassBridge.register(this);
    }

    /** Thread-safe summary of the exact point list currently shown by this effect. */
    public NavigationRouteStatistics getRouteStatistics() {
        return routeStatistics;
    }

    @Override public void render(Queue queue, float tickFraction) {
        // Waypointer geometry has one owner in WorldRender's stable late pass.
    }

    @Override public boolean isLatePassAlive() {
        return alive;
    }

    @Override public void renderInLateWorldPass(Queue queue) {
        if (!alive || queue == null) return;
        requestPlanIfNeeded();
        renderRoute(queue, getLayer() < 0);
    }

    private void renderRoute(Queue queue, boolean tunnelMainPass) {
        RouteSnapshot visible = route;
        int pointCount = visible.count;

        if (visualStyle == NavigationRouteVisualStyle.PULSE) {
            if (pointCount < 2 && pulsePointCount < 2) return;
            renderPulse(queue, visible, tunnelMainPass);
        } else if (pointCount < 2) {
            return;
        } else if (visualStyle == NavigationRouteVisualStyle.SOLID) {
            renderSolid(queue, visible, tunnelMainPass);
        } else {
            renderMovingDashes(queue, visible, tunnelMainPass);
        }
    }

    private void renderMovingDashes(Queue queue, RouteSnapshot visible,
                                    boolean tunnelMainPass) {
        int pointCount = visible.count;
        FloatBuffer vertices = vbo.lock();
        float originX = world.getRenderOriginX();
        float originY = world.getRenderOriginY();
        float phase = routeDashPhase(System.nanoTime());
        float travelled = 0.0f;
        int dashCount = 0;
        int cartXrayDashCount = 0;
        for (int i = 0; i + 1 < pointCount && dashCount < MAX_DASHES; i++) {
            float fromX = tileCentre(visible.tileX[i]) - originX;
            float fromY = tileCentre(visible.tileY[i]) - originY;
            float toX = tileCentre(visible.tileX[i + 1]) - originX;
            float toY = tileCentre(visible.tileY[i + 1]) - originY;
            float directionX = toX - fromX;
            float directionY = toY - fromY;
            float length = (float) Math.sqrt(directionX * directionX
                    + directionY * directionY);
            if (length <= 0.0001f) continue;
            float sideX = -directionY / length;
            float sideY = directionX / length;
            float segmentEnd = travelled + length;
            float dashStart = phase + (float) Math.floor(
                    (travelled - phase) / DASH_PERIOD_METRES)
                    * DASH_PERIOD_METRES;
            while (dashStart + DASH_LENGTH_METRES <= travelled) {
                dashStart += DASH_PERIOD_METRES;
            }
            while (dashStart < segmentEnd && dashCount < MAX_DASHES) {
                float visibleStart = Math.max(travelled, dashStart);
                float visibleEnd = Math.min(segmentEnd,
                        dashStart + DASH_LENGTH_METRES);
                if (visibleEnd > visibleStart + 0.001f) {
                    float startFraction = (visibleStart - travelled) / length;
                    float endFraction = (visibleEnd - travelled) / length;
                    float startX = fromX + directionX * startFraction;
                    float startY = fromY + directionY * startFraction;
                    float endX = fromX + directionX * endFraction;
                    float endY = fromY + directionY * endFraction;
                    float startHeight = visible.height[i]
                            + (visible.height[i + 1] - visible.height[i])
                            * startFraction + GROUND_LIFT;
                    float endHeight = visible.height[i]
                            + (visible.height[i + 1] - visible.height[i])
                            * endFraction + GROUND_LIFT;
                    putDash(vertices, startX, startY, startHeight,
                            endX, endY, endHeight, sideX, sideY);
                    dashCount++;
                    if (visibleStart < CART_XRAY_DISTANCE_METRES) {
                        cartXrayDashCount++;
                    }
                }
                dashStart += DASH_PERIOD_METRES;
            }
            travelled = segmentEnd;
        }
        vbo.unlock();
        if (dashCount == 0) return;

        queueRoutePrimitive(queue, dashCount * 2, tunnelMainPass);
        if (!tunnelMainPass && cartXrayDashCount > 0) {
            queueRoutePrimitive(queue, cartXrayDashCount * 2, true);
        }
    }

    private void renderSolid(Queue queue, RouteSnapshot visible,
                             boolean tunnelMainPass) {
        FloatBuffer vertices = vbo.lock();
        float originX = world.getRenderOriginX();
        float originY = world.getRenderOriginY();
        float travelled = 0.0f;
        int sections = 0;
        int cartXraySections = 0;
        for (int i = 0; i + 1 < visible.count && sections < MAX_DASHES; i++) {
            float fromX = tileCentre(visible.tileX[i]) - originX;
            float fromY = tileCentre(visible.tileY[i]) - originY;
            float toX = tileCentre(visible.tileX[i + 1]) - originX;
            float toY = tileCentre(visible.tileY[i + 1]) - originY;
            float dx = toX - fromX;
            float dy = toY - fromY;
            float length = (float) Math.hypot(dx, dy);
            if (length <= 0.0001f) continue;
            putTaperedRibbon(vertices, fromX, fromY,
                    visible.height[i] + GROUND_LIFT,
                    toX, toY, visible.height[i + 1] + GROUND_LIFT,
                    HALF_WIDTH, HALF_WIDTH, 1.0f, 1.0f);
            sections++;
            if (travelled < CART_XRAY_DISTANCE_METRES) cartXraySections++;
            travelled += length;
        }
        vbo.unlock();
        if (sections == 0) return;
        queueRoutePrimitive(queue, sections * 2, tunnelMainPass);
        if (!tunnelMainPass && cartXraySections > 0) {
            queueRoutePrimitive(queue, cartXraySections * 2, true);
        }
    }

    private void renderPulse(Queue queue, RouteSnapshot visible,
                             boolean tunnelMainPass) {
        long now = System.nanoTime();
        if (pulseEpochNanos == Long.MIN_VALUE) pulseEpochNanos = now;
        long elapsed = now - pulseEpochNanos;
        long cycle = pulseCycleIndex(elapsed);
        float originX = world.getRenderOriginX();
        float originY = world.getRenderOriginY();
        if (capturedPulseCycle != cycle) {
            if (visible.count < 2) return;
            float capturedLength = buildPulsePath(visible);
            if (pulsePointCount < 2 || capturedLength < 0.10f) return;
            if (!pulseCaptureDiagnosticWritten) {
                LOGGER.log(Level.INFO, "Navigation pulse captured: "
                                + "routePoints={0}, capturedPoints={1}, "
                                + "capturedMetres={2}, maximumMetres={3}",
                        new Object[]{visible.count, pulsePointCount,
                                capturedLength, pulseMaximumDistanceMetres});
                pulseCaptureDiagnosticWritten = true;
            }
            capturedPulseCycle = cycle;
        }
        float progress = pulseTravelProgress(elapsed);
        if (progress < 0.0f) return;
        boolean headVisible = pulseHeadVisible(elapsed);
        float pathLength = pulseDistance[pulsePointCount - 1];
        if (pulsePointCount < 2 || pathLength < 0.10f) return;

        float headDistance = pathLength * progress;
        float trailStart = pathLength * pulseTrailStartProgress(elapsed);

        FloatBuffer vertices = vbo.lock();
        int triangles = 0;
        if (headDistance > trailStart + 0.01f) {
            float fromDistance = trailStart;
            samplePulsePath(fromDistance, pulseSampleA);
            int endIndex = pulsePointAfter(fromDistance);
            while (fromDistance < headDistance - 0.001f
                    && triangles < MAX_DASHES * 2) {
                float toDistance = endIndex < pulsePointCount
                        ? Math.min(headDistance, pulseDistance[endIndex])
                        : headDistance;
                if (toDistance <= fromDistance + 0.001f) {
                    endIndex++;
                    continue;
                }
                samplePulsePath(toDistance, pulseSampleB);
                float fromAlpha = pulseTrailAlpha(elapsed,
                        fromDistance / pathLength);
                float toAlpha = pulseTrailAlpha(elapsed,
                        toDistance / pathLength);
                putTaperedRibbon(vertices,
                        pulseSampleA[0] - originX,
                        pulseSampleA[1] - originY,
                        pulseSampleA[2] + GROUND_LIFT,
                        pulseSampleB[0] - originX,
                        pulseSampleB[1] - originY,
                        pulseSampleB[2] + GROUND_LIFT,
                        0.04f + HALF_WIDTH * fromAlpha,
                        0.04f + HALF_WIDTH * toAlpha,
                        0.85f * fromAlpha,
                        0.85f * toAlpha);
                pulseSampleA[0] = pulseSampleB[0];
                pulseSampleA[1] = pulseSampleB[1];
                pulseSampleA[2] = pulseSampleB[2];
                fromDistance = toDistance;
                if (endIndex < pulsePointCount
                        && toDistance >= pulseDistance[endIndex] - 0.001f) {
                    endIndex++;
                }
                triangles += 2;
            }
        }
        if (headVisible) {
            putPulseHead(vertices, headDistance, pathLength, originX, originY);
            triangles += 2;
        }
        vbo.unlock();

        if (triangles == 0) return;
        queueRoutePrimitive(queue, triangles, tunnelMainPass);
        if (!tunnelMainPass && trailStart < CART_XRAY_DISTANCE_METRES) {
            queueRoutePrimitive(queue, triangles, true);
        }
    }

    private float buildPulsePath(RouteSnapshot visible) {
        // Store an immutable world-space trajectory for this shot. Replans and
        // player motion may update the next shot, never the one already flying.
        pulsePointCount = capturePulsePath(world.getPlayerPosX(),
                world.getPlayerPosY(), world.getPlayerPosH(), visible.tileX,
                visible.tileY, visible.height, visible.count,
                pulseMaximumDistanceMetres, pulseX, pulseY, pulseHeight,
                pulseDistance);
        return pulseDistance[pulsePointCount - 1];
    }

    static int capturePulsePath(float startX, float startY, float startHeight,
                                int[] tileX, int[] tileY, float[] height,
                                int count, float maximumDistanceMetres,
                                float[] capturedX, float[] capturedY,
                                float[] capturedHeight,
                                float[] capturedDistance) {
        int capacity = Math.min(Math.min(capturedX.length, capturedY.length),
                Math.min(capturedHeight.length, capturedDistance.length));
        if (capacity < 1) throw new IllegalArgumentException(
                "pulse capture output is empty");
        int pointCount = 1;
        capturedX[0] = startX;
        capturedY[0] = startY;
        capturedHeight[0] = startHeight;
        capturedDistance[0] = 0.0f;
        for (int i = 0; i < count && pointCount < capacity; i++) {
            float x = tileCentre(tileX[i]);
            float y = tileCentre(tileY[i]);
            float pointHeight = height[i];
            int previous = pointCount - 1;
            float dx = x - capturedX[previous];
            float dy = y - capturedY[previous];
            float length = (float) Math.hypot(dx, dy);
            if (length <= 0.001f) {
                capturedHeight[previous] = pointHeight;
                continue;
            }
            float travelled = capturedDistance[previous];
            if (travelled + length >= maximumDistanceMetres) {
                float fraction = (maximumDistanceMetres - travelled) / length;
                capturedX[pointCount] = capturedX[previous] + dx * fraction;
                capturedY[pointCount] = capturedY[previous] + dy * fraction;
                capturedHeight[pointCount] = capturedHeight[previous]
                        + (pointHeight - capturedHeight[previous]) * fraction;
                capturedDistance[pointCount] = maximumDistanceMetres;
                pointCount++;
                break;
            }
            capturedX[pointCount] = x;
            capturedY[pointCount] = y;
            capturedHeight[pointCount] = pointHeight;
            capturedDistance[pointCount] = travelled + length;
            pointCount++;
        }
        return pointCount;
    }

    private void samplePulsePath(float distance, float[] result) {
        float bounded = Math.max(0.0f, Math.min(distance,
                pulseDistance[pulsePointCount - 1]));
        int low = 1;
        int high = pulsePointCount - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (pulseDistance[middle] < bounded) low = middle + 1;
            else high = middle;
        }
        int end = low;
        int start = Math.max(0, end - 1);
        float span = pulseDistance[end] - pulseDistance[start];
        float fraction = span <= 0.0001f ? 0.0f
                : (bounded - pulseDistance[start]) / span;
        result[0] = pulseX[start] + (pulseX[end] - pulseX[start]) * fraction;
        result[1] = pulseY[start] + (pulseY[end] - pulseY[start]) * fraction;
        result[2] = pulseHeight[start]
                + (pulseHeight[end] - pulseHeight[start]) * fraction;
    }

    private int pulsePointAfter(float distance) {
        int low = 1;
        int high = pulsePointCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (pulseDistance[middle] <= distance) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private void putPulseHead(FloatBuffer target, float distance,
                              float pathLength, float originX, float originY) {
        samplePulsePath(distance, pulseSampleA);
        samplePulsePath(Math.min(pathLength, distance + 0.45f), pulseSampleB);
        float dx = pulseSampleB[0] - pulseSampleA[0];
        float dy = pulseSampleB[1] - pulseSampleA[1];
        if (Math.abs(dx) + Math.abs(dy) < 0.001f) {
            samplePulsePath(Math.max(0.0f, distance - 0.45f), pulseSampleB);
            dx = pulseSampleA[0] - pulseSampleB[0];
            dy = pulseSampleA[1] - pulseSampleB[1];
        }
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0.0001f) { dx = 1.0f; dy = 0.0f; length = 1.0f; }
        dx /= length;
        dy /= length;
        float sx = -dy;
        float sy = dx;
        float x = pulseSampleA[0] - originX;
        float y = pulseSampleA[1] - originY;
        float h = pulseSampleA[2] + GROUND_LIFT + PULSE_HEAD_LIFT;
        float frontX = x + dx * PULSE_HEAD_HALF_LENGTH;
        float frontY = y + dy * PULSE_HEAD_HALF_LENGTH;
        float backX = x - dx * PULSE_HEAD_HALF_LENGTH;
        float backY = y - dy * PULSE_HEAD_HALF_LENGTH;
        float leftX = x + sx * PULSE_HEAD_HALF_WIDTH;
        float leftY = y + sy * PULSE_HEAD_HALF_WIDTH;
        float rightX = x - sx * PULSE_HEAD_HALF_WIDTH;
        float rightY = y - sy * PULSE_HEAD_HALF_WIDTH;
        putVertex(target, frontX, h, frontY, 1.35f);
        putVertex(target, leftX, h, leftY, 1.10f);
        putVertex(target, backX, h, backY, 0.90f);
        putVertex(target, frontX, h, frontY, 1.35f);
        putVertex(target, backX, h, backY, 0.90f);
        putVertex(target, rightX, h, rightY, 1.10f);
    }

    static float pulseTravelProgress(long elapsedNanos) {
        long cycle = elapsedNanos % PULSE_CYCLE_NANOS;
        if (cycle < 0L) cycle += PULSE_CYCLE_NANOS;
        if (cycle >= PULSE_TRAVEL_NANOS + PULSE_TAIL_LINGER_NANOS) {
            return -1.0f;
        }
        if (cycle >= PULSE_TRAVEL_NANOS) return 1.0f;
        return cycle / (float) PULSE_TRAVEL_NANOS;
    }

    static float pulseTrailStartProgress(long elapsedNanos) {
        long cycle = elapsedNanos % PULSE_CYCLE_NANOS;
        if (cycle < 0L) cycle += PULSE_CYCLE_NANOS;
        if (cycle >= PULSE_TRAVEL_NANOS + PULSE_TAIL_LINGER_NANOS) {
            return 1.0f;
        }
        long oldestVisiblePass = Math.max(0L,
                cycle - PULSE_TAIL_LINGER_NANOS);
        return Math.min(1.0f,
                oldestVisiblePass / (float) PULSE_TRAVEL_NANOS);
    }

    static float pulseTrailAlpha(long elapsedNanos, float routeProgress) {
        long cycle = elapsedNanos % PULSE_CYCLE_NANOS;
        if (cycle < 0L) cycle += PULSE_CYCLE_NANOS;
        float boundedProgress = Math.max(0.0f,
                Math.min(1.0f, routeProgress));
        float ageNanos = cycle
                - boundedProgress * PULSE_TRAVEL_NANOS;
        if (ageNanos < 0.0f
                || ageNanos >= PULSE_TAIL_LINGER_NANOS) return 0.0f;
        return 1.0f - ageNanos / PULSE_TAIL_LINGER_NANOS;
    }

    static boolean pulseHeadVisible(long elapsedNanos) {
        long cycle = elapsedNanos % PULSE_CYCLE_NANOS;
        if (cycle < 0L) cycle += PULSE_CYCLE_NANOS;
        return cycle < PULSE_TRAVEL_NANOS;
    }

    static long pulseCycleIndex(long elapsedNanos) {
        if (elapsedNanos >= 0L) return elapsedNanos / PULSE_CYCLE_NANOS;
        return Math.floorDiv(elapsedNanos, PULSE_CYCLE_NANOS);
    }

    private void queueRoutePrimitive(Queue queue, int triangleCount,
                                     boolean visibleThroughCart) {
        Primitive primitive = queue.reservePrimitive();
        primitive.copyStateFrom(RenderState.RENDERSTATE_ALPHABLEND);
        primitive.blendmode = WaypointWorldBlend.luminous();
        primitive.clearTextures();
        if (material != null) {
            primitive.materialInstance = material;
            primitive.program = material.getProgram();
            primitive.bindings = material.getProgramBindings();
        }
        primitive.type = Primitive.Type.TRIANGLES;
        primitive.twosided = true;
        primitive.nolight = true;
        primitive.depthwrite = false;
        if (visibleThroughCart) primitive.depthtest = Primitive.TestFunc.ALWAYS;
        primitive.vertex = vbo;
        primitive.index = null;
        primitive.offset = 0;
        primitive.num = triangleCount;
        queue.queue(primitive, null);
    }

    private void requestPlanIfNeeded() {
        final int startX = (int) Math.floor(world.getPlayerPosX() / TILE_SIZE);
        final int startY = (int) Math.floor(world.getPlayerPosY() / TILE_SIZE);
        final TravelLayer travelLayer = currentTravelLayer();
        setLayer(numericLayer(travelLayer));
        long now = System.nanoTime();
        final long highwayRevision = highwaySource == null
                ? 0L : highwaySource.revision();
        if (planning || (startX == requestedStartX && startY == requestedStartY
                && travelLayer == requestedTravelLayer
                && highwayRevision == requestedHighwayRevision
                && now < nextTerrainRecheckNanos)) return;
        requestedStartX = startX;
        requestedStartY = startY;
        requestedTravelLayer = travelLayer;
        requestedHighwayRevision = highwayRevision;
        nextTerrainRecheckNanos = now + TERRAIN_RECHECK_NANOS;
        planning = true;
        planningWorker.execute(new Runnable() {
            @Override public void run() {
                try {
                    HighwayTileIndex highways = highwaySource == null
                            ? HighwayTileIndex.empty() : highwaySource.current();
                    PlannedRoute plan = planRoute(startX, startY, highways,
                            travelLayer);
                    if (!alive) return;
                    RouteSnapshot next = RouteSnapshot.from(plan.points);
                    route = next;
                    routeStatistics = plan.completeStatistics == null
                            ? NavigationRouteStatistics.calculate(
                            plan.points, plan.reachedFinalTarget,
                            targetTileX, targetTileY, maximumSlopeDirt,
                            maximumWaterDepthMetres)
                            : plan.completeStatistics;
                    if (diagnosticLog != null && shouldDiagnose(next, plan,
                            travelLayer, highwayRevision)) {
                        GroundRouteTrace trace = GroundRouteTrace.analyse(
                                targetTileX, targetTileY,
                                numericLayer(travelLayer),
                                plan.points.size(), plan.reachedFinalTarget,
                                maximumSlopeDirt,
                                maximumWaterDepthMetres, plan.points);
                        diagnosticLog.routeEvaluated(trace, plan.strategy,
                                plan.expandedNodes, plan.rejectedSlopeEdges,
                                plan.rejectedWaterEdges,
                                plan.rejectedUnknownEdges,
                                plan.rejectedCornerEdges,
                                highwayRevision);
                        rememberDiagnosedRoute(next, plan, travelLayer,
                                highwayRevision);
                    }
                } catch (Throwable failure) {
                    LOGGER.log(Level.WARNING,
                            "Cart navigation route planning failed open", failure);
                } finally {
                    planning = false;
                }
            }
        });
    }

    private PlannedRoute planRoute(int startX, int startY,
                                   HighwayTileIndex highways,
                                   TravelLayer travelLayer) {
        HighwayRoutePlanner.NetworkLayer networkLayer = networkLayer(
                travelLayer);
        if (travelLayer == TravelLayer.TUNNEL) {
            if (highways.get(startX, startY).hasKind(
                    HighwayTileIndex.Kind.TUNNEL)) {
                if (targetLayer < 0) {
                    HighwayRoutePlanner.Plan directTunnel = highwayPlanner
                            .planIncludingNecessaryDetours(startX, startY,
                                    targetTileX, targetTileY, highways,
                                    networkLayer);
                    if (planEndsAt(directTunnel, targetTileX, targetTileY)) {
                        PlannedRoute throughTunnel = tunnelRoute(startX,
                                startY, directTunnel, highways, travelLayer);
                        if (throughTunnel != null) return throughTunnel;
                    }
                }
                HighwayRoutePlanner.Plan layered = highwayPlanner
                        .planAcrossLayers(startX, startY, true, targetTileX,
                                targetTileY, targetLayer < 0, highways,
                                false);
                HighwayRoutePlanner.Plan selectedTunnel = highwayPlanner
                        .leadingTunnelStage(layered);
                if (selectedTunnel.usesHighway()) {
                    PlannedRoute toSelectedExit = tunnelRoute(startX, startY,
                            selectedTunnel, highways, travelLayer);
                    if (toSelectedExit != null) return toSelectedExit;
                }
                HighwayRoutePlanner.Plan exitTunnel = highwayPlanner
                        .planTunnelToSurfacePortal(startX, startY,
                                targetTileX, targetTileY, highways);
                if (exitTunnel.usesHighway()) {
                    PlannedRoute toSurface = tunnelRoute(startX, startY,
                            exitTunnel, highways, travelLayer);
                    if (toSurface != null) return toSurface;
                }
                return unavailableTunnelRoute(startX, startY, highways,
                        travelLayer);
            }
            // No published tunnel covers this cave tile. Retain the cave-only
            // terrain fallback, but never consult the surface Highway graph.
            return localRoute(startX, startY, highways, travelLayer);
        }
        if (travelLayer == TravelLayer.BRIDGE) {
            return bridgeStage(startX, startY, highways, travelLayer);
        }
        PlannedRoute tunnelShortcut = surfaceTunnelStage(startX, startY,
                highways, travelLayer, targetLayer >= 0);
        if (tunnelShortcut != null) return tunnelShortcut;
        if (targetLayer < 0) {
            return surfaceApproachToUndergroundTarget(startX, startY,
                    highways, travelLayer);
        }
        HighwayRoutePlanner.Plan highway = highwayPlanner.plan(startX, startY,
                targetTileX, targetTileY, highways, networkLayer);
        if (highway != null && highway.usesHighway()) {
            PlannedRoute preferred = highwayRoute(startX, startY, highway,
                    highways, "highway_graph_with_terrain_connectors",
                    travelLayer);
            if (preferred != null) return preferred;
        }

        PlannedRoute local = localRoute(startX, startY, highways, travelLayer);
        if (local.reachedFinalTarget || travelLayer == TravelLayer.TUNNEL) {
            return local;
        }

        tunnelShortcut = surfaceTunnelStage(startX, startY, highways,
                travelLayer, false);
        if (tunnelShortcut != null) return tunnelShortcut;

        // A straight-line estimate cannot know that water or a cliff makes the
        // apparent shortcut impossible. Once terrain A* proves that case, allow
        // a slower published detour so a nearby bridge can become mandatory.
        if (highway == null || !highway.usesHighway()) {
            HighwayRoutePlanner.Plan necessary = highwayPlanner
                    .planIncludingNecessaryDetours(startX, startY, targetTileX,
                            targetTileY, highways, networkLayer);
            if (necessary.usesHighway()) {
                PlannedRoute detour = highwayRoute(startX, startY, necessary,
                        highways,
                        "necessary_highway_graph_with_terrain_connectors",
                        travelLayer);
                if (detour != null) return detour;
            }
        }
        return local;
    }

    private PlannedRoute surfaceTunnelStage(
            int startX, int startY, HighwayTileIndex highways,
            TravelLayer travelLayer, boolean requireMeaningfulSaving) {
        if (targetLayer < 0 && !highways.get(targetTileX, targetTileY)
                .hasKind(HighwayTileIndex.Kind.TUNNEL)) return null;
        HighwayRoutePlanner.Plan complete = highwayPlanner.planAcrossLayers(
                startX, startY, false, targetTileX, targetTileY,
                targetLayer < 0, highways, requireMeaningfulSaving);
        if (!HighwayRoutePlanner.containsKind(complete,
                HighwayTileIndex.Kind.TUNNEL)) return null;
        HighwayRoutePlanner.Plan surface = highwayPlanner
                .leadingSurfaceStage(complete);
        if (!surface.usesHighway()) return null;
        PlannedRoute approach = highwayRouteTo(startX, startY, surface,
                highways, requireMeaningfulSaving
                        ? "surface_multilayer_to_tunnel_portal"
                        : "necessary_surface_multilayer_to_tunnel_portal",
                travelLayer, surface.getExitX(), surface.getExitY(), false);
        if (approach == null) return null;
        HighwayRoutePlanner.TileStep tunnel = firstStepOfKind(complete,
                HighwayTileIndex.Kind.TUNNEL);
        if (tunnel == null) return null;
        PlannedRoute preview = appendLoadedTunnelPreview(approach,
                complete, tunnel.getTileX(), tunnel.getTileY(), highways,
                travelLayer);
        return preview.withCompleteStatistics(
                NavigationRouteStatistics.calculateCompleteHighwayPlan(
                        complete, startX, startY, targetTileX, targetTileY));
    }

    private PlannedRoute bridgeStage(int startX, int startY,
                                     HighwayTileIndex highways,
                                     TravelLayer travelLayer) {
        if (!hasNearbyHighwayKind(highways, startX, startY,
                HighwayTileIndex.Kind.BRIDGE)) {
            return unavailableLayerTransitionRoute(startX, startY, highways,
                    travelLayer, "live_bridge_has_no_published_segment");
        }
        int towardX = targetTileX;
        int towardY = targetTileY;
        if (targetLayer < 0) {
            HighwayRoutePlanner.TileStep tunnelEntrance =
                    undergroundSurfaceEntrance(startX, startY, highways);
            if (tunnelEntrance != null) {
                towardX = tunnelEntrance.getTileX();
                towardY = tunnelEntrance.getTileY();
            }
        }
        HighwayRoutePlanner.Plan bridge = highwayPlanner
                .planFromOccupiedBridgeToTarget(startX, startY, towardX,
                        towardY, highways);
        if (!bridge.usesHighway()) {
            return unavailableLayerTransitionRoute(startX, startY, highways,
                    travelLayer, "bridge_graph_has_no_surface_ramp");
        }
        PlannedRoute across = occupiedBridgeRouteToTarget(startX, startY,
                bridge, highways, travelLayer);
        return across == null
                ? unavailableLayerTransitionRoute(startX, startY, highways,
                travelLayer, "bridge_geometry_is_not_loaded")
                : across;
    }

    private PlannedRoute occupiedBridgeRouteToTarget(
            int startX, int startY, HighwayRoutePlanner.Plan route,
            HighwayTileIndex highways, TravelLayer travelLayer) {
        PlannedRoute network = specialLayerRoute(startX, startY, route,
                highways, travelLayer, "mixed_bridge_highway_graph");
        if (network == null || network.points.isEmpty()) return null;
        GroundRouteTrace.Point last = network.points.get(
                network.points.size() - 1);
        if (last.getTileX() == targetTileX
                && last.getTileY() == targetTileY) {
            return new PlannedRoute(network.points, true, network.strategy,
                    network.expandedNodes, network.rejectedSlopeEdges,
                    network.rejectedWaterEdges,
                    network.rejectedUnknownEdges,
                    network.rejectedCornerEdges);
        }
        PlannedRoute exit = terrainRoute(last.getTileX(), last.getTileY(),
                targetTileX, targetTileY, connectorPlanner,
                "bridge_network_exit", highways, TravelLayer.SURFACE);
        if (!exit.reachedFinalTarget) return network;
        List<GroundRouteTrace.Point> combined =
                new ArrayList<GroundRouteTrace.Point>(network.points);
        append(combined, exit.points);
        GroundRouteTrace validation = GroundRouteTrace.analyse(
                targetTileX, targetTileY, targetLayer, combined.size(), true,
                maximumSlopeDirt, maximumWaterDepthMetres, combined);
        if (validation.getResult() == GroundRouteTrace.Result.BLOCKED) {
            return network;
        }
        return new PlannedRoute(combined, true, network.strategy,
                network.expandedNodes + exit.expandedNodes,
                network.rejectedSlopeEdges + exit.rejectedSlopeEdges,
                network.rejectedWaterEdges + exit.rejectedWaterEdges,
                network.rejectedUnknownEdges + exit.rejectedUnknownEdges,
                network.rejectedCornerEdges + exit.rejectedCornerEdges);
    }

    static boolean planEndsAt(HighwayRoutePlanner.Plan plan,
                              int tileX, int tileY) {
        if (plan == null || plan.getHighwaySteps().isEmpty()) return false;
        HighwayRoutePlanner.TileStep last = plan.getHighwaySteps().get(
                plan.getHighwaySteps().size() - 1);
        return last.getTileX() == tileX && last.getTileY() == tileY;
    }

    private PlannedRoute surfaceApproachToUndergroundTarget(
            int startX, int startY, HighwayTileIndex highways,
            TravelLayer travelLayer) {
        HighwayRoutePlanner.TileStep entrance = undergroundSurfaceEntrance(
                startX, startY, highways);
        if (entrance == null) {
            String reason = highways.get(targetTileX, targetTileY).hasKind(
                    HighwayTileIndex.Kind.TUNNEL)
                    ? "underground_target_has_no_surface_tunnel_portal"
                    : "underground_target_is_not_on_published_tunnel";
            return unavailableLayerTransitionRoute(startX, startY, highways,
                    travelLayer, reason);
        }
        return surfaceRouteToTunnelPortal(startX, startY,
                entrance.getTileX(), entrance.getTileY(), highways,
                travelLayer);
    }

    private HighwayRoutePlanner.TileStep undergroundSurfaceEntrance(
            int startX, int startY, HighwayTileIndex highways) {
        if (!highways.get(targetTileX, targetTileY).hasKind(
                HighwayTileIndex.Kind.TUNNEL)) return null;
        HighwayRoutePlanner.Plan underground = highwayPlanner
                .planTunnelToSurfacePortal(targetTileX, targetTileY,
                        startX, startY, highways);
        if (!underground.usesHighway()
                || underground.getHighwaySteps().isEmpty()) {
            return null;
        }
        HighwayRoutePlanner.TileStep entrance = underground
                .getHighwaySteps().get(
                        underground.getHighwaySteps().size() - 1);
        if (entrance.getKind() != HighwayTileIndex.Kind.TUNNEL
                || !entrance.isPortal()) {
            return null;
        }
        return entrance;
    }

    private PlannedRoute surfaceRouteToTunnelPortal(
            int startX, int startY, int portalX, int portalY,
            HighwayTileIndex highways, TravelLayer travelLayer) {
        HighwayRoutePlanner.NetworkLayer layer = networkLayer(travelLayer);
        HighwayRoutePlanner.Plan highway = highwayPlanner.plan(startX, startY,
                portalX, portalY, highways, layer);
        if (highway.usesHighway()) {
            HighwayRoutePlanner.TileStep bridge = firstStepOfKind(highway,
                    HighwayTileIndex.Kind.BRIDGE);
            if (bridge != null) {
                return surfaceRouteToFirstBridge(startX, startY, highway,
                        highways, travelLayer);
            }
            PlannedRoute preferred = highwayRouteTo(startX, startY, highway,
                    highways, "surface_highway_to_tunnel_portal",
                    travelLayer, portalX, portalY, false);
            if (preferred != null) {
                return markSpecialPortalEndpoint(preferred, portalX, portalY,
                        HighwayTileIndex.Kind.TUNNEL);
            }
        }

        PlannedRoute local = terrainRoute(startX, startY, portalX, portalY,
                planner, "surface_terrain_to_tunnel_portal", highways,
                travelLayer);
        if (local.reachedFinalTarget) {
            return markSpecialPortalEndpoint(local, portalX, portalY,
                    HighwayTileIndex.Kind.TUNNEL);
        }
        if (!highway.usesHighway()) {
            HighwayRoutePlanner.Plan necessary = highwayPlanner
                    .planIncludingNecessaryDetours(startX, startY, portalX,
                            portalY, highways, layer);
            if (necessary.usesHighway()) {
                HighwayRoutePlanner.TileStep bridge = firstStepOfKind(
                        necessary, HighwayTileIndex.Kind.BRIDGE);
                if (bridge != null) {
                    return surfaceRouteToFirstBridge(startX, startY,
                            necessary, highways, travelLayer);
                }
                PlannedRoute detour = highwayRouteTo(startX, startY,
                        necessary, highways,
                        "necessary_surface_highway_to_tunnel_portal",
                        travelLayer, portalX, portalY, false);
                if (detour != null) {
                    return markSpecialPortalEndpoint(detour, portalX, portalY,
                            HighwayTileIndex.Kind.TUNNEL);
                }
            }
        }
        return markSpecialPortalEndpoint(local, portalX, portalY,
                HighwayTileIndex.Kind.TUNNEL);
    }

    private PlannedRoute surfaceRouteToFirstBridge(
            int startX, int startY, HighwayRoutePlanner.Plan route,
            HighwayTileIndex highways, TravelLayer travelLayer) {
        List<HighwayRoutePlanner.TileStep> steps = route.getHighwaySteps();
        int bridgeIndex = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getKind() == HighwayTileIndex.Kind.BRIDGE) {
                bridgeIndex = i;
                break;
            }
        }
        if (bridgeIndex < 0) return unavailableLayerTransitionRoute(startX,
                startY, highways, travelLayer,
                "surface_route_has_no_bridge_entry");
        HighwayRoutePlanner.TileStep portal = steps.get(bridgeIndex);
        HighwayRoutePlanner.TileStep approach = bridgeIndex > 0
                ? steps.get(bridgeIndex - 1) : portal;
        return surfaceRouteToBridgePortal(startX, startY,
                approach.getTileX(), approach.getTileY(),
                portal.getTileX(), portal.getTileY(), highways, travelLayer);
    }

    private PlannedRoute surfaceRouteToBridgePortal(
            int startX, int startY, int approachX, int approachY,
            int portalX, int portalY, HighwayTileIndex highways,
            TravelLayer travelLayer) {
        HighwayRoutePlanner.NetworkLayer roadOnly =
                HighwayRoutePlanner.NetworkLayer.ROAD;
        HighwayRoutePlanner.Plan highway = highwayPlanner.plan(startX, startY,
                approachX, approachY, highways, roadOnly);
        if (highway.usesHighway()) {
            PlannedRoute preferred = highwayRouteTo(startX, startY, highway,
                    highways, "surface_road_to_bridge_ramp", travelLayer,
                    approachX, approachY, false);
            if (preferred != null) {
                return appendBridgePortal(preferred, approachX, approachY,
                        portalX, portalY, highways, travelLayer);
            }
        }
        PlannedRoute local = terrainRoute(startX, startY, approachX, approachY,
                planner, "surface_terrain_to_bridge_ramp", highways,
                travelLayer);
        return appendBridgePortal(local, approachX, approachY, portalX,
                portalY, highways, travelLayer);
    }

    private PlannedRoute appendBridgePortal(
            PlannedRoute source, int approachX, int approachY, int portalX,
            int portalY, HighwayTileIndex highways, TravelLayer travelLayer) {
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>(source.points);
        if (!points.isEmpty()) {
            GroundRouteTrace.Point last = points.get(points.size() - 1);
            if (last.getTileX() == approachX
                    && last.getTileY() == approachY
                    && Math.max(Math.abs(portalX - approachX),
                    Math.abs(portalY - approachY)) <= 1) {
                GroundRouteTrace.Point portal = sampleLoadedBridgeGeometry(
                        portalX, portalY, true);
                if (portal == null) {
                    portal = sampleTerrain(portalX, portalY, highways,
                            travelLayer);
                }
                if (portal == null) {
                    portal = new GroundRouteTrace.Point(portalX, portalY,
                            last.getGroundHeightMetres(),
                            GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED,
                            0.0f,
                            GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                            HighwayTileIndex.Kind.BRIDGE, true, 0.0f, true);
                } else {
                    portal = portal.withHighway(
                            HighwayTileIndex.Kind.BRIDGE, true);
                }
                append(points, portal);
            }
        }
        return new PlannedRoute(points, false, source.strategy,
                source.expandedNodes, source.rejectedSlopeEdges,
                source.rejectedWaterEdges, source.rejectedUnknownEdges,
                source.rejectedCornerEdges);
    }

    private PlannedRoute appendTunnelPortal(
            PlannedRoute source, int portalX, int portalY,
            HighwayTileIndex highways, TravelLayer travelLayer) {
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>(source.points);
        if (!points.isEmpty()) {
            GroundRouteTrace.Point last = points.get(points.size() - 1);
            if (Math.max(Math.abs(portalX - last.getTileX()),
                    Math.abs(portalY - last.getTileY())) <= 1) {
                GroundRouteTrace.Point portal = sampleTerrain(portalX,
                        portalY, highways, travelLayer);
                if (portal == null) {
                    portal = interpolatedTunnelPoint(portalX, portalY, true,
                            last.getGroundHeightMetres());
                } else {
                    portal = portal.withHighway(
                            HighwayTileIndex.Kind.TUNNEL, true);
                }
                append(points, portal);
            }
        }
        return new PlannedRoute(points, false, source.strategy,
                source.expandedNodes, source.rejectedSlopeEdges,
                source.rejectedWaterEdges, source.rejectedUnknownEdges,
                source.rejectedCornerEdges);
    }

    /**
     * Keeps the portal as the active surface-stage objective, but extends the
     * visible ribbon over the contiguous cave floor that Wurm has already
     * loaded behind it. Missing cave geometry ends the preview immediately.
     */
    private PlannedRoute appendLoadedTunnelPreview(
            PlannedRoute source, HighwayRoutePlanner.Plan complete,
            int portalX, int portalY, HighwayTileIndex highways,
            TravelLayer travelLayer) {
        PlannedRoute portalRoute = appendTunnelPortal(source, portalX,
                portalY, highways, travelLayer);
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>(portalRoute.points);
        if (points.isEmpty()) return portalRoute;
        GroundRouteTrace.Point endpoint = points.get(points.size() - 1);
        if (endpoint.getTileX() != portalX
                || endpoint.getTileY() != portalY) return portalRoute;
        int portalPointCount = points.size();
        for (HighwayRoutePlanner.TileStep step
                : firstTunnelStageSteps(complete)) {
            GroundRouteTrace.Point cave = sampleTerrain(step.getTileX(),
                    step.getTileY(), highways, TravelLayer.TUNNEL);
            if (cave == null) break;
            append(points, cave.withHighway(HighwayTileIndex.Kind.TUNNEL,
                    step.isPortal()));
        }
        boolean extendsPastPortal = points.size() > portalPointCount;
        return new PlannedRoute(points, false,
                extendsPastPortal
                        ? portalRoute.strategy + "_loaded_tunnel_preview"
                        : portalRoute.strategy,
                portalRoute.expandedNodes, portalRoute.rejectedSlopeEdges,
                portalRoute.rejectedWaterEdges,
                portalRoute.rejectedUnknownEdges,
                portalRoute.rejectedCornerEdges);
    }

    private PlannedRoute markSpecialPortalEndpoint(PlannedRoute source,
                                                    int portalX, int portalY,
                                                    HighwayTileIndex.Kind kind) {
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>(source.points);
        if (!points.isEmpty()) {
            int lastIndex = points.size() - 1;
            GroundRouteTrace.Point last = points.get(lastIndex);
            if (last.getTileX() == portalX && last.getTileY() == portalY) {
                points.set(lastIndex, last.withHighway(
                        kind, true));
            }
        }
        return new PlannedRoute(points, false, source.strategy,
                source.expandedNodes, source.rejectedSlopeEdges,
                source.rejectedWaterEdges, source.rejectedUnknownEdges,
                source.rejectedCornerEdges);
    }

    private static HighwayRoutePlanner.TileStep firstStepOfKind(
            HighwayRoutePlanner.Plan plan, HighwayTileIndex.Kind kind) {
        if (plan == null) return null;
        for (HighwayRoutePlanner.TileStep step : plan.getHighwaySteps()) {
            if (step.getKind() == kind) return step;
        }
        return null;
    }

    static List<HighwayRoutePlanner.TileStep> firstTunnelStageSteps(
            HighwayRoutePlanner.Plan complete) {
        List<HighwayRoutePlanner.TileStep> result =
                new ArrayList<HighwayRoutePlanner.TileStep>();
        if (complete == null) return result;
        boolean tunnelSeen = false;
        for (HighwayRoutePlanner.TileStep step : complete.getHighwaySteps()) {
            if (step.getKind() == HighwayTileIndex.Kind.TUNNEL) {
                tunnelSeen = true;
                result.add(step);
            } else if (tunnelSeen) {
                break;
            }
        }
        return result;
    }

    private PlannedRoute unavailableLayerTransitionRoute(
            int startX, int startY, HighwayTileIndex highways,
            TravelLayer travelLayer, String strategy) {
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>(1);
        GroundRouteTrace.Point current = sampleTerrain(startX, startY,
                highways, travelLayer);
        if (current != null) points.add(current);
        return new PlannedRoute(points, false, strategy, 0, 0, 0, 0, 0);
    }

    private PlannedRoute unavailableTunnelRoute(
            int startX, int startY, HighwayTileIndex highways,
            TravelLayer travelLayer) {
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>(1);
        GroundRouteTrace.Point current = sampleTerrain(startX, startY,
                highways, travelLayer);
        if (current != null) points.add(current);
        return new PlannedRoute(points, false,
                "tunnel_graph_has_no_safe_exit", 0, 0, 0, 0, 0);
    }

    /**
     * While underground, publish only the authoritative tunnel span. The
     * surface continuation is deliberately omitted: reaching the portal
     * changes the live layer and triggers a fresh surface plan.
     */
    private PlannedRoute tunnelRoute(int startX, int startY,
                                     HighwayRoutePlanner.Plan tunnel,
                                     HighwayTileIndex highways,
                                     TravelLayer travelLayer) {
        return specialLayerRoute(startX, startY, tunnel, highways,
                travelLayer, "tunnel_graph_until_surface_portal");
    }

    private PlannedRoute specialLayerRoute(
            int startX, int startY, HighwayRoutePlanner.Plan special,
            HighwayTileIndex highways, TravelLayer travelLayer,
            String strategy) {
        List<GroundRouteTrace.Point> points = sampleHighway(
                special.getHighwaySteps(), highways, travelLayer);
        if (points.isEmpty()) return null;
        GroundRouteTrace.Point first = points.get(0);
        if (first.getTileX() != startX || first.getTileY() != startY) {
            if (travelLayer != TravelLayer.BRIDGE
                    || Math.max(Math.abs(first.getTileX() - startX),
                    Math.abs(first.getTileY() - startY)) > 1) {
                return null;
            }
            // Wurm can report a wider live deck than the minimum two-tile
            // published corridor. Anchor that confirmed extra deck tile at
            // the live bridge height instead of dropping navigation.
            points.add(0, occupiedBridgePoint(startX, startY));
        }
        GroundRouteTrace.Point last = points.get(points.size() - 1);
        boolean reached = travelLayer == TravelLayer.TUNNEL
                && targetLayer < 0
                && last.getTileX() == targetTileX
                && last.getTileY() == targetTileY;
        GroundRouteTrace validation = GroundRouteTrace.analyse(
                targetTileX, targetTileY, numericLayer(travelLayer),
                points.size(), reached, maximumSlopeDirt,
                maximumWaterDepthMetres, points);
        if (validation.getResult() == GroundRouteTrace.Result.BLOCKED) {
            return null;
        }
        return new PlannedRoute(points, reached, strategy,
                special.getExpandedNodes(), 0, 0, 0, 0);
    }

    private GroundRouteTrace.Point occupiedBridgePoint(int tileX, int tileY) {
        return new GroundRouteTrace.Point(tileX, tileY,
                world.getPlayerPosH(),
                GroundRouteTrace.HeightSource.BRIDGE_GEOMETRY, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                HighwayTileIndex.Kind.BRIDGE, false, 0.0f, true);
    }

    static boolean hasNearbyHighwayKind(HighwayTileIndex highways,
                                        int tileX, int tileY,
                                        HighwayTileIndex.Kind kind) {
        if (highways == null || kind == null
                || kind == HighwayTileIndex.Kind.NONE) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (highways.get(tileX + dx, tileY + dy).hasKind(kind)) {
                    return true;
                }
            }
        }
        return false;
    }

    private PlannedRoute highwayRoute(int startX, int startY,
                                      HighwayRoutePlanner.Plan highway,
                                      HighwayTileIndex highways,
                                      String strategy,
                                      TravelLayer travelLayer) {
        return highwayRouteTo(startX, startY, highway, highways, strategy,
                travelLayer, targetTileX, targetTileY, true);
    }

    private PlannedRoute highwayRouteTo(int startX, int startY,
                                        HighwayRoutePlanner.Plan highway,
                                        HighwayTileIndex highways,
                                        String strategy,
                                        TravelLayer travelLayer,
                                        int routeTargetX, int routeTargetY,
                                        boolean routeTargetIsFinal) {
        PlannedRoute entry = terrainRoute(startX, startY,
                highway.getEntryX(), highway.getEntryY(), connectorPlanner,
                "highway_entry", highways, travelLayer);
        PlannedRoute exit = terrainRoute(highway.getExitX(),
                highway.getExitY(), routeTargetX, routeTargetY,
                connectorPlanner, "highway_exit", highways, travelLayer);
        if (!entry.reachedFinalTarget || !exit.reachedFinalTarget) return null;

        List<GroundRouteTrace.Point> combined =
                new ArrayList<GroundRouteTrace.Point>();
        append(combined, entry.points);
        List<GroundRouteTrace.Point> highwayPoints =
                sampleHighway(highway.getHighwaySteps(), highways,
                        travelLayer);
        if (highwayPoints.isEmpty()) return null;
        append(combined, highwayPoints);
        if (combined.size() < MAX_POINTS) append(combined, exit.points);
        boolean reached = !combined.isEmpty()
                && combined.get(combined.size() - 1).getTileX() == routeTargetX
                && combined.get(combined.size() - 1).getTileY() == routeTargetY;
        GroundRouteTrace validation = GroundRouteTrace.analyse(
                routeTargetX, routeTargetY, numericLayer(travelLayer),
                combined.size(),
                reached, maximumSlopeDirt, maximumWaterDepthMetres, combined);
        if (validation.getResult() == GroundRouteTrace.Result.BLOCKED) return null;
        return new PlannedRoute(combined, routeTargetIsFinal && reached,
                strategy,
                highway.getExpandedNodes() + entry.expandedNodes
                        + exit.expandedNodes,
                entry.rejectedSlopeEdges + exit.rejectedSlopeEdges,
                entry.rejectedWaterEdges + exit.rejectedWaterEdges,
                entry.rejectedUnknownEdges + exit.rejectedUnknownEdges,
                entry.rejectedCornerEdges + exit.rejectedCornerEdges);
    }

    private List<GroundRouteTrace.Point> sampleHighway(
            List<HighwayRoutePlanner.TileStep> steps,
            HighwayTileIndex highways, TravelLayer travelLayer) {
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>(steps.size());
        float lastKnownHeight = world.getPlayerPosH();
        for (HighwayRoutePlanner.TileStep step : steps) {
            GroundRouteTrace.Point point;
            if (step.getKind() == HighwayTileIndex.Kind.BRIDGE) {
                point = sampleLoadedBridgeGeometry(step);
                if (point == null) {
                    // Surface buffers contain the river or valley floor under
                    // a bridge. It is never valid bridge geometry and must not
                    // reject a published deck for depth or terrain slope.
                    point = interpolatedBridgePoint(step.getTileX(),
                            step.getTileY(), step.isPortal(),
                            world.getPlayerPosH());
                }
            } else if (step.getKind() == HighwayTileIndex.Kind.TUNNEL) {
                point = sampleTerrain(step.getTileX(), step.getTileY(),
                        highways, travelLayer);
                if (point == null) {
                    // The cave buffer is local-only. Published tunnel
                    // topology remains usable beyond it and is corrected as
                    // fresh floor samples arrive during movement.
                    point = interpolatedTunnelPoint(step.getTileX(),
                            step.getTileY(), step.isPortal(),
                            lastKnownHeight);
                }
            } else {
                point = sampleTerrain(step.getTileX(), step.getTileY(),
                        highways, travelLayer);
            }
            if (point == null) return new ArrayList<GroundRouteTrace.Point>();
            points.add(point.withHighway(step.getKind(), step.isPortal()));
            lastKnownHeight = point.getGroundHeightMetres();
        }
        interpolateSpecialHighwaySpans(points);
        return points;
    }

    private GroundRouteTrace.Point sampleLoadedBridgeGeometry(
            HighwayRoutePlanner.TileStep step) {
        return sampleLoadedBridgeGeometry(step.getTileX(), step.getTileY(),
                step.isPortal());
    }

    static GroundRouteTrace.Point interpolatedBridgePoint(
            int tileX, int tileY, boolean portal, float height) {
        return new GroundRouteTrace.Point(tileX, tileY, height,
                GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                HighwayTileIndex.Kind.BRIDGE, portal, 0.0f, true);
    }

    static GroundRouteTrace.Point interpolatedTunnelPoint(
            int tileX, int tileY, boolean portal, float height) {
        return new GroundRouteTrace.Point(tileX, tileY, height,
                GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                HighwayTileIndex.Kind.TUNNEL, portal, 0.0f, true);
    }

    private GroundRouteTrace.Point sampleLoadedBridgeGeometry(
            int tileX, int tileY, boolean portal) {
        try {
            if (world.getCellRenderer() == null) return null;
            BridgePartData bridge = world.getCellRenderer().getBridgePartAt(
                    tileX, tileY, 0);
            if (bridge == null || !bridge.isCompleted()) return null;
            float height = bridge.getBridgeInterpolatedHeight(
                    tileCentre(tileX), tileCentre(tileY));
            if (!finite(height)) return null;
            return new GroundRouteTrace.Point(tileX, tileY,
                    height, GroundRouteTrace.HeightSource.BRIDGE_GEOMETRY,
                    0.0f, GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                    HighwayTileIndex.Kind.BRIDGE, portal, 0.0f, true);
        } catch (Throwable unavailableBridgeGeometry) {
            return null;
        }
    }

    private void interpolateSpecialHighwaySpans(
            List<GroundRouteTrace.Point> points) {
        int start = 0;
        while (start < points.size()) {
            HighwayTileIndex.Kind kind = points.get(start).getHighwayKind();
            int end = start;
            while (end + 1 < points.size()
                    && points.get(end + 1).getHighwayKind() == kind) end++;
            if ((kind == HighwayTileIndex.Kind.BRIDGE
                    || kind == HighwayTileIndex.Kind.TUNNEL) && end > start) {
                if (kind == HighwayTileIndex.Kind.BRIDGE
                        && interpolateMissingBridgeGeometry(points, start,
                        end)) {
                    start = end + 1;
                    continue;
                }
                if (kind == HighwayTileIndex.Kind.TUNNEL
                        && interpolateMissingTunnelGeometry(points, start,
                        end)) {
                    start = end + 1;
                    continue;
                }
                float fromHeight;
                GroundRouteTrace.Point first = points.get(start);
                int playerTileX = (int) Math.floor(
                        world.getPlayerPosX() / TILE_SIZE);
                int playerTileY = (int) Math.floor(
                        world.getPlayerPosY() / TILE_SIZE);
                if (first.getTileX() == playerTileX
                        && first.getTileY() == playerTileY) {
                    // Terrain buffers expose the river bottom beneath a bridge.
                    // The live player's H is the authoritative deck anchor when
                    // replanning starts from the bridge itself.
                    fromHeight = world.getPlayerPosH();
                } else {
                    fromHeight = start > 0
                            ? points.get(start - 1).getGroundHeightMetres()
                            : first.getGroundHeightMetres();
                }
                float toHeight = end + 1 < points.size()
                        ? points.get(end + 1).getGroundHeightMetres()
                        : points.get(end).getGroundHeightMetres();
                for (int i = start; i <= end; i++) {
                    GroundRouteTrace.Point source = points.get(i);
                    float fraction = (i - start) / (float) (end - start);
                    float height = fromHeight + (toHeight - fromHeight) * fraction;
                    points.set(i, new GroundRouteTrace.Point(
                            source.getTileX(), source.getTileY(), height,
                            GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED,
                            0.0f,
                            GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                            kind, source.isHighwayPortal(), 0.0f, true));
                }
            }
            start = end + 1;
        }
    }

    /** Preserves exact straight/arched deck samples and fills unloaded gaps. */
    private boolean interpolateMissingBridgeGeometry(
            List<GroundRouteTrace.Point> points, int start, int end) {
        boolean hasExactGeometry = false;
        for (int i = start; i <= end; i++) {
            if (points.get(i).getHeightSource()
                    == GroundRouteTrace.HeightSource.BRIDGE_GEOMETRY) {
                hasExactGeometry = true;
                break;
            }
        }
        if (!hasExactGeometry) return false;
        int cursor = start;
        while (cursor <= end) {
            if (points.get(cursor).getHeightSource()
                    == GroundRouteTrace.HeightSource.BRIDGE_GEOMETRY) {
                cursor++;
                continue;
            }
            int gapStart = cursor;
            while (cursor <= end && points.get(cursor).getHeightSource()
                    != GroundRouteTrace.HeightSource.BRIDGE_GEOMETRY) cursor++;
            int gapEnd = cursor - 1;
            int left = gapStart - 1;
            int right = cursor;
            float leftHeight = left >= start
                    ? points.get(left).getGroundHeightMetres()
                    : world.getPlayerPosH();
            float rightHeight = right <= end
                    ? points.get(right).getGroundHeightMetres()
                    : (end + 1 < points.size()
                    ? points.get(end + 1).getGroundHeightMetres()
                    : leftHeight);
            int span = right - left;
            for (int i = gapStart; i <= gapEnd; i++) {
                float fraction = span <= 0 ? 0.0f
                        : (i - left) / (float) span;
                GroundRouteTrace.Point source = points.get(i);
                float height = leftHeight
                        + (rightHeight - leftHeight) * fraction;
                points.set(i, interpolatedSpecialPoint(source, height));
            }
        }
        return true;
    }

    /** Keeps loaded cave floors exact and fills only the unseen route tail. */
    private boolean interpolateMissingTunnelGeometry(
            List<GroundRouteTrace.Point> points, int start, int end) {
        boolean hasExactGeometry = false;
        for (int i = start; i <= end; i++) {
            if (points.get(i).getHeightSource()
                    == GroundRouteTrace.HeightSource.CAVE) {
                hasExactGeometry = true;
                break;
            }
        }
        if (!hasExactGeometry) return false;
        int cursor = start;
        while (cursor <= end) {
            if (points.get(cursor).getHeightSource()
                    == GroundRouteTrace.HeightSource.CAVE) {
                cursor++;
                continue;
            }
            int gapStart = cursor;
            while (cursor <= end && points.get(cursor).getHeightSource()
                    != GroundRouteTrace.HeightSource.CAVE) cursor++;
            int gapEnd = cursor - 1;
            int left = gapStart - 1;
            int right = cursor;
            float leftHeight = left >= start
                    ? points.get(left).getGroundHeightMetres()
                    : world.getPlayerPosH();
            float rightHeight = right <= end
                    ? points.get(right).getGroundHeightMetres()
                    : leftHeight;
            int span = right - left;
            for (int i = gapStart; i <= gapEnd; i++) {
                float fraction = span <= 0 ? 0.0f
                        : (i - left) / (float) span;
                GroundRouteTrace.Point source = points.get(i);
                float height = leftHeight
                        + (rightHeight - leftHeight) * fraction;
                points.set(i, interpolatedSpecialPoint(source, height));
            }
        }
        return true;
    }

    private static GroundRouteTrace.Point interpolatedSpecialPoint(
            GroundRouteTrace.Point source, float height) {
        return new GroundRouteTrace.Point(source.getTileX(),
                source.getTileY(), height,
                GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                source.getHighwayKind(), source.isHighwayPortal(), 0.0f, true);
    }

    private PlannedRoute localRoute(int startX, int startY,
                                    HighwayTileIndex highways,
                                    TravelLayer travelLayer) {
        return terrainRoute(startX, startY, targetTileX, targetTileY, planner,
                "chained_terrain_a_star", highways, travelLayer);
    }

    private PlannedRoute terrainRoute(int startX, int startY, int targetX,
                                      int targetY,
                                      CartTerrainRoutePlanner selectedPlanner,
                                      String strategy,
                                      HighwayTileIndex highways,
                                      TravelLayer travelLayer) {
        ChainedCartTerrainRoutePlanner.Plan plan =
                ChainedCartTerrainRoutePlanner.plan(selectedPlanner,
                        terrain(highways, travelLayer), startX, startY,
                        targetX, targetY, MAXIMUM_CHAINED_LEGS, MAX_POINTS);
        return new PlannedRoute(plan.getPoints(),
                plan.isReachedFinalTarget(), strategy,
                plan.getExpandedNodes(), plan.getRejectedSlopeEdges(),
                plan.getRejectedWaterEdges(), plan.getRejectedUnknownEdges(),
                plan.getRejectedCornerEdges());
    }

    private CartTerrainRoutePlanner.Terrain terrain(
            final HighwayTileIndex highways,
            final TravelLayer travelLayer) {
        return new CartTerrainRoutePlanner.Terrain() {
            @Override public GroundRouteTrace.Point sample(int tileX, int tileY) {
                return sampleTerrain(tileX, tileY, highways, travelLayer);
            }
        };
    }

    private static void append(List<GroundRouteTrace.Point> target,
                               List<GroundRouteTrace.Point> source) {
        for (GroundRouteTrace.Point point : source) {
            append(target, point);
            if (target.size() >= MAX_POINTS) return;
        }
    }

    private static void append(List<GroundRouteTrace.Point> target,
                               GroundRouteTrace.Point point) {
        if (!target.isEmpty()) {
            GroundRouteTrace.Point last = target.get(target.size() - 1);
            if (last.getTileX() == point.getTileX()
                    && last.getTileY() == point.getTileY()) {
                target.set(target.size() - 1, point);
                return;
            }
        }
        if (target.size() < MAX_POINTS) target.add(point);
    }

    private static final class PlannedRoute {
        private final List<GroundRouteTrace.Point> points;
        private final boolean reachedFinalTarget;
        private final String strategy;
        private final int expandedNodes;
        private final int rejectedSlopeEdges;
        private final int rejectedWaterEdges;
        private final int rejectedUnknownEdges;
        private final int rejectedCornerEdges;
        private final NavigationRouteStatistics completeStatistics;

        private PlannedRoute(List<GroundRouteTrace.Point> points,
                             boolean reachedFinalTarget, String strategy,
                             int expandedNodes, int rejectedSlopeEdges,
                             int rejectedWaterEdges, int rejectedUnknownEdges,
                             int rejectedCornerEdges) {
            this(points, reachedFinalTarget, strategy, expandedNodes,
                    rejectedSlopeEdges, rejectedWaterEdges,
                    rejectedUnknownEdges, rejectedCornerEdges, null);
        }

        private PlannedRoute(List<GroundRouteTrace.Point> points,
                             boolean reachedFinalTarget, String strategy,
                             int expandedNodes, int rejectedSlopeEdges,
                             int rejectedWaterEdges, int rejectedUnknownEdges,
                             int rejectedCornerEdges,
                             NavigationRouteStatistics completeStatistics) {
            this.points = points;
            this.reachedFinalTarget = reachedFinalTarget;
            this.strategy = strategy;
            this.expandedNodes = expandedNodes;
            this.rejectedSlopeEdges = rejectedSlopeEdges;
            this.rejectedWaterEdges = rejectedWaterEdges;
            this.rejectedUnknownEdges = rejectedUnknownEdges;
            this.rejectedCornerEdges = rejectedCornerEdges;
            this.completeStatistics = completeStatistics;
        }

        private PlannedRoute withCompleteStatistics(
                NavigationRouteStatistics statistics) {
            return new PlannedRoute(points, reachedFinalTarget, strategy,
                    expandedNodes, rejectedSlopeEdges, rejectedWaterEdges,
                    rejectedUnknownEdges, rejectedCornerEdges, statistics);
        }
    }

    private GroundRouteTrace.Point sampleTerrain(int tileX, int tileY,
                                                 HighwayTileIndex highways,
                                                 TravelLayer travelLayer) {
        float worldX = tileCentre(tileX);
        float worldY = tileCentre(tileY);
        if (travelLayer == TravelLayer.TUNNEL) {
            CaveDataBuffer cave = world.getCaveBuffer();
            if (cave == null || !cave.isValid(worldX, worldY)) return null;
            TileGeometry geometry = caveGeometry(cave, tileX, tileY);
            if (geometry == null) return null;
            float ground = cave.getInterpolatedFloor(worldX, worldY);
            if (!finite(ground)) return null;
            float waterLevel = cave.getWaterHeight(tileX, tileY) / 10.0f;
            return applyPublishedHighway(new GroundRouteTrace.Point(tileX, tileY, ground,
                    GroundRouteTrace.HeightSource.CAVE,
                    Math.max(0.0f, waterLevel - geometry.minimumHeight),
                    GroundRouteTrace.WaterSource.CAVE,
                    cave.isOnReinforcedTile(tileX, tileY)
                            ? HighwayTileIndex.Kind.ROAD
                            : HighwayTileIndex.Kind.NONE,
                    false, geometry.maximumSlopeDirt), tileX, tileY,
                    highways, travelLayer);
        }
        NearTerrainDataBuffer near = world.getNearTerrainBuffer();
        if (near != null && near.isValid(worldX, worldY)) {
            TileGeometry geometry = nearGeometry(near, tileX, tileY);
            if (geometry == null) return null;
            float ground = near.getInterpolatedHeight(worldX, worldY);
            if (!finite(ground)) return null;
            float waterLevel = near.getWaterHeight(tileX, tileY) / 10.0f;
            return applyPublishedHighway(new GroundRouteTrace.Point(tileX, tileY, ground,
                    GroundRouteTrace.HeightSource.NEAR,
                    Math.max(0.0f, waterLevel - geometry.minimumHeight),
                    GroundRouteTrace.WaterSource.NEAR,
                    isRoad(near.getTileType(tileX, tileY))
                            ? HighwayTileIndex.Kind.ROAD
                            : HighwayTileIndex.Kind.NONE,
                    false, geometry.maximumSlopeDirt), tileX, tileY,
                    highways, travelLayer);
        }
        DistantTerrainDataBuffer distant = world.getDistantTerrainBuffer();
        if (distant == null || !distant.isValid(worldX, worldY)) return null;
        TileGeometry geometry = distantGeometry(distant, tileX, tileY);
        if (geometry == null) return null;
        float ground = distant.getInterpolatedHeight(worldX, worldY);
        if (!finite(ground)) return null;
        return applyPublishedHighway(new GroundRouteTrace.Point(tileX, tileY, ground,
                GroundRouteTrace.HeightSource.DISTANT,
                Math.max(0.0f, -geometry.minimumHeight),
                GroundRouteTrace.WaterSource.DISTANT_SEA_LEVEL_ESTIMATE,
                isRoad(distant.getTileType(tileX, tileY))
                        ? HighwayTileIndex.Kind.ROAD
                        : HighwayTileIndex.Kind.NONE,
                false, geometry.maximumSlopeDirt), tileX, tileY,
                highways, travelLayer);
    }

    private static TileGeometry nearGeometry(NearTerrainDataBuffer terrain,
                                             int tileX, int tileY) {
        return geometry(terrain.getHeight(tileX, tileY),
                terrain.getHeight(tileX + 1, tileY),
                terrain.getHeight(tileX, tileY + 1),
                terrain.getHeight(tileX + 1, tileY + 1));
    }

    private static TileGeometry caveGeometry(CaveDataBuffer terrain,
                                             int tileX, int tileY) {
        if (terrain.getRawFloor(tileX, tileY) == -100
                || terrain.getRawFloor(tileX + 1, tileY) == -100
                || terrain.getRawFloor(tileX, tileY + 1) == -100
                || terrain.getRawFloor(tileX + 1, tileY + 1) == -100) {
            return null;
        }
        return geometry(terrain.getAdjustedFloor(tileX, tileY),
                terrain.getAdjustedFloor(tileX + 1, tileY),
                terrain.getAdjustedFloor(tileX, tileY + 1),
                terrain.getAdjustedFloor(tileX + 1, tileY + 1));
    }

    private static TileGeometry distantGeometry(
            DistantTerrainDataBuffer terrain, int tileX, int tileY) {
        float x = tileX * TILE_SIZE;
        float y = tileY * TILE_SIZE;
        if (!terrain.isValid(x, y) || !terrain.isValid(x + TILE_SIZE, y)
                || !terrain.isValid(x, y + TILE_SIZE)
                || !terrain.isValid(x + TILE_SIZE, y + TILE_SIZE)) {
            return null;
        }
        return geometry(terrain.getInterpolatedHeight(x, y),
                terrain.getInterpolatedHeight(x + TILE_SIZE, y),
                terrain.getInterpolatedHeight(x, y + TILE_SIZE),
                terrain.getInterpolatedHeight(x + TILE_SIZE, y + TILE_SIZE));
    }

    private static TileGeometry geometry(float northWest, float northEast,
                                         float southWest, float southEast) {
        if (!finite(northWest) || !finite(northEast) || !finite(southWest)
                || !finite(southEast) || northWest <= -3000.0f
                || northEast <= -3000.0f || southWest <= -3000.0f
                || southEast <= -3000.0f) return null;
        return new TileGeometry(minimumTileHeight(northWest, northEast,
                southWest, southEast), tileMaximumSlopeDirt(northWest,
                northEast, southWest, southEast));
    }

    static float tileMaximumSlopeDirt(float northWest, float northEast,
                                      float southWest, float southEast) {
        // Wurm's client and server MovementChecker implementations call
        // getTileSteepness() when a vehicle enters a tile. That method uses
        // the complete range of the four corner heights, not only the four
        // cardinal edges. In particular, opposite corners can make a tile
        // impassable even when every individual edge is below the limit.
        float minimum = Math.min(Math.min(northWest, northEast),
                Math.min(southWest, southEast));
        float maximum = Math.max(Math.max(northWest, northEast),
                Math.max(southWest, southEast));
        return (maximum - minimum) * 10.0f;
    }

    static float minimumTileHeight(float northWest, float northEast,
                                   float southWest, float southEast) {
        return Math.min(Math.min(northWest, northEast),
                Math.min(southWest, southEast));
    }

    private static final class TileGeometry {
        private final float minimumHeight;
        private final float maximumSlopeDirt;

        private TileGeometry(float minimumHeight, float maximumSlopeDirt) {
            this.minimumHeight = minimumHeight;
            this.maximumSlopeDirt = maximumSlopeDirt;
        }
    }

    private GroundRouteTrace.Point applyPublishedHighway(
            GroundRouteTrace.Point point, int tileX, int tileY,
            HighwayTileIndex highways, TravelLayer travelLayer) {
        if (highways == null) return point;
        HighwayTileIndex.Tile highway = highways.get(tileX, tileY);
        HighwayTileIndex.Kind kind = publishedKindFor(highway, travelLayer);
        if (kind == HighwayTileIndex.Kind.NONE) return point;
        return point.withHighway(kind, highway.isPortal(kind));
    }

    static HighwayTileIndex.Kind publishedKindFor(
            HighwayTileIndex.Tile highway, TravelLayer travelLayer) {
        if (highway == null || travelLayer == null) {
            return HighwayTileIndex.Kind.NONE;
        }
        if (travelLayer == TravelLayer.TUNNEL) {
            return highway.hasKind(HighwayTileIndex.Kind.TUNNEL)
                    ? HighwayTileIndex.Kind.TUNNEL
                    : HighwayTileIndex.Kind.NONE;
        }
        if (travelLayer == TravelLayer.BRIDGE
                && highway.hasKind(HighwayTileIndex.Kind.BRIDGE)) {
            return HighwayTileIndex.Kind.BRIDGE;
        }
        return highway.hasKind(HighwayTileIndex.Kind.ROAD)
                ? HighwayTileIndex.Kind.ROAD
                : HighwayTileIndex.Kind.NONE;
    }

    private static boolean isRoad(Tiles.Tile tile) {
        return tile != null && tile.isRoad();
    }

    private boolean shouldDiagnose(RouteSnapshot next, PlannedRoute plan,
                                   TravelLayer travelLayer,
                                   long highwayRevision) {
        if (lastDiagnosedHighwayRevision == Long.MIN_VALUE) return true;
        if (lastDiagnosedReachedTarget != plan.reachedFinalTarget
                || lastDiagnosedTravelLayer != travelLayer
                || lastDiagnosedHighwayRevision != highwayRevision
                || !equal(lastDiagnosedStrategy, plan.strategy)) return true;
        return !stableDiagnosticContinuation(lastDiagnosedRoute, next,
                diagnosticTileInterval);
    }

    private void rememberDiagnosedRoute(RouteSnapshot route,
                                         PlannedRoute plan,
                                         TravelLayer travelLayer,
                                         long highwayRevision) {
        lastDiagnosedRoute = route;
        lastDiagnosedStrategy = plan.strategy;
        lastDiagnosedReachedTarget = plan.reachedFinalTarget;
        lastDiagnosedTravelLayer = travelLayer;
        lastDiagnosedHighwayRevision = highwayRevision;
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    static boolean stableDiagnosticContinuation(
            List<GroundRouteTrace.Point> previous,
            List<GroundRouteTrace.Point> next, int allowedChangedPrefix) {
        return stableDiagnosticContinuation(RouteSnapshot.from(previous),
                RouteSnapshot.from(next), allowedChangedPrefix);
    }

    private static boolean stableDiagnosticContinuation(
            RouteSnapshot previous, RouteSnapshot next,
            int allowedChangedPrefix) {
        if (previous.count == 0 || next.count == 0) {
            return previous.count == next.count;
        }
        int previousIndex = previous.count - 1;
        int nextIndex = next.count - 1;
        int commonSuffix = 0;
        while (previousIndex >= 0 && nextIndex >= 0
                && previous.signature[previousIndex]
                == next.signature[nextIndex]) {
            previousIndex--;
            nextIndex--;
            commonSuffix++;
        }
        if (commonSuffix == next.count) return true;
        int requiredStableTail = Math.min(next.count,
                Math.max(2, allowedChangedPrefix));
        return commonSuffix >= requiredStableTail
                && next.count - commonSuffix <= allowedChangedPrefix;
    }

    private static final class RouteSnapshot {
        private static final RouteSnapshot EMPTY = new RouteSnapshot(
                new int[0], new int[0], new float[0], new int[0], 0);
        private final int[] tileX;
        private final int[] tileY;
        private final float[] height;
        private final int[] signature;
        private final int count;

        private RouteSnapshot(int[] tileX, int[] tileY, float[] height,
                              int[] signature, int count) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.height = height;
            this.signature = signature;
            this.count = count;
        }

        private static RouteSnapshot empty() { return EMPTY; }

        private static RouteSnapshot from(List<GroundRouteTrace.Point> points) {
            if (points == null || points.isEmpty()) return EMPTY;
            int count = Math.min(MAX_POINTS, points.size());
            int[] x = new int[count];
            int[] y = new int[count];
            float[] height = new float[count];
            int[] signature = new int[count];
            for (int i = 0; i < count; i++) {
                GroundRouteTrace.Point point = points.get(i);
                x[i] = point.getTileX();
                y[i] = point.getTileY();
                height[i] = point.getGroundHeightMetres();
                int pointSignature = 1;
                pointSignature = 31 * pointSignature + x[i];
                pointSignature = 31 * pointSignature + y[i];
                pointSignature = 31 * pointSignature
                        + Float.floatToIntBits(height[i]);
                pointSignature = 31 * pointSignature
                        + Float.floatToIntBits(point.getWaterDepthMetres());
                pointSignature = 31 * pointSignature
                        + Float.floatToIntBits(point.getTileMaximumSlopeDirt());
                pointSignature = 31 * pointSignature
                        + (point.isRoad() ? 1 : 0);
                pointSignature = 31 * pointSignature
                        + point.getHighwayKind().ordinal();
                pointSignature = 31 * pointSignature
                        + (point.isPublishedHighway() ? 1 : 0);
                pointSignature = 31 * pointSignature
                        + (point.isHighwayPortal() ? 1 : 0);
                signature[i] = pointSignature;
            }
            return new RouteSnapshot(x, y, height, signature, count);
        }
    }

    private int playerLayer() {
        return world.getPlayerLayer() < 0 ? -1 : 0;
    }

    private TravelLayer currentTravelLayer() {
        if (playerLayer() < 0) return TravelLayer.TUNNEL;
        try {
            if (world.getPlayer() != null
                    && world.getPlayer().getBridgeMaterial() != null) {
                return TravelLayer.BRIDGE;
            }
        } catch (Throwable unavailableBridgeState) {
            // Client revisions without bridge state still remain safe on the
            // surface layer. The next terrain tick will try again.
        }
        return TravelLayer.SURFACE;
    }

    private static HighwayRoutePlanner.NetworkLayer networkLayer(
            TravelLayer travelLayer) {
        if (travelLayer == TravelLayer.TUNNEL) {
            return HighwayRoutePlanner.NetworkLayer.TUNNEL;
        }
        return travelLayer == TravelLayer.BRIDGE
                ? HighwayRoutePlanner.NetworkLayer.BRIDGE
                : HighwayRoutePlanner.NetworkLayer.SURFACE;
    }

    private static int numericLayer(TravelLayer travelLayer) {
        return travelLayer == TravelLayer.TUNNEL ? -1 : 0;
    }

    private static float tileCentre(int tile) {
        return tile * TILE_SIZE + TILE_SIZE * 0.5f;
    }

    private void putVertex(FloatBuffer target, float x, float h, float y) {
        putVertex(target, x, h, y, 1.0f);
    }

    private void putVertex(FloatBuffer target, float x, float h, float y,
                           float alphaScale) {
        target.put(x).put(h).put(y);
        target.put(red).put(green).put(blue)
                .put(Math.min(1.0f, alpha * Math.max(0.0f, alphaScale)));
    }

    private void putTaperedRibbon(FloatBuffer target,
                                  float startX, float startY, float startHeight,
                                  float endX, float endY, float endHeight,
                                  float startHalfWidth, float endHalfWidth,
                                  float startAlpha, float endAlpha) {
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0.0001f) return;
        float sideX = -dy / length;
        float sideY = dx / length;
        float startLeftX = startX - sideX * startHalfWidth;
        float startLeftY = startY - sideY * startHalfWidth;
        float startRightX = startX + sideX * startHalfWidth;
        float startRightY = startY + sideY * startHalfWidth;
        float endLeftX = endX - sideX * endHalfWidth;
        float endLeftY = endY - sideY * endHalfWidth;
        float endRightX = endX + sideX * endHalfWidth;
        float endRightY = endY + sideY * endHalfWidth;
        putVertex(target, startLeftX, startHeight, startLeftY, startAlpha);
        putVertex(target, startRightX, startHeight, startRightY, startAlpha);
        putVertex(target, endLeftX, endHeight, endLeftY, endAlpha);
        putVertex(target, endLeftX, endHeight, endLeftY, endAlpha);
        putVertex(target, startRightX, startHeight, startRightY, startAlpha);
        putVertex(target, endRightX, endHeight, endRightY, endAlpha);
    }

    private void putDash(FloatBuffer target, float startX, float startY,
                         float startHeight, float endX, float endY,
                         float endHeight, float sideX, float sideY) {
        float startLeftX = startX - sideX * HALF_WIDTH;
        float startLeftY = startY - sideY * HALF_WIDTH;
        float startRightX = startX + sideX * HALF_WIDTH;
        float startRightY = startY + sideY * HALF_WIDTH;
        float endLeftX = endX - sideX * HALF_WIDTH;
        float endLeftY = endY - sideY * HALF_WIDTH;
        float endRightX = endX + sideX * HALF_WIDTH;
        float endRightY = endY + sideY * HALF_WIDTH;
        putVertex(target, startLeftX, startHeight, startLeftY);
        putVertex(target, startRightX, startHeight, startRightY);
        putVertex(target, endLeftX, endHeight, endLeftY);
        putVertex(target, endLeftX, endHeight, endLeftY);
        putVertex(target, startRightX, startHeight, startRightY);
        putVertex(target, endRightX, endHeight, endRightY);
    }

    static float routeDashPhase(long monotonicNanos) {
        long cycle = monotonicNanos % DASH_CYCLE_NANOS;
        if (cycle < 0L) cycle += DASH_CYCLE_NANOS;
        return cycle / (float) DASH_CYCLE_NANOS * DASH_PERIOD_METRES;
    }

    @Override public boolean gameTick() {
        return alive;
    }

    public boolean isAlive() {
        return alive;
    }

    @Override public void delete() {
        if (!alive) return;
        alive = false;
        WaypointLatePassBridge.unregister(this);
        planningWorker.shutdownNow();
        vbo.delete();
        if (material != null) material.destroy();
    }

    private static float unit(float value, String label) {
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(label + " must be in 0..1");
        }
        return value;
    }

    private static float positive(float value, String label) {
        if (!finite(value) || value <= 0.0f) throw new IllegalArgumentException(
                label + " must be positive");
        return value;
    }

    private static float nonNegative(float value, String label) {
        if (!finite(value) || value < 0.0f) throw new IllegalArgumentException(
                label + " must be non-negative");
        return value;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
