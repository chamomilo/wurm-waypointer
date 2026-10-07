package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.WaypointerMiniMapFonts;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.Texture;
import com.wurmonline.client.resources.textures.WaypointerCaveTexture;
import org.waypoints.next.integration.WurmCaveMapSnapshot;
import org.waypoints.next.map.MapPoint;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.MiniMapState;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.ServerMapSnapshot;

/** Square player-centred map field hosted by {@link MiniMapWindow}. */
class MiniMapCanvas extends FlexComponent implements InputFieldListener {
    private static final int FRAME_INSET = 22;
    private static final int OPEN_BUTTON_WIDTH = MiniMapControlsLayout.OPEN_BUTTON_WIDTH;
    private static final int OPEN_BUTTON_HEIGHT = MiniMapControlsLayout.BUTTON_HEIGHT;
    private static final int MODE_BUTTON_WIDTH = MiniMapControlsLayout.MODE_BUTTON_WIDTH;
    private static final int TOPOGRAPHIC_BLOCK_WIDTH = MiniMapControlsLayout.TOPOGRAPHIC_BLOCK_WIDTH;
    private static final int TOPOGRAPHIC_BUTTON_WIDTH = MiniMapControlsLayout.TOPOGRAPHIC_BUTTON_WIDTH;
    private static final int TOPOGRAPHIC_FIELD_WIDTH = MiniMapControlsLayout.TOPOGRAPHIC_FIELD_WIDTH;
    private final MiniMapState settings;
    private final TextFont titleText = WaypointerMiniMapFonts.healthbarTitle();
    private String viewportProfileId = "";
    private int viewportPixels = -1;
    private int viewportVisibleTiles = -1;
    private MapViewport viewport;
    private boolean leftPressedInside;
    private boolean openMapPressed;
    private boolean openMapHover;
    private boolean modePressed;
    private boolean modeHover;
    private WurmCaveMapSnapshot caveMap;
    private final WaypointerCaveTexture caveTexture = new WaypointerCaveTexture();
    private long nextCaveRefresh;
    private long textureRevision;
    private boolean textureInitialized;
    private boolean draggingTitle;
    private int lastDragX;
    private int lastDragY;
    private String titleLabel = "Mini Map";
    private final WurmInputField topographicInput;
    private String lastTopographicInput;
    private boolean topographicEditing;
    private int topographicPressed = -1;
    private int topographicHover = -1;
    private final WaypointerCaveTexture topographicTexture = new WaypointerCaveTexture();
    private int topographicInterval = -1, topographicOriginX, topographicOriginY;
    private int topographicColumns, topographicRows;
    private boolean topographicCave;
    private String topographicProfileId = "";
    private long topographicRevision, nextTopographicRefresh;
    private int topographicPixels;
    private double topographicCenterX, topographicCenterY, topographicScale;

    MiniMapCanvas(MiniMapState settings, int size) {
        super("wurm-waypointer.mini-map.canvas");
        this.settings = settings;
        setInitialSize(size, size, false);
        topographicInput = new WurmInputField("waypointer.mini-map.topographic", this, 1, 2);
        topographicInput.parent = this;
        topographicInput.simpleInput = true;
        topographicInput.prompt = "";
        topographicInput.setBackgroundColor(0.10f, 0.06f, 0.035f);
        topographicInput.setPenColor(1.0f, 0.92f, 0.72f);
        topographicInput.setInitialSize(TOPOGRAPHIC_FIELD_WIDTH - 2,
                OPEN_BUTTON_HEIGHT - 2, false);
        normalizeTopographicInput();
    }

    void settingsChanged() {
        viewportVisibleTiles = -1;
        nextCaveRefresh = 0;
    }

    void disposeCaveMap() {
        prepareDetach();
        caveTexture.dispose();
        topographicTexture.dispose();
        topographicInterval = -1;
        caveMap = null;
        textureInitialized = false;
    }

    void setTitleLabel(String value) {
        titleLabel = value == null ? "Mini Map" : value;
    }

    boolean mouseWheeledAt(int mouseX, int mouseY, int wheelDelta) {
        if (!contains(mouseX, mouseY) || wheelDelta == 0) return false;
        if (insideControls(mouseX, mouseY)) return true;
        int steps = settings.miniMapWheelTiles(wheelDelta);
        if (wheelDelta < 0) settings.zoomIn(steps);
        else settings.zoomOut(steps);
        settingsChanged();
        return true;
    }

