package com.wurmonline.client.renderer.gui;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.map.Deed;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the deed-information window for the active HUD. */
public final class DeedInformationWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Map");
    private static HeadsUpDisplay owner;
    private static DeedInformationWindow window;

    private DeedInformationWindowBridge() { }

    public static synchronized void open(HeadsUpDisplay hud, Deed deed) {
        if (hud == null || deed == null) return;
        try {
            detach(owner, "replace deed details");
            owner = hud;
            window = new DeedInformationWindow(deed);
            int mottoLines = Math.max(1,
                    (deed.getMotto() == null ? 0 : deed.getMotto().length()) / 56 + 1);
            window.setInitialSize(470, Math.min(390, 285 + mottoLines * 20), true);
            window.setPosition(Math.max(20, (hud.getWidth() - window.width) / 2),
                    Math.max(35, (hud.getHeight() - window.height) / 2));
            add(hud, window);
            hud.setActiveWindow(window);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Deed information window failed open",
                    failure);
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null) return;
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            if (target != null) remove(target, window);
            LOGGER.info("Deed information detached: reason=" + oneLine(reason));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Deed information detach failed open", failure);
        }
        window = null;
        if (target == owner) owner = null;
    }

    static synchronized void closed(DeedInformationWindow value) {
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

    private static String oneLine(String value) {
        return value == null ? ""
                : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
