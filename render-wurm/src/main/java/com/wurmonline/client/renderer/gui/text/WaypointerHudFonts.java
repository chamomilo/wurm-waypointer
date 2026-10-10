package com.wurmonline.client.renderer.gui.text;

import com.wurmonline.client.options.Options;
import org.chamomilo.wurm.ui.v1.UiDensity;

/** Shared kit typography for compact HUD controls and their expanded value captions. */
public final class WaypointerHudFonts {
    private WaypointerHudFonts() { }
    public static TextFont compact(boolean bold) {
        return WaypointerFonts.compact(bold);
    }
    public static TextFont doubled(boolean bold) {
        int size = Options.fontSizeDefault.value() * 2;
        return ChamomiloUiV1Fonts.caption(size,bold,UiDensity.HIGH);
    }
}