    @Override public void pick(PickData pickData, int mouseX, int mouseY) {
        int fullSize = fullSize();
        int fullLeft = fullLeft(fullSize);
        int fullTop = fullTop(fullSize);
        openMapHover = inside(mouseX, mouseY,
                openButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize),
                OPEN_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        modeHover = inside(mouseX, mouseY, modeButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize), MODE_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        topographicHover = topographicZoneAt(mouseX, mouseY);
        if (pickData == null) return;
        if (insideTopographicBlock(mouseX, mouseY)) {
            pickData.addText("Topographic view: " + settings.getTopographicIntervalMetres()
                    + " m | 0: off | - / +: change height interval | click number: edit");
            if (settings.isCaveView()) pickData.addText("Contours follow received cave floor heights.");
            return;
        }
        if (openMapHover) {
            pickData.addText("Open the full map");
            return;
        }
        if (modeHover) {
            pickData.addText("Map view: " + (settings.isCaveView() ? "CAVE" : "GROUND")
                    + " | Click to switch");
            return;
        }
        int mapSize = mapSize(fullSize);
        int mapLeft = mapLeft(fullSize);
        int mapTop = mapTop(fullSize);
        if (viewport == null
                || !inside(mouseX, mouseY, mapLeft, mapTop, mapSize)) {
            return;
        }
        if (settings.isCaveView()) {
            MapPoint point = viewport.screenToMap(mouseX - mapLeft, mouseY - mapTop);
            int tileX = (int) Math.floor(point.getX()), tileY = (int) Math.floor(point.getY());
            if (caveMap != null) for (String line : caveMap.hoverLines(
                    tileX, tileY))
                pickData.addText(line);
            WurmWaypointerRuntime.caveMapStructureHover(pickData, tileX, tileY);
            String mark = ServerMapWindowBridge.caveWaypointHover(viewport,
                    mouseX - mapLeft, mouseY - mapTop);
            if (!mark.isEmpty()) pickData.addText(mark);
            return;
        }
        for (String hover : ServerMapWindowBridge.mapHoverLines(viewport,
                mouseX - mapLeft, mouseY - mapTop,
                settings.areDeedsVisible(), true)) {
            pickData.addText(hover);
        }
    }

    @Override protected void leftPressed(int mouseX, int mouseY,
                                         int clickCount) {
        layoutTopographicInput();
        topographicPressed = topographicZoneAt(mouseX, mouseY);
        if (insideTopographicBlock(mouseX, mouseY)) {
            leftPressedInside = openMapPressed = modePressed = draggingTitle = false;
            if (topographicPressed == 1) {
                focusTopographicInput();
                topographicInput.leftPressed(mouseX, mouseY, clickCount);
            } else prepareDetach();
            return;
        }
        prepareDetach();
        int fullSize = fullSize();
        int fullLeft = fullLeft(fullSize);
        int fullTop = fullTop(fullSize);
        int mapSize = mapSize(fullSize);
        openMapPressed = inside(mouseX, mouseY,
                openButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize),
                OPEN_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        modePressed = inside(mouseX, mouseY, modeButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize), MODE_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        draggingTitle = !openMapPressed && !modePressed && inside(mouseX, mouseY,
                nameplateLeft(fullLeft, fullSize), fullTop,
                nameplateWidth(fullSize), 22);
        lastDragX = mouseX;
        lastDragY = mouseY;
        leftPressedInside = inside(mouseX, mouseY,
                mapLeft(fullSize), mapTop(fullSize), mapSize)
                && !openMapPressed && !modePressed && !draggingTitle;
    }

