package com.wurmonline.client.renderer.gui;

/** Footer geometry kept independent of native GUI/font initialisation. */
final class MiniMapControlsLayout {
    static final int BUTTON_HEIGHT = 20;
    static final int OPEN_BUTTON_WIDTH = 64;
    static final int MODE_BUTTON_WIDTH = 56;
    static final int TOPOGRAPHIC_BUTTON_WIDTH = 18;
    static final int TOPOGRAPHIC_FIELD_WIDTH = 28;
    static final int TOPOGRAPHIC_BLOCK_WIDTH = TOPOGRAPHIC_BUTTON_WIDTH * 2 + TOPOGRAPHIC_FIELD_WIDTH;
    static final int BLOCK_GAP = 8;
    static final int ROW_WIDTH = TOPOGRAPHIC_BLOCK_WIDTH + MODE_BUTTON_WIDTH + OPEN_BUTTON_WIDTH + BLOCK_GAP * 2;

    private MiniMapControlsLayout() { }

    static int openButtonLeft(int fullLeft, int fullSize) {
        return modeButtonLeft(fullLeft, fullSize) + MODE_BUTTON_WIDTH + BLOCK_GAP;
    }

    static int modeButtonLeft(int fullLeft, int fullSize) {
        return topographicBlockLeft(fullLeft, fullSize) + TOPOGRAPHIC_BLOCK_WIDTH + BLOCK_GAP;
    }

    static int topographicBlockLeft(int fullLeft, int fullSize) {
        // At 300 px the 50 px side margins leave both metal corner plates visible.
        return fullLeft + (fullSize - ROW_WIDTH) / 2;
    }

    static int buttonTop(int fullTop, int fullSize) {
        return fullTop + fullSize - BUTTON_HEIGHT - 8;
    }

    static int topographicZoneLeft(int fullLeft, int fullSize, int zone) {
        int first = topographicBlockLeft(fullLeft, fullSize);
        return zone == 0 ? first : zone == 1 ? first + TOPOGRAPHIC_BUTTON_WIDTH
                : first + TOPOGRAPHIC_BUTTON_WIDTH + TOPOGRAPHIC_FIELD_WIDTH;
    }
}
