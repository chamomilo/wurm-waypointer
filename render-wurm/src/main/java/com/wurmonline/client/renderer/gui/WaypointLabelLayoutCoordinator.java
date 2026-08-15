package com.wurmonline.client.renderer.gui;

import org.waypoints.next.navigation.WaypointLabelCollisionLayout;

import java.util.ArrayList;
import java.util.List;

/** Coordinates collision-free next-frame bounds for one set of waypoint labels. */
public final class WaypointLabelLayoutCoordinator {
    static final int TOP_OFFSET = 14;
    private static final int HORIZONTAL_GAP = 2;
    private static final int VERTICAL_GAP = 2;

    private final List<WaypointLabelComponent> components =
            new ArrayList<WaypointLabelComponent>();
    private WaypointLabelComponent[] ordered = new WaypointLabelComponent[0];
    private boolean[] used = new boolean[0];
    private int[] left = new int[0];
    private int[] width = new int[0];
    private int[] height = new int[0];
    private int[] top = new int[0];

    synchronized void register(WaypointLabelComponent component) {
        if (component == null || components.contains(component)) return;
        components.add(component);
        prepareAll();
    }

    synchronized void unregister(WaypointLabelComponent component) {
        if (component == null || !components.remove(component)) return;
        prepareAll();
    }

    synchronized void prepareNextFrame(WaypointLabelComponent source) {
        if (source == null || source != leader()) return;
        prepareAll();
    }

    private WaypointLabelComponent leader() {
        WaypointLabelComponent result = null;
        for (int i = 0; i < components.size(); i++) {
            WaypointLabelComponent component = components.get(i);
            if (result == null || component.layoutOrder() < result.layoutOrder()) {
                result = component;
            }
        }
        return result;
    }

    private void prepareAll() {
        int count = components.size();
        if (count == 0) return;
        ensureCapacity(count);
        for (int i = 0; i < count; i++) used[i] = false;
        for (int position = 0; position < count; position++) {
            int best = -1;
            for (int candidate = 0; candidate < count; candidate++) {
                if (used[candidate]) continue;
                if (best < 0 || before(components.get(candidate),
                        components.get(best))) best = candidate;
            }
            used[best] = true;
            WaypointLabelComponent component = components.get(best);
            ordered[position] = component;
            component.measureForLayout();
            left[position] = component.measuredLeft();
            width[position] = component.measuredWidth();
            height[position] = component.measuredHeight();
        }
        WaypointLabelCollisionLayout.stack(left, width, height, count,
                TOP_OFFSET, HORIZONTAL_GAP, VERTICAL_GAP, top);
        for (int i = 0; i < count; i++) ordered[i].applyMeasuredTop(top[i]);
    }

    private static boolean before(WaypointLabelComponent left,
                                  WaypointLabelComponent right) {
        int order = Integer.compare(left.layoutOrder(), right.layoutOrder());
        return order < 0 || (order == 0
                && left.creationOrder() < right.creationOrder());
    }

    private void ensureCapacity(int count) {
        if (ordered.length >= count) return;
        int capacity = Math.max(count, Math.max(8, ordered.length * 2));
        ordered = new WaypointLabelComponent[capacity];
        used = new boolean[capacity];
        left = new int[capacity];
        width = new int[capacity];
        height = new int[capacity];
        top = new int[capacity];
    }
}
