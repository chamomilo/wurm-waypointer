package com.wurmonline.client.renderer.gui;

import org.waypoints.next.integration.WurmWaypointerRuntime;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Adds one Waypointer action to Wurm's existing compass context menu. */
public final class CompassSurroundingsPopupBridge {
    private static final Logger LOGGER = Logger.getLogger(
            "WurmWaypointer.CompassMenu");

    private CompassSurroundingsPopupBridge() { }

    public static void augment(Object value) {
        try {
            if (!(value instanceof WurmPopup)) return;
            WurmPopup popup = (WurmPopup) value;
            if (!"compassMenu".equals(popup.id)) return;
            for (FlexComponent component : popup.getComponents()) {
                if (component instanceof SurroundingsButton) return;
            }
            popup.addSeparator();
            popup.addButton(new SurroundingsButton(popup));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Compass Surroundings menu item failed open", failure);
        }
    }

    private static final class SurroundingsButton
            extends WurmPopup.WPopupLiveButton {
        private SurroundingsButton(WurmPopup owner) {
            owner.super("Surroundings");
            setHoverString("Open the live catalog of nearby animals, containers and items.");
        }

        @Override protected void handleLeftClick() {
            WurmWaypointerRuntime.openSurroundings();
        }
    }
}
