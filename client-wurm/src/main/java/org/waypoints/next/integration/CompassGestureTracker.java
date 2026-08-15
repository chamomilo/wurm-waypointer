package org.waypoints.next.integration;

import java.util.Map;
import java.util.WeakHashMap;

/** Distinguishes a compass click from the native drag gesture. */
public final class CompassGestureTracker {
    public enum ClickTarget { NONE, COMPASS, WAYPOINT_MARKER }

    private final int thresholdSquared;
    private final Map<Object, Gesture> gestures = new WeakHashMap<Object, Gesture>();

    public CompassGestureTracker(int thresholdPixels) {
        if (thresholdPixels < 0) throw new IllegalArgumentException("threshold must be non-negative");
        thresholdSquared = thresholdPixels * thresholdPixels;
    }

    public synchronized void pressed(Object component, int x, int y) {
        pressed(component, x, y, false);
    }

    public synchronized void pressed(Object component, int x, int y,
                                     boolean waypointMarker) {
        if (component == null) return;
        gestures.put(component, new Gesture(x, y, waypointMarker));
    }

    public synchronized void dragged(Object component, int x, int y) {
        Gesture gesture = gestures.get(component);
        if (gesture != null) gesture.observe(x, y, thresholdSquared);
    }

    public synchronized boolean released(Object component, int x, int y) {
        return releasedTarget(component, x, y) != ClickTarget.NONE;
    }

    public synchronized ClickTarget releasedTarget(Object component, int x, int y) {
        Gesture gesture = gestures.remove(component);
        if (gesture == null) return ClickTarget.NONE;
        gesture.observe(x, y, thresholdSquared);
        if (gesture.dragged) return ClickTarget.NONE;
        return gesture.waypointMarker ? ClickTarget.WAYPOINT_MARKER : ClickTarget.COMPASS;
    }

    synchronized int activeGestureCount() {
        return gestures.size();
    }

    private static final class Gesture {
        private final int startX;
        private final int startY;
        private final boolean waypointMarker;
        private boolean dragged;

        private Gesture(int startX, int startY, boolean waypointMarker) {
            this.startX = startX;
            this.startY = startY;
            this.waypointMarker = waypointMarker;
        }

        private void observe(int x, int y, int thresholdSquared) {
            long dx = (long) x - startX;
            long dy = (long) y - startY;
            if (dx * dx + dy * dy > thresholdSquared) dragged = true;
        }
    }
}
