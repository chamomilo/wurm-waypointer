package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.WaypointerMiniMapFonts;
import com.wurmonline.client.resources.textures.ResourceTexture;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.MiniMapState;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.ServerMapSnapshot;

/** Square player-centred map field hosted by {@link MiniMapWindow}. */
class MiniMapCanvas extends FlexComponent {
    private static final int FRAME_INSET = 22;
    private static final int OPEN_BUTTON_WIDTH = 120;
    private static final int OPEN_BUTTON_HEIGHT = 20;
    private static final int WHEEL_ZOOM_TILES = 8;
    private final MiniMapState settings;
    private final TextFont titleText = WaypointerMiniMapFonts.healthbarTitle();
    private String viewportProfileId = "";
    private int viewportPixels = -1;
    private int viewportVisibleTiles = -1;
    private MapViewport viewport;
    private boolean leftPressedInside;
    private boolean openMapPressed;
    private boolean openMapHover;
    private boolean draggingTitle;
    private int lastDragX;
    private int lastDragY;
    private String titleLabel = "Mini Map";

    MiniMapCanvas(MiniMapState settings, int size) {
        super("wurm-waypointer.mini-map.canvas");
        this.settings = settings;
        setInitialSize(size, size, false);
    }

    void settingsChanged() {
        viewportVisibleTiles = -1;
    }

    void setTitleLabel(String value) {
        titleLabel = value == null ? "Mini Map" : value;
    }

    boolean mouseWheeledAt(int mouseX, int mouseY, int wheelDelta) {
        if (!contains(mouseX, mouseY) || wheelDelta == 0) return false;
        int steps = Math.max(1, Math.abs(wheelDelta) / 3)
                * WHEEL_ZOOM_TILES;
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
        if (pickData == null) return;
        if (openMapHover) {
            pickData.addText("Open the full map");
            return;
        }
        int mapSize = mapSize(fullSize);
        int mapLeft = mapLeft(fullSize);
        int mapTop = mapTop(fullSize);
        if (viewport == null
                || !inside(mouseX, mouseY, mapLeft, mapTop, mapSize)) {
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
        int fullSize = fullSize();
        int fullLeft = fullLeft(fullSize);
        int fullTop = fullTop(fullSize);
        int mapSize = mapSize(fullSize);
        openMapPressed = inside(mouseX, mouseY,
                openButtonLeft(fullLeft, fullSize),
                openButtonTop(fullTop, fullSize),
                OPEN_BUTTON_WIDTH, OPEN_BUTTON_HEIGHT);
        draggingTitle = !openMapPressed && inside(mouseX, mouseY,
                fullLeft + 3, fullTop,
                nameplateWidth(fullSize), 22);
        lastDragX = mouseX;
        lastDragY = mouseY;
        leftPressedInside = inside(mouseX, mouseY,
                mapLeft(fullSize), mapTop(fullSize), mapSize)
                && !openMapPressed && !draggingTitle;
    }

    @Override protected void mouseDragged(int mouseX, int mouseY) {
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
        leftPressedInside = false;
        openMapPressed = false;
        draggingTitle = false;
        if (open) {
            MiniMapWindowBridge.openWorldMap((MiniMapWindow) this);
            return;
        }
        if (create) ServerMapWindowBridge.requestWaypointAt(viewport,
                mouseX - mapLeft, mouseY - mapTop);
    }

    @Override void rightPressed(int mouseX, int mouseY, int clickCount) {
        int fullSize = fullSize();
        int mapSize = mapSize(fullSize);
        int mapLeft = mapLeft(fullSize);
        int mapTop = mapTop(fullSize);
        if (inside(mouseX, mouseY, mapLeft, mapTop, mapSize)) {
            ServerMapWindowBridge.requestCustomMarkAt(viewport,
                    mouseX - mapLeft, mouseY - mapTop);
        }
    }

    @Override protected void mouseExited() {
        openMapHover = false;
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
        if (snapshot == null || snapshot.getProfile() == null
                || !snapshot.hasSurface()) {
            status(queue, "Map loading...", left, top);
        } else {
            MapViewport active = viewport(snapshot.getProfile(), size);
            active.centerOn(WurmWaypointerRuntime.currentPlayerTileX() + 0.5d,
                    WurmWaypointerRuntime.currentPlayerTileY() + 0.5d);
            if (!ServerMapWindowBridge.renderMiniMap(this, queue, active,
                    snapshot, settings.areDeedsVisible(), left, top, size)) {
                status(queue, "Map loading...", left, top);
            }
        }
        frameArtwork(queue, ServerMapWindowBridge.miniMapFrameTexture(),
                fullLeft, fullTop, fullSize);
        drawNameplate(queue, fullLeft, fullTop, fullSize);
        drawFrameControls(queue, fullLeft, fullTop, fullSize);
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
        int left = fullLeft + 3;
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

        int textX = fullLeft + 11;
        int textY = top + 20;
        titleText.moveTo(textX, textY);
        titleText.paint(queue, titleLabel,
                0.97f, 0.92f, 0.78f, 1.0f);
    }

    private int nameplateWidth(int fullSize) {
        return Math.max(54, Math.min(fullSize - 5,
                titleText.getWidth(titleLabel) + 30));
    }

    private static int openButtonLeft(int fullLeft, int fullSize) {
        return fullLeft + (fullSize - OPEN_BUTTON_WIDTH) / 2;
    }

    private static int openButtonTop(int fullTop, int fullSize) {
        return fullTop + fullSize - OPEN_BUTTON_HEIGHT - 10;
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
        String button = "OPEN FULL MAP";
        int buttonTextX = buttonLeft
                + (OPEN_BUTTON_WIDTH - font.getWidth(button)) / 2;
        int baseline = buttonTop + 15;
        font.moveTo(buttonTextX + 1, baseline + 1);
        font.paint(queue, button, 0.02f, 0.01f, 0.005f, 1.0f);
        font.moveTo(buttonTextX, baseline);
        font.paint(queue, button, 1.0f, 0.92f, 0.72f, 1.0f);

        String scale = "VIEW: " + settings.getVisibleTiles() * 4 + " m";
        int scaleX = fullLeft + 18;
        int scaleY = fullTop + fullSize - 7;
        font.moveTo(scaleX + 1, scaleY + 1);
        font.paint(queue, scale, 0.02f, 0.01f, 0.005f, 1.0f);
        font.moveTo(scaleX, scaleY);
        font.paint(queue, scale, 0.91f, 0.80f, 0.59f, 1.0f);
    }

}
