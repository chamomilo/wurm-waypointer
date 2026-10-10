package com.wurmonline.client.renderer.gui;

/** Footer geometry kept independent of native GUI/font initialisation. */
final class MiniMapControlsLayout {
    static final int BUTTON_HEIGHT = 26;
    static final int FIELD_HEIGHT = 20;
    static final int FOOTER_HEIGHT = 30;
    static final int TOPOGRAPHIC_BUTTON_WIDTH = 18;
    static final int TOPOGRAPHIC_FIELD_WIDTH = 40;
    static final int TOPOGRAPHIC_BLOCK_WIDTH = TOPOGRAPHIC_BUTTON_WIDTH * 2 + TOPOGRAPHIC_FIELD_WIDTH;
    static final int BLOCK_GAP = 4;
    final int openButtonWidth;
    final int modeButtonWidth;
    final int rowWidth;

    MiniMapControlsLayout(int openButtonWidth, int modeButtonWidth) {
        this.openButtonWidth = openButtonWidth;
        this.modeButtonWidth = modeButtonWidth;
        rowWidth = TOPOGRAPHIC_BLOCK_WIDTH + modeButtonWidth + openButtonWidth + BLOCK_GAP * 2;
    }

    int minimumSize() { return rowWidth + 44; }

    int openButtonLeft(int fullLeft, int fullSize) {
        return modeButtonLeft(fullLeft, fullSize) + modeButtonWidth + BLOCK_GAP;
    }

    int modeButtonLeft(int fullLeft, int fullSize) {
        return topographicBlockLeft(fullLeft, fullSize) + TOPOGRAPHIC_BLOCK_WIDTH + BLOCK_GAP;
    }

    int topographicBlockLeft(int fullLeft, int fullSize) {
        return fullLeft + (fullSize - rowWidth) / 2;
    }

    static int buttonTop(int fullTop, int fullSize) {
        return fullTop + fullSize - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
    }

    int topographicZoneLeft(int fullLeft, int fullSize, int zone) {
        int first = topographicBlockLeft(fullLeft, fullSize);
        return zone == 0 ? first : zone == 1 ? first + TOPOGRAPHIC_BUTTON_WIDTH
                : first + TOPOGRAPHIC_BUTTON_WIDTH + TOPOGRAPHIC_FIELD_WIDTH;
    }
}