    @Override protected void mouseDragged(int mouseX, int mouseY) {
        if (topographicPressed == 1) {
            topographicInput.mouseDragged(mouseX, mouseY);
            return;
        }
        if (!draggingTitle) return;
        int nextX = x + mouseX - lastDragX;
        int nextY = y + mouseY - lastDragY;
        HeadsUpDisplay hud = WurmComponent.hud;
        if (hud != null) {
            nextX = Math.max(0, Math.min(nextX,
                    Math.max(0, hud.getWidth() - width)));
            nextY = Math.max(0, Math.min(nextY,
                    Math.max(0, hud.getHeight() - height)));
        }
        setPosition(nextX, nextY);
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    @Override protected void leftReleased(int mouseX, int mouseY) {
        if (topographicPressed >= 0) {
            int pressed = topographicPressed;
            topographicPressed = -1;
            if (pressed == 1) topographicInput.leftReleased(mouseX, mouseY);
            else if (pressed == topographicZoneAt(mouseX, mouseY)
                    && (pressed == 0 ? settings.getTopographicIntervalMetres() > 0
                    : settings.getTopographicIntervalMetres() < MiniMapState.MAXIMUM_TOPOGRAPHIC_INTERVAL_METRES)) {
                settings.changeTopographicInterval(pressed == 0 ? -1 : 1);
                normalizeTopographicInput();
                nextTopographicRefresh = 0;
            }
            return;
        }
        int fullSize = fullSize();
        int mapSize = mapSize(fullSize);
        int mapLeft = mapLeft(fullSize);
        int mapTop = mapTop(fullSize);
        int fullLeft = fullLeft(fullSize);
        int fullTop = fullTop(fullSize);
        boolean open = openMapPressed && inside(mouseX, mouseY,
                openButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize),
                OPEN_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        boolean create = leftPressedInside && inside(mouseX, mouseY,
                mapLeft, mapTop, mapSize);
        boolean mode = modePressed && inside(mouseX, mouseY,
                modeButtonLeft(fullLeft, fullSize), openButtonTop(fullTop, fullSize),
                MODE_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        leftPressedInside = false;
        openMapPressed = false;
        modePressed = false;
        draggingTitle = false;
        if (open) {
            MiniMapWindowBridge.openWorldMap((MiniMapWindow) this);
            return;
        }
        if (mode) {
            settings.toggleMapView();
            settingsChanged();
            return;
        }
        if (create && settings.isCaveView() && viewport != null) {
            MapPoint point = viewport.screenToMap(mouseX - mapLeft, mouseY - mapTop);
            if (viewport.containsMapPoint(point)) WurmWaypointerRuntime.serverMapWaypointRequested(
                    (int) Math.floor(point.getX()), (int) Math.floor(point.getY()), WaypointLayer.CAVE);
            return;
        }
        if (create) ServerMapWindowBridge.requestWaypointAt(viewport,
                mouseX - mapLeft, mouseY - mapTop);
    }

    @Override void rightPressed(int mouseX, int mouseY, int clickCount) {
        if (insideControls(mouseX, mouseY)) return;
        int fullSize = fullSize();
        int mapSize = mapSize(fullSize);
        int mapLeft = mapLeft(fullSize);
        int mapTop = mapTop(fullSize);
        if (inside(mouseX, mouseY, mapLeft, mapTop, mapSize)) {
            ServerMapWindowBridge.requestCustomMarkAt(viewport,
                    mouseX - mapLeft, mouseY - mapTop,
                    settings.isCaveView() ? WaypointLayer.CAVE : WaypointLayer.SURFACE);
        }
    }

    @Override protected void mouseExited() {
        topographicHover = topographicPressed = -1;
        openMapHover = false;
        modeHover = false;
        modePressed = false;
        openMapPressed = false;
        leftPressedInside = false;
    }

    @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
        int fullSize = fullSize();
        int fullLeft = fullLeft(fullSize);
        int fullTop = fullTop(fullSize);
        int size = mapSize(fullSize);
        int left = mapLeft(fullSize);
        int top = mapTop(fullSize);

        artwork(queue, ServerMapWindowBridge.miniMapBackgroundTexture(),
                fullLeft, fullTop, fullSize);
        fillRect(queue, 55.0f / 255.0f, 63.0f / 255.0f,
                111.0f / 255.0f, 1.0f, left, top, size, size);

        ServerMapSnapshot snapshot = WurmWaypointerRuntime.serverMapSnapshot();
        if (snapshot != null && snapshot.getProfile() != null && settings.isCaveView()) {
            MapViewport active = viewport(snapshot.getProfile(), size);
            int visible = settings.getVisibleTiles();
            double offset = visible % 2 == 0 ? 0.0d : 0.5d;
            active.centerOn(WurmWaypointerRuntime.currentPlayerTileX() + offset,
                    WurmWaypointerRuntime.currentPlayerTileY() + offset);
            try {
                renderCave(queue, active, left, top, size);
            } catch (RuntimeException refreshing) {
                status(queue, "Cave data loading...", left, top);
            }
        } else if (snapshot == null || snapshot.getProfile() == null
                || !snapshot.hasSurface()) {
            status(queue, "Map loading...", left, top);
        } else {
            MapViewport active = viewport(snapshot.getProfile(), size);
            active.centerOn(WurmWaypointerRuntime.currentPlayerTileX() + 0.5d,
                    WurmWaypointerRuntime.currentPlayerTileY() + 0.5d);
            if (!ServerMapWindowBridge.renderMiniMap(this, queue, active,
                    snapshot, settings.areDeedsVisible(), settings.areTileBordersVisible(),
                    topographicOverlay(active, size, null),
                    left, top, size)) {
                status(queue, "Map loading...", left, top);
            }
        }
        frameArtwork(queue, ServerMapWindowBridge.miniMapFrameTexture(),
                fullLeft, fullTop, fullSize);
        drawNameplate(queue, fullLeft, fullTop, fullSize);
        drawFrameControls(queue, fullLeft, fullTop, fullSize);
    }

    private void renderCave(Queue queue, MapViewport active, int left, int top, int size) {
        long now = System.nanoTime();
        if (caveMap == null || now >= nextCaveRefresh
                || caveMap.getOriginX() != WurmWaypointerRuntime.currentPlayerTileX() - 24
                || caveMap.getOriginY() != WurmWaypointerRuntime.currentPlayerTileY() - 24) {
            caveMap = WurmWaypointerRuntime.currentCaveMap();
            nextCaveRefresh = now + 200_000_000L;
            if (caveMap != null && (!textureInitialized || textureRevision != caveMap.getRevision())) {
                caveTexture.update(caveMap.image());
                textureRevision = caveMap.getRevision();
                textureInitialized = true;
            }
        }
        if (caveMap == null || caveTexture.get() == null) {
            status(queue, "Cave data loading...", left, top);
            return;
        }
        MapPoint corner = active.screenToMap(0, 0);
        HeadsUpDisplay.scissor.pushClip(left, top, size, size);
        try {
            Renderer.texturedQuadAlphaBlend(queue, caveTexture.get(), 1, 1, 1, 1,
                    left, top, size, size,
                    (float) ((corner.getX() - caveMap.getOriginX()) / WurmCaveMapSnapshot.SIZE),
                    (float) ((corner.getY() - caveMap.getOriginY()) / WurmCaveMapSnapshot.SIZE),
                    (float) (settings.getVisibleTiles() / (double) WurmCaveMapSnapshot.SIZE),
                    (float) (settings.getVisibleTiles() / (double) WurmCaveMapSnapshot.SIZE));
            ServerMapWindowBridge.renderCaveMiniMapOverlays(this, queue, active,
                    settings.areTileBordersVisible(), topographicOverlay(active, size, caveMap),
                    left, top, size);
        } finally {
            HeadsUpDisplay.scissor.popClip();
        }
    }

    private MapViewport viewport(ServerMapProfile profile, int size) {
        int visibleTiles = settings.getVisibleTiles();
        if (viewport == null || !profile.getId().equals(viewportProfileId)
                || viewportPixels != size
                || viewportVisibleTiles != visibleTiles) {
            viewport = new MapViewport(profile.getMapWidth(),
                    profile.getMapHeight(), size, size,
                    WurmWaypointerRuntime.currentPlayerTileX() + 0.5d,
                    WurmWaypointerRuntime.currentPlayerTileY() + 0.5d,
                    settings.pixelsPerTile(size));
            viewportProfileId = profile.getId();
            viewportPixels = size;
            viewportVisibleTiles = visibleTiles;
        }
        return viewport;
    }

    private static void status(Queue queue, String value, int left, int top) {
        TextFont font = TextFont.getFixedSizeText();
        font.moveTo(left + 8, top + 18);
        font.paint(queue, value, 1.0f, 0.92f, 0.72f, 1.0f);
    }

    private int fullSize() {
        return Math.max(1, Math.min(width, height));
    }

    private static int mapSize(int fullSize) {
        return Math.max(1, fullSize - FRAME_INSET * 2);
    }

    private int mapLeft(int fullSize) {
        return fullLeft(fullSize) + FRAME_INSET;
    }

    private int mapTop(int fullSize) {
        return fullTop(fullSize) + FRAME_INSET;
    }

    private int fullLeft(int fullSize) {
        return x + Math.max(0, (width - fullSize) / 2);
    }

    private int fullTop(int fullSize) {
        return y + Math.max(0, (height - fullSize) / 2);
    }

    private static boolean inside(int mouseX, int mouseY,
                                  int left, int top, int size) {
        return inside(mouseX, mouseY, left, top, size, size);
    }

    private static boolean inside(int mouseX, int mouseY,
                                  int left, int top,
                                  int width, int height) {
        return mouseX >= left && mouseY >= top
                && mouseX < left + width && mouseY < top + height;
    }

    private static void artwork(Queue queue, ResourceTexture texture,
                                int left, int top, int size) {
        if (texture == null) return;
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, 1.0f,
                left, top, size, size,
                0.0f, 0.0f, 1.0f, 1.0f);
    }

