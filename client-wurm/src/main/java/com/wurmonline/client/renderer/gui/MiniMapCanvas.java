package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.ui.v1.*;

import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.WaypointerFonts;
import com.wurmonline.client.resources.textures.WaypointerCaveTexture;
import org.waypoints.next.integration.WurmCaveMapSnapshot;
import org.waypoints.next.map.MapPoint;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.MiniMapState;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.i18n.Messages;

/** Square player-centred map field hosted by {@link MiniMapWindow}. */
class MiniMapCanvas extends FlexComponent implements InputFieldListener {
    private static final int FRAME_INSET = 22;
    private static final int OPEN_BUTTON_HEIGHT = MiniMapControlsLayout.BUTTON_HEIGHT;
    private static final int TOPOGRAPHIC_BLOCK_WIDTH = MiniMapControlsLayout.TOPOGRAPHIC_BLOCK_WIDTH;
    private static final int TOPOGRAPHIC_BUTTON_WIDTH = MiniMapControlsLayout.TOPOGRAPHIC_BUTTON_WIDTH;
    private static final int TOPOGRAPHIC_FIELD_WIDTH = MiniMapControlsLayout.TOPOGRAPHIC_FIELD_WIDTH;
    private final MiniMapState settings;
    private MiniMapControlsLayout controls;
    private long languageRevision = -1;
    private final TextFont titleText = WaypointerFonts.title();
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
    private final FlexComponent topographicField;
    private WaypointerButtonGroup footerTypography;
    private int numberBaseline;
    private String lastTopographicInput;
    private boolean topographicEditing;
    private int topographicPressed = -1;
    private int topographicHover = -1;
    private final WaypointerCaveTexture topographicTexture = new WaypointerCaveTexture();
    private final MiniMapContourCache contourCache = new MiniMapContourCache();
    private MiniMapContourCache.Result contourImage;

    MiniMapCanvas(MiniMapState settings, int size) {
        super("wurm-waypointer.mini-map.canvas");
        this.settings = settings;
        updateControls();
        size = Math.max(size, controls.minimumSize());
        setInitialSize(size, size, false);
        topographicInput = WaypointerUi.input("waypointer.mini-map.topographic", this, 1, 2);

        topographicInput.simpleInput = true;
        topographicInput.prompt = org.waypoints.next.i18n.Messages.text("");
        topographicInput.setInitialSize(TOPOGRAPHIC_FIELD_WIDTH - 2,
                MiniMapControlsLayout.FIELD_HEIGHT - 2, false);
        topographicField = WaypointerUi.compactView(topographicInput, TOPOGRAPHIC_FIELD_WIDTH, MiniMapControlsLayout.FIELD_HEIGHT);
        topographicField.parent = this;
        applyTopographicTypography();
        normalizeTopographicInput();
    }

    void settingsChanged() {
        viewportVisibleTiles = -1;
        nextCaveRefresh = 0;
        java.util.logging.Logger.getLogger("WurmWaypointer.Map").info("Mini-map settings: cave="
                +settings.isCaveView()+", tiles="+settings.getVisibleTiles()+", contours="+settings.getTopographicIntervalMetres());
    }

    private void updateControls() {
        controls = new MiniMapControlsLayout(footerWidth("FULL MAP"),
                Math.max(footerWidth("GROUND"), footerWidth("CAVE")));
        footerTypography=WaypointerButtonGroup.compact("mini-map.footer",OPEN_BUTTON_HEIGHT,
                Messages.texts(new String[]{"FULL MAP","GROUND","CAVE","0123456789"}),
                new int[]{controls.openButtonWidth,controls.modeButtonWidth,controls.modeButtonWidth,160});
        if(topographicField!=null)applyTopographicTypography();
        languageRevision = Messages.revision();
    }
    private static int footerWidth(String source) {
        String caption=Messages.text(source);
        return WaypointerButtonGroup.compactWidth(caption,OPEN_BUTTON_HEIGHT);
    }
    private void applyTopographicTypography() {
        topographicInput.text=com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts.caption(footerTypography.fontPixels,false,org.chamomilo.wurm.ui.v1.UiDensity.HIGH);
        topographicInput.textBold=com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts.caption(footerTypography.fontPixels,true,org.chamomilo.wurm.ui.v1.UiDensity.HIGH);
        topographicField.text=topographicInput.text;topographicField.textBold=topographicInput.textBold;
        int top=0,bottom=0;
        for(boolean bold:new boolean[]{false,true}) {
            java.awt.Rectangle ink=UiTypography.ink("0123456789",footerTypography.fontPixels,bold,UiDensity.HIGH);
            top=Math.min(top,ink.y);bottom=Math.max(bottom,ink.y+ink.height);
        }
        numberBaseline=(MiniMapControlsLayout.FIELD_HEIGHT-(bottom-top))/2-top;
        topographicInput.setLineHeight(numberBaseline);
        topographicInput.setMaxLines(1);
    }

