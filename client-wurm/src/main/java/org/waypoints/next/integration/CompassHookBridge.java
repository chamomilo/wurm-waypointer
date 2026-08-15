package org.waypoints.next.integration;

import com.wurmonline.client.renderer.gui.WaypointCompassMarkerBridge;
import org.waypoints.next.navigation.NavigationTargetKey;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.WeakHashMap;

/** Static methods called from code inserted into package-private CompassComponent. */
public final class CompassHookBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Compass");
    private static final CompassGestureTracker GESTURES = new CompassGestureTracker(5);
    private static final Map<Object, Object> PRESSED_TARGETS =
            new WeakHashMap<Object, Object>();

    private CompassHookBridge() {
    }

    public static void pressed(Object compass, int x, int y) {
        try {
            Object target = WaypointCompassMarkerBridge.hitTarget(compass, x, y);
            synchronized (PRESSED_TARGETS) {
                if (target == null) PRESSED_TARGETS.remove(compass);
                else PRESSED_TARGETS.put(compass, target);
            }
            GESTURES.pressed(compass, x, y, target != null);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass press probe failed open", failure);
        }
    }

    public static void dragged(Object compass, int x, int y) {
        try {
            GESTURES.dragged(compass, x, y);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass drag probe failed open", failure);
        }
    }

    public static void released(Object compass, int x, int y) {
        try {
            CompassGestureTracker.ClickTarget target =
                    GESTURES.releasedTarget(compass, x, y);
            Object pressedTarget;
            synchronized (PRESSED_TARGETS) {
                pressedTarget = PRESSED_TARGETS.remove(compass);
            }
            if (target == CompassGestureTracker.ClickTarget.WAYPOINT_MARKER) {
                WurmWaypointerRuntime.compassWaypointMarkerClicked(pressedTarget);
            } else if (target == CompassGestureTracker.ClickTarget.COMPASS) {
                WurmWaypointerRuntime.compassClicked();
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass release probe failed open", failure);
        }
    }

    /** Consumes a native right press only when it opens one exact static waypoint. */
    public static boolean rightPressed(Object compass, int x, int y) {
        try {
            Object target = WaypointCompassMarkerBridge.hitTarget(compass, x, y);
            if (!(target instanceof NavigationTargetKey)) return false;
            WurmWaypointerRuntime.compassWaypointMarkerRightClicked(
                    (NavigationTargetKey) target);
            return true;
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Compass right-click probe failed open", failure);
            return false;
        }
    }

}
