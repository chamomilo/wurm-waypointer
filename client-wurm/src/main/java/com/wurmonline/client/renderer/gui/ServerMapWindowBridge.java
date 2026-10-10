package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.ui.v1.*;

import com.wurmonline.client.resources.WaypointerFileResourceUrl;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.Texture;
import com.wurmonline.client.resources.textures.ResourceTextureLoader;
import com.wurmonline.client.resources.textures.WaypointerTextureFilters;
import com.wurmonline.client.resources.textures.WaypointerCaveTexture;
import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.effects.GroundNavigationRouteEffect;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.WaypointerFonts;
import com.wurmonline.client.renderer.gui.text.WaypointerMiniMapFonts;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.map.Deed;
import org.waypoints.next.map.MapPoint;
import org.waypoints.next.map.MapOverlayVisibility;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.map.SklotopolisMapProfiles;
import org.waypoints.next.map.SurfaceTileIndex;
import org.waypoints.next.map.LocalSurfaceMap;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.navigation.HighwayTileIndex;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.render.NavigationRenderFrame;
import org.waypoints.next.service.WaypointRevisionSnapshot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Replaces only the native WorldMap content while preserving its M-window lifecycle. */
public final class ServerMapWindowBridge {
    private static final Map<Long,LocalSurfaceTexture> LOCAL_TEXTURES=new HashMap<Long,LocalSurfaceTexture>();
    private static List<LocalSurfaceMap.Chunk> lastLocalSnapshot;
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Map");
    private static final int CONTENT_OFFSET_X = 3;
    private static final int CONTENT_OFFSET_Y = 21;
    private static final int CONTENT_WIDTH = 920;
    private static final int CONTENT_HEIGHT = 620;
    private static final int DRAG_THRESHOLD_PIXELS = 5;
    private static final int MAIN_MAP_FRAME_INSET = 22;
    private static final int MAIN_MAP_FRAME_OVERSCAN = 2;
    private static final int MAIN_MAP_EDGE_GUARD = 2;
    private static final double OVERVIEW_PIXELS_PER_TILE = 0.25d;
    private static final int MAXIMUM_OVERVIEW_HIGHWAY_SEGMENTS = 3_000;
    private static final int SEARCH_BUTTON_SIZE = 32;
    private static final int SEARCH_BUTTON_RIGHT = MAIN_MAP_FRAME_INSET + 8;
    private static final int SEARCH_BUTTON_TOP = 25;
    private static final int LAYER_BUTTON_WIDTH = 64;
    private static final int LAYER_BUTTON_HEIGHT = 32;
    private static final int LAYER_BUTTON_GAP = 4;
    private static final int LAYER_BUTTON_COUNT = 3;
    private static final int MINI_MAP_BUTTON_WIDTH = 88;
    private static final int NAV_LINE_BUTTON_WIDTH = 88;
    private static final int ZOOM_FACTOR_BUTTON_WIDTH = 128;
    private static final int ALL_MAPS_BUTTON_WIDTH = 200;
    private static final int GALLERY_CARD_SIZE = 164;
    private static final int GALLERY_CARD_GAP = 30;
    private static WaypointerButtonGroup toolbarGroup;
    private static long toolbarLanguageRevision = -1;
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
    private static final Path[] MAP_GALLERY_FILES = {
            galleryFile("liberty.png"), galleryFile("novus.png"),
            galleryFile("caza.png"), galleryFile("infinity-r5.png"),
            galleryFile("old-infinity.png")
    };
    private static final String[] MAP_GALLERY_LABELS = {
            "LIBERTY", "NOVUS", "CAZA", "INFINITY R5", "OLD INFINITY"
    };
    private static final String[] MAP_GALLERY_PROFILE_IDS = {
            "sklotopolis-liberty", "sklotopolis-novus", "sklotopolis-caza",
            "sklotopolis-infinity-r5", "sklotopolis-old-infinity"
    };
    private static final TextFont MAIN_TITLE_TEXT =
            WaypointerMiniMapFonts.healthbarTitle();
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
    private static PreparedSurface browsedSurface;
    private static final PreparedArtwork[] mapGallery =
            new PreparedArtwork[MAP_GALLERY_FILES.length];

    private ServerMapWindowBridge() { }

    private static Path galleryFile(String name) {
        return Paths.get("mods", "wurm-waypointer", "assets", "gallery", name);
    }