    /** Crops the generator's transparent safety margin from the visible frame. */
    private static void frameArtwork(Queue queue, ResourceTexture texture,
                                     int left, int top, int size) {
        if (texture == null) return;
        float u = 24.0f / Math.max(1.0f, texture.getWidth());
        float v = 24.0f / Math.max(1.0f, texture.getHeight());
        Renderer.texturedQuadAlphaBlend(queue, texture,
                1.0f, 1.0f, 1.0f, 1.0f,
                left, top, size, size,
                u, v, 1.0f - u * 2.0f, 1.0f - v * 2.0f);
    }

    private void drawNameplate(Queue queue, int fullLeft, int fullTop,
                               int fullSize) {
        ResourceTexture texture = ServerMapWindowBridge
                .miniMapNameplateTexture();
        if (texture == null) return;
        int left = nameplateLeft(fullLeft, fullSize);
        int top = fullTop;
        int width = nameplateWidth(fullSize);
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

        int textX = fullLeft + (fullSize - titleText.getWidth(titleLabel)) / 2;
        int textY = top + 20;
        titleText.moveTo(textX, textY);
        titleText.paint(queue, titleLabel,
                0.97f, 0.92f, 0.78f, 1.0f);
    }

    private int nameplateWidth(int fullSize) {
        return Math.max(54, Math.min(fullSize - 5,
                titleText.getWidth(titleLabel) + 30));
    }

