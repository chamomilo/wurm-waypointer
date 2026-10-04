package com.wurmonline.client.renderer.gui;
import org.waypoints.next.model.WaypointLayer;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the custom map-mark editor for the active HUD. */
public final class CustomMapMarkWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Map");
    private static HeadsUpDisplay owner;
    private static CustomMapMarkWindow window;

    private CustomMapMarkWindowBridge() { }

    public static synchronized void open(HeadsUpDisplay hud,
                                         int tileX, int tileY) {
        open(hud, tileX, tileY, WaypointLayer.SURFACE);
    }

    public static synchronized void open(HeadsUpDisplay hud,
                                         int tileX, int tileY, WaypointLayer layer) {
        if (hud == null) return;
        try {
            detach(owner, "replace custom map mark editor");
            owner = hud;
            window = new CustomMapMarkWindow(tileX, tileY, layer);
            window.setInitialSize(390, 155, true);
            window.setPosition(Math.max(20, (hud.getWidth() - window.width) / 2),
                    Math.max(35, (hud.getHeight() - window.height) / 2));
            add(hud, window);
            hud.setActiveWindow(window);
            window.focusInput();
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Custom map mark window failed open",
                    failure);
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null) return;
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            window.prepareDetach();
            if (target != null) remove(target, window);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Custom map mark detach failed open", failure);
        }
        window = null;
        if (target == owner) owner = null;
    }

    static synchronized void closed(CustomMapMarkWindow value) {
        if (value != null && value == window) detach(owner, "window close");
    }

    private static void add(HeadsUpDisplay hud, WurmComponent component)
            throws ReflectiveOperationException {
        Method method = ReflectionUtil.getMethod(HeadsUpDisplay.class,
                "addComponent", new Class<?>[]{WurmComponent.class});
        ReflectionUtil.callPrivateMethod(hud, method, component);
    }

    private static void remove(HeadsUpDisplay hud, WurmComponent component)
            throws ReflectiveOperationException {
        Method method = ReflectionUtil.getMethod(HeadsUpDisplay.class,
                "removeComponent", new Class<?>[]{WurmComponent.class});
        ReflectionUtil.callPrivateMethod(hud, method, component);
    }
}
