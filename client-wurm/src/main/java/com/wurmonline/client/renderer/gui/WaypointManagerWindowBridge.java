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
import java.util.ArrayList;
import java.util.List;

/** Owns exactly one native manager window for the active HUD. */
public final class WaypointManagerWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Manager");
    private static HeadsUpDisplay owner;
    private static WaypointerHubWindow window;

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
                window = new WaypointerHubWindow(controller);
                window.setInitialSize(Math.min(1380, Math.max(1100, hud.getWidth() - 40)),
                        Math.min(680, Math.max(430, hud.getHeight() - 100)), true);
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
        WaypointerHubWindow closing = window;
        HeadsUpDisplay target = owner == null ? hud : owner;
        boolean ownedFocus = false;
        try {
            if (target != null) {
                WurmComponent focus = ReflectionUtil.getPrivateField(target,
                        ReflectionUtil.getField(HeadsUpDisplay.class, "kbFocusComponent"));
                ownedFocus = closing.ownsHudComponent(focus);
                if (ownedFocus) target.stopTyping();
                List<WurmDropdownPopup> popups = ReflectionUtil.getPrivateField(target,
                        ReflectionUtil.getField(HeadsUpDisplay.class, "dropdownPopups"));
                popups.removeIf(popup -> closing.ownsHudComponent(popup.dropDown));
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Unable to clear manager input/popups", failure);
        }
        try { closing.prepareDetach(); }
        catch (Throwable failure) { LOGGER.log(Level.WARNING, "Unable to finish manager cleanup", failure); }
        if (target != null) {
            // Also collect cached panels accidentally promoted by earlier focus code.
            // Removing the frame alone would leave those panels drawing over the world.
            for (WurmComponent component : new ArrayList<WurmComponent>(target.getComponents())) {
                if (!closing.ownsHudComponent(component)) continue;
                try { remove(target, component); }
                catch (Throwable failure) { LOGGER.log(Level.WARNING, "Unable to remove manager component", failure); }
            }
            if (ownedFocus) {
                try { ReflectionUtil.callPrivateMethod(target,
                        ReflectionUtil.getMethod(HeadsUpDisplay.class, "resetKeyboardFocus", new Class<?>[0])); }
                catch (Throwable failure) { LOGGER.log(Level.WARNING, "Unable to restore keyboard focus", failure); }
            }
        }
        LOGGER.info("Waypoint Manager detached: reason=" + oneLine(reason));
        window = null;
        if (target == owner) owner = null;
    }

    static synchronized void closed(WaypointerHubWindow value) {
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
                                         WaypointerHubWindow value)
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

    public static synchronized void openSection(HeadsUpDisplay hud,
            WaypointManagerController controller, org.waypoints.next.ui.WaypointerSection section) {
        open(hud, controller);
        if (window != null) window.select(section);
    }

    public static synchronized boolean mouseWheeled(HeadsUpDisplay hud,int x,int y,int delta) {
        if (owner != hud || window == null || !hud.getComponents().contains(window)) return false;
        java.util.List<WurmComponent> components=hud.getComponents();
        for(int i=components.size()-1;i>=0;i--)if(components.get(i).contains(x,y)&&components.get(i).isAvailable())
            return components.get(i)==window&&window.mouseWheeledAt(x,y,delta);
        return false;
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
