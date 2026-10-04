package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.resources.WaypointerFileResourceUrl;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.ResourceTextureLoader;
import com.wurmonline.client.resources.textures.WaypointerTextureFilters;
import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.effects.GroundNavigationRouteEffect;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.WaypointerMiniMapFonts;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.map.Deed;
import org.waypoints.next.map.MapPoint;
import org.waypoints.next.map.MapOverlayVisibility;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.map.SklotopolisMapProfiles;
import org.waypoints.next.map.SurfaceTileIndex;
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
    private static final Path MINI_MAP_FRAME_FILE = Paths.get("mods",
            "wurm-waypointer", "assets", "mini-map-frame.png");
    private static final Path MINI_MAP_BACKGROUND_FILE = Paths.get("mods",
            "wurm-waypointer", "assets", "mini-map-background.png");
    private static final Path MINI_MAP_NAMEPLATE_FILE = Paths.get("mods",
            "wurm-waypointer", "assets", "mini-map-nameplate.png");
    private static final Path MAIN_MAP_FRAME_FILE = Paths.get("mods",
            "wurm-waypointer", "assets", "main-map-frame.png");
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
    private static PreparedArtwork wordmark;
    private static PreparedArtwork miniMapFrame;
    private static PreparedArtwork miniMapBackground;
    private static PreparedArtwork miniMapNameplate;
    private static PreparedArtwork mainMapFrame;
    private static final PreparedArtwork[] mapGallery =
            new PreparedArtwork[MAP_GALLERY_FILES.length];

    private ServerMapWindowBridge() { }

    private static Path galleryFile(String name) {
        return Paths.get("mods", "wurm-waypointer", "assets", "gallery", name);
    }

    /** Called instead of ClusterMap.render; false means render vanilla content. */
    public static boolean render(Queue queue) {
        try {
            HeadsUpDisplay hud = WurmComponent.hud;
            WorldMap map = hud == null ? null : hud.getWorldMap();
            ServerMapSnapshot snapshot = WurmWaypointerRuntime.serverMapSnapshot();
            ServerMapProfile profile = customProfile(snapshot);
            if (map == null || queue == null || profile == null) return false;

            int left = map.x + CONTENT_OFFSET_X;
            int top = map.y + CONTENT_OFFSET_Y;
            PreparedSurface surface = snapshot != null && snapshot.hasSurface()
                    ? prepare(snapshot) : null;
            if (surface == null || !surface.ready || surface.failed) {
                renderLoadingGallery(map, queue, profile, left, top);
                return true;
            }
            if (surface.texture == null) {
                surface.texture = WaypointerTextureFilters
                        .useCrispMagnification(
                                ResourceTextureLoader.getPreparedTexture(
                                        surface.url, surface.request));
            }
            scheduleSurfaceIndex(surface, profile);
            if (surface.texture == null) {
                reportOnce("texture-unavailable",
                        "Prepared server map texture is missing", null);
                renderLoadingGallery(map, queue, profile, left, top);
                return true;
            }
            if (!surface.texture.isValid() && !surface.texture.needReinit()) {
                reportOnce("texture-invalid",
                        "Prepared server map texture cannot be initialized", null);
                renderLoadingGallery(map, queue, profile, left, top);
                return true;
            }

            State state = state(map, profile);
            state.viewport.resize(CONTENT_WIDTH, CONTENT_HEIGHT);
            HeadsUpDisplay.scissor.pushClip(
                    left, top, CONTENT_WIDTH, CONTENT_HEIGHT);
            try {
                drawWaterBacking(map, queue, left, top);
                drawSurface(queue, surface.texture, state.viewport, left, top);
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
        String display = profile.getDisplayName() == null ? ""
                : profile.getDisplayName().trim().replace(' ', '-');
        drawNameplate(queue, miniMapNameplateTexture(),
                map.x + CONTENT_OFFSET_X + 3, map.y,
                Math.max(54, Math.min(CONTENT_WIDTH - 5,
                        MAIN_TITLE_TEXT.getWidth("Map of: " + display) + 30)),
                "Map of: " + display);
    }

    /** Draws the compact player-centred view without taking ownership of it. */
    static boolean renderMiniMap(WurmComponent map, Queue queue,
                                 MapViewport viewport,
                                 ServerMapSnapshot snapshot,
                                 boolean showDeeds,
                                 int left, int top, int size) {
        try {
            if (map == null || queue == null || viewport == null
                    || snapshot == null || snapshot.getProfile() == null
                    || !snapshot.hasSurface() || size < 1) return false;
            PreparedSurface surface = prepare(snapshot);
            if (!surface.ready || surface.failed) return false;
            if (surface.texture == null) {
                surface.texture = WaypointerTextureFilters
                        .useCrispMagnification(
                                ResourceTextureLoader.getPreparedTexture(
                                        surface.url, surface.request));
            }
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
                                         MapViewport viewport, int left, int top, int size) {
        if (MiniMapWindowBridge.isNavigationLineVisible())
            drawNavigationLine(queue, viewport, left, top, size, size);
        drawMiniMapWaypoints(map, queue, viewport, left, top, size,
                WurmWaypointerRuntime.serverMapWaypoints(),
                WurmWaypointerRuntime.currentServerIdentity(),
                WurmWaypointerRuntime.currentPlayerName(), WaypointLayer.CAVE);
        drawPlayer(map, queue, viewport, left, top, size, size);
    }

    private static void drawOverlays(WorldMap map, Queue queue, State state,
                                     ServerMapSnapshot snapshot,
                                     int left, int top) {
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
        if (state.centerButtonPressed) {
            state.centerButtonPressed = false;
            if (insideCenterButton(map, mouseX, mouseY)) {
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
        boolean create = !state.dragged && insideContent(map, mouseX, mouseY);
        state.dragging = false;
        updateHover(map, state, mouseX, mouseY);
        if (create) requestWaypoint(map, state, mouseX, mouseY);
        return true;
    }

    public static boolean rightPressed(WorldMap map, int mouseX, int mouseY) {
        State state = activeState(map);
        if (state == null || !insideContent(map, mouseX, mouseY)) return false;
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
        if (layerButtonAt(map, mouseX, mouseY) != null
                || insideSearchButton(map, mouseX, mouseY)
                || insideCloseButton(map, mouseX, mouseY)
                || insideMiniMapButton(map, mouseX, mouseY)
                || insideNavigationLineButton(map, mouseX, mouseY)
                || insideCenterButton(map, mouseX, mouseY)) return true;
        double steps = -wheelDelta / 3.0d;
        if (steps == 0.0d) steps = wheelDelta < 0 ? 1.0d : -1.0d;
        steps = Math.max(-4.0d, Math.min(4.0d, steps));
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
        if (state.centerButtonHover) {
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
        miniMapFrame = null;
        miniMapBackground = null;
        miniMapNameplate = null;
        mainMapFrame = null;
        for (int index = 0; index < mapGallery.length; index++) {
            mapGallery[index] = null;
        }
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

    static synchronized ResourceTexture miniMapFrameTexture() {
        miniMapFrame = prepareArtwork(miniMapFrame, MINI_MAP_FRAME_FILE,
                "Mini-map frame", "mini-map-frame-prepare");
        return readyArtwork(miniMapFrame, "mini-map-frame");
    }

    static synchronized ResourceTexture miniMapBackgroundTexture() {
        miniMapBackground = prepareArtwork(miniMapBackground,
                MINI_MAP_BACKGROUND_FILE, "Mini-map background",
                "mini-map-background-prepare");
        return readyArtwork(miniMapBackground, "mini-map-background");
    }

    static synchronized ResourceTexture miniMapNameplateTexture() {
        miniMapNameplate = prepareArtwork(miniMapNameplate,
                MINI_MAP_NAMEPLATE_FILE, "Mini-map nameplate",
                "mini-map-nameplate-prepare");
        return readyArtwork(miniMapNameplate, "mini-map-nameplate");
    }

    private static synchronized ResourceTexture mainMapFrameTexture() {
        mainMapFrame = prepareArtwork(mainMapFrame, MAIN_MAP_FRAME_FILE,
                "Main-map frame", "main-map-frame-prepare");
        return readyArtwork(mainMapFrame, "main-map-frame");
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
            if (artwork.texture == null) {
                artwork.texture = ResourceTextureLoader.getPreparedTexture(
                        artwork.url, artwork.request);
            }
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
        if (snapshot == null || snapshot.getProfile() == null || !snapshot.hasSurface()
                || prepared == null || prepared.texture == null || prepared.failed) {
            return null;
        }
        return state(map, snapshot.getProfile());
    }

    private static void drawSurface(Queue queue, ResourceTexture texture,
                                    MapViewport viewport, int left, int top) {
        drawSurface(queue, texture, viewport, left, top,
                CONTENT_WIDTH, CONTENT_HEIGHT);
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
            drawArtwork(queue, miniMapBackgroundTexture(), left, top,
                    CONTENT_WIDTH, CONTENT_HEIGHT, 0.92f);

            int cardSize = 164;
            int rowGap = 30;
            int firstRowLeft = left
                    + (CONTENT_WIDTH - cardSize * 3 - rowGap * 2) / 2;
            int secondRowLeft = left
                    + (CONTENT_WIDTH - cardSize * 2 - rowGap) / 2;
            int firstRowTop = top + 116;
            int secondRowTop = top + 348;
            for (int index = 0; index < MAP_GALLERY_FILES.length; index++) {
                int column = index < 3 ? index : index - 3;
                int x = (index < 3 ? firstRowLeft : secondRowLeft)
                        + column * (cardSize + rowGap);
                int y = index < 3 ? firstRowTop : secondRowTop;
                drawGalleryCard(map, queue, profile, index,
                        x, y, cardSize);
            }

            drawMainMapEdgeGuard(map, queue, left, top);
            drawMainMapFrame(queue, left, top);
            drawBranding(queue, wordmarkTexture(), left, top);
            String loading = "LOADING " + mapWorldName(profile) + " MAP...";
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
            drawMainMapFrame(queue, left, top);
        } catch (Throwable failure) {
            reportOnce("main-map-frame", "Server map frame failed open",
                    failure);
        }
        try {
            drawBranding(queue, wordmarkTexture(), left, top);
        } catch (Throwable failure) {
            reportOnce("main-map-branding", "Server map branding failed open",
                    failure);
        }
        try {
            drawStatus(map, queue, state, profile, left, top);
        } catch (Throwable failure) {
            reportOnce("main-map-status", "Server map status failed open",
                    failure);
        }
        try {
            drawLayerButtons(map, queue, state, left, top);
            drawMiniMapButton(map, queue, state, left, top);
            drawCenterButton(map, queue, state, left, top);
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

    private static void drawMainMapFrame(Queue queue, int left, int top) {
        ResourceTexture texture = mainMapFrameTexture();
        if (texture == null) return;
        int frameLeft = left - MAIN_MAP_FRAME_OVERSCAN;
        int frameTop = top - MAIN_MAP_FRAME_OVERSCAN;
        int frameWidth = CONTENT_WIDTH + MAIN_MAP_FRAME_OVERSCAN * 2;
        int frameHeight = CONTENT_HEIGHT + MAIN_MAP_FRAME_OVERSCAN * 2;
        int border = MAIN_MAP_FRAME_INSET + MAIN_MAP_FRAME_OVERSCAN;
        float textureWidth = Math.max(1.0f, texture.getWidth());
        float textureHeight = Math.max(1.0f, texture.getHeight());
        float outerLeft = 20.0f / textureWidth;
        float outerTop = 24.0f / textureHeight;
        float innerLeft = 96.0f / textureWidth;
        float innerTop = 96.0f / textureHeight;
        float innerRight = (textureWidth - 96.0f) / textureWidth;
        float innerBottom = (textureHeight - 96.0f) / textureHeight;
        float outerRight = (textureWidth - 19.0f) / textureWidth;
        float outerBottom = (textureHeight - 22.0f) / textureHeight;
        int middleWidth = frameWidth - border * 2;
        int middleHeight = frameHeight - border * 2;
        drawFramePiece(queue, texture, frameLeft, frameTop, border, border,
                outerLeft, outerTop,
                innerLeft - outerLeft, innerTop - outerTop);
        drawFramePiece(queue, texture, frameLeft + border, frameTop,
                middleWidth, border, innerLeft, outerTop,
                innerRight - innerLeft, innerTop - outerTop);
        drawFramePiece(queue, texture, frameLeft + frameWidth - border,
                frameTop,
                border, border, innerRight, outerTop,
                outerRight - innerRight, innerTop - outerTop);
        drawFramePiece(queue, texture, frameLeft, frameTop + border,
                border, middleHeight, outerLeft, innerTop,
                innerLeft - outerLeft, innerBottom - innerTop);
        drawFramePiece(queue, texture, frameLeft + frameWidth - border,
                frameTop + border, border, middleHeight,
                innerRight, innerTop,
                outerRight - innerRight, innerBottom - innerTop);
        drawFramePiece(queue, texture, frameLeft,
                frameTop + frameHeight - border,
                border, border, outerLeft, innerBottom,
                innerLeft - outerLeft, outerBottom - innerBottom);
        drawFramePiece(queue, texture, frameLeft + border,
                frameTop + frameHeight - border, middleWidth, border,
                innerLeft, innerBottom,
                innerRight - innerLeft, outerBottom - innerBottom);
        drawFramePiece(queue, texture,
                frameLeft + frameWidth - border,
                frameTop + frameHeight - border, border, border,
                innerRight, innerBottom,
                outerRight - innerRight, outerBottom - innerBottom);
    }

    private static void drawFramePiece(Queue queue, ResourceTexture texture,
                                       int left, int top, int width, int height,
                                       float u, float v,
                                       float uScale, float vScale) {
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, 1.0f,
                left, top, width, height,
                u, v, uScale, vScale);
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

    private static void drawNameplate(Queue queue, ResourceTexture texture,
                                      int left, int top, int width,
                                      String label) {
        if (texture == null || label == null) return;
        int height = 22;
        int leftCap = 12;
        int rightCap = 24;
        float leftCapU = leftCap / 128.0f;
        float rightCapU = rightCap / 128.0f;
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, 1.0f,
                left, top, leftCap, height,
                0.0f, 0.0f, leftCapU, 1.0f);
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, 1.0f,
                left + leftCap, top, width - leftCap - rightCap, height,
                leftCapU, 0.0f, 1.0f - leftCapU - rightCapU, 1.0f);
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, 1.0f,
                left + width - rightCap, top, rightCap, height,
                1.0f - rightCapU, 0.0f, rightCapU, 1.0f);
        MAIN_TITLE_TEXT.moveTo(left + 8, top + 20);
        MAIN_TITLE_TEXT.paint(queue, label,
                0.97f, 0.92f, 0.78f, 1.0f);
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

    private static void drawBranding(Queue queue, ResourceTexture logo,
                                     int left, int top) {
        if (logo != null) {
            Renderer.texturedQuadAlphaBlend(queue, logo,
                    1.0f, 1.0f, 1.0f, 0.96f,
                    left + 28.0f, top + 28.0f, 188.0f, 43.0f,
                    0.0f, 0.0f, 1.0f, 1.0f);
        }
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
        int selectionAccumulator = 0;
        for (HighwayTileIndex.Segment segment : segments) {
            if (segments.size() > maximum) {
                selectionAccumulator += maximum;
                if (selectionAccumulator < segments.size()) continue;
                selectionAccumulator -= segments.size();
            }
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
        NavigationTarget active = activeNavigationTarget();
        if (active == null || active.getCoordinate() == null) return;
        GroundNavigationRouteEffect.RouteSnapshot route =
                WurmWaypointerRuntime.currentNavigationRoute();
        if (route == null || route.getPointCount() < 2) return;
        MarkerStyle style = active.getMarkerStyle();
        float red = style == null ? 1.0f : style.getRed();
        float green = style == null ? 0.25f : style.getGreen();
        float blue = style == null ? 0.20f : style.getBlue();
        MapPoint emitted = viewport.mapToScreen(
                route.getTileX(0) + 0.5d,
                route.getTileY(0) + 0.5d);
        for (int index = 1; index < route.getPointCount(); index++) {
            MapPoint next = viewport.mapToScreen(
                    route.getTileX(index) + 0.5d,
                    route.getTileY(index) + 0.5d);
            double dx = next.getX() - emitted.getX();
            double dy = next.getY() - emitted.getY();
            boolean last = index == route.getPointCount() - 1;
            if (!last && dx * dx + dy * dy < 0.75d * 0.75d) continue;
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
                        3.0f, red, green, blue, 0.98f);
            }
            emitted = next;
        }
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
        map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.9f, x - 7, y - 2, 15, 5);
        map.fillRect(queue, 0.0f, 0.0f, 0.0f, 0.9f, x - 2, y - 7, 5, 15);
        map.fillRect(queue, 1.0f, 1.0f, 1.0f, 1.0f, x - 6, y - 1, 13, 3);
        map.fillRect(queue, 1.0f, 1.0f, 1.0f, 1.0f, x - 1, y - 6, 3, 13);
    }

    private static void drawStatus(WorldMap map, Queue queue, State state,
                                   ServerMapProfile profile, int left, int top) {
        map.fillRect(queue, 0.16f, 0.09f, 0.035f, 0.90f,
                left + 22, top + CONTENT_HEIGHT - 42,
                CONTENT_WIDTH - 44, 20);
        String coordinate = state.hoverInside
                ? "X=" + state.hoverTileX + " Y=" + state.hoverTileY
                        + "  Tile: " + hoveredTileDescription(state) + "  "
                : "";
        String action = state.navigationLineButtonHover
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

    private static void drawLayerButtons(WorldMap map, Queue queue, State state,
                                         int left, int top) {
        for (MapOverlayVisibility.Layer layer : LAYER_BUTTONS) {
            int x = layerButtonLeft(left, layer);
            int y = top + SEARCH_BUTTON_TOP;
            boolean visible = layerVisible(state, layer);
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

    private static boolean insideCenterButton(WorldMap map, int x, int y) {
        int left = navigationLineButtonLeft(map.x + CONTENT_OFFSET_X) - LAYER_BUTTON_GAP - 72;
        int top = map.y + CONTENT_OFFSET_Y + SEARCH_BUTTON_TOP;
        return x >= left && x < left + 72 && y >= top && y < top + LAYER_BUTTON_HEIGHT;
    }

    private static void drawCenterButton(WorldMap map, Queue queue, State state, int left, int top) {
        int x = navigationLineButtonLeft(left) - LAYER_BUTTON_GAP - 72;
        int y = top + SEARCH_BUTTON_TOP;
        float edge = state.centerButtonHover ? 0.96f : 0.72f;
        map.fillRect(queue, edge, edge * 0.79f, edge * 0.42f, 0.95f,
                x, y, 72, LAYER_BUTTON_HEIGHT);
        map.fillRect(queue, 0.10f, 0.055f, 0.02f, 0.96f,
                x + 2, y + 2, 68, LAYER_BUTTON_HEIGHT - 4);
        text(queue, "CENTER", x + 8, y + 21, 1.0f, 0.92f, 0.72f, 1.0f, left, top);
    }

    private static void drawMiniMapButton(WorldMap map, Queue queue,
                                          State state, int left, int top) {
        int x = miniMapButtonLeft(left);
        int y = top + SEARCH_BUTTON_TOP;
        boolean enabled = MiniMapWindowBridge.isEnabled();
        boolean hovered = state.miniMapButtonHover;
        boolean pressed = state.miniMapButtonPressed && hovered;
        float edge = pressed ? 1.0f : hovered ? 0.96f
                : enabled ? 0.72f : 0.38f;
        map.fillRect(queue, 0.10f, 0.055f, 0.02f, 0.94f,
                x, y, MINI_MAP_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT);
        map.fillRect(queue, edge, edge * 0.79f, edge * 0.42f, 0.95f,
                x + 2, y + 2, MINI_MAP_BUTTON_WIDTH - 4,
                LAYER_BUTTON_HEIGHT - 4);
        float fill = enabled ? 0.20f : 0.09f;
        map.fillRect(queue, fill, enabled ? 0.12f : 0.09f,
                enabled ? 0.05f : 0.08f, 0.96f,
                x + 4, y + 4, MINI_MAP_BUTTON_WIDTH - 8,
                LAYER_BUTTON_HEIGHT - 8);
        text(queue, "MINI MAP", x + 8, y + 21,
                enabled ? 1.0f : 0.58f,
                enabled ? 0.92f : 0.55f,
                enabled ? 0.72f : 0.52f, 1.0f, left, top);
    }

    private static void drawNavigationLineButton(
            WorldMap map, Queue queue, State state, int left, int top) {
        int x = navigationLineButtonLeft(left);
        int y = top + SEARCH_BUTTON_TOP;
        boolean enabled = MiniMapWindowBridge.isNavigationLineVisible();
        boolean hovered = state.navigationLineButtonHover;
        boolean pressed = state.navigationLineButtonPressed && hovered;
        float edge = pressed ? 1.0f : hovered ? 0.96f
                : enabled ? 0.72f : 0.38f;
        map.fillRect(queue, 0.10f, 0.055f, 0.02f, 0.94f,
                x, y, NAV_LINE_BUTTON_WIDTH, LAYER_BUTTON_HEIGHT);
        map.fillRect(queue, edge, edge * 0.79f, edge * 0.42f, 0.95f,
                x + 2, y + 2, NAV_LINE_BUTTON_WIDTH - 4,
                LAYER_BUTTON_HEIGHT - 4);
        float fill = enabled ? 0.20f : 0.09f;
        map.fillRect(queue, fill, enabled ? 0.12f : 0.09f,
                enabled ? 0.05f : 0.08f, 0.96f,
                x + 4, y + 4, NAV_LINE_BUTTON_WIDTH - 8,
                LAYER_BUTTON_HEIGHT - 8);
        text(queue, "NAV LINE", x + 8, y + 21,
                enabled ? 1.0f : 0.58f,
                enabled ? 0.92f : 0.55f,
                enabled ? 0.72f : 0.52f, 1.0f, left, top);
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

    private static void drawSearchButton(WorldMap map, Queue queue, State state,
                                         int left, int top) {
        int x = searchButtonLeft(left);
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

    private static void drawCloseButton(WorldMap map, Queue queue, State state,
                                        int left, int top) {
        int x = closeButtonLeft(left);
        int y = top + SEARCH_BUTTON_TOP;
        boolean hovered = state.closeButtonHover;
        boolean pressed = state.closeButtonPressed && hovered;
        float edge = pressed ? 1.0f : hovered ? 0.96f : 0.72f;
        map.fillRect(queue, 0.10f, 0.055f, 0.02f, 0.94f,
                x, y, SEARCH_BUTTON_SIZE, SEARCH_BUTTON_SIZE);
        map.fillRect(queue, edge, edge * 0.64f, edge * 0.34f, 0.95f,
                x + 2, y + 2, SEARCH_BUTTON_SIZE - 4,
                SEARCH_BUTTON_SIZE - 4);
        map.fillRect(queue, hovered ? 0.28f : 0.16f, 0.07f, 0.035f, 0.98f,
                x + 4, y + 4, SEARCH_BUTTON_SIZE - 8,
                SEARCH_BUTTON_SIZE - 8);
        float icon = hovered ? 1.0f : 0.90f;
        line(queue, x + 10, y + 10, x + 22, y + 22,
                2.5f, icon, icon * 0.82f, icon * 0.58f, 1.0f);
        line(queue, x + 22, y + 10, x + 10, y + 22,
                2.5f, icon, icon * 0.82f, icon * 0.58f, 1.0f);
    }

    private static void requestWaypoint(WorldMap map, State state,
                                        int mouseX, int mouseY) {
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
        state.centerButtonHover = insideCenterButton(map, mouseX, mouseY);
        state.searchButtonHover = insideSearchButton(map, mouseX, mouseY);
        state.closeButtonHover = insideCloseButton(map, mouseX, mouseY);
        state.miniMapButtonHover = insideMiniMapButton(
                map, mouseX, mouseY);
        state.navigationLineButtonHover = insideNavigationLineButton(
                map, mouseX, mouseY);
        state.hoveredLayerButton = layerButtonAt(map, mouseX, mouseY);
        if (state.centerButtonHover || state.searchButtonHover || state.closeButtonHover
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
        if (!layerVisible(state, MapOverlayVisibility.Layer.DEEDS)) return;
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
        if (layer == MapOverlayVisibility.Layer.DEEDS) {
            MiniMapWindowBridge.toggleDeeds();
        } else if (layer == MapOverlayVisibility.Layer.HIGHWAYS) {
            MiniMapWindowBridge.toggleRoads();
        } else {
            state.overlays.toggle(layer);
        }
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
        private boolean centerButtonPressed;
        private boolean centerButtonHover;
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
            this.overlays = overlays;
        }
    }
}
