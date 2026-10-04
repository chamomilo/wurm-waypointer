package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.settings.SavePosManager;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.map.Deed;
import org.waypoints.next.map.ServerMapSnapshot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the native deed search window associated with the active M-map HUD. */
public final class DeedSearchWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Map");
    private static HeadsUpDisplay owner;
    private static DeedSearchWindow window;

    private DeedSearchWindowBridge() { }

    public static synchronized void open(HeadsUpDisplay hud, List<Deed> deeds) {
        open(hud, deeds, null);
    }

    public static synchronized void open(HeadsUpDisplay hud,
                                         ServerMapSnapshot snapshot) {
        open(hud, snapshot == null ? null : snapshot.getDeeds(), snapshot);
    }

    private static void open(HeadsUpDisplay hud, List<Deed> deeds,
                             ServerMapSnapshot snapshot) {
        if (hud == null) return;
        try {
            if (owner != hud) detach(owner, "HUD replacement");
            owner = hud;
            if (window == null) {
                window = snapshot == null ? new DeedSearchWindow(deeds)
                        : new DeedSearchWindow(snapshot);
                window.setInitialSize(600,
                        Math.min(560, Math.max(340, hud.getHeight() - 180)), true);
                window.setPosition(Math.max(20, hud.getWidth() - window.width - 50),
                        Math.max(45, (hud.getHeight() - window.height) / 2));
                add(hud, window);
                registerPosition(hud, window);
            } else {
                if (snapshot == null) window.updateDeeds(deeds);
                else window.updateSnapshot(snapshot);
                if (!hud.getComponents().contains(window)) add(hud, window);
            }
            hud.setActiveWindow(window);
            window.focusSearch();
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Deed search window failed open", failure);
        }
    }

    /** Refreshes an already-open picker without opening one from a HUD tick. */
    public static synchronized void refresh(HeadsUpDisplay hud,
                                            ServerMapSnapshot snapshot) {
        if (window == null || hud == null || hud != owner || snapshot == null) return;
        try {
            window.updateSnapshot(snapshot);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Deed search refresh failed open", failure);
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null) return;
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            window.prepareDetach();
            if (target != null) remove(target, window);
            LOGGER.info("Deed search detached: reason=" + oneLine(reason));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Deed search detach failed open", failure);
        }
        window = null;
        if (target == owner) owner = null;
    }

    static synchronized void closed(DeedSearchWindow value) {
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
                                         DeedSearchWindow value)
            throws ReflectiveOperationException {
        Field field = ReflectionUtil.getField(HeadsUpDisplay.class,
                "savePosManager");
        SavePosManager positions = ReflectionUtil.getPrivateField(hud, field);
        if (positions != null) positions.registerAndRefresh(
                value, "wurm-waypointer.deed-search");
    }

    private static String oneLine(String value) {
        return value == null ? ""
                : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
