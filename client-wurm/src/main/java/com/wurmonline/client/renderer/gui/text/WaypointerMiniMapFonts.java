package com.wurmonline.client.renderer.gui.text;

import java.awt.Font;

/** Creates the same title face used by the High-res Healthbar nameplate. */
public final class WaypointerMiniMapFonts {
    private WaypointerMiniMapFonts() { }

    public static TextFont healthbarTitle() {
        try {
            return new SimpleTextFont(
                    new Font("SansSerif", Font.BOLD, 13), true);
        } catch (Throwable ignored) {
            return TextFont.getBoldText();
        }
    }
}
