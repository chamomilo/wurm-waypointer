package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.resources.WaypointerFileResourceUrl;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.ResourceTextureLoader;
import com.wurmonline.client.resources.textures.WaypointerTextureFilters;
import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.map.Deed;
import org.waypoints.next.map.MapPoint;
import org.waypoints.next.map.MapOverlayVisibility;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.map.SurfaceTileIndex;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.navigation.HighwayTileIndex;
import org.waypoints.next.service.WaypointRevisionSnapshot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Replaces only the native WorldMap content while preserving its M-window lifecycle. */
public final class ServerMapWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Map");
    private static final int CONTENT_OFFSET_X = 3;
    private static final int CONTENT_OFFSET_Y = 21;
    private static final int CONTENT_WIDTH = 920;
    private static final int CONTENT_HEIGHT = 620;
    private static final int DRAG_THRESHOLD_PIXELS = 5;
    private static final int SEARCH_BUTTON_SIZE = 32;
    private static final int SEARCH_BUTTON_RIGHT = 8;
    private static final int SEARCH_BUTTON_TOP = 25;
    private static final int LAYER_BUTTON_WIDTH = 64;
    private static final int LAYER_BUTTON_HEIGHT = 32;
    private static final int LAYER_BUTTON_GAP = 4;
    private static final int LAYER_BUTTON_COUNT = 3;
    private static final MapOverlayVisibility.Layer[] LAYER_BUTTONS = {
            MapOverlayVisibility.Layer.DEEDS,
            MapOverlayVisibility.Layer.HIGHWAYS,
            MapOverlayVisibility.Layer.WAYPOINTS
    };
    private static final double WAYPOINT_HIT_RADIUS = 11.0d;
    private static final double DEED_HIT_RADIUS = 11.0d;
    private static final double DEED_FOCUS_PIXELS_PER_TILE = 1.5d;
    private static final double INITIAL_PIXELS_PER_TILE = 0.42d;
    // Dominant open-water pixel in the published Sklotopolis surface PNGs.
    private static final float MAP_WATER_RED = 55.0f / 255.0f;
    private static final float MAP_WATER_GREEN = 63.0f / 255.0f;
    private static final float MAP_WATER_BLUE = 111.0f / 255.0f;
    private static final Path WORDMARK_FILE = Paths.get("mods",
            "wurm-waypointer", "assets", "sklotopolis-wordmark.png");
    private static final Matrix LINE_MATRIX = new Matrix();
    private static final Map<WorldMap, State> STATES =
            new WeakHashMap<WorldMap, State>();
    private static final Set<String> REPORTED_FAILURES = new HashSet<String>();
    private static final ExecutorService TEXTURE_WORKER =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable,
                            "wurm-waypointer-map-texture");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private static PreparedSurface prepared;
    private static PreparedArtwork wordmark;

    private ServerMapWindowBridge() { }

    /** Called instead of ClusterMap.render; false means render vanilla content. */
    public static boolean render(Queue queue) {
        try {
            HeadsUpDisplay hud = WurmComponent.hud;
            WorldMap map = hud == null ? null : hud.getWorldMap();
            ServerMapSnapshot snapshot = WurmWaypointerRuntime.serverMapSnapshot();
            if (map == null || queue == null || snapshot == null
                    || snapshot.getProfile() == null || !snapshot.hasSurface()) {
                return false;
            }
            PreparedSurface surface = prepare(snapshot);
            if (!surface.ready || surface.failed) return false;
            if (surface.texture == null) {
                surface.texture = WaypointerTextureFilters
                        .useCrispMagnification(
                                ResourceTextureLoader.getPreparedTexture(
                                        surface.url, surface.request));
            }
            scheduleSurfaceIndex(surface, snapshot.getProfile());
            if (surface.texture == null) {
                reportOnce("texture-unavailable",
                        "Prepared server map texture is missing", null);
                return false;
            }
            if (!surface.texture.isValid() && !surface.texture.needReinit()) {
                reportOnce("texture-invalid",
                        "Prepared server map texture cannot be initialized", null);
                return false;
            }

            State state = state(map, snapshot.getProfile());
            state.viewport.resize(CONTENT_WIDTH, CONTENT_HEIGHT);
            int left = map.x + CONTENT_OFFSET_X;
            int top = map.y + CONTENT_OFFSET_Y;
            HeadsUpDisplay.scissor.pushClip(
                    left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
            try {
                drawWaterBacking(map, queue, left, top);
                drawSurface(queue, surface.texture, state.viewport, left, top);
                if (!state.firstFrameLogged) {
                    state.firstFrameLogged = true;
                    LOGGER.info("Native server map rendered its first frame: profile="
                            + snapshot.getProfile().getId() + ", texture="
                            + surface.texture.getWidth() + "x"
                            + surface.texture.getHeight() + ", glReady="
                            + surface.texture.isValid() + ", queuedForGlInit="
                            + surface.texture.needReinit());
                }

                int overlayLeft = Math.max(left, left + (int) Math.floor(
                        state.viewport.getImageLeft()));
                int overlayTop = Math.max(top + 20, top + (int) Math.floor(
                        state.viewport.getImageTop()));
                int overlayRight = Math.min(left + CONTENT_WIDTH,
                        left + (int) Math.ceil(state.viewport.getImageLeft()
                                + state.viewport.getImageWidth()));
                int overlayBottom = Math.min(top + CONTENT_HEIGHT - 20,
                        top + (int) Math.ceil(state.viewport.getImageTop()
                                + state.viewport.getImageHeight()));
                if (overlayRight > overlayLeft && overlayBottom > overlayTop) {
                    HeadsUpDisplay.scissor.pushClip(overlayLeft, overlayTop,
                            overlayRight - overlayLeft,
                            overlayBottom - overlayTop);
                    try {
                        drawOverlays(map, queue, state, snapshot, left, top);
                    } finally {
                        HeadsUpDisplay.scissor.popClip();
                    }
                }
                try {
                    drawBranding(queue, wordmarkTexture(),
                            snapshot.getProfile(), left, top);
                    drawStatus(map, queue, state, snapshot.getProfile(), left, top);
                    drawLayerButtons(map, queue, state, left, top);
                    drawSearchButton(map, queue, state, left, top);
                } catch (Throwable failure) {
                    reportOnce("status", "Server map status overlay failed open",
                            failure);
                }
                return true;
            } finally {
                HeadsUpDisplay.scissor.popClip();
            }
        } catch (Throwable failure) {
            reportOnce("surface", "Server map surface render failed open", failure);
            return false;
        }
    }

    private static void drawOverlays(WorldMap map, Queue queue, State state,
                                     ServerMapSnapshot snapshot,
                                     int left, int top) {
        // Once the validated surface has rendered, an optional overlay
        // failure must not return control to vanilla ClusterMap: it would
        // paint its own map on top and hide the working server surface.
        if (state.overlays.isVisible(
                MapOverlayVisibility.Layer.HIGHWAYS)) try {
            drawHighways(map, queue, state.viewport, left, top,
                    WurmWaypointerRuntime.serverMapHighways());
        } catch (Throwable failure) {
            reportOnce("highways", "Server map Highways overlay failed open",
                    failure);
        }
        if (state.overlays.isVisible(MapOverlayVisibility.Layer.DEEDS)) try {
            drawDeeds(map, queue, state.viewport, left, top,
                    snapshot.getDeeds(), state);
        } catch (Throwable failure) {
            reportOnce("deeds", "Server map deed overlay failed open", failure);
        }
        if (state.overlays.isVisible(MapOverlayVisibility.Layer.WAYPOINTS)) try {
            drawWaypoints(map, queue, state.viewport, left, top,
                    WurmWaypointerRuntime.serverMapWaypoints(),
                    WurmWaypointerRuntime.currentServerIdentity(),
                    WurmWaypointerRuntime.currentPlayerName(), state);
        } catch (Throwable failure) {
            reportOnce("waypoints", "Server map waypoint overlay failed open",
                    failure);
        }
        try { drawPlayer(map, queue, state.viewport, left, top); }
        catch (Throwable failure) {
            reportOnce("player", "Server map player overlay failed open", failure);
        }
    }

    public static boolean leftPressed(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state == null || !insideContent(map, mouseX, mouseY)) return false;
        MapOverlayVisibility.Layer layer = layerButtonAt(map, mouseX, mouseY);
        if (layer != null) {
            state.pressedLayerButton = layer;
            state.dragging = false;
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (insideSearchButton(map, mouseX, mouseY)) {
            state.searchButtonPressed = true;
            state.dragging = false;
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        state.dragging = true;
        state.dragged = false;
        state.pressX = mouseX;
        state.pressY = mouseY;
        state.lastX = mouseX;
        state.lastY = mouseY;
        updateHover(map, state, mouseX, mouseY);
        return true;
    }

    public static boolean mouseDragged(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state == null) return false;
        if (state.pressedLayerButton != null) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.searchButtonPressed) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (!state.dragging) return false;
        int dx = mouseX - state.lastX;
        int dy = mouseY - state.lastY;
        if (Math.abs(mouseX - state.pressX) >= DRAG_THRESHOLD_PIXELS
                || Math.abs(mouseY - state.pressY) >= DRAG_THRESHOLD_PIXELS) {
            state.dragged = true;
        }
        state.viewport.panByPixels(dx, dy);
        state.lastX = mouseX;
        state.lastY = mouseY;
        updateHover(map, state, mouseX, mouseY);
        return true;
    }

    public static boolean leftReleased(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state == null) return false;
        if (state.pressedLayerButton != null) {
            MapOverlayVisibility.Layer pressed = state.pressedLayerButton;
            state.pressedLayerButton = null;
            if (pressed == layerButtonAt(map, mouseX, mouseY)) {
                state.overlays.toggle(pressed);
            }
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.searchButtonPressed) {
            state.searchButtonPressed = false;
            updateHover(map, state, mouseX, mouseY);
            if (insideSearchButton(map, mouseX, mouseY)) {
                HeadsUpDisplay current = WurmComponent.hud;
                ServerMapSnapshot snapshot = WurmWaypointerRuntime
                        .serverMapSnapshot();
                if (current != null && snapshot != null) {
                    DeedSearchWindowBridge.open(current, snapshot.getDeeds());
                }
            }
            return true;
        }
        if (!state.dragging) return false;
        boolean create = !state.dragged && insideContent(map, mouseX, mouseY);
        state.dragging = false;
        updateHover(map, state, mouseX, mouseY);
        if (create) requestWaypoint(map, state, mouseX, mouseY);
        return true;
    }

    public static boolean rightPressed(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state == null || !insideContent(map, mouseX, mouseY)) return false;
        if (layerButtonAt(map, mouseX, mouseY) != null) return true;
        if (insideSearchButton(map, mouseX, mouseY)) return true;
        updateHover(map, state, mouseX, mouseY);
        requestWaypoint(map, state, mouseX, mouseY);
        return true;
    }

    public static boolean mouseWheeled(WorldMap map, int mouseX, int mouseY,
                                       int wheelDelta) {
        State state = activeState(map);
        if (state == null || !insideContent(map, mouseX, mouseY)) return false;
        if (layerButtonAt(map, mouseX, mouseY) != null
                || insideSearchButton(map, mouseX, mouseY)) return true;
        double steps = -wheelDelta / 3.0d;
        if (steps == 0.0d) steps = wheelDelta < 0 ? 1.0d : -1.0d;
        steps = Math.max(-4.0d, Math.min(4.0d, steps));
        state.viewport.zoomAt(mouseX - map.x - CONTENT_OFFSET_X,
                mouseY - map.y - CONTENT_OFFSET_Y, steps);
        updateHover(map, state, mouseX, mouseY);
        return true;
    }

    public static void mouseMoved(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state != null) updateHover(map, state, mouseX, mouseY);
    }

    public static boolean suppressVanillaContextMenu(WorldMap map) {
        return activeState(map) != null;
    }

    /** Centers the active M-map on a deed selected in the native search window. */
    public static synchronized void centerOnDeed(int tileX, int tileY) {
        HeadsUpDisplay current = WurmComponent.hud;
        WorldMap map = current == null ? null : current.getWorldMap();
        State state = activeState(map);
        if (state != null) state.viewport.focusOn(
                tileX + 0.5d, tileY + 0.5d,
                DEED_FOCUS_PIXELS_PER_TILE);
    }

    public static synchronized void reset(WorldMap map) {
        if (map != null) STATES.remove(map);
    }

    public static synchronized void resetAll() {
        STATES.clear();
        prepared = null;
        wordmark = null;
        REPORTED_FAILURES.clear();
    }

    private static synchronized PreparedSurface prepare(
            ServerMapSnapshot snapshot) {
        Path file = snapshot.getSurfaceImage().toAbsolutePath().normalize();
        String key = snapshot.getProfile().getId() + "|" + file + "|"
                + snapshot.getSurfaceRevision();
        if (prepared != null && key.equals(prepared.key)) return prepared;
        final PreparedSurface next = new PreparedSurface(key, file,
                new WaypointerFileResourceUrl(file, snapshot.getSurfaceRevision()));
        prepared = next;
        TEXTURE_WORKER.execute(new Runnable() {
            @Override public void run() {
                try {
                    ResourceTextureLoader.prepareTexture(next.url,
                            next.request, false);
                    next.ready = true;
                } catch (Throwable failure) {
                    next.failed = true;
                    next.ready = true;
                    LOGGER.log(Level.WARNING,
                            "Cached server map texture could not be prepared", failure);
                }
            }
        });
        return next;
    }

    private static void scheduleSurfaceIndex(final PreparedSurface surface,
                                             final ServerMapProfile profile) {
        synchronized (surface) {
            if (surface.indexScheduled) return;
            surface.indexScheduled = true;
        }
        TEXTURE_WORKER.execute(new Runnable() {
            @Override public void run() {
                try {
                    surface.tileIndex = SurfaceTileIndex.load(surface.file,
                            profile.getMapWidth(), profile.getMapHeight());
                    LOGGER.info("Server map terrain hover index ready: profile="
                            + profile.getId());
                } catch (Throwable failure) {
                    surface.indexFailed = true;
                    reportOnce("surface-tile-index-" + profile.getId(),
                            "Server map terrain hover index failed open", failure);
                }
            }
        });
    }

    private static synchronized ResourceTexture wordmarkTexture() {
        try {
            if (wordmark == null) {
                final PreparedArtwork next = new PreparedArtwork(
                        new WaypointerFileResourceUrl(WORDMARK_FILE, 1L),
                        "Sklotopolis wordmark");
                wordmark = next;
                TEXTURE_WORKER.execute(new Runnable() {
                    @Override public void run() {
                        try {
                            ResourceTextureLoader.prepareTexture(next.url,
                                    next.request, false);
                            next.ready = true;
                        } catch (Throwable failure) {
                            next.failed = true;
                            next.ready = true;
                            reportOnce("wordmark-prepare",
                                    next.label + " could not be prepared",
                                    failure);
                        }
                    }
                });
            }
            if (!wordmark.ready || wordmark.failed) return null;
            if (wordmark.texture == null) {
                wordmark.texture = ResourceTextureLoader.getPreparedTexture(
                        wordmark.url, wordmark.request);
            }
            ResourceTexture texture = wordmark.texture;
            return texture != null && (texture.isValid() || texture.needReinit())
                    ? texture : null;
        } catch (Throwable failure) {
            reportOnce("wordmark", "Sklotopolis wordmark failed open", failure);
            return null;
        }
    }

    private static synchronized State state(WorldMap map,
                                            ServerMapProfile profile) {
        State value = STATES.get(map);
        if (value != null && value.profileId.equals(profile.getId())) return value;
        double x = WurmWaypointerRuntime.currentPlayerTileX() + 0.5d;
        double y = WurmWaypointerRuntime.currentPlayerTileY() + 0.5d;
        value = new State(profile.getId(), new MapViewport(
                profile.getMapWidth(), profile.getMapHeight(),
                CONTENT_WIDTH, CONTENT_HEIGHT, x, y,
                INITIAL_PIXELS_PER_TILE), new MapOverlayVisibility(
                WurmWaypointerRuntime.serverMapShowsDeeds(),
                WurmWaypointerRuntime.serverMapShowsHighways(), true));
        STATES.put(map, value);
        return value;
    }

    private static synchronized State activeState(WorldMap map) {
        if (map == null) return null;
        ServerMapSnapshot snapshot = WurmWaypointerRuntime.serverMapSnapshot();
        if (snapshot == null || snapshot.getProfile() == null || !snapshot.hasSurface()
                || prepared == null || prepared.texture == null || prepared.failed) {
            return null;
        }
        return state(map, snapshot.getProfile());
    }

    private static void drawSurface(Queue queue, ResourceTexture texture,
                                    MapViewport viewport, int left, int top) {
        double imageLeft = viewport.getImageLeft();
        double imageTop = viewport.getImageTop();
        double imageWidth = viewport.getImageWidth();
        double imageHeight = viewport.getImageHeight();
        double clipLeft = Math.max(0.0d, imageLeft);
        double clipTop = Math.max(0.0d, imageTop);
        double clipRight = Math.min(CONTENT_WIDTH, imageLeft + imageWidth);
        double clipBottom = Math.min(CONTENT_HEIGHT, imageTop + imageHeight);
        if (clipRight <= clipLeft || clipBottom <= clipTop) return;
        float u = (float) ((clipLeft - imageLeft) / imageWidth);
        float v = (float) ((clipTop - imageTop) / imageHeight);
        float uScale = (float) ((clipRight - clipLeft) / imageWidth);
        float vScale = (float) ((clipBottom - clipTop) / imageHeight);
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, 1.0f,
                (float) (left + clipLeft), (float) (top + clipTop),
                (float) (clipRight - clipLeft),
                (float) (clipBottom - clipTop), u, v, uScale, vScale);
    }

    private static void drawBranding(Queue queue, ResourceTexture logo,
                                     ServerMapProfile profile,
                                     int left, int top) {
        if (logo != null) {
            Renderer.texturedQuadAlphaBlend(queue, logo,
                    1.0f, 1.0f, 1.0f, 0.96f,
                    left + 10.0f, top + 28.0f, 188.0f, 43.0f,
                    0.0f, 0.0f, 1.0f, 1.0f);
        }
        String display = profile == null ? "" : profile.getDisplayName();
        String prefix = "Sklotopolis ";
        String world = display.regionMatches(true, 0, prefix, 0,
                Math.min(prefix.length(), display.length()))
                && display.length() >= prefix.length()
                ? display.substring(prefix.length()) : display;
        world = world.trim().toUpperCase(Locale.ENGLISH);
        if (world.isEmpty()) return;
        TextFont font = TextFont.getHeaderText();
        font.moveTo(left + 14, top + 98);
        font.paint(queue, world, 0.19f, 0.09f, 0.025f, 0.88f);
        font.moveTo(left + 12, top + 96);
        font.paint(queue, world, 0.92f, 0.72f, 0.42f, 1.0f);
    }

    /** Seamless surround matching Sklotopolis open water (#373F6F). */
    private static void drawWaterBacking(WorldMap map, Queue queue,
                                         int left, int top) {
        map.fillRect(queue,
                MAP_WATER_RED, MAP_WATER_GREEN, MAP_WATER_BLUE, 1.0f,
                left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
    }

    private static void drawDeeds(WorldMap map, Queue queue,
                                  MapViewport viewport, int left, int top,
                                  List<Deed> deeds, State state) {
        if (deeds == null || deeds.isEmpty()) return;
        int labels = 0;
        for (Deed deed : deeds) {
            MapPoint perimeterA = viewport.mapToScreen(
                    deed.getPerimeterMinimumX(), deed.getPerimeterMinimumY());
            MapPoint perimeterB = viewport.mapToScreen(
                    deed.getPerimeterMaximumX() + 1.0d,
                    deed.getPerimeterMaximumY() + 1.0d);
            outline(map, queue, left, top, perimeterA, perimeterB,
                    0.25f, 0.78f, 1.0f, 0.55f, 1);
            MapPoint deedA = viewport.mapToScreen(
                    deed.getMinimumX(), deed.getMinimumY());
            MapPoint deedB = viewport.mapToScreen(
                    deed.getMaximumX() + 1.0d, deed.getMaximumY() + 1.0d);
            outline(map, queue, left, top, deedA, deedB,
                    deed.isSpawnPoint() ? 1.0f : 0.25f,
                    deed.isSpawnPoint() ? 0.8f : 1.0f,
                    0.25f, 0.9f, 2);
            MapPoint point = viewport.mapToScreen(
                    deed.getX() + 0.5d, deed.getY() + 0.5d);
            int x = left + (int) Math.round(point.getX());
            int y = top + (int) Math.round(point.getY());
            if (x >= left - 8 && y >= top - 8
                    && x <= left + CONTENT_WIDTH + 8
                    && y <= top + CONTENT_HEIGHT + 8) {
                float markerRed = deed.isSpawnPoint() ? 1.0f : 0.92f;
                float markerGreen = deed.isSpawnPoint() ? 0.76f : 1.0f;
                float markerBlue = deed.isSpawnPoint() ? 0.12f : 0.60f;
                boolean hovered = sameDeed(deed, state.hoveredDeed);
                if (hovered) {
                    map.fillRect(queue, 1.0f, 0.91f, 0.48f, 1.0f,
                            x - 7, y - 7, 15, 15);
                    map.fillRect(queue, 0.13f, 0.075f, 0.025f, 1.0f,
                            x - 5, y - 5, 11, 11);
                }
                map.fillRect(queue, 0.08f, 0.05f, 0.02f, 0.92f,
                        x - 4, y - 4, 9, 9);
                map.fillRect(queue, markerRed, markerGreen, markerBlue, 1.0f,
                        x - 2, y - 2, 5, 5);
                if (hovered || (viewport.getPixelsPerTile() >= 0.42d
                        && labels++ < 140)) {
                    text(queue, deed.getName(), x + 6, y - 4,
                            1.0f, 0.94f, 0.70f, 1.0f, left, top);
                }
            }
        }
    }

    private static void drawHighways(WorldMap map, Queue queue,
                                     MapViewport viewport, int left, int top,
                                     HighwayTileIndex index) {
        if (index == null || index.isEmpty()) return;
        for (HighwayTileIndex.Segment segment : index.getSegments()) {
            MapPoint a = viewport.mapToScreen(segment.getStartX() + 0.5d,
                    segment.getStartY() + 0.5d);
            MapPoint b = viewport.mapToScreen(segment.getEndX() + 0.5d,
                    segment.getEndY() + 0.5d);
            float red = 0.96f, green = 0.76f, blue = 0.22f;
            float width = 2.0f;
            if (segment.getKind() == HighwayTileIndex.Kind.BRIDGE) {
                red = 0.25f; green = 0.92f; blue = 1.0f; width = 3.0f;
            } else if (segment.getKind() == HighwayTileIndex.Kind.TUNNEL) {
                red = 1.0f; green = 0.3f; blue = 0.86f; width = 3.0f;
            }
            line(queue, left + (float) a.getX(), top + (float) a.getY(),
                    left + (float) b.getX(), top + (float) b.getY(),
                    width, red, green, blue, 0.92f);
        }
    }

    private static void drawWaypoints(WorldMap map, Queue queue,
                                      MapViewport viewport, int left, int top,
                                      WaypointRevisionSnapshot snapshot,
                                      ServerIdentity currentServer,
                                      String currentUser, State state) {
        if (snapshot == null) return;
        int labels = 0;
        for (WaypointRecord record : snapshot.getRecords()) {
            WaypointCoordinate coordinate = record.getCoordinate();
            if (!visibleWaypoint(record, currentServer, currentUser)) continue;
            MapPoint point = viewport.mapToScreen(coordinate.getTileX() + 0.5d,
                    coordinate.getTileY() + 0.5d);
            int x = left + (int) Math.round(point.getX());
            int y = top + (int) Math.round(point.getY());
            if (x < left - 8 || y < top - 8 || x > left + CONTENT_WIDTH + 8
                    || y > top + CONTENT_HEIGHT + 8) continue;
            MarkerStyle style = record.getMarkerStyle();
            float red = style == null ? 1.0f : style.getRed();
            float green = style == null ? 0.85f : style.getGreen();
            float blue = style == null ? 0.2f : style.getBlue();
            boolean hovered = record.getId().equals(state.hoveredWaypointId);
            if (hovered) {
                map.fillRect(queue, 1.0f, 0.96f, 0.72f, 1.0f,
                        x - 7, y - 7, 15, 15);
                map.fillRect(queue, 0.12f, 0.07f, 0.025f, 1.0f,
                        x - 5, y - 5, 11, 11);
            }
            if (isSurroundingsMark(record)) {
                drawSurroundingsMark(map, queue, x, y, red, green, blue);
            } else {
                map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.8f,
                        x - 4, y - 4, 9, 9);
                map.fillRect(queue, red, green, blue, 1.0f,
                        x - 3, y - 3, 7, 7);
            }
            if (hovered || (viewport.getPixelsPerTile() >= 1.0d
                    && labels++ < 60)) {
                text(queue, record.getName(), x + 6, y - 4,
                        red, green, blue, 1.0f, left, top);
            }
        }
    }

    private static void drawSurroundingsMark(WorldMap map, Queue queue,
                                             int x, int y, float red,
                                             float green, float blue) {
        map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.86f,
                x - 2, y - 6, 5, 8);
        map.fillRect(queue, red, green, blue, 1.0f,
                x - 1, y - 5, 3, 6);
        map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.86f,
                x - 2, y + 3, 5, 5);
        map.fillRect(queue, red, green, blue, 1.0f,
                x - 1, y + 4, 3, 3);
    }

    private static void drawPlayer(WorldMap map, Queue queue,
                                   MapViewport viewport, int left, int top) {
        MapPoint point = viewport.mapToScreen(
                WurmWaypointerRuntime.currentPlayerTileX() + 0.5d,
                WurmWaypointerRuntime.currentPlayerTileY() + 0.5d);
        int x = left + (int) Math.round(point.getX());
        int y = top + (int) Math.round(point.getY());
        if (x < left || y < top || x >= left + CONTENT_WIDTH
                || y >= top + CONTENT_HEIGHT) return;
        map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.9f, x - 7, y - 2, 15, 5);
        map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.9f, x - 2, y - 7, 5, 15);
        map.fillRect(queue, 1.0f, 1.0f, 1.0f, 1.0f, x - 6, y - 1, 13, 3);
        map.fillRect(queue, 1.0f, 1.0f, 1.0f, 1.0f, x - 1, y - 6, 3, 13);
    }

    private static void drawStatus(WorldMap map, Queue queue, State state,
                                   ServerMapProfile profile, int left, int top) {
        map.fillRect(queue, 0.16f, 0.09f, 0.035f, 0.90f,
                left, top, CONTENT_WIDTH, 20);
        map.fillRect(queue, 0.16f, 0.09f, 0.035f, 0.90f,
                left, top + CONTENT_HEIGHT - 20, CONTENT_WIDTH, 20);
        String header = profile.getDisplayName() + "  zoom "
                + String.format(Locale.ENGLISH, "%.2f px/tile",
                Double.valueOf(state.viewport.getPixelsPerTile()));
        text(queue, header, left + 6, top + 15,
                1.0f, 0.92f, 0.72f, 1.0f, left, top);
        String coordinate = state.hoverInside
                ? "X=" + state.hoverTileX + " Y=" + state.hoverTileY
                        + "  Tile: " + hoveredTileDescription(state) + "  "
                : "";
        String action = state.hoveredLayerButton != null
                ? layerButtonHelp(state.hoveredLayerButton, state.overlays
                        .isVisible(state.hoveredLayerButton))
                : state.searchButtonHover
                ? "Search deeds"
                : state.hoveredWaypointId != null
                ? "Waypoint: " + state.hoveredWaypointName
                        + (state.hoveredWaypointEditable
                        ? " | click: edit" : " | managed marker")
                : state.hoveredDeed != null
                ? "Deed: " + state.hoveredDeed.getName()
                        + " | click: information"
                : "Wheel: zoom | drag: pan | click/right-click: add waypoint";
        text(queue, coordinate + action,
                left + 6, top + CONTENT_HEIGHT - 5,
                1.0f, 0.92f, 0.72f, 1.0f, left, top);
    }

    private static void drawLayerButtons(WorldMap map, Queue queue, State state,
                                         int left, int top) {
        for (MapOverlayVisibility.Layer layer : LAYER_BUTTONS) {
            int x = layerButtonLeft(left, layer);
            int y = top + SEARCH_BUTTON_TOP;
            boolean visible = state.overlays.isVisible(layer);
            boolean hovered = state.hoveredLayerButton == layer;
            boolean pressed = state.pressedLayerButton == layer && hovered;
            float edge = pressed ? 1.0f : hovered ? 0.96f
                    : visible ? 0.72f : 0.38f;
            map.fillRect(queue, 0.10f, 0.055f, 0.02f, 0.94f,
                    x, y, LAYER_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT);
            map.fillRect(queue, edge, edge * 0.79f, edge * 0.42f, 0.95f,
                    x + 2, y + 2, LAYER_BUTTON_WIDTH - 4,
                    LAYER_BUTTON_HEIGHT - 4);
            float fill = visible ? 0.20f : 0.09f;
            map.fillRect(queue, fill, visible ? 0.12f : 0.09f,
                    visible ? 0.05f : 0.08f, 0.96f,
                    x + 4, y + 4, LAYER_BUTTON_WIDTH - 8,
                    LAYER_BUTTON_HEIGHT - 8);
            text(queue, layerButtonLabel(layer), x + 7, y + 21,
                    visible ? 1.0f : 0.58f,
                    visible ? 0.92f : 0.55f,
                    visible ? 0.72f : 0.52f, 1.0f, left, top);
        }
    }

    private static String layerButtonLabel(MapOverlayVisibility.Layer layer) {
        switch (layer) {
            case DEEDS: return "DEEDS";
            case HIGHWAYS: return "ROADS";
            case WAYPOINTS: return "MARKS";
            default: return "";
        }
    }

    private static String layerButtonHelp(MapOverlayVisibility.Layer layer,
                                          boolean visible) {
        String name;
        switch (layer) {
            case DEEDS: name = "deeds"; break;
            case HIGHWAYS: name = "published roads"; break;
            case WAYPOINTS: name = "waypoint and Surroundings marks"; break;
            default: name = "map layer"; break;
        }
        return "Click: turn " + name + (visible ? " off" : " on");
    }

    private static String hoveredTileDescription(State state) {
        String live = WurmWaypointerRuntime.serverMapLiveTileDescription(
                state.hoverTileX, state.hoverTileY);
        if (live != null && !live.isEmpty()) return live;
        PreparedSurface current = prepared;
        SurfaceTileIndex index = current == null ? null : current.tileIndex;
        if (index == null) return current != null && !current.indexFailed
                ? "loading..." : "unknown";
        String broad = index.describe(state.hoverTileX, state.hoverTileY);
        return broad.isEmpty() ? "unknown" : broad + " (map)";
    }

    private static void drawSearchButton(WorldMap map, Queue queue, State state,
                                         int left, int top) {
        int x = left + CONTENT_WIDTH - SEARCH_BUTTON_RIGHT - SEARCH_BUTTON_SIZE;
        int y = top + SEARCH_BUTTON_TOP;
        float edge = state.searchButtonHover ? 1.0f : 0.72f;
        map.fillRect(queue, 0.10f, 0.055f, 0.02f, 0.94f,
                x, y, SEARCH_BUTTON_SIZE, SEARCH_BUTTON_SIZE);
        map.fillRect(queue, edge, edge * 0.79f, edge * 0.42f, 0.95f,
                x + 2, y + 2, SEARCH_BUTTON_SIZE - 4, SEARCH_BUTTON_SIZE - 4);
        map.fillRect(queue, 0.20f, 0.12f, 0.05f, 0.96f,
                x + 4, y + 4, SEARCH_BUTTON_SIZE - 8, SEARCH_BUTTON_SIZE - 8);
        float icon = state.searchButtonHover ? 1.0f : 0.90f;
        int cx = x + 14;
        int cy = y + 13;
        line(queue, cx - 5, cy, cx - 3, cy - 5,
                2.0f, icon, icon * 0.86f, icon * 0.55f, 1.0f);
        line(queue, cx - 3, cy - 5, cx + 3, cy - 5,
                2.0f, icon, icon * 0.86f, icon * 0.55f, 1.0f);
        line(queue, cx + 3, cy - 5, cx + 5, cy,
                2.0f, icon, icon * 0.86f, icon * 0.55f, 1.0f);
        line(queue, cx + 5, cy, cx + 3, cy + 5,
                2.0f, icon, icon * 0.86f, icon * 0.55f, 1.0f);
        line(queue, cx + 3, cy + 5, cx - 3, cy + 5,
                2.0f, icon, icon * 0.86f, icon * 0.55f, 1.0f);
        line(queue, cx - 3, cy + 5, cx - 5, cy,
                2.0f, icon, icon * 0.86f, icon * 0.55f, 1.0f);
        line(queue, cx + 4, cy + 4, cx + 10, cy + 10,
                3.0f, icon, icon * 0.86f, icon * 0.55f, 1.0f);
    }

    private static void requestWaypoint(WorldMap map, State state,
                                        int mouseX, int mouseY) {
        if (state.hoveredWaypointId != null) {
            if (state.hoveredWaypointEditable) {
                WurmWaypointerRuntime.serverMapWaypointEditRequested(
                        state.hoveredWaypointId);
            }
            return;
        }
        if (state.hoveredDeed != null) {
            Deed selected = state.hoveredDeed;
            state.viewport.focusOn(selected.getX() + 0.5d,
                    selected.getY() + 0.5d,
                    DEED_FOCUS_PIXELS_PER_TILE);
            HeadsUpDisplay current = WurmComponent.hud;
            if (current != null) {
                DeedInformationWindowBridge.open(current, selected);
            }
            return;
        }
        MapPoint point = state.viewport.screenToMap(
                mouseX - map.x - CONTENT_OFFSET_X,
                mouseY - map.y - CONTENT_OFFSET_Y);
        if (!state.viewport.containsMapPoint(point)) return;
        int tileX = (int) Math.floor(point.getX());
        int tileY = (int) Math.floor(point.getY());
        HighwayTileIndex.Tile highway = WurmWaypointerRuntime
                .serverMapHighways().get(tileX, tileY);
        WaypointLayer layer = highway.hasKind(HighwayTileIndex.Kind.TUNNEL)
                && !highway.hasKind(HighwayTileIndex.Kind.ROAD)
                ? WaypointLayer.CAVE : WaypointLayer.SURFACE;
        WurmWaypointerRuntime.serverMapWaypointRequested(tileX, tileY, layer);
    }

    private static void updateHover(WorldMap map, State state,
                                    int mouseX, int mouseY) {
        state.searchButtonHover = insideSearchButton(map, mouseX, mouseY);
        state.hoveredLayerButton = layerButtonAt(map, mouseX, mouseY);
        if (state.searchButtonHover || state.hoveredLayerButton != null) {
            state.hoverInside = false;
            clearHoveredWaypoint(state);
            state.hoveredDeed = null;
            return;
        }
        MapPoint point = state.viewport.screenToMap(
                mouseX - map.x - CONTENT_OFFSET_X,
                mouseY - map.y - CONTENT_OFFSET_Y);
        state.hoverInside = state.viewport.containsMapPoint(point);
        if (state.hoverInside) {
            state.hoverTileX = (int) Math.floor(point.getX());
            state.hoverTileY = (int) Math.floor(point.getY());
            updateHoveredWaypoint(map, state, mouseX, mouseY);
            updateHoveredDeed(map, state, mouseX, mouseY);
        } else {
            clearHoveredWaypoint(state);
            state.hoveredDeed = null;
        }
    }

    private static void updateHoveredDeed(WorldMap map, State state,
                                          int mouseX, int mouseY) {
        state.hoveredDeed = null;
        if (!state.overlays.isVisible(MapOverlayVisibility.Layer.DEEDS)) return;
        ServerMapSnapshot snapshot = WurmWaypointerRuntime.serverMapSnapshot();
        if (snapshot == null || snapshot.getDeeds() == null) return;
        double bestDistanceSquared = DEED_HIT_RADIUS * DEED_HIT_RADIUS;
        for (Deed deed : snapshot.getDeeds()) {
            MapPoint marker = state.viewport.mapToScreen(
                    deed.getX() + 0.5d, deed.getY() + 0.5d);
            double dx = map.x + CONTENT_OFFSET_X + marker.getX() - mouseX;
            double dy = map.y + CONTENT_OFFSET_Y + marker.getY() - mouseY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                state.hoveredDeed = deed;
            }
        }
    }

    private static void updateHoveredWaypoint(WorldMap map, State state,
                                               int mouseX, int mouseY) {
        if (!state.overlays.isVisible(MapOverlayVisibility.Layer.WAYPOINTS)) {
            clearHoveredWaypoint(state);
            return;
        }
        WaypointRevisionSnapshot snapshot = WurmWaypointerRuntime
                .serverMapWaypoints();
        ServerIdentity currentServer = WurmWaypointerRuntime
                .currentServerIdentity();
        String currentUser = WurmWaypointerRuntime.currentPlayerName();
        WaypointRecord best = null;
        double bestDistanceSquared = WAYPOINT_HIT_RADIUS * WAYPOINT_HIT_RADIUS;
        if (snapshot != null) for (WaypointRecord record : snapshot.getRecords()) {
            if (!visibleWaypoint(record, currentServer, currentUser)) continue;
            WaypointCoordinate coordinate = record.getCoordinate();
            MapPoint marker = state.viewport.mapToScreen(
                    coordinate.getTileX() + 0.5d,
                    coordinate.getTileY() + 0.5d);
            double dx = map.x + CONTENT_OFFSET_X + marker.getX() - mouseX;
            double dy = map.y + CONTENT_OFFSET_Y + marker.getY() - mouseY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = record;
            }
        }
        if (best == null) {
            clearHoveredWaypoint(state);
            return;
        }
        state.hoveredWaypointId = best.getId();
        state.hoveredWaypointName = best.getName();
        state.hoveredWaypointEditable = WurmWaypointerRuntime
                .serverMapWaypointEditable(best.getId());
    }

    private static void clearHoveredWaypoint(State state) {
        state.hoveredWaypointId = null;
        state.hoveredWaypointName = "";
        state.hoveredWaypointEditable = false;
    }

    private static void outline(WorldMap map, Queue queue, int left, int top,
                                MapPoint a, MapPoint b, float red, float green,
                                float blue, float alpha, int thickness) {
        int x1 = left + (int) Math.floor(Math.min(a.getX(), b.getX()));
        int y1 = top + (int) Math.floor(Math.min(a.getY(), b.getY()));
        int x2 = left + (int) Math.ceil(Math.max(a.getX(), b.getX()));
        int y2 = top + (int) Math.ceil(Math.max(a.getY(), b.getY()));
        fillClipped(map, queue, red, green, blue, alpha,
                x1, y1, x2 - x1, thickness, left, top);
        fillClipped(map, queue, red, green, blue, alpha,
                x1, y2 - thickness, x2 - x1, thickness, left, top);
        fillClipped(map, queue, red, green, blue, alpha,
                x1, y1, thickness, y2 - y1, left, top);
        fillClipped(map, queue, red, green, blue, alpha,
                x2 - thickness, y1, thickness, y2 - y1, left, top);
    }

    private static void fillClipped(WorldMap map, Queue queue,
                                    float red, float green, float blue, float alpha,
                                    int x, int y, int width, int height,
                                    int left, int top) {
        int clipX = Math.max(left, x);
        int clipY = Math.max(top, y);
        int clipRight = Math.min(left + CONTENT_WIDTH, x + width);
        int clipBottom = Math.min(top + CONTENT_HEIGHT, y + height);
        if (clipRight <= clipX || clipBottom <= clipY) return;
        map.fillRect(queue, red, green, blue, alpha, clipX, clipY,
                clipRight - clipX, clipBottom - clipY);
    }

    private static void line(Queue queue, float x1, float y1, float x2, float y2,
                             float thickness, float red, float green,
                             float blue, float alpha) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.5f) return;
        float angle = (float) Math.atan2(dy, dx);
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        Primitive primitive = queue.reservePrimitive();
        primitive.copyStateFrom(Renderer.stateAlphaBlend);
        primitive.setColor(red, green, blue, alpha);
        primitive.program = null;
        primitive.vertex = Primitive.staticVertexSquare2D;
        primitive.index = null;
        primitive.setType(Primitive.Type.TRIANGLESTRIP);
        primitive.num = 2;
        primitive.lightManager = null;
        primitive.clearTextures();
        primitive.texenv[0] = Primitive.TexEnv.MODULATE;
        primitive.offset = 0;
        primitive.clipRect = HeadsUpDisplay.scissor.getCurrent();
        LINE_MATRIX.fromTranslationRotationAndNonUniformScale(
                x1 + sin * thickness * 0.5f,
                y1 - cos * thickness * 0.5f, 0.0f,
                0.0f, 0.0f, angle, length, thickness, 0.0f);
        queue.queue(primitive, LINE_MATRIX);
    }

    private static void text(Queue queue, String value, int x, int y,
                             float red, float green, float blue, float alpha,
                             int left, int top) {
        if (value == null || value.isEmpty() || x < left || y < top
                || x >= left + CONTENT_WIDTH || y >= top + CONTENT_HEIGHT) return;
        TextFont font = TextFont.getFixedSizeText();
        font.moveTo(x, y);
        font.paint(queue, value, red, green, blue, alpha);
    }

    private static boolean insideContent(WorldMap map, int x, int y) {
        return x >= map.x + CONTENT_OFFSET_X && y >= map.y + CONTENT_OFFSET_Y
                && x < map.x + CONTENT_OFFSET_X + CONTENT_WIDTH
                && y < map.y + CONTENT_OFFSET_Y + CONTENT_HEIGHT;
    }

    private static boolean insideSearchButton(WorldMap map, int x, int y) {
        if (map == null) return false;
        int left = map.x + CONTENT_OFFSET_X + CONTENT_WIDTH
                - SEARCH_BUTTON_RIGHT - SEARCH_BUTTON_SIZE;
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && y >= top && x < left + SEARCH_BUTTON_SIZE
                && y < top + SEARCH_BUTTON_SIZE;
    }

    private static MapOverlayVisibility.Layer layerButtonAt(
            WorldMap map, int x, int y) {
        if (map == null) return null;
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        if (y < top || y >= top + LAYER_BUTTON_HEIGHT) return null;
        int left = map.x + CONTENT_OFFSET_X;
        for (MapOverlayVisibility.Layer layer : LAYER_BUTTONS) {
            int buttonLeft = layerButtonLeft(left, layer);
            if (x >= buttonLeft && x < buttonLeft + LAYER_BUTTON_WIDTH) {
                return layer;
            }
        }
        return null;
    }

    private static int layerButtonLeft(int contentLeft,
                                       MapOverlayVisibility.Layer layer) {
        int searchLeft = contentLeft + CONTENT_WIDTH
                - SEARCH_BUTTON_RIGHT - SEARCH_BUTTON_SIZE;
        int index = layer.ordinal();
        return searchLeft - LAYER_BUTTON_GAP
                - (LAYER_BUTTON_COUNT - index) * LAYER_BUTTON_WIDTH
                - (LAYER_BUTTON_COUNT - index - 1) * LAYER_BUTTON_GAP;
    }

    private static boolean visibleWaypoint(WaypointRecord record,
                                           ServerIdentity currentServer,
                                           String currentUser) {
        return record != null && record.isEnabled()
                && record.getCoordinate() != null && currentServer != null
                && currentServer.sameServer(record.getServerIdentity())
                && sameUser(currentUser, record.getCreatedByUser());
    }

    private static boolean isSurroundingsMark(WaypointRecord record) {
        return record.getSourceType()
                == org.waypoints.next.model.WaypointSourceType.MANAGED_ANIMAL
                || record.getSourceType()
                == org.waypoints.next.model.WaypointSourceType.MANAGED_ITEM;
    }

    private static boolean sameDeed(Deed left, Deed right) {
        return left != null && right != null && left.getX() == right.getX()
                && left.getY() == right.getY()
                && left.getName().equals(right.getName());
    }

    private static boolean sameUser(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.isEmpty() || b.isEmpty() || a.equalsIgnoreCase(b);
    }

    private static void reportOnce(String key, String message,
                                   Throwable failure) {
        synchronized (REPORTED_FAILURES) {
            if (!REPORTED_FAILURES.add(key)) return;
        }
        if (failure == null) LOGGER.warning(message);
        else LOGGER.log(Level.WARNING, message + "; "
                + failure.getClass().getName() + ": "
                + String.valueOf(failure.getMessage()), failure);
    }

    private static final class PreparedSurface {
        private final String key;
        private final Path file;
        private final WaypointerFileResourceUrl url;
        private final Object request = new Object();
        private volatile boolean ready;
        private volatile boolean failed;
        private volatile boolean indexScheduled;
        private volatile boolean indexFailed;
        private volatile SurfaceTileIndex tileIndex;
        private ResourceTexture texture;

        private PreparedSurface(String key, Path file,
                                WaypointerFileResourceUrl url) {
            this.key = key;
            this.file = file;
            this.url = url;
        }
    }

    private static final class PreparedArtwork {
        private final WaypointerFileResourceUrl url;
        private final String label;
        private final Object request = new Object();
        private volatile boolean ready;
        private volatile boolean failed;
        private ResourceTexture texture;

        private PreparedArtwork(WaypointerFileResourceUrl url, String label) {
            this.url = url;
            this.label = label;
        }
    }

    private static final class State {
        private final String profileId;
        private final MapViewport viewport;
        private final MapOverlayVisibility overlays;
        private boolean dragging;
        private boolean dragged;
        private int pressX;
        private int pressY;
        private int lastX;
        private int lastY;
        private boolean hoverInside;
        private int hoverTileX;
        private int hoverTileY;
        private UUID hoveredWaypointId;
        private String hoveredWaypointName = "";
        private boolean hoveredWaypointEditable;
        private Deed hoveredDeed;
        private boolean searchButtonHover;
        private boolean searchButtonPressed;
        private MapOverlayVisibility.Layer hoveredLayerButton;
        private MapOverlayVisibility.Layer pressedLayerButton;
        private boolean firstFrameLogged;

        private State(String profileId, MapViewport viewport,
                      MapOverlayVisibility overlays) {
            this.profileId = profileId;
            this.viewport = viewport;
            this.overlays = overlays;
        }
    }
}
