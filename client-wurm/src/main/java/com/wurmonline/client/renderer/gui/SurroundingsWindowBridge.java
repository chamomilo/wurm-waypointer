package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.settings.SavePosManager;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.ui.SurroundingsController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Switches one active HUD between full Surroundings and compact Monitoring. */
public final class SurroundingsWindowBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Surroundings");
    private static HeadsUpDisplay owner;
    private static SurroundingsWindow window;
    private static SurroundingsMonitoringWindow monitoring;
    private static SurroundingsController activeController;

    private SurroundingsWindowBridge() { }

    /** Routes the wheel before the HUD replaces its absolute coordinates. */
    public static synchronized boolean mouseWheeled(HeadsUpDisplay hud,
                                                     int mouseX, int mouseY,
                                                     int wheelDelta) {
        if (hud == null || hud != owner || wheelDelta == 0) return false;
        if (window != null && hud.getComponents().contains(window)
                && isTopmostAt(hud, window, mouseX, mouseY)
                && window.mouseWheeledAt(mouseX, mouseY, wheelDelta)) {
            return true;
        }
        return monitoring != null && hud.getComponents().contains(monitoring)
                && isTopmostAt(hud, monitoring, mouseX, mouseY)
                && monitoring.mouseWheeledAt(mouseX, mouseY, wheelDelta);
    }

    public static synchronized void open(HeadsUpDisplay hud,
                                         SurroundingsController controller) {
        if (hud == null || controller == null) return;
        try {
            if (owner != hud) detach(owner, "HUD replacement");
            owner = hud;
            activeController = controller;
            removeMonitoring(hud);
            if (window == null) {
                window = new SurroundingsWindow(controller);
                window.setInitialSize(Math.min(1040, Math.max(940, hud.getWidth() - 100)),
                        Math.min(620, Math.max(400, hud.getHeight() - 120)), true);
                window.setPosition(Math.max(20, (hud.getWidth() - window.width) / 2),
                        Math.max(20, (hud.getHeight() - window.height) / 2));
                add(hud, window);
                registerPosition(hud, window,
                        "wurm-waypointer.surroundings");
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

    static synchronized void showMonitoring(
            SurroundingsWindow source,
            List<SurroundingsQuery> queries) {
        if (source == null || source != window || owner == null
                || activeController == null || queries == null
                || queries.isEmpty()) return;
        try {
            if (owner.getComponents().contains(window)) remove(owner, window);
            removeMonitoring(owner);
            monitoring = new SurroundingsMonitoringWindow(
                    activeController, queries);
            monitoring.setInitialSize(SurroundingsMonitoringWindow.WINDOW_WIDTH,
                    Math.min(540, Math.max(300, owner.getHeight() - 180)), true);
            monitoring.setPosition(Math.max(20, owner.getWidth()
                            - monitoring.width - 35),
                    Math.max(35, (owner.getHeight() - monitoring.height) / 2));
            add(owner, monitoring);
            registerPosition(owner, monitoring,
                    "wurm-waypointer.surroundings-monitoring");
            owner.setActiveWindow(monitoring);
            LOGGER.info("Surroundings switched to Monitoring: filters="
                    + queries.size() + ", size=" + monitoring.width
                    + "x" + monitoring.height);
        } catch (Throwable failure) {
            SurroundingsMonitoringWindow failed = monitoring;
            if (failed != null) try {
                if (owner.getComponents().contains(failed)) remove(owner, failed);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            monitoring = null;
            try {
                if (!owner.getComponents().contains(window)) add(owner, window);
                owner.setActiveWindow(window);
            } catch (Throwable restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            activeController.reportFailure("open monitoring", failure);
        }
    }

    static synchronized void showSurroundings(
            SurroundingsMonitoringWindow source) {
        if (source == null || source != monitoring || owner == null
                || window == null) return;
        SurroundingsMonitoringWindow previous = monitoring;
        try {
            if (owner.getComponents().contains(previous)) remove(owner, previous);
            monitoring = null;
            if (!owner.getComponents().contains(window)) add(owner, window);
            window.refreshFromController();
            owner.setActiveWindow(window);
            LOGGER.info("Monitoring switched to full Surroundings");
        } catch (Throwable failure) {
            try {
                if (owner.getComponents().contains(window)) remove(owner, window);
                if (!owner.getComponents().contains(previous)) add(owner, previous);
                monitoring = previous;
                owner.setActiveWindow(previous);
            } catch (Throwable restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            if (activeController != null) {
                activeController.reportFailure("return to surroundings", failure);
            } else {
                LOGGER.log(Level.WARNING,
                        "Return to Surroundings failed", failure);
            }
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null && monitoring == null) return;
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            if (target != null) {
                if (window != null && target.getComponents().contains(window)) {
                    remove(target, window);
                }
                if (monitoring != null
                        && target.getComponents().contains(monitoring)) {
                    remove(target, monitoring);
                }
            }
            LOGGER.info("Surroundings window detached: reason=" + oneLine(reason));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Surroundings detach failed open", failure);
        }
        window = null;
        monitoring = null;
        activeController = null;
        if (target == owner) owner = null;
    }

    static synchronized void closed(SurroundingsWindow value) {
        if (value != null && value == window) detach(owner, "window close");
    }

    static synchronized void closed(SurroundingsMonitoringWindow value) {
        if (value == null || value != monitoring) return;
        try {
            removeMonitoring(owner);
            LOGGER.info("Monitoring window closed");
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Monitoring close failed open", failure);
        }
    }

    private static void removeMonitoring(HeadsUpDisplay hud)
            throws ReflectiveOperationException {
        if (monitoring != null && hud != null
                && hud.getComponents().contains(monitoring)) {
            remove(hud, monitoring);
        }
        monitoring = null;
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
                                         WWindow value, String key)
            throws ReflectiveOperationException {
        Field field = ReflectionUtil.getField(HeadsUpDisplay.class, "savePosManager");
        SavePosManager positions = ReflectionUtil.getPrivateField(hud, field);
        if (positions != null) {
            positions.registerAndRefresh(value, key);
        }
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /** Mirrors HeadsUpDisplay#getTopComponentAt without descending into rows. */
    private static boolean isTopmostAt(HeadsUpDisplay hud,
                                       WurmComponent expected,
                                       int mouseX, int mouseY) {
        List<WurmComponent> components = hud.getComponents();
        for (int index = components.size() - 1; index >= 0; index--) {
            WurmComponent candidate = components.get(index);
            if (candidate.contains(mouseX, mouseY) && candidate.isAvailable()) {
                return candidate == expected;
            }
        }
        return false;
    }
}
