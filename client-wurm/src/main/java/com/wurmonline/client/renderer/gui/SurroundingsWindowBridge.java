package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.settings.SavePosManager;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.ui.SurroundingsController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns exactly one native Surroundings window for the active HUD. */
public final class SurroundingsWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Surroundings");
    private static HeadsUpDisplay owner;
    private static SurroundingsWindow window;

    private SurroundingsWindowBridge() { }

    public static synchronized void open(HeadsUpDisplay hud,
                                         SurroundingsController controller) {
        if (hud == null || controller == null) return;
        try {
            if (owner != hud) detach(owner, "HUD replacement");
            owner = hud;
            if (window == null) {
                window = new SurroundingsWindow(controller);
                window.setInitialSize(Math.min(1040, Math.max(940, hud.getWidth() - 100)),
                        Math.min(620, Math.max(400, hud.getHeight() - 120)), true);
                window.setPosition(Math.max(20, (hud.getWidth() - window.width) / 2),
                        Math.max(20, (hud.getHeight() - window.height) / 2));
                add(hud, window);
                registerPosition(hud, window);
                LOGGER.info("Surroundings window attached: size=" + window.width
                        + "x" + window.height);
            } else if (!hud.getComponents().contains(window)) {
                add(hud, window);
            }
            window.refreshFromController();
            hud.setActiveWindow(window);
        } catch (Throwable failure) {
            controller.reportFailure("open window", failure);
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null) return;
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            if (target != null) remove(target, window);
            LOGGER.info("Surroundings window detached: reason=" + oneLine(reason));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings detach failed open", failure);
        }
        window = null;
        if (target == owner) owner = null;
    }

    static synchronized void closed(SurroundingsWindow value) {
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

    private static void registerPosition(HeadsUpDisplay hud,
                                         SurroundingsWindow value)
            throws ReflectiveOperationException {
        Field field = ReflectionUtil.getField(HeadsUpDisplay.class, "savePosManager");
        SavePosManager positions = ReflectionUtil.getPrivateField(hud, field);
        if (positions != null) {
            positions.registerAndRefresh(value, "wurm-waypointer.surroundings");
        }
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