    /** Called instead of ClusterMap.render; false means render vanilla content. */
    public static boolean render(Queue queue) {
        long renderStart=System.nanoTime();
        try {
            HeadsUpDisplay hud = WurmComponent.hud;
            WorldMap map = hud == null ? null : hud.getWorldMap();
            ServerMapSnapshot snapshot = WurmWaypointerRuntime.serverMapSnapshot();
            ServerMapProfile profile = customProfile(snapshot);
            if (map == null || queue == null || profile == null) return false;

            int left = map.x + CONTENT_OFFSET_X;
            int top = map.y + CONTENT_OFFSET_Y;
            State state = state(map, profile);
            state.viewport.resize(CONTENT_WIDTH, CONTENT_HEIGHT);
            if (state.allMaps || state.browsedGalleryIndex >= 0) {
                renderMapBrowser(map, queue, state, profile, left, top);
                return true;
            }
            PreparedSurface surface = snapshot != null && snapshot.hasSurface()
                    ? prepare(snapshot) : null;
            if (surface == null || !surface.ready || surface.failed) {
                state.loadingGallery = true;
                renderLoadingGallery(map, queue, profile, left, top);
                drawMainMapChrome(map, queue, state, profile, left, top);
                return true;
            }
            scheduleSurfaceIndex(surface, profile);
            if (surface.texture == null) {
                reportOnce("texture-unavailable",
                        "Prepared server map texture is missing", null);
                state.loadingGallery = true;
                renderLoadingGallery(map, queue, profile, left, top);
                drawMainMapChrome(map, queue, state, profile, left, top);
                return true;
            }
            if (!surface.texture.isValid() && !surface.texture.needReinit()) {
                reportOnce("texture-invalid",
                        "Prepared server map texture cannot be initialized", null);
                state.loadingGallery = true;
                renderLoadingGallery(map, queue, profile, left, top);
                drawMainMapChrome(map, queue, state, profile, left, top);
                return true;
            }

            state.loadingGallery = false;
            HeadsUpDisplay.scissor.pushClip(
                    left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
            try {
                drawWaterBacking(map, queue, left, top);
                drawSurface(queue, surface.texture, state.viewport, left, top);
                drawLocalSurface(queue,state.viewport,left,top,CONTENT_WIDTH,CONTENT_HEIGHT);
                if (!state.firstFrameLogged) {
                    state.firstFrameLogged = true;
                    LOGGER.info("Native server map rendered its first frame: profile="
                            + profile.getId() + ", texture="
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
                drawMainMapChrome(map, queue, state, profile, left, top);
                return true;
            } finally {
                HeadsUpDisplay.scissor.popClip();
            }
        } catch (Throwable failure) {
            reportOnce("surface", "Server map surface render failed open", failure);
            return false;
        } finally {
            org.waypoints.next.render.WaypointRenderProfiler.recordMap(System.nanoTime()-renderStart,
                    queue==null?0:queue.getQueueCount());
        }
    }

    /** True only on a known Sklotopolis world; other servers keep vanilla chrome. */
    public static boolean usesCustomWindow() {
        try {
            return customProfile(WurmWaypointerRuntime.serverMapSnapshot()) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Replaces the stock World Map title bar with the reused healthbar plate. */
    public static void renderWindowTitle(WorldMap map, Queue queue) {
        if (map == null || queue == null) return;
        ServerMapProfile profile = customProfile(
                WurmWaypointerRuntime.serverMapSnapshot());
        if (profile == null) return;
        State state = state(map, profile);
        if (state.browsedGalleryIndex >= 0) profile = galleryProfile(state.browsedGalleryIndex);
        String display = profile.getDisplayName() == null ? ""
                : profile.getDisplayName().trim().replace(' ', '-');
        String title = state.allMaps ? Messages.text("ALL MAPS ON SERVER") : "Map of: " + display;
        drawNameplate(map, queue,
                map.x + CONTENT_OFFSET_X + 3, map.y,
                Math.max(54, Math.min(CONTENT_WIDTH - 5,
                        MAIN_TITLE_TEXT.getWidth(title) + 48)), title);
    }

    /** Draws the compact player-centred view without taking ownership of it. */
    static boolean renderMiniMap(WurmComponent map, Queue queue,
                                 MapViewport viewport,
                                 ServerMapSnapshot snapshot,
                                 boolean showDeeds,
                                 boolean showTileBorders,
                                 MiniMapContourOverlay contours,
                                 int left, int top, int size) {
        try {
            if (map == null || queue == null || viewport == null
                    || snapshot == null || snapshot.getProfile() == null
                    || !snapshot.hasSurface() || size < 1) return false;
            PreparedSurface surface = prepare(snapshot);
            if (!surface.ready || surface.failed) return false;
            scheduleSurfaceIndex(surface, snapshot.getProfile());
            if (surface.texture == null
                    || (!surface.texture.isValid()
                    && !surface.texture.needReinit())) return false;

            HeadsUpDisplay.scissor.pushClip(left, top, size, size);
            try {
                map.fillRect(queue, MAP_WATER_RED, MAP_WATER_GREEN,
                        MAP_WATER_BLUE, 1.0f, left, top, size, size);
                drawSurface(queue, surface.texture, viewport,
                        left, top, size, size);
                drawLocalSurface(queue,viewport,left,top,size,size);
                drawTopographicContours(queue, contours, left, top, size);
                if (showTileBorders) drawTileBorders(map, queue, viewport,
                        left, top, size, size);
                if (MiniMapWindowBridge.areRoadsVisible()) {
                    drawHighways(queue, viewport, left, top, size, size,
                            WurmWaypointerRuntime.serverMapHighways());
                }
                if (showDeeds) drawMiniMapDeeds(map, queue, viewport,
                        left, top, size, snapshot.getDeeds());
                if (MiniMapWindowBridge.isNavigationLineVisible()) {
                    drawNavigationLine(queue, viewport, left, top, size, size);
                }
                drawMiniMapWaypoints(map, queue, viewport, left, top, size,
                        WurmWaypointerRuntime.serverMapWaypoints(),
                        WurmWaypointerRuntime.currentServerIdentity(),
                        WurmWaypointerRuntime.currentPlayerName());
                drawPlayer(map, queue, viewport, left, top, size, size);
                return true;
            } finally {
                HeadsUpDisplay.scissor.popClip();
            }
        } catch (Throwable failure) {
            reportOnce("mini-map", "Mini-map render failed open", failure);
            return false;
        }
    }

    static void renderCaveMiniMapOverlays(WurmComponent map, Queue queue,
                                         MapViewport viewport, boolean showTileBorders,
                                         MiniMapContourOverlay contours,
                                         int left, int top, int size) {
        drawTopographicContours(queue, contours, left, top, size);
        if (showTileBorders) drawTileBorders(map, queue, viewport,
                left, top, size, size);
        if (MiniMapWindowBridge.isNavigationLineVisible())
            drawNavigationLine(queue, viewport, left, top, size, size, true);
        drawMiniMapWaypoints(map, queue, viewport, left, top, size,
                WurmWaypointerRuntime.serverMapWaypoints(),
                WurmWaypointerRuntime.currentServerIdentity(),
                WurmWaypointerRuntime.currentPlayerName(), WaypointLayer.CAVE);
        drawPlayer(map, queue, viewport, left, top, size, size);
    }

    private static void drawOverlays(WorldMap map, Queue queue, State state,
                                     ServerMapSnapshot snapshot,
                                     int left, int top) {
        if (state.viewport.isMaximumZoom()) drawTileBorders(map, queue,
                state.viewport, left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
        // Once the validated surface has rendered, an optional overlay
        // failure must not return control to vanilla ClusterMap: it would
        // paint its own map on top and hide the working server surface.
        if (layerVisible(state,
                MapOverlayVisibility.Layer.HIGHWAYS)) try {
            drawHighways(queue, state.viewport, left, top,
                    CONTENT_WIDTH, CONTENT_HEIGHT,
                    WurmWaypointerRuntime.serverMapHighways());
        } catch (Throwable failure) {
            reportOnce("highways", "Server map Highways overlay failed open",
                    failure);
        }
        if (layerVisible(state, MapOverlayVisibility.Layer.DEEDS)) try {
            drawDeeds(map, queue, state.viewport, left, top,
                    snapshot.getDeeds(), state);
        } catch (Throwable failure) {
            reportOnce("deeds", "Server map deed overlay failed open", failure);
        }
        if (MiniMapWindowBridge.isNavigationLineVisible()) try {
            drawNavigationLine(queue, state.viewport, left, top,
                    CONTENT_WIDTH, CONTENT_HEIGHT);
        } catch (Throwable failure) {
            reportOnce("navigation-line",
                    "Server map navigation line failed open", failure);
        }
        if (layerVisible(state, MapOverlayVisibility.Layer.WAYPOINTS)) try {
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
        if (insideAllMapsButton(map, mouseX, mouseY)) {
            state.allMapsButtonPressed = true; state.dragging = false;
            updateHover(map, state, mouseX, mouseY); return true;
        }
        if (!homeView(state) && (insideNavigationLineButton(map, mouseX, mouseY)
                || (layerButtonAt(map, mouseX, mouseY) != null
                && !layerAvailable(state, layerButtonAt(map, mouseX, mouseY)))
                || insideSearchButton(map, mouseX, mouseY))) return true;
        if (insideZoomFactorButton(map, mouseX, mouseY)) {
            state.zoomFactorButtonPressed = true;
            state.dragging = false;
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (insideCenterButton(map, mouseX, mouseY)) {
            state.centerButtonPressed = true;
            state.dragging = false;
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (insideCloseButton(map, mouseX, mouseY)) {
            state.closeButtonPressed = true;
            state.dragging = false;
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (insideNavigationLineButton(map, mouseX, mouseY)) {
            state.navigationLineButtonPressed = true;
            state.dragging = false;
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (insideMiniMapButton(map, mouseX, mouseY)) {
            state.miniMapButtonPressed = true;
            state.dragging = false;
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
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
        if (galleryVisible(state)) {
            state.pressedGalleryCard = galleryCardAt(map, mouseX, mouseY);
            state.dragging = false; updateHover(map, state, mouseX, mouseY); return true;
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
        if (state.allMapsButtonPressed || galleryVisible(state)) {
            updateHover(map, state, mouseX, mouseY); return true;
        }
        if (state.zoomFactorButtonPressed) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.centerButtonPressed) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.pressedLayerButton != null) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.searchButtonPressed) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.closeButtonPressed) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.miniMapButtonPressed) {
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.navigationLineButtonPressed) {
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
        if (state.allMapsButtonPressed) {
            state.allMapsButtonPressed = false;
            if (insideAllMapsButton(map, mouseX, mouseY)) {
                state.allMaps = !state.allMaps;
                if (state.allMaps) WurmWaypointerRuntime.stopMapBrowsing();
            }
            state.dragging = false; state.pressedGalleryCard = -1;
            updateHover(map, state, mouseX, mouseY); return true;
        }
        if (state.pressedGalleryCard >= 0) {
            int index = state.pressedGalleryCard; state.pressedGalleryCard = -1;
            if (index == galleryCardAt(map, mouseX, mouseY)) selectGallery(state, index);
            updateHover(map, state, mouseX, mouseY); return true;
        }
        if (state.zoomFactorButtonPressed) {
            state.zoomFactorButtonPressed = false;
            if (insideZoomFactorButton(map, mouseX, mouseY)) MiniMapWindowBridge.cycleZoomFactor();
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.centerButtonPressed) {
            state.centerButtonPressed = false;
            if (insideCenterButton(map, mouseX, mouseY)) {
                returnHome(state);
                state.viewport.centerOn(WurmWaypointerRuntime.currentPlayerTileX() + 0.5d,
                        WurmWaypointerRuntime.currentPlayerTileY() + 0.5d);
            }
            updateHover(map, state, mouseX, mouseY);
            return true;
        }
        if (state.closeButtonPressed) {
            state.closeButtonPressed = false;
            updateHover(map, state, mouseX, mouseY);
            if (insideCloseButton(map, mouseX, mouseY)) {
                WurmWaypointerRuntime.stopMapBrowsing();
                HeadsUpDisplay current = WurmComponent.hud;
                if (current != null) current.hideComponent(map);
            }
            return true;
        }
        if (state.navigationLineButtonPressed) {
            state.navigationLineButtonPressed = false;
            updateHover(map, state, mouseX, mouseY);
            if (insideNavigationLineButton(map, mouseX, mouseY)) {
                MiniMapWindowBridge.toggleNavigationLine();
            }
            return true;
        }
        if (state.miniMapButtonPressed) {
            state.miniMapButtonPressed = false;
            updateHover(map, state, mouseX, mouseY);
            if (insideMiniMapButton(map, mouseX, mouseY)) {
                MiniMapWindowBridge.toggle(WurmComponent.hud);
            }
            return true;
        }
        if (state.pressedLayerButton != null) {
            MapOverlayVisibility.Layer pressed = state.pressedLayerButton;
            state.pressedLayerButton = null;
            if (pressed == layerButtonAt(map, mouseX, mouseY)) {
                toggleLayer(state, pressed);
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
                    DeedSearchWindowBridge.open(current, snapshot);
                }
            }
            return true;
        }
        if (!state.dragging) return false;
        boolean create = homeView(state) && !state.dragged && insideContent(map, mouseX, mouseY);
        state.dragging = false;
        updateHover(map, state, mouseX, mouseY);
        if (create) requestWaypoint(map, state, mouseX, mouseY);
        return true;
    }

    public static boolean rightPressed(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state == null || !insideContent(map, mouseX, mouseY)) return false;
        if (!homeView(state) || insideAllMapsButton(map, mouseX, mouseY)) return true;
        if (insideZoomFactorButton(map, mouseX, mouseY)) return true;
        if (insideCenterButton(map, mouseX, mouseY)) return true;
        if (insideNavigationLineButton(map, mouseX, mouseY)) return true;
        if (insideMiniMapButton(map, mouseX, mouseY)) return true;
        if (layerButtonAt(map, mouseX, mouseY) != null) return true;
        if (insideSearchButton(map, mouseX, mouseY)) return true;
        if (insideCloseButton(map, mouseX, mouseY)) return true;
        updateHover(map, state, mouseX, mouseY);
        requestCustomMarkAt(state.viewport,
                mouseX - map.x - CONTENT_OFFSET_X,
                mouseY - map.y - CONTENT_OFFSET_Y);
        return true;
    }

    public static boolean mouseWheeled(WorldMap map, int mouseX, int mouseY,
                                       int wheelDelta) {
        State state = activeState(map);
        if (state == null || !insideContent(map, mouseX, mouseY)
                || !isTopmostMapTarget(map, mouseX, mouseY)) return false;
        if (galleryVisible(state) || insideAllMapsButton(map, mouseX, mouseY)
                || layerButtonAt(map, mouseX, mouseY) != null
                || insideSearchButton(map, mouseX, mouseY)
                || insideCloseButton(map, mouseX, mouseY)
                || insideMiniMapButton(map, mouseX, mouseY)
                || insideNavigationLineButton(map, mouseX, mouseY)
                || insideZoomFactorButton(map, mouseX, mouseY)
                || insideCenterButton(map, mouseX, mouseY)) return true;
        double steps = MiniMapWindowBridge.fullMapWheelSteps(wheelDelta);
        state.viewport.zoomAt(mouseX - map.x - CONTENT_OFFSET_X,
                mouseY - map.y - CONTENT_OFFSET_Y, steps);
        updateHover(map, state, mouseX, mouseY);
        return true;
    }

    /**
     * The HUD wheel hook runs before normal component dispatch, so geometric
     * map bounds alone are insufficient: another window can cover the map.
     */
    private static boolean isTopmostMapTarget(WorldMap map, int x, int y) {
        HeadsUpDisplay current = WurmComponent.hud;
        return current != null && belongsTo(current.getComponentAt(x, y), map);
    }

    private static boolean belongsTo(WurmComponent target,
                                     WurmComponent ancestor) {
        if (target == null || ancestor == null) return false;
        for (WurmComponent component = target; component != null;
             component = component.parent) {
            if (component == ancestor) return true;
        }
        return false;
    }

    public static void mouseMoved(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state != null) updateHover(map, state, mouseX, mouseY);
    }

    public static boolean pick(WorldMap map, PickData pickData,
                               int mouseX, int mouseY) {
        State state = activeState(map);
        if (state == null || pickData == null
                || !insideContent(map, mouseX, mouseY)) return false;
        updateHover(map, state, mouseX, mouseY);
        String hover = "";
        if (state.allMapsButtonHover) {
            hover = Messages.text("Show all five server maps");
        } else if (state.centerButtonHover) {
            hover = "Center map on your character";
        } else if (state.navigationLineButtonHover) {
            hover = "NAV LINE: continuous active-navigation line is "
                    + (MiniMapWindowBridge.isNavigationLineVisible()
                    ? "visible" : "hidden") + " on both maps";
        } else if (state.miniMapButtonHover) {
            hover = "MINI MAP: " + (MiniMapWindowBridge.isEnabled()
                    ? "enabled" : "disabled");
        } else if (state.hoveredLayerButton != null) {
            hover = layerButtonHelp(state.hoveredLayerButton,
                    layerVisible(state, state.hoveredLayerButton));
        } else if (state.searchButtonHover) {
            hover = "Search deeds";
        } else if (state.closeButtonHover) {
            hover = "Close map";
        } else if (galleryVisible(state)) {
            hover = state.hoveredGalleryCard >= 0 ? MAP_GALLERY_LABELS[state.hoveredGalleryCard]
                    : Messages.text("Choose a map to view");
        } else if (!homeView(state)) {
            hover = state.hoverInside ? "X=" + state.hoverTileX + " Y=" + state.hoverTileY : "";
            if (state.hoveredDeed != null) hover += " | Deed: " + state.hoveredDeed.getName();
        } else {
            List<String> lines = mapHoverLines(state.viewport,
                    mouseX - map.x - CONTENT_OFFSET_X,
                    mouseY - map.y - CONTENT_OFFSET_Y,
                    layerVisible(state, MapOverlayVisibility.Layer.DEEDS),
                    layerVisible(state, MapOverlayVisibility.Layer.WAYPOINTS));
            for (String line : lines) pickData.addText(line);
            return true;
        }
        if (hover != null && !hover.isEmpty()) pickData.addText(hover);
        return true;
    }

    public static boolean suppressVanillaContextMenu(WorldMap map) {
        return activeState(map) != null;
    }

    /** Centers the active M-map on a deed selected in the native search window. */
    public static synchronized void centerOnDeed(int tileX, int tileY) {
        HeadsUpDisplay current = WurmComponent.hud;
        WorldMap map = current == null ? null : current.getWorldMap();
        State state = activeState(map);
        if (state != null) { returnHome(state); state.viewport.focusOn(
                tileX + 0.5d, tileY + 0.5d,
                DEED_FOCUS_PIXELS_PER_TILE); }
    }

    public static synchronized void reset(WorldMap map) {
        if (map != null) {
            STATES.remove(map);
            WurmWaypointerRuntime.stopMapBrowsing();
        }
    }

    public static void visibilityChanged(Object component, boolean visible) {
        if (component instanceof WorldMap && !visible) WurmWaypointerRuntime.stopMapBrowsing();
    }

    public static synchronized void resetAll() {
        STATES.clear();
        WurmWaypointerRuntime.stopMapBrowsing();
        for(LocalSurfaceTexture value:LOCAL_TEXTURES.values())value.texture.dispose();
        LOCAL_TEXTURES.clear();lastLocalSnapshot=null;
        // The published image and its terrain index are independent of the HUD/world session.
        // Keep one revision across reconnects; prepare() replaces it when its identity changes.
        REPORTED_FAILURES.clear();
    }

    private static ServerMapProfile customProfile(ServerMapSnapshot snapshot) {
        if (snapshot != null && snapshot.getProfile() != null) {
            return snapshot.getProfile();
        }
        return SklotopolisMapProfiles.resolve(
                WurmWaypointerRuntime.currentServerIdentity());
    }

    private static synchronized PreparedSurface prepare(
            ServerMapSnapshot snapshot) {
        return prepareSurface(snapshot, false);
    }

    private static synchronized PreparedSurface prepareSurface(ServerMapSnapshot snapshot, boolean browsing) {
        Path file = snapshot.getSurfaceImage().toAbsolutePath().normalize();
        String key = snapshot.getProfile().getId() + "|" + file + "|"
                + snapshot.getSurfaceRevision();
        PreparedSurface current = browsing ? browsedSurface : prepared;
        if (current != null && key.equals(current.key)) return current;
        final PreparedSurface next = new PreparedSurface(key, file,
                new WaypointerFileResourceUrl(file, snapshot.getSurfaceRevision()));
        if (browsing) browsedSurface = next; else prepared = next;
        TEXTURE_WORKER.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (!isCurrentSurface(next)) return;
                    ResourceTextureLoader.prepareTexture(next.url,
                            next.request, false);
                    // Consume the native request on this CPU worker even if the HUD changed.
                    // Otherwise abandoned requests retain decoded pixels or a texture indefinitely.
                    ResourceTexture texture = WaypointerTextureFilters.useCrispMagnification(
                            ResourceTextureLoader.getPreparedTexture(next.url, next.request));
                    if (isCurrentSurface(next)) next.texture = texture;
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

    private static synchronized boolean isCurrentSurface(PreparedSurface surface) {
        return prepared == surface || browsedSurface == surface;
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
                    if (!isCurrentSurface(surface)) return;
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

    private static synchronized ResourceTexture mapGalleryTexture(int index) {
        if (index < 0 || index >= mapGallery.length) return null;
        mapGallery[index] = prepareArtwork(mapGallery[index],
                MAP_GALLERY_FILES[index],
                "Sklotopolis map preview " + MAP_GALLERY_LABELS[index],
                "map-gallery-prepare-" + index);
        return readyArtwork(mapGallery[index], "map-gallery-" + index);
    }

    private static PreparedArtwork prepareArtwork(PreparedArtwork current,
                                                    Path path,
                                                    String label,
                                                    final String failureKey) {
        if (current != null) return current;
        final PreparedArtwork next = new PreparedArtwork(
                new WaypointerFileResourceUrl(path, 1L), label);
        TEXTURE_WORKER.execute(new Runnable() {
            @Override public void run() {
                try {
                    ResourceTextureLoader.prepareTexture(next.url,
                            next.request, false);
                    next.texture = ResourceTextureLoader.getPreparedTexture(next.url, next.request);
                    next.ready = true;
                } catch (Throwable failure) {
                    next.failed = true;
                    next.ready = true;
                    reportOnce(failureKey,
                            next.label + " could not be prepared", failure);
                }
            }
        });
        return next;
    }

    private static ResourceTexture readyArtwork(PreparedArtwork artwork,
                                                 String failureKey) {
        try {
            if (artwork == null || !artwork.ready || artwork.failed) return null;
            ResourceTexture texture = artwork.texture;
            return texture != null && (texture.isValid() || texture.needReinit())
                    ? texture : null;
        } catch (Throwable failure) {
            reportOnce(failureKey, artwork == null
                    ? "Map artwork failed open"
                    : artwork.label + " failed open", failure);
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
        ServerMapProfile profile = customProfile(snapshot);
        if (profile == null) return null;
        State state = state(map, profile);
        if (state.browsedGalleryIndex < 0 && (snapshot == null || !snapshot.hasSurface()
                || prepared == null || !prepared.ready || prepared.failed || prepared.texture == null)) state.loadingGallery = true;
        return state;
    }

    private static boolean galleryVisible(State state) {
        return state.allMaps || (state.loadingGallery && state.browsedGalleryIndex < 0);
    }
    private static boolean homeView(State state) {
        return !galleryVisible(state) && state.browsedGalleryIndex < 0;
    }
    private static ServerMapProfile galleryProfile(int index) {
        for (ServerMapProfile profile : SklotopolisMapProfiles.all())
            if (profile.getId().equals(MAP_GALLERY_PROFILE_IDS[index])) return profile;
        throw new IllegalArgumentException("Unknown gallery map: " + index);
    }
    private static void returnHome(State state) {
        WurmWaypointerRuntime.stopMapBrowsing();
        state.allMaps = false; state.loadingGallery = false; state.browsedGalleryIndex = -1;
        state.viewport = state.homeViewport; state.dragging = false; state.pressedGalleryCard = -1;
        clearHoveredWaypoint(state); state.hoveredDeed = null;
    }
    private static void selectGallery(State state, int index) {
        ServerMapProfile profile = galleryProfile(index);
        if (state.profileId.equals(profile.getId())) { returnHome(state); return; }
        MapViewport viewport = state.galleryViewports.get(index);
        if (viewport == null) {
            viewport = new MapViewport(profile.getMapWidth(), profile.getMapHeight(), CONTENT_WIDTH, CONTENT_HEIGHT,
                    profile.getMapWidth() / 2d, profile.getMapHeight() / 2d);
            state.galleryViewports.put(index, viewport);
        }
        state.viewport = viewport; state.browsedGalleryIndex = index; state.allMaps = false; state.browsedMapReady = false;
        state.loadingGallery = false; state.dragging = false; state.hoverInside = false;
        clearHoveredWaypoint(state); state.hoveredDeed = null;
    }
    private static int galleryCardLeft(int index) {
        int count = index < 3 ? 3 : 2, column = index < 3 ? index : index - 3;
        return (CONTENT_WIDTH - GALLERY_CARD_SIZE * count - GALLERY_CARD_GAP * (count - 1)) / 2
                + column * (GALLERY_CARD_SIZE + GALLERY_CARD_GAP);
    }
    private static int galleryCardTop(int index) { return index < 3 ? 116 : 348; }
    private static int galleryCardAt(WorldMap map, int x, int y) {
        x -= map.x + CONTENT_OFFSET_X; y -= map.y + CONTENT_OFFSET_Y;
        for (int i = 0; i < MAP_GALLERY_FILES.length; i++)
            if (x >= galleryCardLeft(i) - 5 && x < galleryCardLeft(i) + GALLERY_CARD_SIZE + 5
                    && y >= galleryCardTop(i) - 5 && y < galleryCardTop(i) + GALLERY_CARD_SIZE + 28) return i;
        return -1;
    }
    private static void renderMapBrowser(WorldMap map, Queue queue, State state,
                                         ServerMapProfile home, int left, int top) {
        if (state.allMaps) {
            renderLoadingGallery(map, queue, home, left, top);
            drawMainMapChrome(map, queue, state, home, left, top); return;
        }
        HeadsUpDisplay.scissor.pushClip(left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
        try {
            drawWaterBacking(map, queue, left, top);
            ServerMapSnapshot snapshot = WurmWaypointerRuntime.browsedMapSnapshot(galleryProfile(state.browsedGalleryIndex));
            PreparedSurface surface = snapshot != null && snapshot.hasSurface() ? prepareSurface(snapshot, true) : null;
            state.browsedMapReady = surface != null && surface.ready && !surface.failed && surface.texture != null
                    && (surface.texture.isValid() || surface.texture.needReinit());
            ResourceTexture texture = state.browsedMapReady ? surface.texture : mapGalleryTexture(state.browsedGalleryIndex);
            if (texture != null) drawSurface(queue, texture, state.viewport, left, top);
            if (layerVisible(state, MapOverlayVisibility.Layer.HIGHWAYS)) try {
                drawHighways(queue, state.viewport, left, top, CONTENT_WIDTH, CONTENT_HEIGHT,
                        WurmWaypointerRuntime.browsedMapHighways());
            } catch (Throwable failure) {
                reportOnce("browsed-highways", "Published map highways overlay failed open", failure);
            }
            if (snapshot != null && layerVisible(state, MapOverlayVisibility.Layer.DEEDS)) try {
                drawDeeds(map, queue, state.viewport, left, top, snapshot.getDeeds(), state);
            } catch (Throwable failure) {
                reportOnce("browsed-deeds", "Published map deeds overlay failed open", failure);
            }
            drawMainMapChrome(map, queue, state, galleryProfile(state.browsedGalleryIndex), left, top);
        } finally { HeadsUpDisplay.scissor.popClip(); }
    }

    private static void drawSurface(Queue queue, ResourceTexture texture,
                                    MapViewport viewport, int left, int top) {
        drawSurface(queue, texture, viewport, left, top,
                CONTENT_WIDTH, CONTENT_HEIGHT);
    }

    private static void drawLocalSurface(Queue queue,MapViewport viewport,int left,int top,int width,int height) {
        List<LocalSurfaceMap.Chunk> chunks=WurmWaypointerRuntime.localSurfaceMap();
        if(chunks!=lastLocalSnapshot){
            Set<Long> retained=new HashSet<Long>();for(LocalSurfaceMap.Chunk chunk:chunks)retained.add(chunk.getKey());
            java.util.Iterator<Map.Entry<Long,LocalSurfaceTexture>> entries=LOCAL_TEXTURES.entrySet().iterator();
            while(entries.hasNext()){
                Map.Entry<Long,LocalSurfaceTexture> entry=entries.next();
                if(!retained.contains(entry.getKey())){entry.getValue().texture.dispose();entries.remove();}
            }
            lastLocalSnapshot=chunks;
        }
        for(LocalSurfaceMap.Chunk chunk:chunks){
            MapPoint start=viewport.mapToScreen(chunk.getOriginX(),chunk.getOriginY());
            MapPoint end=viewport.mapToScreen(chunk.getOriginX()+LocalSurfaceMap.CHUNK_SIZE,chunk.getOriginY()+LocalSurfaceMap.CHUNK_SIZE);
            double size=end.getX()-start.getX();
            double x=Math.max(0,start.getX()),y=Math.max(0,start.getY());
            double right=Math.min(width,end.getX()),bottom=Math.min(height,end.getY());
            if(right<=x||bottom<=y)continue;
            LocalSurfaceTexture cached=LOCAL_TEXTURES.get(chunk.getKey());
            if(cached==null){cached=new LocalSurfaceTexture();LOCAL_TEXTURES.put(chunk.getKey(),cached);}
            if(cached.revision!=chunk.getRevision()){
                cached.texture.update(chunk.image());cached.revision=chunk.getRevision();
            }
            Renderer.texturedQuadAlphaBlend(queue,cached.texture.get(),1,1,1,1,
                    (float)(left+x),(float)(top+y),(float)(right-x),(float)(bottom-y),
                    (float)((x-start.getX())/size),(float)((y-start.getY())/size),
                    (float)((right-x)/size),(float)((bottom-y)/size));
        }
    }

    private static final class LocalSurfaceTexture {
        final WaypointerCaveTexture texture=new WaypointerCaveTexture();
        long revision=Long.MIN_VALUE;
    }

    private static void drawSurface(Queue queue, ResourceTexture texture,
                                    MapViewport viewport, int left, int top,
                                    int viewportWidth, int viewportHeight) {
        double imageLeft = viewport.getImageLeft();
        double imageTop = viewport.getImageTop();
        double imageWidth = viewport.getImageWidth();
        double imageHeight = viewport.getImageHeight();
        double clipLeft = Math.max(0.0d, imageLeft);
        double clipTop = Math.max(0.0d, imageTop);
        double clipRight = Math.min(viewportWidth, imageLeft + imageWidth);
        double clipBottom = Math.min(viewportHeight, imageTop + imageHeight);
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

    private static void renderLoadingGallery(WorldMap map, Queue queue,
                                             ServerMapProfile profile,
                                             int left, int top) {
        HeadsUpDisplay.scissor.pushClip(left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
        try {
            map.fillRect(queue, 0.075f, 0.050f, 0.030f, 1.0f,
                    left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
            UiPainter.background(WaypointerUi.canvas(map, queue), UiBackground.SOLID, 1, left, top, CONTENT_WIDTH, CONTENT_HEIGHT);

            for (int index = 0; index < MAP_GALLERY_FILES.length; index++) {
                int x = left + galleryCardLeft(index);
                int y = top + galleryCardTop(index);
                drawGalleryCard(map, queue, profile, index,
                        x, y, GALLERY_CARD_SIZE);
            }

            drawMainMapEdgeGuard(map, queue, left, top);
            drawMainMapFrame(map, queue, left, top);

            String loading = state(map, profile).allMaps ? Messages.text("Choose a map to view")
                    : "LOADING " + mapWorldName(profile) + " MAP...";
            int loadingX = left
                    + (CONTENT_WIDTH - MAIN_TITLE_TEXT.getWidth(loading)) / 2;
            MAIN_TITLE_TEXT.moveTo(loadingX, top + CONTENT_HEIGHT - 32);
            MAIN_TITLE_TEXT.paint(queue, loading,
                    0.97f, 0.90f, 0.68f, 1.0f);
        } finally {
            HeadsUpDisplay.scissor.popClip();
        }
    }

    private static void drawGalleryCard(WorldMap map, Queue queue,
                                        ServerMapProfile profile, int index,
                                        int x, int y, int size) {
        boolean active = profile != null && profile.getId().equals(
                MAP_GALLERY_PROFILE_IDS[index]);
        active |= state(map, profile).hoveredGalleryCard == index;
        float edgeRed = active ? 0.96f : 0.28f;
        float edgeGreen = active ? 0.76f : 0.21f;
        float edgeBlue = active ? 0.30f : 0.13f;
        map.fillRect(queue, 0.025f, 0.020f, 0.015f, 0.98f,
                x - 5, y - 5, size + 10, size + 10);
        map.fillRect(queue, edgeRed, edgeGreen, edgeBlue, 1.0f,
                x - 3, y - 3, size + 6, size + 6);
        map.fillRect(queue, 0.055f, 0.040f, 0.028f, 1.0f,
                x, y, size, size);
        ResourceTexture texture = mapGalleryTexture(index);
        if (texture == null) {
            map.fillRect(queue, MAP_WATER_RED, MAP_WATER_GREEN,
                    MAP_WATER_BLUE, 1.0f, x + 3, y + 3,
                    size - 6, size - 6);
        } else {
            drawArtwork(queue, texture, x + 3, y + 3,
                    size - 6, size - 6, 1.0f);
        }
        String label = MAP_GALLERY_LABELS[index];
        int labelX = x + (size - MAIN_TITLE_TEXT.getWidth(label)) / 2;
        MAIN_TITLE_TEXT.moveTo(labelX + 1, y + size + 22);
        MAIN_TITLE_TEXT.paint(queue, label,
                0.03f, 0.015f, 0.008f, 1.0f);
        MAIN_TITLE_TEXT.moveTo(labelX, y + size + 21);
        MAIN_TITLE_TEXT.paint(queue, label,
                active ? 1.0f : 0.88f,
                active ? 0.86f : 0.78f,
                active ? 0.53f : 0.62f, 1.0f);
    }

    private static void drawMainMapChrome(WorldMap map, Queue queue,
                                          State state,
                                          ServerMapProfile profile,
                                          int left, int top) {
        try {
            drawMainMapEdgeGuard(map, queue, left, top);
            drawMainMapFrame(map, queue, left, top);
        } catch (Throwable failure) {
            reportOnce("main-map-frame", "Server map frame failed open",
                    failure);
        }
        try {
            drawStatus(map, queue, state, profile, left, top);
        } catch (Throwable failure) {
            reportOnce("main-map-status", "Server map status failed open",
                    failure);
        }
        try {
            drawAllMapsButton(map, queue, state, left, top);
            drawLayerButtons(map, queue, state, left, top);
            drawMiniMapButton(map, queue, state, left, top);
            drawCenterButton(map, queue, state, left, top);
            drawZoomFactorButton(map, queue, state, left, top);
            drawNavigationLineButton(map, queue, state, left, top);
            drawSearchButton(map, queue, state, left, top);
            drawCloseButton(map, queue, state, left, top);
        } catch (Throwable failure) {
            reportOnce("main-map-controls", "Server map controls failed open",
                    failure);
        }
    }

    private static void drawMainMapEdgeGuard(WorldMap map, Queue queue,
                                             int left, int top) {
        float red = 0.025f;
        float green = 0.018f;
        float blue = 0.012f;
        map.fillRect(queue, red, green, blue, 1.0f,
                left, top, CONTENT_WIDTH, MAIN_MAP_EDGE_GUARD);
        map.fillRect(queue, red, green, blue, 1.0f,
                left, top + CONTENT_HEIGHT - MAIN_MAP_EDGE_GUARD,
                CONTENT_WIDTH, MAIN_MAP_EDGE_GUARD);
        map.fillRect(queue, red, green, blue, 1.0f,
                left, top, MAIN_MAP_EDGE_GUARD, CONTENT_HEIGHT);
        map.fillRect(queue, red, green, blue, 1.0f,
                left + CONTENT_WIDTH - MAIN_MAP_EDGE_GUARD, top,
                MAIN_MAP_EDGE_GUARD, CONTENT_HEIGHT);
    }

    private static void drawMainMapFrame(WorldMap map, Queue queue, int left, int top) {
        UiPainter.frame(WaypointerUi.canvas(map, queue), MAIN_MAP_FRAME_INSET, 1, left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
    }



    private static void drawArtwork(Queue queue, ResourceTexture texture,
                                    int left, int top, int width, int height,
                                    float alpha) {
        if (texture == null) return;
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, alpha,
                left, top, width, height,
                0.0f, 0.0f, 1.0f, 1.0f);
    }

    private static void drawNameplate(WorldMap map, Queue queue, int x, int y, int width, String label) {
        ChamomiloUiV1Canvas canvas = WaypointerUi.canvas(map, queue);
        UiHudPainter.nameplate(canvas, false, UiScale.BASE, 1, x, y, width);
        MAIN_TITLE_TEXT.moveTo(x + 12 + (width - 36 - MAIN_TITLE_TEXT.getWidth(label)) / 2, y + 15);
        MAIN_TITLE_TEXT.paint(queue, label, UiColor.TEXT.red, UiColor.TEXT.green, UiColor.TEXT.blue, 1);
        UiHudPainter.nameplate(canvas, true, UiScale.BASE, 1, x, y, width);
    }

    private static String mapWorldName(ServerMapProfile profile) {
        String display = profile == null || profile.getDisplayName() == null
                ? "SKLOTOPOLIS" : profile.getDisplayName();
        String prefix = "Sklotopolis ";
        String world = display.regionMatches(true, 0, prefix, 0,
                Math.min(prefix.length(), display.length()))
                && display.length() >= prefix.length()
                ? display.substring(prefix.length()) : display;
        world = world.trim().toUpperCase(Locale.ENGLISH);
        return world.isEmpty() ? "SKLOTOPOLIS" : world;
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
        boolean overview = viewport.getPixelsPerTile()
                < OVERVIEW_PIXELS_PER_TILE;
        int labels = 0;
        for (Deed deed : deeds) {
            MapPoint point = viewport.mapToScreen(
                    deed.getX() + 0.5d, deed.getY() + 0.5d);
            int x = left + (int) Math.round(point.getX());
            int y = top + (int) Math.round(point.getY());
            boolean anchorVisible = x >= left - 8 && y >= top - 8
                    && x <= left + CONTENT_WIDTH + 8
                    && y <= top + CONTENT_HEIGHT + 8;
            if (overview) {
                boolean hovered = sameDeed(deed, state.hoveredDeed);
                MapPoint deedA = viewport.mapToScreen(
                        deed.getMinimumX(), deed.getMinimumY());
                MapPoint deedB = viewport.mapToScreen(
                        deed.getMaximumX() + 1.0d,
                        deed.getMaximumY() + 1.0d);
                outline(map, queue, left, top, deedA, deedB,
                        hovered ? 1.0f
                                : deed.isSpawnPoint() ? 1.0f : 0.25f,
                        hovered ? 0.91f
                                : deed.isSpawnPoint() ? 0.8f : 1.0f,
                        hovered ? 0.48f : 0.25f,
                        0.92f, hovered ? 2 : 1);
                if (anchorVisible) {
                    float markerRed = deed.isSpawnPoint() ? 1.0f : 0.92f;
                    float markerGreen = deed.isSpawnPoint() ? 0.76f : 1.0f;
                    float markerBlue = deed.isSpawnPoint() ? 0.12f : 0.60f;
                    map.fillRect(queue,
                            hovered ? 1.0f : 0.08f,
                            hovered ? 0.91f : 0.05f,
                            hovered ? 0.48f : 0.02f,
                            1.0f, x - 3, y - 3, 7, 7);
                    map.fillRect(queue, markerRed, markerGreen, markerBlue,
                            1.0f, x - 1, y - 1, 3, 3);
                }
                continue;
            }
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
            if (anchorVisible) {
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

    private static void drawMiniMapDeeds(WurmComponent map, Queue queue,
                                         MapViewport viewport,
                                         int left, int top, int size,
                                         List<Deed> deeds) {
        if (deeds == null || deeds.isEmpty()) return;
        int labels = 0;
        for (Deed deed : deeds) {
            MapPoint perimeterA = viewport.mapToScreen(
                    deed.getPerimeterMinimumX(), deed.getPerimeterMinimumY());
            MapPoint perimeterB = viewport.mapToScreen(
                    deed.getPerimeterMaximumX() + 1.0d,
                    deed.getPerimeterMaximumY() + 1.0d);
            outline(map, queue, left, top, size, size,
                    perimeterA, perimeterB,
                    0.25f, 0.78f, 1.0f, 0.55f, 1);
            MapPoint deedA = viewport.mapToScreen(
                    deed.getMinimumX(), deed.getMinimumY());
            MapPoint deedB = viewport.mapToScreen(
                    deed.getMaximumX() + 1.0d,
                    deed.getMaximumY() + 1.0d);
            outline(map, queue, left, top, size, size, deedA, deedB,
                    deed.isSpawnPoint() ? 1.0f : 0.25f,
                    deed.isSpawnPoint() ? 0.8f : 1.0f,
                    0.25f, 0.92f, 2);

            MapPoint anchor = viewport.mapToScreen(
                    deed.getX() + 0.5d, deed.getY() + 0.5d);
            int anchorX = left + (int) Math.round(anchor.getX());
            int anchorY = top + (int) Math.round(anchor.getY());
            if (anchorX < left - 8 || anchorY < top - 8
                    || anchorX > left + size + 8
                    || anchorY > top + size + 8) continue;
            float markerRed = deed.isSpawnPoint() ? 1.0f : 0.92f;
            float markerGreen = deed.isSpawnPoint() ? 0.76f : 1.0f;
            float markerBlue = deed.isSpawnPoint() ? 0.12f : 0.60f;
            map.fillRect(queue, 0.08f, 0.05f, 0.02f, 0.92f,
                    anchorX - 4, anchorY - 4, 9, 9);
            map.fillRect(queue, markerRed, markerGreen, markerBlue, 1.0f,
                    anchorX - 2, anchorY - 2, 5, 5);
            if (labels++ < 80) {
                text(queue, deed.getName(), anchorX + 6, anchorY - 4,
                        1.0f, 0.94f, 0.70f, 1.0f,
                        left, top, size, size);
            }
        }
    }

    private static void drawHighways(Queue queue, MapViewport viewport,
                                     int left, int top,
                                     int clipWidth, int clipHeight,
                                     HighwayTileIndex index) {
        if (index == null || index.isEmpty()) return;
        List<HighwayTileIndex.Segment> segments = index.getSegments();
        int maximum = viewport.getPixelsPerTile() < OVERVIEW_PIXELS_PER_TILE
                ? MAXIMUM_OVERVIEW_HIGHWAY_SEGMENTS : Integer.MAX_VALUE;
        MapPoint visibleFirst=viewport.screenToMap(-3,-3);
        MapPoint visibleLast=viewport.screenToMap(clipWidth+3,clipHeight+3);
        int selectionAccumulator = 0;
        for (HighwayTileIndex.Segment segment : segments) {
            if (segments.size() > maximum) {
                selectionAccumulator += maximum;
                if (selectionAccumulator < segments.size()) continue;
                selectionAccumulator -= segments.size();
            }
            // Most server roads are outside the tiny player-centred viewport.
            // Reject them before allocating projected points and clip arrays.
            if(Math.max(segment.getStartX(),segment.getEndX())+.5<visibleFirst.getX()
                    || Math.min(segment.getStartX(),segment.getEndX())+.5>visibleLast.getX()
                    || Math.max(segment.getStartY(),segment.getEndY())+.5<visibleFirst.getY()
                    || Math.min(segment.getStartY(),segment.getEndY())+.5>visibleLast.getY())continue;
            MapPoint a = viewport.mapToScreen(segment.getStartX() + 0.5d,
                    segment.getStartY() + 0.5d);
            MapPoint b = viewport.mapToScreen(segment.getEndX() + 0.5d,
                    segment.getEndY() + 0.5d);
            float[] clipped = clipLine(
                    left + (float) a.getX(), top + (float) a.getY(),
                    left + (float) b.getX(), top + (float) b.getY(),
                    left, top, left + clipWidth, top + clipHeight);
            if (clipped == null) continue;
            float red = 0.96f, green = 0.76f, blue = 0.22f;
            float width = 2.0f;
            if (segment.getKind() == HighwayTileIndex.Kind.BRIDGE) {
                red = 0.25f; green = 0.92f; blue = 1.0f; width = 3.0f;
            } else if (segment.getKind() == HighwayTileIndex.Kind.TUNNEL) {
                red = 1.0f; green = 0.3f; blue = 0.86f; width = 3.0f;
            }
            line(queue, clipped[0], clipped[1], clipped[2], clipped[3],
                    width, red, green, blue, 0.92f);
        }
    }

    private static void drawNavigationLine(Queue queue,
                                           MapViewport viewport,
                                           int left, int top,
                                           int width, int height) {
        drawNavigationLine(queue, viewport, left, top, width, height, false);
    }

    private static void drawNavigationLine(Queue queue, MapViewport viewport,
                                           int left, int top, int width, int height,
                                           boolean cave) {
        NavigationTarget active = activeNavigationTarget();
        if (active == null || active.getCoordinate() == null) return;
        GroundNavigationRouteEffect.RouteSnapshot route =
                cave ? WurmWaypointerRuntime.currentCaveNavigationRoute()
                        : WurmWaypointerRuntime.currentNavigationRoute();
        if (route == null || route.getPointCount() < 2) return;
        MarkerStyle style = active.getMarkerStyle();
        float red = style == null ? 1.0f : style.getRed();
        float green = style == null ? 0.25f : style.getGreen();
        float blue = style == null ? 0.20f : style.getBlue();
        float grey = red * 0.2126f + green * 0.7152f + blue * 0.0722f;
        MapPoint emitted = viewport.mapToScreen(
                route.getTileX(0) + 0.5d,
                route.getTileY(0) + 0.5d);
        for (int index = 1; index < route.getPointCount(); index++) {
            MapPoint next = viewport.mapToScreen(
                    route.getTileX(index) + 0.5d,
                    route.getTileY(index) + 0.5d);
            if (cave && !navigationSegmentOnLayer(route, index, -1)) {
                emitted = next;
                continue;
            }
            double dx = next.getX() - emitted.getX();
            double dy = next.getY() - emitted.getY();
            boolean last = index == route.getPointCount() - 1;
            boolean layerBoundary = route.getLayer(index - 1) != route.getLayer(index)
                    || (!last && route.getLayer(index) != route.getLayer(index + 1));
            if (!last && !layerBoundary && dx * dx + dy * dy < 0.75d * 0.75d) continue;
            boolean underground = !cave && (route.getLayer(index - 1) < 0
                    || route.getLayer(index) < 0);
            float[] clipped = clipLine(
                    left + (float) emitted.getX(),
                    top + (float) emitted.getY(),
                    left + (float) next.getX(),
                    top + (float) next.getY(),
                    left, top, left + width, top + height);
            if (clipped != null) {
                line(queue, clipped[0], clipped[1], clipped[2], clipped[3],
                        5.0f, 0.0f, 0.0f, 0.0f, 0.72f);
                line(queue, clipped[0], clipped[1], clipped[2], clipped[3],
                        3.0f, underground ? (red + grey) * 0.5f : red,
                        underground ? (green + grey) * 0.5f : green,
                        underground ? (blue + grey) * 0.5f : blue, 0.98f);
            }
            emitted = next;
        }
    }

    static boolean navigationSegmentOnLayer(GroundNavigationRouteEffect.RouteSnapshot route,
                                             int endIndex, int layer) {
        return route.getLayer(endIndex - 1) == layer && route.getLayer(endIndex) == layer;
    }

    private static NavigationTarget activeNavigationTarget() {
        NavigationRenderFrame frame = WurmWaypointerRuntime
                .currentNavigationFrame();
        return frame == null || frame.getSnapshot() == null
                ? null : frame.getSnapshot().getActiveNavigator();
    }

    /** Liang-Barsky clipping keeps distant targets from creating huge quads. */
    private static float[] clipLine(float x1, float y1, float x2, float y2,
                                    float minimumX, float minimumY,
                                    float maximumX, float maximumY) {
        double deltaX = x2 - x1;
        double deltaY = y2 - y1;
        double[] p = {-deltaX, deltaX, -deltaY, deltaY};
        double[] q = {x1 - minimumX, maximumX - x1,
                y1 - minimumY, maximumY - y1};
        double start = 0.0d;
        double end = 1.0d;
        for (int index = 0; index < p.length; index++) {
            if (Math.abs(p[index]) < 0.000001d) {
                if (q[index] < 0.0d) return null;
                continue;
            }
            double ratio = q[index] / p[index];
            if (p[index] < 0.0d) {
                if (ratio > end) return null;
                start = Math.max(start, ratio);
            } else {
                if (ratio < start) return null;
                end = Math.min(end, ratio);
            }
        }
        return new float[]{
                (float) (x1 + start * deltaX),
                (float) (y1 + start * deltaY),
                (float) (x1 + end * deltaX),
                (float) (y1 + end * deltaY)};
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
            if (hovered || isCustomMapMark(record)
                    || (viewport.getPixelsPerTile() >= 1.0d
                    && labels++ < 60)) {
                text(queue, record.getName(), x + 6, y - 4,
                        red, green, blue, 1.0f, left, top);
            }
        }
    }

    private static void drawMiniMapWaypoints(
            WurmComponent map, Queue queue, MapViewport viewport,
            int left, int top, int size, WaypointRevisionSnapshot snapshot,
            ServerIdentity currentServer, String currentUser) {
        drawMiniMapWaypoints(map, queue, viewport, left, top, size, snapshot,
                currentServer, currentUser, null);
    }

    private static void drawMiniMapWaypoints(
            WurmComponent map, Queue queue, MapViewport viewport,
            int left, int top, int size, WaypointRevisionSnapshot snapshot,
            ServerIdentity currentServer, String currentUser, WaypointLayer layer) {
        if (snapshot == null) return;
        int customLabels = 0;
        for (WaypointRecord record : snapshot.getRecords()) {
            if (!visibleWaypoint(record, currentServer, currentUser)) continue;
            WaypointCoordinate coordinate = record.getCoordinate();
            if (layer != null && coordinate.getLayer() != layer) continue;
            MapPoint point = viewport.mapToScreen(
                    coordinate.getTileX() + 0.5d,
                    coordinate.getTileY() + 0.5d);
            int x = left + (int) Math.round(point.getX());
            int y = top + (int) Math.round(point.getY());
            if (x < left - 6 || y < top - 6 || x > left + size + 6
                    || y > top + size + 6) continue;
            MarkerStyle style = record.getMarkerStyle();
            float red = style == null ? 1.0f : style.getRed();
            float green = style == null ? 0.85f : style.getGreen();
            float blue = style == null ? 0.2f : style.getBlue();
            if (isSurroundingsMark(record)) {
                drawSurroundingsMark(map, queue, x, y, red, green, blue);
            } else {
                map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.82f,
                        x - 3, y - 3, 7, 7);
                map.fillRect(queue, red, green, blue, 1.0f,
                        x - 2, y - 2, 5, 5);
            }
            if (isCustomMapMark(record) && customLabels++ < 24) {
                text(queue, record.getName(), x + 5, y - 3,
                        red, green, blue, 1.0f, left, top);
            }
        }
    }

    private static void drawSurroundingsMark(WurmComponent map, Queue queue,
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

    private static void drawTopographicContours(Queue queue, MiniMapContourOverlay contours,
                                                 int left, int top, int size) {
        if (contours == null) return;
        Renderer.texturedQuadAlphaBlend(queue, contours.texture, 1, 1, 1, 1,
                left+contours.x, top+contours.y, contours.size, contours.size, 0, 0, 1, 1);
    }

    private static void drawTileBorders(WurmComponent map, Queue queue,
                                        MapViewport viewport, int left, int top,
                                        int width, int height) {
        MapPoint first = viewport.screenToMap(0, 0);
        MapPoint last = viewport.screenToMap(width, height);
        int gridLeft = Math.max(left, left + (int) Math.round(viewport.getImageLeft()));
        int gridTop = Math.max(top, top + (int) Math.round(viewport.getImageTop()));
        int gridRight = Math.min(left + width, left + (int) Math.round(
                viewport.getImageLeft() + viewport.getImageWidth()));
        int gridBottom = Math.min(top + height, top + (int) Math.round(
                viewport.getImageTop() + viewport.getImageHeight()));
        if (gridRight <= gridLeft || gridBottom <= gridTop) return;
        for (int tileX = Math.max(0, (int) Math.ceil(first.getX()));
                tileX <= Math.min(viewport.getMapWidth(), (int) Math.floor(last.getX())); tileX++) {
            int x = left + (int) Math.round(viewport.mapToScreen(tileX, 0).getX());
            map.fillRect(queue, 0, 0, 0, 0.37f, x, gridTop, 1, gridBottom - gridTop);
        }
        for (int tileY = Math.max(0, (int) Math.ceil(first.getY()));
                tileY <= Math.min(viewport.getMapHeight(), (int) Math.floor(last.getY())); tileY++) {
            int y = top + (int) Math.round(viewport.mapToScreen(0, tileY).getY());
            map.fillRect(queue, 0, 0, 0, 0.37f, gridLeft, y, gridRight - gridLeft, 1);
        }
    }

    private static void drawPlayer(WorldMap map, Queue queue,
                                   MapViewport viewport, int left, int top) {
        drawPlayer(map, queue, viewport, left, top,
                CONTENT_WIDTH, CONTENT_HEIGHT);
    }

    private static void drawPlayer(WurmComponent map, Queue queue,
                                   MapViewport viewport, int left, int top,
                                   int width, int height) {
        MapPoint point = viewport.mapToScreen(
                WurmWaypointerRuntime.currentPlayerTileX() + 0.5d,
                WurmWaypointerRuntime.currentPlayerTileY() + 0.5d);
        int x = left + (int) Math.round(point.getX());
        int y = top + (int) Math.round(point.getY());
        if (x < left || y < top || x >= left + width
                || y >= top + height) return;
        float[] arrow = PlayerArrowGeometry.points(x, y,
                WurmWaypointerRuntime.currentPlayerHeadingDegrees());
        for (int pass = 0; pass < 2; pass++) {
            float colour = pass == 0 ? 0.0f : 1.0f;
            float thickness = pass == 0 ? PlayerArrowGeometry.OUTLINE_WIDTH_PIXELS
                    : PlayerArrowGeometry.STROKE_WIDTH_PIXELS;
            for (int end = 1; end < 4; end++) {
                line(queue, arrow[end * 2], arrow[end * 2 + 1], arrow[0], arrow[1],
                        thickness, colour, colour, colour, pass == 0 ? 0.9f : 1.0f);
            }
        }
    }

    private static void drawStatus(WorldMap map, Queue queue, State state,
                                   ServerMapProfile profile, int left, int top) {
        map.fillRect(queue, 0.16f, 0.09f, 0.035f, 0.90f,
                left + 22, top + CONTENT_HEIGHT - 42,
                CONTENT_WIDTH - 44, 20);
        if (!homeView(state) || state.allMapsButtonHover) {
            String help = state.allMapsButtonHover ? Messages.text("Show all five server maps")
                    : galleryVisible(state) ? Messages.text("Choose a map to view")
                    : mapWorldName(profile) + " | " + (state.hoverInside ? "X=" + state.hoverTileX + " Y=" + state.hoverTileY + " | " : "")
                    + Messages.text(state.browsedMapReady ? "Wheel: zoom | drag: pan | CENTER: return to your character" : "Loading full map...");
            text(queue, help, left + 28, top + CONTENT_HEIGHT - 27, 1f, .92f, .72f, 1f, left, top); return;
        }
        String coordinate = state.hoverInside
                ? "X=" + state.hoverTileX + " Y=" + state.hoverTileY
                        + "  Tile: " + hoveredTileDescription(state) + "  "
                : "";
        String action = state.zoomFactorButtonHover
                ? "Click: cycle shared map and mini-map wheel speed (1X / 2X / 4X)"
                : state.navigationLineButtonHover
                ? "Click: turn active navigation line "
                        + (MiniMapWindowBridge.isNavigationLineVisible()
                        ? "off" : "on")
                : state.miniMapButtonHover
                ? "Click: turn mini-map "
                        + (MiniMapWindowBridge.isEnabled() ? "off" : "on")
                : state.hoveredLayerButton != null
                ? layerButtonHelp(state.hoveredLayerButton,
                        layerVisible(state, state.hoveredLayerButton))
                : state.searchButtonHover
                ? "Search deeds"
                : state.closeButtonHover
                ? "Close map"
                : state.hoveredDeed != null
                ? "Deed: " + state.hoveredDeed.getName()
                        + " | click: information"
                : state.hoveredWaypointId != null
                ? "Waypoint: " + state.hoveredWaypointName
                        + (state.hoveredWaypointEditable
                        ? " | click: edit" : " | managed marker")
                : "Wheel: zoom | drag: pan | click: waypoint | right-click: custom mark";
        String zoom = String.format(Locale.ENGLISH, "Zoom %.2f px/tile | ",
                Double.valueOf(state.viewport.getPixelsPerTile()));
        text(queue, zoom + coordinate + action,
                left + 28, top + CONTENT_HEIGHT - 27,
                1.0f, 0.92f, 0.72f, 1.0f, left, top);
    }

    private static void drawLayerButtons(WorldMap map, Queue queue, State state, int left, int top) {
        for (MapOverlayVisibility.Layer layer : LAYER_BUTTONS)
            WaypointerUi.paintButton(map, queue, "layer" + layer, layerButtonLabel(layer), state.hoveredLayerButton == layer,
                    state.pressedLayerButton == layer, layerAvailable(state, layer), layerVisible(state, layer), layerButtonLeft(left, layer), top + SEARCH_BUTTON_TOP, LAYER_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT, toolbarGroup());
    }

    static WaypointerButtonGroup toolbarGroup() {
        if (toolbarGroup == null || toolbarLanguageRevision != Messages.revision()) {
            toolbarGroup = WaypointerButtonGroup.painted("main-map.controls", LAYER_BUTTON_HEIGHT,
                    Messages.texts(new String[]{"ALL MAPS ON SERVER", "Zoom speed: 1X", "Zoom speed: 2X", "Zoom speed: 4X",
                            "CENTER", "NAV LINE", "MINI MAP", "DEEDS", "ROADS", "MARKS"}),
                    new int[]{ALL_MAPS_BUTTON_WIDTH, ZOOM_FACTOR_BUTTON_WIDTH, ZOOM_FACTOR_BUTTON_WIDTH, ZOOM_FACTOR_BUTTON_WIDTH,
                            72, NAV_LINE_BUTTON_WIDTH, MINI_MAP_BUTTON_WIDTH, LAYER_BUTTON_WIDTH, LAYER_BUTTON_WIDTH, LAYER_BUTTON_WIDTH});
            toolbarLanguageRevision = Messages.revision();
        }
        return toolbarGroup;
    }
    private static boolean insideAllMapsButton(WorldMap map, int x, int y) {
        int left = map.x + CONTENT_OFFSET_X + MAIN_MAP_FRAME_INSET;
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && x < left + ALL_MAPS_BUTTON_WIDTH && y >= top && y < top + LAYER_BUTTON_HEIGHT;
    }
    private static void drawAllMapsButton(WorldMap map, Queue queue, State state, int left, int top) {
        WaypointerUi.paintButton(map, queue, "AllMaps", "ALL MAPS ON SERVER", state.allMapsButtonHover,
                state.allMapsButtonPressed, true, galleryVisible(state), left + MAIN_MAP_FRAME_INSET,
                top + SEARCH_BUTTON_TOP, ALL_MAPS_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT, toolbarGroup());
    }

    private static boolean insideCenterButton(WorldMap map, int x, int y) {
        int left = navigationLineButtonLeft(map.x + CONTENT_OFFSET_X) - LAYER_BUTTON_GAP - 72;
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && x < left + 72 && y >= top && y < top + LAYER_BUTTON_HEIGHT;
    }

    private static boolean insideZoomFactorButton(WorldMap map, int x, int y) {
        int left = zoomFactorButtonLeft(map.x + CONTENT_OFFSET_X);
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && x < left + ZOOM_FACTOR_BUTTON_WIDTH
                && y >= top && y < top + LAYER_BUTTON_HEIGHT;
    }

    private static int zoomFactorButtonLeft(int contentLeft) {
        return navigationLineButtonLeft(contentLeft) - LAYER_BUTTON_GAP - 72
                - LAYER_BUTTON_GAP - ZOOM_FACTOR_BUTTON_WIDTH;
    }

    private static void drawZoomFactorButton(WorldMap map, Queue queue, State state, int left, int top) {
        WaypointerUi.paintButton(map, queue, "ZoomFactor", "Zoom speed: " + MiniMapWindowBridge.getZoomFactor() + "X", state.zoomFactorButtonHover, state.zoomFactorButtonPressed, true, false, zoomFactorButtonLeft(left), top + SEARCH_BUTTON_TOP, ZOOM_FACTOR_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT, toolbarGroup());
    }

    private static void drawCenterButton(WorldMap map, Queue queue, State state, int left, int top) {
        WaypointerUi.paintButton(map, queue, "Center", "CENTER", state.centerButtonHover, state.centerButtonPressed, true, false, navigationLineButtonLeft(left) - LAYER_BUTTON_GAP - 72, top + SEARCH_BUTTON_TOP, 72, LAYER_BUTTON_HEIGHT, toolbarGroup());
    }

    private static void drawMiniMapButton(WorldMap map, Queue queue, State state, int left, int top) {
        WaypointerUi.paintButton(map, queue, "MiniMap", "MINI MAP", state.miniMapButtonHover, state.miniMapButtonPressed, true, MiniMapWindowBridge.isEnabled(), miniMapButtonLeft(left), top + SEARCH_BUTTON_TOP, MINI_MAP_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT, toolbarGroup());
    }

    private static void drawNavigationLineButton(WorldMap map, Queue queue, State state, int left, int top) {
        WaypointerUi.paintButton(map, queue, "NavigationLine", "NAV LINE", state.navigationLineButtonHover, state.navigationLineButtonPressed, homeView(state), MiniMapWindowBridge.isNavigationLineVisible(), navigationLineButtonLeft(left), top + SEARCH_BUTTON_TOP, NAV_LINE_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT, toolbarGroup());
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

    static List<String> mapHoverLines(MapViewport viewport,
                                      int screenX, int screenY,
                                      boolean showDeeds,
                                      boolean showWaypoints) {
        List<String> lines = new ArrayList<String>(3);
        if (viewport == null) return lines;
        MapPoint point = viewport.screenToMap(screenX, screenY);
        if (!viewport.containsMapPoint(point)) return lines;
        int tileX = (int) Math.floor(point.getX());
        int tileY = (int) Math.floor(point.getY());

        String live = WurmWaypointerRuntime.serverMapLiveTileDescription(
                tileX, tileY);
        if (live != null && !live.isEmpty()) {
            lines.add("X=" + tileX + " Y=" + tileY + " | Tile: " + live);
        } else {
            PreparedSurface current = prepared;
            SurfaceTileIndex index = current == null ? null : current.tileIndex;
            String broad = index == null ? "" : index.describe(tileX, tileY);
            lines.add("X=" + tileX + " Y=" + tileY + " | Tile: "
                    + (broad.isEmpty() ? "Unknown terrain"
                    : broad + " (published map)"));
        }

        if (showDeeds) {
            String deed = deedHoverText(viewport, screenX, screenY,
                    tileX, tileY);
            if (!deed.isEmpty()) lines.add(deed);
        }
        if (showWaypoints) {
            WaypointRecord nearest = nearestWaypoint(
                    viewport, screenX, screenY);
            if (nearest != null) lines.add(waypointHoverText(nearest));
        }
        return lines;
    }

    private static WaypointRecord nearestWaypoint(
            MapViewport viewport, int screenX, int screenY) {
        return nearestWaypoint(viewport, screenX, screenY, null);
    }

    static String caveWaypointHover(MapViewport viewport, int screenX, int screenY) {
        WaypointRecord record = nearestWaypoint(viewport, screenX, screenY, WaypointLayer.CAVE);
        return record == null ? "" : waypointHoverText(record);
    }

    private static WaypointRecord nearestWaypoint(
            MapViewport viewport, int screenX, int screenY, WaypointLayer layer) {
        WaypointRevisionSnapshot snapshot = WurmWaypointerRuntime
                .serverMapWaypoints();
        if (snapshot == null) return null;
        ServerIdentity server = WurmWaypointerRuntime.currentServerIdentity();
        String user = WurmWaypointerRuntime.currentPlayerName();
        WaypointRecord best = null;
        double bestDistance = WAYPOINT_HIT_RADIUS * WAYPOINT_HIT_RADIUS;
        for (WaypointRecord record : snapshot.getRecords()) {
            if (!visibleWaypoint(record, server, user)) continue;
            WaypointCoordinate coordinate = record.getCoordinate();
            if (layer != null && coordinate.getLayer() != layer) continue;
            MapPoint marker = viewport.mapToScreen(
                    coordinate.getTileX() + 0.5d,
                    coordinate.getTileY() + 0.5d);
            double deltaX = marker.getX() - screenX;
            double deltaY = marker.getY() - screenY;
            double distance = deltaX * deltaX + deltaY * deltaY;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = record;
            }
        }
        return best;
    }

    private static String waypointHoverText(WaypointRecord record) {
        String state = title(record.getResolution().name());
        NavigationTarget active = activeNavigationTarget();
        if (active != null && active.getKey() != null
                && record.getId().equals(active.getKey().getWaypointId())) {
            state = "Active NAV, " + state;
        } else if (record.isTemporary()) {
            state = "Temporary, " + state;
        }
        return "Waypoint: " + record.getName() + " | " + state;
    }

    private static String deedHoverText(MapViewport viewport,
                                        int screenX, int screenY,
                                        int tileX, int tileY) {
        ServerMapSnapshot snapshot = WurmWaypointerRuntime.serverMapSnapshot();
        if (snapshot == null || snapshot.getDeeds() == null) return "";
        Deed best = null;
        String bestState = "";
        double bestScore = Double.POSITIVE_INFINITY;
        for (Deed deed : snapshot.getDeeds()) {
            String state = "";
            double priority = 0.0d;
            if (inside(tileX, tileY, deed.getMinimumX(), deed.getMinimumY(),
                    deed.getMaximumX(), deed.getMaximumY())) {
                state = "inside settlement";
            } else if (inside(tileX, tileY,
                    deed.getPerimeterMinimumX(), deed.getPerimeterMinimumY(),
                    deed.getPerimeterMaximumX(),
                    deed.getPerimeterMaximumY())) {
                state = "inside perimeter";
                priority = 1_000_000.0d;
            } else {
                MapPoint marker = viewport.mapToScreen(
                        deed.getX() + 0.5d, deed.getY() + 0.5d);
                double deltaX = marker.getX() - screenX;
                double deltaY = marker.getY() - screenY;
                double distance = deltaX * deltaX + deltaY * deltaY;
                if (distance > DEED_HIT_RADIUS * DEED_HIT_RADIUS) continue;
                state = "settlement centre";
                priority = 2_000_000.0d;
            }
            double centerX = deed.getX() - tileX;
            double centerY = deed.getY() - tileY;
            double score = priority + centerX * centerX + centerY * centerY;
            if (score < bestScore) {
                bestScore = score;
                best = deed;
                bestState = state;
            }
        }
        return best == null ? "" : "Deed: " + best.getName()
                + " | " + bestState;
    }

    private static boolean inside(int x, int y, int minimumX, int minimumY,
                                  int maximumX, int maximumY) {
        return x >= minimumX && y >= minimumY
                && x <= maximumX && y <= maximumY;
    }

    private static String title(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ENGLISH)
                .replace('_', ' ');
        return clean.isEmpty() ? clean
                : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static void drawSearchButton(WorldMap map, Queue queue, State state, int left, int top) {
        WaypointerUi.paintButton(map, queue, "Search", "@search", state.searchButtonHover, false, homeView(state), false, searchButtonLeft(left), top + SEARCH_BUTTON_TOP, SEARCH_BUTTON_SIZE, LAYER_BUTTON_HEIGHT);
    }

    private static void drawCloseButton(WorldMap map, Queue queue, State state, int left, int top) {
        WaypointerUi.paintButton(map, queue, "Close", "@close", state.closeButtonHover, state.closeButtonPressed, true, false, closeButtonLeft(left), top + SEARCH_BUTTON_TOP, SEARCH_BUTTON_SIZE, LAYER_BUTTON_HEIGHT);
    }

    private static void requestWaypoint(WorldMap map, State state,
                                        int mouseX, int mouseY) {
        if (!homeView(state)) return;
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
        if (state.hoveredWaypointId != null) {
            if (state.hoveredWaypointEditable) {
                WurmWaypointerRuntime.serverMapWaypointEditRequested(
                        state.hoveredWaypointId);
            }
            return;
        }
        MapPoint point = state.viewport.screenToMap(
                mouseX - map.x - CONTENT_OFFSET_X,
                mouseY - map.y - CONTENT_OFFSET_Y);
        requestWaypointAt(point, state.viewport);
    }

    static void requestWaypointAt(MapViewport viewport,
                                  int screenX, int screenY) {
        if (viewport == null) return;
        requestWaypointAt(viewport.screenToMap(screenX, screenY), viewport);
    }

    static void requestCustomMarkAt(MapViewport viewport,
                                    int screenX, int screenY) {
        requestCustomMarkAt(viewport, screenX, screenY, WaypointLayer.SURFACE);
    }

    static void requestCustomMarkAt(MapViewport viewport,
                                    int screenX, int screenY, WaypointLayer layer) {
        if (viewport == null) return;
        MapPoint point = viewport.screenToMap(screenX, screenY);
        if (point == null || !viewport.containsMapPoint(point)) return;
        HeadsUpDisplay current = WurmComponent.hud;
        if (current == null) return;
        CustomMapMarkWindowBridge.open(current,
                (int) Math.floor(point.getX()),
                (int) Math.floor(point.getY()), layer);
    }

    private static void requestWaypointAt(MapPoint point,
                                          MapViewport viewport) {
        if (point == null || viewport == null
                || !viewport.containsMapPoint(point)) return;
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
        state.allMapsButtonHover = insideAllMapsButton(map, mouseX, mouseY);
        state.hoveredGalleryCard = galleryVisible(state) ? galleryCardAt(map, mouseX, mouseY) : -1;
        state.zoomFactorButtonHover = insideZoomFactorButton(map, mouseX, mouseY);
        state.centerButtonHover = insideCenterButton(map, mouseX, mouseY);
        state.searchButtonHover = insideSearchButton(map, mouseX, mouseY);
        state.closeButtonHover = insideCloseButton(map, mouseX, mouseY);
        state.miniMapButtonHover = insideMiniMapButton(
                map, mouseX, mouseY);
        state.navigationLineButtonHover = insideNavigationLineButton(
                map, mouseX, mouseY);
        state.hoveredLayerButton = layerButtonAt(map, mouseX, mouseY);
        if (galleryVisible(state) || state.allMapsButtonHover || state.zoomFactorButtonHover || state.centerButtonHover || state.searchButtonHover || state.closeButtonHover
                || state.miniMapButtonHover
                || state.navigationLineButtonHover
                || state.hoveredLayerButton != null) {
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
            if (homeView(state)) {
                updateHoveredWaypoint(map, state, mouseX, mouseY);
            } else { clearHoveredWaypoint(state); }
            updateHoveredDeed(map, state, mouseX, mouseY);
        } else {
            clearHoveredWaypoint(state);
            state.hoveredDeed = null;
        }
    }

    private static void updateHoveredDeed(WorldMap map, State state,
                                          int mouseX, int mouseY) {
        state.hoveredDeed = null;
        if (!layerVisible(state, MapOverlayVisibility.Layer.DEEDS)) return;
        ServerMapSnapshot snapshot = homeView(state) ? WurmWaypointerRuntime.serverMapSnapshot()
                : WurmWaypointerRuntime.browsedMapSnapshot(galleryProfile(state.browsedGalleryIndex));
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
        if (!layerVisible(state, MapOverlayVisibility.Layer.WAYPOINTS)) {
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
        outline(map, queue, left, top, CONTENT_WIDTH, CONTENT_HEIGHT,
                a, b, red, green, blue, alpha, thickness);
    }

    private static void outline(WurmComponent map, Queue queue,
                                int left, int top,
                                int clipWidth, int clipHeight,
                                MapPoint a, MapPoint b, float red, float green,
                                float blue, float alpha, int thickness) {
        int x1 = left + (int) Math.floor(Math.min(a.getX(), b.getX()));
        int y1 = top + (int) Math.floor(Math.min(a.getY(), b.getY()));
        int x2 = left + (int) Math.ceil(Math.max(a.getX(), b.getX()));
        int y2 = top + (int) Math.ceil(Math.max(a.getY(), b.getY()));
        fillClipped(map, queue, red, green, blue, alpha, x1, y1,
                x2 - x1, thickness, left, top, clipWidth, clipHeight);
        fillClipped(map, queue, red, green, blue, alpha,
                x1, y2 - thickness, x2 - x1, thickness,
                left, top, clipWidth, clipHeight);
        fillClipped(map, queue, red, green, blue, alpha, x1, y1,
                thickness, y2 - y1, left, top, clipWidth, clipHeight);
        fillClipped(map, queue, red, green, blue, alpha,
                x2 - thickness, y1, thickness, y2 - y1,
                left, top, clipWidth, clipHeight);
    }

    private static void fillClipped(WurmComponent map, Queue queue,
                                    float red, float green, float blue, float alpha,
                                    int x, int y, int width, int height,
                                    int left, int top,
                                    int clipWidth, int clipHeight) {
        int clipX = Math.max(left, x);
        int clipY = Math.max(top, y);
        int clipRight = Math.min(left + clipWidth, x + width);
        int clipBottom = Math.min(top + clipHeight, y + height);
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
        text(queue, value, x, y, red, green, blue, alpha,
                left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
    }

    private static void text(Queue queue, String value, int x, int y,
                             float red, float green, float blue, float alpha,
                             int left, int top, int width, int height) {
        if (value == null || value.isEmpty() || x < left || y < top
                || x >= left + width || y >= top + height) return;
        TextFont font = WaypointerFonts.body();
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
        int left = searchButtonLeft(map.x + CONTENT_OFFSET_X);
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && y >= top && x < left + SEARCH_BUTTON_SIZE
                && y < top + SEARCH_BUTTON_SIZE;
    }

    private static boolean insideCloseButton(WorldMap map, int x, int y) {
        if (map == null) return false;
        int left = closeButtonLeft(map.x + CONTENT_OFFSET_X);
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && y >= top && x < left + SEARCH_BUTTON_SIZE
                && y < top + SEARCH_BUTTON_SIZE;
    }

    private static boolean insideMiniMapButton(WorldMap map, int x, int y) {
        if (map == null) return false;
        int left = miniMapButtonLeft(map.x + CONTENT_OFFSET_X);
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && y >= top && x < left + MINI_MAP_BUTTON_WIDTH
                && y < top + LAYER_BUTTON_HEIGHT;
    }

    private static boolean insideNavigationLineButton(
            WorldMap map, int x, int y) {
        if (map == null) return false;
        int left = navigationLineButtonLeft(map.x + CONTENT_OFFSET_X);
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && y >= top && x < left + NAV_LINE_BUTTON_WIDTH
                && y < top + LAYER_BUTTON_HEIGHT;
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
        int searchLeft = searchButtonLeft(contentLeft);
        int index = layer.ordinal();
        return searchLeft - LAYER_BUTTON_GAP
                - (LAYER_BUTTON_COUNT - index) * LAYER_BUTTON_WIDTH
                - (LAYER_BUTTON_COUNT - index - 1) * LAYER_BUTTON_GAP;
    }

    private static int miniMapButtonLeft(int contentLeft) {
        return layerButtonLeft(contentLeft, MapOverlayVisibility.Layer.DEEDS)
                - LAYER_BUTTON_GAP - MINI_MAP_BUTTON_WIDTH;
    }

    private static int navigationLineButtonLeft(int contentLeft) {
        return miniMapButtonLeft(contentLeft)
                - LAYER_BUTTON_GAP - NAV_LINE_BUTTON_WIDTH;
    }

    private static int closeButtonLeft(int contentLeft) {
        return contentLeft + CONTENT_WIDTH
                - SEARCH_BUTTON_RIGHT - SEARCH_BUTTON_SIZE;
    }

    private static int searchButtonLeft(int contentLeft) {
        return closeButtonLeft(contentLeft)
                - LAYER_BUTTON_GAP - SEARCH_BUTTON_SIZE;
    }

    private static boolean layerVisible(State state,
                                        MapOverlayVisibility.Layer layer) {
        if (!homeView(state)) return layerAvailable(state, layer) && state.overlays.isVisible(layer);
        if (layer == MapOverlayVisibility.Layer.DEEDS) {
            return MiniMapWindowBridge.areDeedsVisible();
        }
        if (layer == MapOverlayVisibility.Layer.HIGHWAYS) {
            return MiniMapWindowBridge.areRoadsVisible();
        }
        return state.overlays.isVisible(layer);
    }

    private static void toggleLayer(State state,
                                    MapOverlayVisibility.Layer layer) {
        if (!homeView(state)) {
            if (layerAvailable(state, layer)) state.overlays.toggle(layer);
            return;
        }
        if (layer == MapOverlayVisibility.Layer.DEEDS) {
            MiniMapWindowBridge.toggleDeeds();
        } else if (layer == MapOverlayVisibility.Layer.HIGHWAYS) {
            MiniMapWindowBridge.toggleRoads();
        } else {
            state.overlays.toggle(layer);
        }
    }

    private static boolean layerAvailable(State state, MapOverlayVisibility.Layer layer) {
        return !galleryVisible(state) && (homeView(state)
                || layer == MapOverlayVisibility.Layer.DEEDS
                || layer == MapOverlayVisibility.Layer.HIGHWAYS);
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

    private static boolean isCustomMapMark(WaypointRecord record) {
        return record != null && record.getSourceType()
                == org.waypoints.next.model.WaypointSourceType.CUSTOM_MAP_MARK;
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
        private volatile ResourceTexture texture;

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
        private volatile ResourceTexture texture;

        private PreparedArtwork(WaypointerFileResourceUrl url, String label) {
            this.url = url;
            this.label = label;
        }
    }

    private static final class State {
        private final String profileId;
        private MapViewport viewport;
        private final MapViewport homeViewport;
        private final Map<Integer,MapViewport> galleryViewports = new HashMap<Integer,MapViewport>();
        private boolean allMaps, loadingGallery, allMapsButtonPressed, allMapsButtonHover, browsedMapReady;
        private int browsedGalleryIndex = -1, pressedGalleryCard = -1, hoveredGalleryCard = -1;
        private final MapOverlayVisibility overlays;
        private boolean dragging;
        private boolean dragged;
        private int pressX;
        private int pressY;
        private int lastX;
        private int lastY;
        private boolean centerButtonPressed;
        private boolean centerButtonHover;
        private boolean zoomFactorButtonPressed;
        private boolean zoomFactorButtonHover;
        private boolean hoverInside;
        private int hoverTileX;
        private int hoverTileY;
        private UUID hoveredWaypointId;
        private String hoveredWaypointName = "";
        private boolean hoveredWaypointEditable;
        private Deed hoveredDeed;
        private boolean searchButtonHover;
        private boolean searchButtonPressed;
        private boolean closeButtonHover;
        private boolean closeButtonPressed;
        private boolean miniMapButtonHover;
        private boolean miniMapButtonPressed;
        private boolean navigationLineButtonHover;
        private boolean navigationLineButtonPressed;
        private MapOverlayVisibility.Layer hoveredLayerButton;
        private MapOverlayVisibility.Layer pressedLayerButton;
        private boolean firstFrameLogged;

        private State(String profileId, MapViewport viewport,
                      MapOverlayVisibility overlays) {
            this.profileId = profileId;
            this.viewport = viewport;
            this.homeViewport = viewport;
            this.overlays = overlays;
        }
    }
}