    private int nameplateLeft(int fullLeft, int fullSize) {
        return fullLeft + (fullSize - nameplateWidth(fullSize)) / 2;
    }

    static int openButtonLeft(int fullLeft, int fullSize) {
        return MiniMapControlsLayout.openButtonLeft(fullLeft, fullSize);
    }

    static int modeButtonLeft(int fullLeft, int fullSize) {
        return MiniMapControlsLayout.modeButtonLeft(fullLeft, fullSize);
    }

    static int topographicBlockLeft(int fullLeft, int fullSize) {
        return MiniMapControlsLayout.topographicBlockLeft(fullLeft, fullSize);
    }

    private static int openButtonTop(int fullTop, int fullSize) {
        return MiniMapControlsLayout.buttonTop(fullTop, fullSize);
    }

    private void drawFrameControls(Queue queue, int fullLeft, int fullTop,
                                   int fullSize) {
        int buttonLeft = openButtonLeft(fullLeft, fullSize);
        int buttonTop = openButtonTop(fullTop, fullSize);
        float edge = openMapPressed ? 1.0f : openMapHover ? 0.92f : 0.66f;
        fillRect(queue, 0.035f, 0.025f, 0.018f, 0.96f,
                buttonLeft, buttonTop, OPEN_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        fillRect(queue, edge, edge * 0.70f, edge * 0.36f, 0.94f,
                buttonLeft + 1, buttonTop + 1,
                OPEN_BUTTON_WIDTH - 2, OPEN_BUTTON_HEIGHT - 2);
        fillRect(queue, 0.10f, 0.06f, 0.035f, 0.98f,
                buttonLeft + 3, buttonTop + 3,
                OPEN_BUTTON_WIDTH - 6, OPEN_BUTTON_HEIGHT - 6);

        TextFont font = TextFont.getFixedSizeText();
        String button = "FULL MAP";
        int buttonTextX = buttonLeft
                + (OPEN_BUTTON_WIDTH - font.getWidth(button)) / 2;
        int baseline = buttonTop + 15;
        font.moveTo(buttonTextX + 1, baseline + 1);
        font.paint(queue, button, 0.02f, 0.01f, 0.005f, 1.0f);
        font.moveTo(buttonTextX, baseline);
        font.paint(queue, button, 1.0f, 0.92f, 0.72f, 1.0f);

        int modeLeft = modeButtonLeft(fullLeft, fullSize);
        boolean cave = settings.isCaveView();
        float edgeMode = modePressed ? 0.95f : modeHover ? 0.78f : 0.52f;
        fillRect(queue, edgeMode, edgeMode, edgeMode, 1,
                modeLeft, buttonTop, MODE_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        fillRect(queue, cave ? 0.16f : 0.10f, cave ? 0.17f : 0.38f,
                cave ? 0.19f : 0.15f, 1,
                modeLeft + 1, buttonTop + 1, MODE_BUTTON_WIDTH - 2, OPEN_BUTTON_HEIGHT - 2);
        String modeText = cave ? "CAVE" : "GROUND";
        font.moveTo(modeLeft + (MODE_BUTTON_WIDTH - font.getWidth(modeText)) / 2, baseline);
        font.paint(queue, modeText, 1, 1, 1, 1);

        drawTopographicControls(queue, fullLeft, fullSize, buttonTop, font);
    }

    private void drawTopographicControls(Queue queue, int fullLeft, int fullSize, int buttonTop, TextFont font) {
        int blockLeft = topographicBlockLeft(fullLeft, fullSize);
        fillRect(queue, 0.66f, 0.46f, 0.24f, 0.94f,
                blockLeft, buttonTop, TOPOGRAPHIC_BLOCK_WIDTH, OPEN_BUTTON_HEIGHT);
        fillRect(queue, 0.08f, 0.05f, 0.03f, 0.98f,
                blockLeft + 1, buttonTop + 1, TOPOGRAPHIC_BLOCK_WIDTH - 2, OPEN_BUTTON_HEIGHT - 2);
        for (int zone : new int[] {0, 2}) {
            int zoneLeft = topographicZoneLeft(zone);
            boolean enabled = zone == 0 ? settings.getTopographicIntervalMetres() > 0
                    : settings.getTopographicIntervalMetres() < MiniMapState.MAXIMUM_TOPOGRAPHIC_INTERVAL_METRES;
            float brightness = !enabled ? 0.28f : topographicPressed == zone ? 1.0f
                    : topographicHover == zone ? 0.92f : 0.66f;
            fillRect(queue, brightness, brightness * 0.70f, brightness * 0.36f, 1,
                    zoneLeft, buttonTop, TOPOGRAPHIC_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
            fillRect(queue, 0.10f, 0.06f, 0.035f, 1, zoneLeft + 1, buttonTop + 1,
                    TOPOGRAPHIC_BUTTON_WIDTH - 2, OPEN_BUTTON_HEIGHT - 2);
            String symbol = zone == 0 ? "-" : "+";
            font.moveTo(zoneLeft + (TOPOGRAPHIC_BUTTON_WIDTH - font.getWidth(symbol)) / 2,
                    buttonTop + 15);
            font.paint(queue, symbol, enabled ? 1 : 0.4f, enabled ? 0.92f : 0.36f,
                    enabled ? 0.72f : 0.28f, 1);
        }
        int fieldLeft = topographicZoneLeft(1);
        float edge = topographicInput.hasKbFocus ? 1 : 0.66f;
        fillRect(queue, edge, edge * 0.70f, edge * 0.36f, 1,
                fieldLeft, buttonTop, TOPOGRAPHIC_FIELD_WIDTH, OPEN_BUTTON_HEIGHT);
        layoutTopographicInput();
        topographicInput.render(queue, 1);
    }

    private int topographicZoneLeft(int zone) {
        int size = fullSize();
        return MiniMapControlsLayout.topographicZoneLeft(fullLeft(size), size, zone);
    }

    private int topographicZoneAt(int mouseX, int mouseY) {
        int top = openButtonTop(fullTop(fullSize()), fullSize());
        for (int zone = 0; zone < 3; zone++) {
            if (inside(mouseX, mouseY, topographicZoneLeft(zone), top,
                    zone == 1 ? TOPOGRAPHIC_FIELD_WIDTH : TOPOGRAPHIC_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT))
                return zone;
        }
        return -1;
    }

    private boolean insideTopographicBlock(int mouseX, int mouseY) {
        int size = fullSize();
        return inside(mouseX, mouseY, topographicBlockLeft(fullLeft(size), size),
                openButtonTop(fullTop(size), size), TOPOGRAPHIC_BLOCK_WIDTH, OPEN_BUTTON_HEIGHT);
    }

    private boolean insideControls(int mouseX, int mouseY) {
        int size = fullSize(), left = fullLeft(size), top = openButtonTop(fullTop(size), size);
        return insideTopographicBlock(mouseX, mouseY)
                || inside(mouseX, mouseY, openButtonLeft(left, size), top, OPEN_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT)
                || inside(mouseX, mouseY, modeButtonLeft(left, size), top, MODE_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
    }

    private void layoutTopographicInput() {
        int fieldX = topographicZoneLeft(1) + 1;
        int fieldY = openButtonTop(fullTop(fullSize()), fullSize()) + 1;
        if (topographicInput.x != fieldX || topographicInput.y != fieldY)
            topographicInput.setLocation(fieldX, fieldY, TOPOGRAPHIC_FIELD_WIDTH - 2, OPEN_BUTTON_HEIGHT - 2);
    }

    private void normalizeTopographicInput() {
        lastTopographicInput = Integer.toString(settings.getTopographicIntervalMetres());
        topographicInput.setTextMoveToEnd(lastTopographicInput);
    }

    private void focusTopographicInput() {
        if (hud == null) return;
        hud.stopTyping();
        topographicEditing = true;
        hud.setActiveWindow(this);
        hud.startTyping();
    }

    void prepareDetach() {
        if (topographicInput.hasKbFocus && hud != null) hud.stopTyping();
        topographicEditing = false;
        normalizeTopographicInput();
    }

    @Override boolean hasInputField() { return topographicEditing; }
    @Override WurmInputField getInputField() { return topographicEditing ? topographicInput : null; }
    @Override public void handleInput(String input) { prepareDetach(); }
    @Override public void handleEscape(WurmInputField field) { prepareDetach(); }

    @Override public void handleInputChanged(WurmInputField field, String input) {
        if (settings.setTopographicInput(input)) {
            lastTopographicInput = input;
            nextTopographicRefresh = 0;
        } else field.setText(lastTopographicInput);
    }

    @Override public void gameTick() {
        topographicInput.gameTick();
        if (topographicEditing && !topographicInput.hasKbFocus) {
            topographicEditing = false;
            normalizeTopographicInput();
        }
    }

    private Texture topographicOverlay(MapViewport active, int size, WurmCaveMapSnapshot cave) {
        int interval = settings.getTopographicIntervalMetres();
        if (interval == 0) {
            if (topographicInterval != 0) topographicTexture.dispose();
            topographicInterval = 0;
            return null;
        }
        MapPoint first = active.screenToMap(0, 0), last = active.screenToMap(size, size);
        int originX = (int) Math.floor(first.getX()), originY = (int) Math.floor(first.getY());
        int columns = (int) Math.ceil(last.getX()) - originX + 1;
        int rows = (int) Math.ceil(last.getY()) - originY + 1;
        boolean caveView = cave != null;
        long revision = caveView ? cave.getRevision() : 0, now = System.nanoTime();
        if (interval != topographicInterval || caveView != topographicCave
                || !viewportProfileId.equals(topographicProfileId)
                || originX != topographicOriginX || originY != topographicOriginY
                || columns != topographicColumns || rows != topographicRows
                || size != topographicPixels || active.getCenterX() != topographicCenterX
                || active.getCenterY() != topographicCenterY || active.getPixelsPerTile() != topographicScale
                || revision != topographicRevision || now >= nextTopographicRefresh) {
            float[] heights;
            if (caveView) {
                heights = new float[columns * rows];
                for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++)
                    heights[x + y * columns] = cave.floorHeightMetres(originX + x, originY + y);
            } else heights = WurmWaypointerRuntime.miniMapSurfaceHeights(originX, originY, columns, rows);
            topographicTexture.update(MiniMapContourImage.render(active, size,
                    originX, originY, columns, rows, heights, interval));
            topographicInterval = interval;
            topographicCave = caveView;
            topographicProfileId = viewportProfileId;
            topographicOriginX = originX; topographicOriginY = originY;
            topographicColumns = columns; topographicRows = rows;
            topographicRevision = revision;
            topographicPixels = size;
            topographicCenterX = active.getCenterX(); topographicCenterY = active.getCenterY();
            topographicScale = active.getPixelsPerTile();
            nextTopographicRefresh = now + 500_000_000L;
        }
        return topographicTexture.get();
    }

}