    void disposeCaveMap() {
        prepareDetach();
        caveTexture.dispose();
        topographicTexture.dispose();
        contourCache.clear();contourImage=null;
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
                controls.openButtonWidth, OPEN_BUTTON_HEIGHT);
        modeHover = inside(mouseX, mouseY, modeButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize), controls.modeButtonWidth, OPEN_BUTTON_HEIGHT);
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
                controls.openButtonWidth, OPEN_BUTTON_HEIGHT);
        modePressed = inside(mouseX, mouseY, modeButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize), controls.modeButtonWidth, OPEN_BUTTON_HEIGHT);
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
                contourCache.clear();
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
                controls.openButtonWidth, OPEN_BUTTON_HEIGHT);
        boolean create = leftPressedInside && inside(mouseX, mouseY,
                mapLeft, mapTop, mapSize);
        boolean mode = modePressed && inside(mouseX, mouseY,
                modeButtonLeft(fullLeft, fullSize), openButtonTop(fullTop, fullSize),
                controls.modeButtonWidth, OPEN_BUTTON_HEIGHT);
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
        long renderStart=System.nanoTime();
        try {
        int fullSize = fullSize();
        int fullLeft = fullLeft(fullSize);
        int fullTop = fullTop(fullSize);
        int size = mapSize(fullSize);
        int left = mapLeft(fullSize);
        int top = mapTop(fullSize);

        UiPainter.background(WaypointerUi.canvas(this, queue), UiBackground.SOLID, 1, fullLeft, fullTop, fullSize, fullSize);
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
        UiPainter.frame(WaypointerUi.canvas(this, queue), FRAME_INSET, 1, fullLeft, fullTop, fullSize, fullSize);
        drawNameplate(queue, fullLeft, fullTop, fullSize);
        drawFrameControls(queue, fullLeft, fullTop, fullSize);
        } finally {
            org.waypoints.next.render.WaypointRenderProfiler.recordMap(System.nanoTime()-renderStart,
                    queue==null?0:queue.getQueueCount());
        }
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
        TextFont font = WaypointerFonts.body();
        font.moveTo(left + 8, top + 18);
        font.paint(queue, org.waypoints.next.i18n.Messages.text(value), 1.0f, 0.92f, 0.72f, 1.0f);
    }

    private int fullSize() {
        return Math.max(1, Math.min(width, height));
    }

    private static int mapSize(int fullSize) {
        return Math.max(1, fullSize - FRAME_INSET - MiniMapControlsLayout.FOOTER_HEIGHT);
    }

    private int mapLeft(int fullSize) {
        return fullLeft(fullSize) + (fullSize-mapSize(fullSize))/2;
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



    private void drawNameplate(Queue queue, int fullLeft, int fullTop, int fullSize) {
        ChamomiloUiV1Canvas canvas = WaypointerUi.canvas(this, queue);
        int left = nameplateLeft(fullLeft, fullSize), width = nameplateWidth(fullSize);
        String title = Messages.text(titleLabel);
        UiHudPainter.nameplate(canvas, false, UiScale.BASE, 1, left, fullTop, width);
        titleText.moveTo(left + 12 + (width - 36 - titleText.getWidth(title)) / 2, fullTop + 15);
        titleText.paint(queue, title, UiColor.TEXT.red, UiColor.TEXT.green, UiColor.TEXT.blue, 1);
        UiHudPainter.nameplate(canvas, true, UiScale.BASE, 1, left, fullTop, width);
    }

    private int nameplateWidth(int fullSize) {
        return Math.max(54, Math.min(fullSize - 5,
                titleText.getWidth(Messages.text(titleLabel)) + 48));
    }

    private int nameplateLeft(int fullLeft, int fullSize) {
        return fullLeft + (fullSize - nameplateWidth(fullSize)) / 2;
    }

    private int openButtonLeft(int fullLeft, int fullSize) {
        return controls.openButtonLeft(fullLeft, fullSize);
    }

    private int modeButtonLeft(int fullLeft, int fullSize) {
        return controls.modeButtonLeft(fullLeft, fullSize);
    }

    private int topographicBlockLeft(int fullLeft, int fullSize) {
        return controls.topographicBlockLeft(fullLeft, fullSize);
    }

    private static int openButtonTop(int fullTop, int fullSize) {
        return MiniMapControlsLayout.buttonTop(fullTop, fullSize);
    }

    private void drawFrameControls(Queue queue, int fullLeft, int fullTop, int fullSize) {
        int top = openButtonTop(fullTop, fullSize);
        WaypointerUi.paintButton(this, queue, "full", "FULL MAP", openMapHover, openMapPressed, true, false,
                openButtonLeft(fullLeft, fullSize), top, controls.openButtonWidth, OPEN_BUTTON_HEIGHT,footerTypography);
        WaypointerUi.paintButton(this, queue, "mode", settings.isCaveView() ? "CAVE" : "GROUND", modeHover, modePressed, true, settings.isCaveView(),
                modeButtonLeft(fullLeft, fullSize), top, controls.modeButtonWidth, OPEN_BUTTON_HEIGHT,footerTypography);
        drawTopographicControls(queue, fullLeft, fullSize, top);
    }

    private void drawTopographicControls(Queue queue, int fullLeft, int fullSize, int top) {
        for (int zone : new int[] {0, 2}) {
            boolean enabled = zone == 0 ? settings.getTopographicIntervalMetres() > 0
                    : settings.getTopographicIntervalMetres() < MiniMapState.MAXIMUM_TOPOGRAPHIC_INTERVAL_METRES;
            WaypointerUi.paintButton(this, queue, "topo" + zone, zone == 0 ? "-" : "+", topographicHover == zone,
                    topographicPressed == zone, enabled, false, topographicZoneLeft(zone), top, TOPOGRAPHIC_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        }
        layoutTopographicInput();
        topographicField.render(queue, 1);
    }

    private int topographicZoneLeft(int zone) {
        int size = fullSize();
        return controls.topographicZoneLeft(fullLeft(size), size, zone);
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
                || inside(mouseX, mouseY, openButtonLeft(left, size), top, controls.openButtonWidth, OPEN_BUTTON_HEIGHT)
                || inside(mouseX, mouseY, modeButtonLeft(left, size), top, controls.modeButtonWidth, OPEN_BUTTON_HEIGHT);
    }

    private void layoutTopographicInput() {
        topographicField.setLocation(topographicZoneLeft(1), openButtonTop(fullTop(fullSize()), fullSize())
                +footerTypography.baseline-numberBaseline, TOPOGRAPHIC_FIELD_WIDTH, MiniMapControlsLayout.FIELD_HEIGHT);
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
            contourCache.clear();
        } else field.setText(lastTopographicInput);
    }

    @Override public void gameTick() {
        if (languageRevision != Messages.revision()) {
            updateControls();
            int size = Math.max(MiniMapWindow.MAP_SIZE, controls.minimumSize());
            setSize(size, size);
            if (hud != null) setPosition(Math.max(0, Math.min(x, hud.getWidth() - size)),
                    Math.max(0, Math.min(y, hud.getHeight() - size)));
        }
        topographicInput.gameTick();
        if (topographicEditing && !topographicInput.hasKbFocus) {
            topographicEditing = false;
            normalizeTopographicInput();
        }
    }

    private MiniMapContourOverlay topographicOverlay(MapViewport active, int size, WurmCaveMapSnapshot cave) {
        int interval = settings.getTopographicIntervalMetres();
        if (interval == 0) {
            if(contourImage!=null)topographicTexture.dispose();
            contourImage=null;contourCache.clear();
            return null;
        }
        MiniMapContourCache.Result image=contourCache.update(active,size,interval,cave!=null,
                viewportProfileId,System.nanoTime(),(originX,originY,columns,rows) -> {
                    if(cave==null)return WurmWaypointerRuntime.miniMapSurfaceHeights(originX,originY,columns,rows);
                    float[] heights=new float[columns*rows];
                    for(int y=0;y<rows;y++)for(int x=0;x<columns;x++)
                        heights[x+y*columns]=cave.floorHeightMetres(originX+x,originY+y);
                    return heights;
                });
        if(image==null)return null;
        if(image!=contourImage) {
            topographicTexture.update(image.image);contourImage=image;
        }
        return new MiniMapContourOverlay(topographicTexture.get(),image,active);
    }

}
