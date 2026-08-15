package org.waypoints.next.navigation;

import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.model.UserMarkerStyles;
import org.waypoints.next.service.WaypointManager;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NavigationEffectSelectorTest {
    @Test public void cullingCapAndSelectedPriorityAreDeterministic() {
        ServerIdentity novus = TestWaypoints.server("Novus", 3726);
        WaypointRecord near = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000001", "Near",
                "Chamomilo", novus, 1, 0);
        WaypointRecord middle = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000002", "Middle",
                "Chamomilo", novus, 2, 0);
        WaypointRecord far = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000003", "Far",
                "Chamomilo", novus, 100, 0);
        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        manager.replaceAll(Arrays.asList(far, middle, near));
        StaticNavigationRegistry registry = new StaticNavigationRegistry();
        NavigationSnapshot snapshot = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTarget middleTarget = find(snapshot, middle);
        snapshot = registry.selectAndToggleBeam(middleTarget.getKey());
        snapshot = registry.selectAndToggleBeam(middleTarget.getKey());

        List<NavigationTarget> selected = NavigationEffectSelector.select(
                snapshot, 0, 0, 50, 1);
        assertEquals(1, selected.size());
        assertEquals(middle.getId(), selected.get(0).getKey().getWaypointId());

        NavigationSnapshot unselected = new StaticNavigationRegistry().reconcile(
                manager.revisionSnapshot(), new NavigationContext(novus, "Chamomilo", 64));
        List<NavigationTarget> nearest = NavigationEffectSelector.select(
                unselected, 0, 0, 50, 2);
        assertEquals(Arrays.asList(near.getId(), middle.getId()), Arrays.asList(
                nearest.get(0).getKey().getWaypointId(),
                nearest.get(1).getKey().getWaypointId()));
    }

    @Test public void everyWorldPresetExceptCompassOnlyCreatesAnEffect() {
        ServerIdentity novus = TestWaypoints.server("Novus", 3726);
        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        int index = 1;
        for (MarkerStyle.WorldStyle style : UserMarkerStyles.values()) {
            if (style == MarkerStyle.WorldStyle.HIDDEN) continue;
            WaypointRecord base = TestWaypoints.staticRecord(String.format(
                    "10000000-0000-0000-0000-%012d", index), style.name(),
                    "Chamomilo", novus, index++, 0);
            MarkerStyle visual = new MarkerStyle(style, 1.0f, 0.5f, 0.2f,
                    0.9f, 9.0f, 2.0f, true, true);
            manager.add(WaypointRecord.copyOf(base).markerStyle(visual).build());
        }
        NavigationSnapshot snapshot = new StaticNavigationRegistry().reconcile(
                manager.revisionSnapshot(), new NavigationContext(novus, "Chamomilo", 64));

        List<NavigationTarget> selected = NavigationEffectSelector.select(
                snapshot, 0, 0, 1000, 64);
        assertEquals(UserMarkerStyles.values().length - 2, selected.size());
        for (NavigationTarget target : selected) {
            assertEquals(false, target.getMarkerStyle().getWorldStyle()
                    == MarkerStyle.WorldStyle.COMPASS_ONLY);
        }
    }

    @Test public void vanillaLandmarksBypassUserDistanceAndEffectCaps() {
        ServerIdentity novus = TestWaypoints.server("Novus", 3726);
        WaypointRecord vanilla = WaypointRecord.copyOf(TestWaypoints.staticRecord(
                "20000000-0000-0000-0000-000000000001", "Vanilla Rift",
                "Wurm", novus, 10000, 10000))
                .sourceType(WaypointSourceType.VANILLA_SYSTEM)
                .sourceKey("RIFT").build();
        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        manager.add(vanilla);
        WaypointRecord ordinary = TestWaypoints.staticRecord(
                "20000000-0000-0000-0000-000000000002", "Ordinary",
                "Chamomilo", novus, 1, 0);
        manager.add(ordinary);
        NavigationSnapshot snapshot = new StaticNavigationRegistry().reconcile(
                manager.revisionSnapshot(), new NavigationContext(novus, "Chamomilo", 1));

        List<NavigationTarget> selected = NavigationEffectSelector.select(
                snapshot, 0, 0, 10, 1);

        assertEquals(2, selected.size());
        assertEquals(vanilla.getId(), selected.get(0).getKey().getWaypointId());
        assertEquals(ordinary.getId(), selected.get(1).getKey().getWaypointId());
    }

    private static NavigationTarget find(NavigationSnapshot snapshot,
                                         WaypointRecord record) {
        for (NavigationTarget target : snapshot.getTargets()) {
            if (record.getId().equals(target.getKey().getWaypointId())) return target;
        }
        throw new AssertionError("target not found");
    }
}
