package com.wurmonline.client.renderer.gui.text;

import org.chamomilo.wurm.ui.v1.UiDensity;

/** Product text roles share the bundled Chamomilo font pool and preserve content case. */
public final class WaypointerFonts {
    private WaypointerFonts() { }
    public static TextFont body() { return body(false); }
    public static TextFont body(boolean bold) { return ChamomiloUiV1Fonts.caption(16,bold,UiDensity.LOW); }
    public static TextFont title() { return ChamomiloUiV1Fonts.caption(18,true,UiDensity.HIGH); }
    public static TextFont compact(boolean bold) { return ChamomiloUiV1Fonts.caption(12,bold,UiDensity.HIGH); }
}
