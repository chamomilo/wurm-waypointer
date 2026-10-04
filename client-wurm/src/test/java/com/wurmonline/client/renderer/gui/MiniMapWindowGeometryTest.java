package com.wurmonline.client.renderer.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class MiniMapWindowGeometryTest {
    @Test
    public void miniMapHasNoInvisibleWWindowMargins() {
        assertFalse(WWindow.class.isAssignableFrom(MiniMapWindow.class));
        assertEquals(MiniMapWindow.MAP_SIZE, MiniMapWindow.WINDOW_WIDTH);
        assertEquals(MiniMapWindow.MAP_SIZE, MiniMapWindow.WINDOW_HEIGHT);
    }
}
