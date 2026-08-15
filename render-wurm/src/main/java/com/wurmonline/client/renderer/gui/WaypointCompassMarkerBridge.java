package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.navigation.CompassMarkerClusterer;
import org.waypoints.next.navigation.NavigationSnapshot;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.render.CompassMarkerGeometry;
import org.waypoints.next.render.CompassMarkerScale;
import org.waypoints.next.render.CompassMarkerSnapshot;
import org.waypoints.next.render.NavigationRenderFrame;
import org.waypoints.next.render.RecentRenderGate;
import org.waypoints.next.render.WaypointDistanceLabel;
import org.waypoints.next.render.WaypointRenderProfiler;
import org.waypoints.next.render.WaypointRenderRuntimeBridge;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Package bridge that draws, hovers, and hit-tests capped compass markers. */
public final class WaypointCompassMarkerBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.CompassMarker");
    private static final Object PHASE0_TARGET = new Object();
    private static final long HIT_FRESHNESS_NANOS = 500_000_000L;
    private static final int CLUSTER_THRESHOLD_PIXELS = 12;
    private static final int[] PROJECTED = new int[3];
    private static final TextFont CLUSTER_FONT = TextFont.getFixedSizeText();
    private static final String[] CLUSTER_COUNT_TEXT = clusterCountText();

    private static NavigationTargetKey[] renderedKeys = new NavigationTargetKey[0];
    private static NavigationTarget[] renderedTargets = new NavigationTarget[0];
    private static int[] renderedX = new int[0];
    private static int[] renderedY = new int[0];
    private static int[] renderedMarkerSize = new int[0];
    private static int[] renderedHitRadius = new int[0];
    private static int[] renderedGroups = new int[0];
    private static int[] clusterParents = new int[0];
    private static int[] groupXSum = new int[0];
    private static int[] groupYSum = new int[0];
    private static int[] groupX = new int[0];
    private static int[] groupY = new int[0];
    private static int[] groupCount = new int[0];
    private static int[] groupFirst = new int[0];
    private static int[] groupMarkerSize = new int[0];
    private static int[] groupHitRadius = new int[0];
    private static boolean[] groupSelected = new boolean[0];
    private static int renderedCount;
    private static int renderedGroupCount;
    private static int phase0X;
    private static int phase0Y;
    private static int phase0HitRadius;
    private static boolean phase0Rendered;
    private static boolean renderFailed;
    private static boolean firstStaticRenderLogged;
    private static boolean firstHoverLogged;
    private static volatile long lastSuccessfulRenderNanos;

    private WaypointCompassMarkerBridge() { }

    public static synchronized void render(Object component, Queue queue) {
        if (renderFailed || !(component instanceof CompassComponent) || queue == null) return;
        long profileStartedNanos = System.nanoTime();
        try {
            CompassComponent compass = (CompassComponent) component;
            renderedCount = 0;
            renderedGroupCount = 0;
            phase0Rendered = false;
            boolean rendered = renderStatic(compass, queue);
            rendered |= renderPhase0(compass, queue);
            if (rendered) lastSuccessfulRenderNanos = System.nanoTime();
        } catch (Throwable failure) {
            renderedCount = 0;
            renderedGroupCount = 0;
            phase0Rendered = false;
            renderFailed = true;
            LOGGER.log(Level.WARNING, "Compass waypoint marker render failed open", failure);
        } finally {
            WaypointRenderProfiler.recordCompass(
                    System.nanoTime() - profileStartedNanos);
        }
    }

    private static boolean renderStatic(CompassComponent compass, Queue queue) {
        NavigationRenderFrame frame = WaypointRenderRuntimeBridge.currentNavigationFrame();
        if (frame == null || frame.getWorld() == null || frame.getSnapshot() == null) {
            return false;
        }
        World world = frame.getWorld();
        NavigationSnapshot snapshot = frame.getSnapshot();
        List<NavigationTarget> targets = snapshot.getTargets();
        ensureCapacity(targets.size());
        float playerX = world.getPlayerPosX();
        float playerY = world.getPlayerPosY();
        float playerFacing = world.getPlayerRotX();
        for (int i = 0; i < targets.size(); i++) {
            NavigationTarget target = targets.get(i);
            if (!target.isCompassVisible()) continue;
            CompassMarkerGeometry.locateInto(playerX, playerY, playerFacing,
                    (float) target.getCoordinate().worldX(),
                    (float) target.getCoordinate().worldY(),
                    compass.x, compass.y, compass.width, compass.height, PROJECTED);
            int markerSize = CompassMarkerScale.pixels(
                    compass.width, compass.height);
            int hitRadius = Math.max(8, markerSize + 1);
            renderedKeys[renderedCount] = target.getKey();
            renderedTargets[renderedCount] = target;
            renderedX[renderedCount] = PROJECTED[0];
            renderedY[renderedCount] = PROJECTED[1];
            renderedMarkerSize[renderedCount] = markerSize;
            renderedHitRadius[renderedCount] = hitRadius;
            renderedCount++;
        }
        renderedGroupCount = CompassMarkerClusterer.cluster(
                renderedX, renderedY, renderedCount, CLUSTER_THRESHOLD_PIXELS,
                renderedGroups, clusterParents);
        prepareGroups();
        for (int group = 0; group < renderedGroupCount; group++) {
            int first = groupFirst[group];
            if (groupCount[group] == 1) {
                drawNavigationMarker(compass, queue, renderedTargets[first],
                        groupX[group], groupY[group], groupMarkerSize[group]);
            } else {
                drawClusterMarker(compass, queue, renderedTargets[first],
                        groupX[group], groupY[group], groupMarkerSize[group],
                        groupCount[group], groupSelected[group]);
            }
        }
        if (renderedCount > 0 && !firstStaticRenderLogged) {
            firstStaticRenderLogged = true;
            LOGGER.info("Phase 2 compass markers rendered their first frame: count="
                    + renderedCount + ", sourceRevision=" + snapshot.getSourceRevision()
                    + ", generation=" + snapshot.getGeneration());
        }
        return renderedCount > 0;
    }

    private static boolean renderPhase0(CompassComponent compass, Queue queue) {
        CompassMarkerSnapshot marker = WaypointRenderRuntimeBridge.currentCompassMarker();
        if (marker == null) return false;
        CompassMarkerGeometry.locateInto(
                marker.getPlayerX(), marker.getPlayerY(), marker.getPlayerFacing(),
                marker.getTargetX(), marker.getTargetY(),
                compass.x, compass.y, compass.width, compass.height, PROJECTED);
        int colorSize = compass.width <= 64 ? 7 : 9;
        drawDiamond(compass, queue, PROJECTED[0], PROJECTED[1], colorSize + 2,
                0.02f, 0.02f, 0.02f, 1.0f);
        drawDiamond(compass, queue, PROJECTED[0], PROJECTED[1], colorSize,
                marker.getRed(), marker.getGreen(), marker.getBlue(), 1.0f);
        if (!marker.isWorldBeamVisible()) {
            drawDiamond(compass, queue, PROJECTED[0], PROJECTED[1],
                    Math.max(1, colorSize - 4), 0.04f, 0.04f, 0.04f, 1.0f);
        }
        phase0X = PROJECTED[0];
        phase0Y = PROJECTED[1];
        phase0HitRadius = compass.width <= 64 ? 8 : 10;
        phase0Rendered = true;
        return true;
    }

    public static synchronized Object hitTarget(Object component, int mouseX, int mouseY) {
        try {
            if (!RecentRenderGate.isFresh(System.nanoTime(),
                    lastSuccessfulRenderNanos, HIT_FRESHNESS_NANOS)
                    || !(component instanceof CompassComponent)) return null;
            int group = hitGroupIndex(mouseX, mouseY);
            if (group >= 0) {
                if (groupCount[group] == 1) return renderedKeys[groupFirst[group]];
                NavigationTargetKey[] keys = new NavigationTargetKey[groupCount[group]];
                int next = 0;
                for (int i = 0; i < renderedCount; i++) {
                    if (renderedGroups[i] == group) keys[next++] = renderedKeys[i];
                }
                return new CompassMarkerClusterHit(
                        keys, groupX[group], groupY[group]);
            }
            if (phase0Rendered && hit(phase0X, phase0Y, mouseX, mouseY,
                    phase0HitRadius)) return PHASE0_TARGET;
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass waypoint marker hit-test failed open", failure);
        }
        return null;
    }

    public static boolean isHit(Object component, int mouseX, int mouseY) {
        return hitTarget(component, mouseX, mouseY) != null;
    }

    /** Adds an independent marker tooltip through Wurm's native hover pipeline. */
    public static synchronized void pick(Object component, PickData pickData,
                                         int mouseX, int mouseY) {
        if (pickData == null) return;
        if (!RecentRenderGate.isFresh(System.nanoTime(),
                lastSuccessfulRenderNanos, HIT_FRESHNESS_NANOS)
                || !(component instanceof CompassComponent)) return;
        int group = hitGroupIndex(mouseX, mouseY);
        if (group < 0) return;
        if (groupCount[group] > 1) {
            String text = CLUSTER_COUNT_TEXT[groupCount[group]]
                    + " waypoints - click to choose";
            pickData.addText(text);
            logFirstHover(text);
            return;
        }
        NavigationTarget target = renderedTargets[groupFirst[group]];
        NavigationRenderFrame frame = WaypointRenderRuntimeBridge.currentNavigationFrame();
        World world = frame == null ? null : frame.getWorld();
        if (target == null || world == null) return;
        int distance = WaypointDistanceLabel.roundedMeters(
                world.getPlayerPosX(), world.getPlayerPosY(),
                (float) target.getCoordinate().worldX(),
                (float) target.getCoordinate().worldY());
        String text = WaypointDistanceLabel.format(target.getName(), distance);
        pickData.addText(text);
        logFirstHover(text);
    }

    private static void prepareGroups() {
        for (int group = 0; group < renderedGroupCount; group++) {
            groupXSum[group] = 0;
            groupYSum[group] = 0;
            groupCount[group] = 0;
            groupFirst[group] = -1;
            groupMarkerSize[group] = 0;
            groupHitRadius[group] = 0;
            groupSelected[group] = false;
        }
        for (int i = 0; i < renderedCount; i++) {
            int group = renderedGroups[i];
            if (groupFirst[group] < 0) groupFirst[group] = i;
            groupXSum[group] += renderedX[i];
            groupYSum[group] += renderedY[i];
            groupCount[group]++;
            groupMarkerSize[group] = Math.max(
                    groupMarkerSize[group], renderedMarkerSize[i]);
            groupHitRadius[group] = Math.max(
                    groupHitRadius[group], renderedHitRadius[i]);
            groupSelected[group] |= renderedTargets[i].isSelected();
        }
        for (int group = 0; group < renderedGroupCount; group++) {
            groupX[group] = Math.round((float) groupXSum[group] / groupCount[group]);
            groupY[group] = Math.round((float) groupYSum[group] / groupCount[group]);
            if (groupCount[group] > 1) {
                groupHitRadius[group] = Math.max(
                        groupHitRadius[group], groupMarkerSize[group] + 5);
            }
        }
    }

    private static void drawClusterMarker(CompassComponent compass, Queue queue,
                                          NavigationTarget priorityTarget,
                                          int x, int y, int markerSize,
                                          int count, boolean selected) {
        MarkerStyle style = priorityTarget.getMarkerStyle();
        int size = CompassMarkerScale.clusterPixels(markerSize);
        if (selected) {
            drawDiamond(compass, queue, x, y, size
                            + CompassMarkerScale.selectionPadding(size) + 2,
                    1.0f, 1.0f, 1.0f, 1.0f);
        }
        // A second offset diamond makes the group visually distinct even when
        // its numeric badge is clipped on a very small compass.
        drawDiamond(compass, queue, x + 3, y + 2, size + 2,
                0.02f, 0.02f, 0.02f, 1.0f);
        drawShape(compass, queue, style.getWorldStyle(), x + 3, y + 2, size,
                style.getRed(), style.getGreen(), style.getBlue(), 1.0f);
        drawDiamond(compass, queue, x, y, size + 2,
                0.02f, 0.02f, 0.02f, 1.0f);
        drawShape(compass, queue, style.getWorldStyle(), x, y, size,
                style.getRed(), style.getGreen(), style.getBlue(), 1.0f);

        String text = CLUSTER_COUNT_TEXT[count];
        int badgeWidth = CLUSTER_FONT.getWidth(text) + 4;
        int badgeHeight = CLUSTER_FONT.getHeight();
        int badgeX = x - badgeWidth / 2;
        int badgeY = y - badgeHeight / 2;
        compass.fillRect(queue, 1.0f, 1.0f, 1.0f, 0.95f,
                badgeX, badgeY, badgeWidth, badgeHeight);
        CLUSTER_FONT.moveTo(badgeX + 2, badgeY + CLUSTER_FONT.getAscent());
        CLUSTER_FONT.paint(queue, text, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    private static void drawNavigationMarker(CompassComponent compass, Queue queue,
                                             NavigationTarget target, int x, int y,
                                             int size) {
        MarkerStyle style = target.getMarkerStyle();
        if (target.isSelected()) {
            drawDiamond(compass, queue, x, y,
                    size + CompassMarkerScale.selectionPadding(size),
                    1.0f, 1.0f, 1.0f, 1.0f);
        }
        drawDiamond(compass, queue, x, y, size + 2,
                0.02f, 0.02f, 0.02f, 1.0f);
        drawShape(compass, queue, style.getWorldStyle(), x, y, size,
                style.getRed(), style.getGreen(), style.getBlue(), 1.0f);
        if (!target.isWorldBeamVisible()) {
            drawDiamond(compass, queue, x, y, Math.max(1, size - 4),
                    0.04f, 0.04f, 0.04f, 1.0f);
        }
    }

    private static void drawShape(CompassComponent compass, Queue queue,
                                  MarkerStyle.WorldStyle shape, int x, int y, int size,
                                  float red, float green, float blue, float alpha) {
        switch (shape) {
            case TARGET_CROSSHAIR:
                drawHollowCircle(compass, queue, x, y, size,
                        red, green, blue, alpha);
                drawPlus(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case PLUS:
                drawPlus(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case EXCLAMATION:
                drawExclamation(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case HOLLOW_CIRCLE:
                drawHollowCircle(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case HOUSE:
                drawHouse(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case DIAMOND:
                drawDetailedDiamond(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case PICKAXE:
                drawPickaxe(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case SHOVEL:
                drawShovel(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case PICKAXE_AND_SHOVEL:
                drawPickaxeAndShovel(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case MONEY_SIGN:
                drawHollowCircle(compass, queue, x, y, size,
                        red, green, blue, alpha);
                drawMoneySign(compass, queue, x, y, Math.max(5, size * 2 / 3),
                        red, green, blue, alpha);
                break;
            case CROSSED_SWORDS:
                drawCrossedSwords(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case LIGHTHOUSE:
                drawLighthouse(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case LOOT_MAP_SCROLL:
                drawLootMapScroll(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case ARCHAEOLOGY_REPORT_SCROLL:
                drawArchaeologyReportScroll(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
            case COMPASS_ONLY:
                compass.fillRect(queue, red, green, blue, alpha,
                        x - size / 2, y - size / 2, size, size);
                break;
            default:
                drawDiamond(compass, queue, x, y, size,
                        red, green, blue, alpha);
                break;
        }
    }

    private static void drawPlus(CompassComponent compass, Queue queue,
                                 int x, int y, int size,
                                 float red, float green, float blue, float alpha) {
        int radius = Math.max(2, size / 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius, y - 1, radius * 2 + 1, 3);
        compass.fillRect(queue, red, green, blue, alpha,
                x - 1, y - radius, 3, radius * 2 + 1);
    }

    private static void drawExclamation(CompassComponent compass, Queue queue,
                                        int x, int y, int size,
                                        float red, float green, float blue,
                                        float alpha) {
        int radius = Math.max(3, size / 2);
        int width = Math.max(3, size / 4);
        int left = x - width / 2;
        compass.fillRect(queue, red, green, blue, alpha,
                left, y - radius, width, Math.max(3, radius + radius / 3));
        compass.fillRect(queue, red, green, blue, alpha,
                left, y + radius - width, width, width);
    }

    private static void drawHollowCircle(CompassComponent compass, Queue queue,
                                         int x, int y, int size,
                                         float red, float green, float blue, float alpha) {
        int radius = Math.max(3, size / 2);
        int straight = Math.max(1, radius * 2 - 3);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 2, y - radius, straight, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 2, y + radius - 1, straight, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius, y - radius + 2, 2, straight);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius - 1, y - radius + 2, 2, straight);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, y - radius + 1, 2, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius - 2, y - radius + 1, 2, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, y + radius - 2, 2, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius - 2, y + radius - 2, 2, 2);
    }

    private static void drawHouse(CompassComponent compass, Queue queue,
                                  int x, int y, int size,
                                  float red, float green, float blue, float alpha) {
        int radius = Math.max(3, size / 2);
        int bodyTop = y - Math.max(1, radius / 5);
        int bodyHeight = Math.max(4, radius + Math.max(1, radius / 5));
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, bodyTop, 2, bodyHeight);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius - 2, bodyTop, 2, bodyHeight);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, y + radius - 2, radius * 2 - 2, 2);
        int roofRows = Math.max(2, radius);
        for (int row = 0; row < roofRows; row++) {
            int half = Math.max(1, row * radius / roofRows);
            compass.fillRect(queue, red, green, blue, alpha,
                    x - half, y - radius + row, half * 2 + 1, 1);
        }
        int doorHeight = Math.max(2, radius / 2);
        int doorHalf = Math.max(1, radius / 4);
        compass.fillRect(queue, red, green, blue, alpha,
                x - doorHalf, y + radius - doorHeight - 1, 1, doorHeight);
        compass.fillRect(queue, red, green, blue, alpha,
                x + doorHalf, y + radius - doorHeight - 1, 1, doorHeight);
        compass.fillRect(queue, red, green, blue, alpha,
                x - doorHalf, y + radius - doorHeight - 1, doorHalf * 2 + 1, 1);
        int window = Math.max(2, radius / 3);
        drawBox(compass, queue, x - radius / 3, bodyTop + 2,
                window, window, red, green, blue, alpha);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius / 2, y - radius, 2, Math.max(2, radius / 2));
    }

    private static void drawDetailedDiamond(CompassComponent compass, Queue queue,
                                            int x, int y, int size,
                                            float red, float green, float blue,
                                            float alpha) {
        int radius = Math.max(3, size / 2);
        int bandY = y - radius / 4;
        int topY = y - radius * 4 / 5;
        drawPixelLine(compass, queue, x, topY,
                x + radius, bandY, red, green, blue, alpha);
        drawPixelLine(compass, queue, x + radius, bandY,
                x, y + radius, red, green, blue, alpha);
        drawPixelLine(compass, queue, x, y + radius,
                x - radius, bandY, red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius, bandY,
                x, topY, red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius, bandY,
                x + radius, bandY, red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius / 3, bandY,
                x, y + radius, red, green, blue, alpha);
        drawPixelLine(compass, queue, x + radius / 3, bandY,
                x, y + radius, red, green, blue, alpha);
    }

    private static void drawPickaxe(CompassComponent compass, Queue queue,
                                    int x, int y, int size,
                                    float red, float green, float blue, float alpha) {
        int radius = Math.max(3, size / 2);
        drawPickaxeTool(compass, queue, x, y, radius, 1.0f,
                0.0f, 1.0f, red, green, blue, alpha);
    }

    private static void drawShovel(CompassComponent compass, Queue queue,
                                   int x, int y, int size,
                                   float red, float green, float blue, float alpha) {
        int radius = Math.max(3, size / 2);
        drawShovelTool(compass, queue, x, y, radius, 1.0f,
                0.0f, 1.0f, red, green, blue, alpha);
    }

    private static void drawPickaxeAndShovel(CompassComponent compass, Queue queue,
                                             int x, int y, int size,
                                             float red, float green, float blue,
                                             float alpha) {
        int radius = Math.max(3, size / 2);
        float diagonal = 0.70710677f;
        drawShovelTool(compass, queue, x, y, radius, 0.78f,
                -diagonal, -diagonal, red, green, blue, alpha);
        drawPickaxeTool(compass, queue, x, y, radius, 0.82f,
                -diagonal, diagonal, red, green, blue, alpha);
    }

    private static void drawPickaxeTool(CompassComponent compass, Queue queue,
                                        int x, int y, int radius, float scale,
                                        float sin, float cos, float red,
                                        float green, float blue, float alpha) {
        drawToolLine(compass, queue, x, y, radius,
                0.0f, -0.96f, 0.0f, 0.5f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                -1.0f, 0.43f, -0.55f, 0.68f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                -0.55f, 0.68f, 0.0f, 0.76f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                0.0f, 0.76f, 0.55f, 0.68f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                0.55f, 0.68f, 1.0f, 0.43f, scale, sin, cos,
                red, green, blue, alpha);
    }

    private static void drawShovelTool(CompassComponent compass, Queue queue,
                                       int x, int y, int radius, float scale,
                                       float sin, float cos, float red,
                                       float green, float blue, float alpha) {
        drawToolLine(compass, queue, x, y, radius,
                -0.24f, 0.92f, 0.24f, 0.92f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                0.24f, 0.92f, 0.3f, 0.7f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                0.3f, 0.7f, -0.3f, 0.7f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                -0.3f, 0.7f, -0.24f, 0.92f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                0.0f, 0.7f, 0.0f, -0.38f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                -0.38f, -0.38f, 0.38f, -0.38f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                -0.38f, -0.38f, 0.0f, -1.0f, scale, sin, cos,
                red, green, blue, alpha);
        drawToolLine(compass, queue, x, y, radius,
                0.0f, -1.0f, 0.38f, -0.38f, scale, sin, cos,
                red, green, blue, alpha);
    }

    private static void drawToolLine(CompassComponent compass, Queue queue,
                                     int x, int y, int radius,
                                     float startU, float startV,
                                     float endU, float endV, float scale,
                                     float sin, float cos, float red,
                                     float green, float blue, float alpha) {
        float scaledRadius = radius * scale;
        int startX = x + Math.round(scaledRadius
                * (startU * cos - startV * sin));
        int startY = y - Math.round(scaledRadius
                * (startU * sin + startV * cos));
        int endX = x + Math.round(scaledRadius
                * (endU * cos - endV * sin));
        int endY = y - Math.round(scaledRadius
                * (endU * sin + endV * cos));
        drawPixelLine(compass, queue, startX, startY, endX, endY,
                red, green, blue, alpha);
    }

    private static void drawMoneySign(CompassComponent compass, Queue queue,
                                      int x, int y, int size,
                                      float red, float green, float blue, float alpha) {
        int radius = Math.max(3, size / 2);
        int span = radius * 2 - 1;
        compass.fillRect(queue, red, green, blue, alpha,
                x - 1, y - radius, 2, radius * 2 + 1);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, y - radius + 1, span, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, y - 1, span, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, y + radius - 2, span, 2);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius + 1, y - radius + 2, 2, radius);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius - 2, y, 2, radius - 1);
    }

    private static void drawCrossedSwords(CompassComponent compass, Queue queue,
                                          int x, int y, int size,
                                          float red, float green, float blue,
                                          float alpha) {
        int radius = Math.max(3, size / 2);
        drawPixelLine(compass, queue, x - radius, y - radius,
                x + radius * 3 / 4, y + radius,
                red, green, blue, alpha);
        drawPixelLine(compass, queue, x + radius, y - radius,
                x - radius * 3 / 4, y + radius,
                red, green, blue, alpha);
        int guardY = y + radius / 4;
        drawPixelLine(compass, queue,
                x - radius / 8 - radius / 4, guardY - radius / 4,
                x - radius / 8 + radius / 4, guardY + radius / 4,
                red, green, blue, alpha);
        drawPixelLine(compass, queue,
                x + radius / 8 - radius / 4, guardY + radius / 4,
                x + radius / 8 + radius / 4, guardY - radius / 4,
                red, green, blue, alpha);
    }

    private static void drawLighthouse(CompassComponent compass, Queue queue,
                                       int x, int y, int size,
                                       float red, float green, float blue,
                                       float alpha) {
        int radius = Math.max(4, size / 2);
        drawPixelLine(compass, queue, x - radius / 3, y + radius * 2 / 3,
                x - radius / 5, y - radius / 4,
                red, green, blue, alpha);
        drawPixelLine(compass, queue, x + radius / 3, y + radius * 2 / 3,
                x + radius / 5, y - radius / 4,
                red, green, blue, alpha);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius / 3, y + radius * 2 / 3,
                radius * 2 / 3 + 1, 2);
        drawBox(compass, queue, x - radius / 3, y - radius / 2,
                radius * 2 / 3 + 1, Math.max(3, radius / 3),
                red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius / 2, y - radius / 2,
                x, y - radius * 3 / 4, red, green, blue, alpha);
        drawPixelLine(compass, queue, x, y - radius * 3 / 4,
                x + radius / 2, y - radius / 2, red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius / 2, y - radius / 3,
                x - radius, y - radius / 2, red, green, blue, alpha);
        drawPixelLine(compass, queue, x + radius / 2, y - radius / 3,
                x + radius, y - radius / 2, red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius, y + radius,
                x - radius / 2, y + radius * 4 / 5,
                red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius / 2, y + radius * 4 / 5,
                x, y + radius, red, green, blue, alpha);
        drawPixelLine(compass, queue, x, y + radius,
                x + radius / 2, y + radius * 4 / 5,
                red, green, blue, alpha);
        drawPixelLine(compass, queue, x + radius / 2, y + radius * 4 / 5,
                x + radius, y + radius, red, green, blue, alpha);
    }

    private static void drawLootMapScroll(CompassComponent compass, Queue queue,
                                          int x, int y, int size,
                                          float red, float green, float blue,
                                          float alpha) {
        int radius = Math.max(4, size / 2);
        int left = x - radius * 2 / 3;
        int right = x + radius * 2 / 3;
        int top = y - radius * 3 / 4;
        int bottom = y + radius * 3 / 4;
        drawBox(compass, queue, left, top,
                right - left + 1, bottom - top + 1,
                red, green, blue, alpha);
        drawPixelLine(compass, queue, left, top,
                x - radius, top - 1, red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius, top - 1,
                x - radius, top + 2, red, green, blue, alpha);
        drawPixelLine(compass, queue, right, bottom,
                x + radius, bottom + 1, red, green, blue, alpha);
        drawPixelLine(compass, queue, x + radius, bottom + 1,
                x + radius, bottom - 2, red, green, blue, alpha);
        // Tiny route bend and X remain legible at the minimum compass size.
        drawPixelLine(compass, queue, left + 2, y - 1,
                x, y + 1, red, green, blue, alpha);
        drawPixelLine(compass, queue, x, y + 1,
                right - 2, y - 2, red, green, blue, alpha);
        drawPixelLine(compass, queue, x, bottom - 3,
                right - 1, bottom - 1, red, green, blue, alpha);
        drawPixelLine(compass, queue, right - 1, bottom - 3,
                x, bottom - 1, red, green, blue, alpha);
    }

    private static void drawArchaeologyReportScroll(CompassComponent compass,
                                                     Queue queue,
                                                     int x, int y, int size,
                                                     float red, float green,
                                                     float blue, float alpha) {
        int radius = Math.max(5, size / 2);
        int left = x - radius * 3 / 5;
        int right = x + radius * 3 / 5;
        int top = y - radius * 3 / 4;
        int bottom = y + radius * 3 / 4;
        drawBox(compass, queue, left, top, right - left + 1,
                bottom - top + 1, red, green, blue, alpha);
        // Full-width rolled wooden spindles distinguish reports from Loot Maps.
        drawPixelLine(compass, queue, x - radius, top - 2,
                x + radius, top - 2, red, green, blue, alpha);
        drawPixelLine(compass, queue, x - radius, bottom + 2,
                x + radius, bottom + 2, red, green, blue, alpha);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius, top - 4, 2, 5);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius - 1, top - 4, 2, 5);
        compass.fillRect(queue, red, green, blue, alpha,
                x - radius, bottom, 2, 5);
        compass.fillRect(queue, red, green, blue, alpha,
                x + radius - 1, bottom, 2, 5);
        // Tiny eight-point compass rose.
        int starY = y - radius / 4;
        int star = Math.max(2, radius / 4);
        drawPixelLine(compass, queue, x, starY - star, x, starY + star,
                red, green, blue, alpha);
        drawPixelLine(compass, queue, x - star, starY, x + star, starY,
                red, green, blue, alpha);
        drawPixelLine(compass, queue, x - star, starY - star,
                x + star, starY + star, red, green, blue, alpha);
        drawPixelLine(compass, queue, x + star, starY - star,
                x - star, starY + star, red, green, blue, alpha);
        // Wax seal at the lower-right of the page.
        int sealX = x + radius / 3;
        int sealY = y + radius / 3;
        int seal = Math.max(2, radius / 5);
        drawPixelLine(compass, queue, sealX, sealY - seal,
                sealX + seal, sealY, red, green, blue, alpha);
        drawPixelLine(compass, queue, sealX + seal, sealY,
                sealX, sealY + seal, red, green, blue, alpha);
        drawPixelLine(compass, queue, sealX, sealY + seal,
                sealX - seal, sealY, red, green, blue, alpha);
        drawPixelLine(compass, queue, sealX - seal, sealY,
                sealX, sealY - seal, red, green, blue, alpha);
    }

    private static void drawBox(CompassComponent compass, Queue queue,
                                int left, int top, int boxWidth, int boxHeight,
                                float red, float green, float blue, float alpha) {
        compass.fillRect(queue, red, green, blue, alpha,
                left, top, boxWidth, 1);
        compass.fillRect(queue, red, green, blue, alpha,
                left, top + boxHeight - 1, boxWidth, 1);
        compass.fillRect(queue, red, green, blue, alpha,
                left, top, 1, boxHeight);
        compass.fillRect(queue, red, green, blue, alpha,
                left + boxWidth - 1, top, 1, boxHeight);
    }

    private static void drawPixelLine(CompassComponent compass, Queue queue,
                                      int startX, int startY, int endX, int endY,
                                      float red, float green, float blue,
                                      float alpha) {
        int deltaX = endX - startX;
        int deltaY = endY - startY;
        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaY));
        if (steps == 0) {
            compass.fillRect(queue, red, green, blue, alpha,
                    startX, startY, 2, 2);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int drawX = startX + deltaX * step / steps;
            int drawY = startY + deltaY * step / steps;
            compass.fillRect(queue, red, green, blue, alpha,
                    drawX - 1, drawY - 1, 2, 2);
        }
    }

    private static NavigationTarget findRendered(NavigationTargetKey key) {
        int index = indexOf(key);
        return index < 0 ? null : renderedTargets[index];
    }

    private static int indexOf(NavigationTargetKey key) {
        if (key == null) return -1;
        for (int i = 0; i < renderedCount; i++) {
            if (key.equals(renderedKeys[i])) return i;
        }
        return -1;
    }

    private static void ensureCapacity(int count) {
        if (renderedKeys.length >= count) return;
        int capacity = Math.max(count, Math.max(16, renderedKeys.length * 2));
        renderedKeys = new NavigationTargetKey[capacity];
        renderedTargets = new NavigationTarget[capacity];
        renderedX = new int[capacity];
        renderedY = new int[capacity];
        renderedMarkerSize = new int[capacity];
        renderedHitRadius = new int[capacity];
        renderedGroups = new int[capacity];
        clusterParents = new int[capacity];
        groupXSum = new int[capacity];
        groupYSum = new int[capacity];
        groupX = new int[capacity];
        groupY = new int[capacity];
        groupCount = new int[capacity];
        groupFirst = new int[capacity];
        groupMarkerSize = new int[capacity];
        groupHitRadius = new int[capacity];
        groupSelected = new boolean[capacity];
    }

    private static int hitGroupIndex(int mouseX, int mouseY) {
        for (int group = 0; group < renderedGroupCount; group++) {
            if (hit(groupX[group], groupY[group], mouseX, mouseY,
                    groupHitRadius[group])) return group;
        }
        return -1;
    }

    private static boolean hit(int centerX, int centerY, int mouseX, int mouseY,
                               int radius) {
        long dx = (long) mouseX - centerX;
        long dy = (long) mouseY - centerY;
        return dx * dx + dy * dy <= (long) radius * radius;
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
    }

    private static void logFirstHover(String text) {
        if (firstHoverLogged) return;
        firstHoverLogged = true;
        LOGGER.info("Phase 2 compass marker supplied its first native hover: text=\""
                + oneLine(text) + "\"");
    }

    private static String[] clusterCountText() {
        String[] result = new String[1025];
        for (int i = 0; i < result.length; i++) result[i] = Integer.toString(i);
        return result;
    }

    private static void drawDiamond(CompassComponent compass, Queue queue,
                                    int centerX, int centerY, int size,
                                    float red, float green, float blue, float alpha) {
        int radius = size / 2;
        for (int row = -radius; row <= radius; row++) {
            int rowWidth = Math.max(1, size - Math.abs(row) * 2);
            compass.fillRect(queue, red, green, blue, alpha,
                    centerX - rowWidth / 2, centerY + row, rowWidth, 1);
        }
    }
}
