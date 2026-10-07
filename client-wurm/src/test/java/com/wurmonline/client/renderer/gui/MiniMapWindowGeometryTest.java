package com.wurmonline.client.renderer.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiniMapWindowGeometryTest {
    @Test public void compactControlsFitBesideEachOtherInsideTheFrame() {
        int left = 100, size = MiniMapWindow.MAP_SIZE;
        int open = MiniMapControlsLayout.openButtonLeft(left, size);
        int mode = MiniMapControlsLayout.modeButtonLeft(left, size);
        int topographic = MiniMapControlsLayout.topographicBlockLeft(left, size);
        assertEquals(MiniMapControlsLayout.BLOCK_GAP,
                open - mode - MiniMapControlsLayout.MODE_BUTTON_WIDTH);
        assertEquals(MiniMapControlsLayout.BLOCK_GAP,
                mode - topographic - MiniMapControlsLayout.TOPOGRAPHIC_BLOCK_WIDTH);
        assertEquals(50, topographic - left);
        assertEquals(50, left + size - open - MiniMapControlsLayout.OPEN_BUTTON_WIDTH);
        assertEquals(20, MiniMapControlsLayout.BUTTON_HEIGHT);
        assertEquals(272, MiniMapControlsLayout.buttonTop(0, size));
        assertEquals(topographic, MiniMapControlsLayout.topographicZoneLeft(left, size, 0));
        assertEquals(MiniMapControlsLayout.topographicZoneLeft(left, size, 0)
                + MiniMapControlsLayout.TOPOGRAPHIC_BUTTON_WIDTH,
                MiniMapControlsLayout.topographicZoneLeft(left, size, 1));
        assertEquals(MiniMapControlsLayout.topographicZoneLeft(left, size, 1)
                + MiniMapControlsLayout.TOPOGRAPHIC_FIELD_WIDTH,
                MiniMapControlsLayout.topographicZoneLeft(left, size, 2));
        assertEquals(MiniMapControlsLayout.topographicZoneLeft(left, size, 2)
                + MiniMapControlsLayout.TOPOGRAPHIC_BUTTON_WIDTH,
                topographic + MiniMapControlsLayout.TOPOGRAPHIC_BLOCK_WIDTH);
    }

    @Test
    public void miniMapHasNoInvisibleWWindowMargins() {
        assertFalse(WWindow.class.isAssignableFrom(MiniMapWindow.class));
        assertEquals(MiniMapWindow.MAP_SIZE, MiniMapWindow.WINDOW_WIDTH);
        assertEquals(MiniMapWindow.MAP_SIZE, MiniMapWindow.WINDOW_HEIGHT);
    }
}
