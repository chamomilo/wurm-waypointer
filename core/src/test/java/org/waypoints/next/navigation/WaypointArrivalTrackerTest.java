package org.waypoints.next.navigation;

import org.junit.Test;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointSourceType;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class WaypointArrivalTrackerTest {
    @Test public void notifiesOncePerEntryAndRearmsOutsideHysteresis() {
        Counter listener = new Counter();
        WaypointArrivalTracker tracker = new WaypointArrivalTracker();
        NavigationSnapshot snapshot = snapshot(target(WaypointSourceType.STATIC,
                WaypointLayer.SURFACE, 20));

        tracker.update(snapshot, 20, 0, WaypointLayer.SURFACE, listener);
        tracker.update(snapshot, 4, 0, WaypointLayer.SURFACE, listener);
        tracker.update(snapshot, 3, 0, WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.count);
        assertEquals(16, listener.lastDistance);

        tracker.update(snapshot, 6, 0, WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.exitedCount);
        tracker.update(snapshot, 4, 0, WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.count);

        tracker.update(snapshot, 7, 0, WaypointLayer.SURFACE, listener);
        tracker.update(snapshot, 4, 0, WaypointLayer.SURFACE, listener);
        assertEquals(2, listener.count);
    }

    @Test public void visibilityGenerationChangeDoesNotBypassHysteresis() {
        Counter listener = new Counter();
        WaypointArrivalTracker tracker = new WaypointArrivalTracker();
        NavigationTarget target = target(WaypointSourceType.STATIC,
                WaypointLayer.SURFACE, 20);
        tracker.update(snapshot(2L, target), 20, 0,
                WaypointLayer.SURFACE, listener);
        tracker.update(snapshot(2L, target), 4, 0,
                WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.count);

        tracker.update(snapshot(3L, target), 6, 0,
                WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.exitedCount);
        tracker.update(snapshot(4L, target), 4, 0,
                WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.count);

        tracker.update(snapshot(4L, target), 7, 0,
                WaypointLayer.SURFACE, listener);
        tracker.update(snapshot(4L, target), 4, 0,
                WaypointLayer.SURFACE, listener);
        assertEquals(2, listener.count);
    }

    @Test public void initialInsideDoesNotNotifyUntilACompleteReentry() {
        Counter listener = new Counter();
        WaypointArrivalTracker tracker = new WaypointArrivalTracker();
        NavigationSnapshot snapshot = snapshot(target(WaypointSourceType.STATIC,
                WaypointLayer.SURFACE, 20));
        tracker.update(snapshot, 1, 0, WaypointLayer.SURFACE, listener);
        assertEquals(0, listener.count);
        tracker.update(snapshot, 8, 0, WaypointLayer.SURFACE, listener);
        tracker.update(snapshot, 1, 0, WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.count);
    }

    @Test public void requiresMatchingLayerAndIgnoresVanillaLandmarks() {
        Counter listener = new Counter();
        WaypointArrivalTracker tracker = new WaypointArrivalTracker();
        NavigationSnapshot ordinary = snapshot(target(WaypointSourceType.STATIC,
                WaypointLayer.CAVE, 20));
        tracker.update(ordinary, 10, 0, WaypointLayer.SURFACE, listener);
        tracker.update(ordinary, 1, 0, WaypointLayer.SURFACE, listener);
        assertEquals(0, listener.count);
        tracker.update(ordinary, 1, 0, WaypointLayer.CAVE, listener);
        assertEquals(1, listener.count);

        tracker.reset();
        NavigationSnapshot vanilla = snapshot(target(
                WaypointSourceType.VANILLA_SYSTEM, WaypointLayer.SURFACE, 20));
        tracker.update(vanilla, 10, 0, WaypointLayer.SURFACE, listener);
        tracker.update(vanilla, 1, 0, WaypointLayer.SURFACE, listener);
        assertEquals(1, listener.count);
    }

    private static NavigationSnapshot snapshot(NavigationTarget target) {
        return snapshot(2L, target);
    }

    private static NavigationSnapshot snapshot(long generation,
                                                 NavigationTarget target) {
        return new NavigationSnapshot(1L, generation,
                Collections.singletonList(target));
    }

    private static NavigationTarget target(WaypointSourceType sourceType,
                                           WaypointLayer layer, int radius) {
        return new NavigationTarget(new NavigationTargetKey("server", UUID.randomUUID()),
                "Target", new WaypointCoordinate(0, 0, null, layer),
                MarkerStyle.defaultColoredBeam(), sourceType, false, true, radius);
    }

    private static final class Counter implements WaypointArrivalTracker.Listener {
        private int count;
        private int exitedCount;
        private int lastDistance;

        @Override public void arrived(NavigationTarget target, int distanceMetres) {
            count++;
            lastDistance = distanceMetres;
        }

        @Override public void exited(NavigationTarget target, int distanceMetres) {
            exitedCount++;
        }
    }
}
