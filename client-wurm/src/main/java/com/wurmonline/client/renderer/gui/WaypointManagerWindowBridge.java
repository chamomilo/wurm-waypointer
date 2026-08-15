package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.settings.SavePosManager;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.ui.WaypointManagerController;
import org.waypoints.next.ui.WaypointManagerVisibilityPolicy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.UUID;

/** Owns exactly one native manager window for the active HUD. */
public final class WaypointManagerWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Manager");
    private static HeadsUpDisplay owner;
    private static WaypointManagerWindow window;

    private WaypointManagerWindowBridge() {
    }

    public static synchronized boolean toggle(
            HeadsUpDisplay hud, WaypointManagerController controller) {
        boolean attached = hud != null && window != null
                && hud.getComponents().contains(window);
        if (WaypointManagerVisibilityPolicy.shouldClose(
                window != null, owner == hud, attached)) {
            detach(hud, "compass toggle");
            return false;
        }
        open(hud, controller);
        return true;
    }

    public static synchronized void open(HeadsUpDisplay hud,
                                         WaypointManagerController controller) {
        if (hud == null || controller == null) return;
        try {
            if (owner != hud) detach(owner, "HUD replacement");
            owner = hud;
            if (window == null) {
                window = new WaypointManagerWindow(controller);
                window.setInitialSize(Math.min(1120, Math.max(900, hud.getWidth() - 120)),
                        Math.min(560, Math.max(360, hud.getHeight() - 140)), true);
                window.setPosition(Math.max(20, (hud.getWidth() - window.width) / 2),
                        Math.max(20, (hud.getHeight() - window.height) / 2));
                add(hud, window);
                registerPosition(hud, window);
                window.normalizeListSizeAfterRestore();
                LOGGER.info("Waypoint Manager attached: hud=" + identity(hud)
                        + ", size=" + window.width + "x" + window.height);
            } else if (!hud.getComponents().contains(window)) {
                add(hud, window);
            }
            window.refreshFromController();
            hud.setActiveWindow(window);
        } catch (Throwable failure) {
            controller.reportFailure("open manager", failure);
        }
    }

    public static synchronized void openEdit(
            HeadsUpDisplay hud, WaypointManagerController controller, UUID id) {
        if (hud == null || controller == null || id == null) return;
        try {
            open(hud, controller);
            if (window == null) return;
            window.openEdit(id);
            hud.setActiveWindow(window);
        } catch (Throwable failure) {
            controller.reportFailure("open waypoint edit", failure);
        }
    }

    public static synchronized void openCreateCoordinates(
            HeadsUpDisplay hud, WaypointManagerController controller,
            String suggestedName, String coordinates) {
        if (hud == null || controller == null || coordinates == null) return;
        try {
            open(hud, controller);
            if (window == null) return;
            window.openCreateCoordinates(suggestedName, coordinates);
            hud.setActiveWindow(window);
        } catch (Throwable failure) {
            controller.reportFailure("open map waypoint", failure);
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null) return;
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            window.prepareDetach();
            if (target != null) remove(target, window);
            LOGGER.info("Waypoint Manager detached: reason=" + oneLine(reason));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Waypoint Manager detach failed open", failure);
        }
        window = null;
        if (target == owner) owner = null;
    }

    static synchronized void closed(WaypointManagerWindow value) {
        if (value == null || value != window) return;
        detach(owner, "window close");
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
                                         WaypointManagerWindow value)
            throws ReflectiveOperationException {
        Field field = ReflectionUtil.getField(HeadsUpDisplay.class, "savePosManager");
        SavePosManager positions = ReflectionUtil.getPrivateField(hud, field);
        if (positions != null) {
            positions.registerAndRefresh(value, "wurm-waypointer.manager");
        }
    }

    private static String identity(Object value) {
        return value == null ? "null" : value.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
