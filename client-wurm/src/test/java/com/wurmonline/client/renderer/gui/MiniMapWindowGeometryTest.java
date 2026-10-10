package com.wurmonline.client.renderer.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiniMapWindowGeometryTest {
    @Test public void compactControlsFitBesideEachOtherInsideTheFrame() {
        int left = 100, size = MiniMapWindow.MAP_SIZE;
        MiniMapControlsLayout layout = new MiniMapControlsLayout(80, 68);
        int open = layout.openButtonLeft(left, size);
        int mode = layout.modeButtonLeft(left, size);
        int topographic = layout.topographicBlockLeft(left, size);
        assertEquals(MiniMapControlsLayout.BLOCK_GAP,
                open - mode - layout.modeButtonWidth);
        assertEquals(MiniMapControlsLayout.BLOCK_GAP,
                mode - topographic - MiniMapControlsLayout.TOPOGRAPHIC_BLOCK_WIDTH);
        assertTrue(topographic - left >= 22);
        assertEquals(topographic - left, left + size - open - layout.openButtonWidth);
        assertEquals(26, MiniMapControlsLayout.BUTTON_HEIGHT);
        assertEquals(272, MiniMapControlsLayout.buttonTop(0, size));
        assertTrue(MiniMapControlsLayout.buttonTop(0,size)>=size-MiniMapControlsLayout.FOOTER_HEIGHT);
        assertTrue(MiniMapControlsLayout.buttonTop(0,size)+MiniMapControlsLayout.BUTTON_HEIGHT<=size);
        assertEquals(topographic, layout.topographicZoneLeft(left, size, 0));
        assertEquals(layout.topographicZoneLeft(left, size, 0)
                + MiniMapControlsLayout.TOPOGRAPHIC_BUTTON_WIDTH,
                layout.topographicZoneLeft(left, size, 1));
        assertEquals(layout.topographicZoneLeft(left, size, 1)
                + MiniMapControlsLayout.TOPOGRAPHIC_FIELD_WIDTH,
                layout.topographicZoneLeft(left, size, 2));
        assertEquals(layout.topographicZoneLeft(left, size, 2)
                + MiniMapControlsLayout.TOPOGRAPHIC_BUTTON_WIDTH,
                topographic + MiniMapControlsLayout.TOPOGRAPHIC_BLOCK_WIDTH);
    }

    @Test public void longCaptionsExpandTheSquareWithoutOverlappingFrameCorners() {
        MiniMapControlsLayout layout = new MiniMapControlsLayout(130, 110);
        int size = Math.max(MiniMapWindow.MAP_SIZE, layout.minimumSize());
        assertTrue(size > MiniMapWindow.MAP_SIZE);
        assertEquals(22, layout.topographicBlockLeft(0, size));
        assertEquals(size - 22, layout.openButtonLeft(0, size) + layout.openButtonWidth);
    }

    @Test
    public void miniMapHasNoInvisibleWWindowMargins() {
        assertFalse(WWindow.class.isAssignableFrom(MiniMapWindow.class));
    }
}
