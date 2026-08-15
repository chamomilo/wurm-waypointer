package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.game.World;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.render.WaypointRenderRuntimeBridge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns exactly one native cluster picker for the active HUD. */
public final class WaypointClusterPickerWindowBridge {
    private static final Logger LOGGER = Logger.getLogger(
            "WurmWaypointer.ClusterPicker");
    private static HeadsUpDisplay owner;
    private static WaypointClusterPickerWindow window;

    private WaypointClusterPickerWindowBridge() {
    }

    public static synchronized void open(HeadsUpDisplay hud,
                                         List<NavigationTarget> targets,
                                         World world, int markerX, int markerY) {
        if (hud == null || targets == null || targets.size() < 2 || world == null) return;
        try {
            detach(owner, "cluster replacement");
            owner = hud;
            window = new WaypointClusterPickerWindow(targets, world);
            int width = Math.min(window.preferredWidth(), Math.max(160, hud.getWidth() - 20));
            int height = Math.min(window.preferredHeight(), Math.max(120, hud.getHeight() - 20));
            window.setInitialSize(width, height, false);
            int x = clamp(markerX + 12, 0, Math.max(0, hud.getWidth() - width));
            int y = clamp(markerY + 12, 0, Math.max(0, hud.getHeight() - height));
            window.setPosition(x, y);
            add(hud, window);
            hud.setActiveWindow(window);
            LOGGER.info("Waypoint cluster picker attached: count=" + targets.size());
        } catch (Throwable failure) {
            detach(hud, "failed open cleanup");
            LOGGER.log(Level.FINE, "Waypoint cluster picker failed open", failure);
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null) return;
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            if (target != null) remove(target, window);
            LOGGER.info("Waypoint cluster picker detached: reason=" + oneLine(reason));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Waypoint cluster picker detach failed open", failure);
        }
        window = null;
        if (target == owner) owner = null;
    }

    /** A compass-marker click acts as a close gesture while the picker is open. */
    public static synchronized boolean closeIfOpen(HeadsUpDisplay hud,
                                                    String reason) {
        if (window == null) return false;
        detach(hud, reason);
        return true;
    }

    static synchronized void chosen(WaypointClusterPickerWindow value,
                                    NavigationTargetKey key) {
        if (value == null || value != window || key == null) return;
        detach(owner, "waypoint chosen");
        WaypointRenderRuntimeBridge.chooseCompassWaypoint(key);
    }

    static synchronized void closed(WaypointClusterPickerWindow value) {
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

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
    }
}
