package org.waypoints.next.navigation;

import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.service.WaypointManager;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class NavigationLabelSelectorTest {
    @Test public void everyEnabledLabelSurvivesSelectionAndBeamToggles() {
        ServerIdentity novus = TestWaypoints.server("Novus", 3726);
        WaypointRecord first = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000001", "First",
                "Chamomilo", novus, 1, 0);
        WaypointRecord second = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000002", "Second",
                "Chamomilo", novus, 2, 0);
        MarkerStyle hiddenLabelStyle = new MarkerStyle(
                MarkerStyle.WorldStyle.COLORED_BEAM, 1.0f, 0.2f, 0.2f,
                0.85f, 9.0f, 2.0f, false, true);
        WaypointRecord withoutLabel = WaypointRecord.copyOf(TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000003", "No label",
                "Chamomilo", novus, 3, 0)).markerStyle(hiddenLabelStyle).build();

        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        manager.replaceAll(Arrays.asList(first, second, withoutLabel));
        StaticNavigationRegistry registry = new StaticNavigationRegistry();
        NavigationSnapshot snapshot = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTarget secondTarget = snapshot.find(new NavigationTargetKey(
                novus.getEndpointFingerprint(), second.getId()));
        snapshot = registry.selectAndToggleBeam(secondTarget.getKey());

        List<NavigationTarget> labels = NavigationLabelSelector.select(snapshot);
        assertEquals(2, labels.size());
        assertEquals(new HashSet<Object>(Arrays.<Object>asList(
                        first.getId(), second.getId())),
                new HashSet<Object>(Arrays.<Object>asList(
                        labels.get(0).getKey().getWaypointId(),
                        labels.get(1).getKey().getWaypointId())));
        assertFalse(snapshot.find(secondTarget.getKey()).isWorldBeamVisible());
    }

    @Test public void compassOnlyDoesNotCreateAProjectedWorldLabel() {
        ServerIdentity novus = TestWaypoints.server("Novus", 3726);
        MarkerStyle compassOnly = new MarkerStyle(
                MarkerStyle.WorldStyle.COMPASS_ONLY, 1.0f, 0.75f, 0.15f,
                0.9f, 9.0f, 2.0f, true, true);
        WaypointRecord record = WaypointRecord.copyOf(TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000009", "Compass",
                "Chamomilo", novus, 3, 0)).markerStyle(compassOnly).build();
        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        manager.add(record);
        NavigationSnapshot snapshot = new StaticNavigationRegistry().reconcile(
                manager.revisionSnapshot(), new NavigationContext(novus, "Chamomilo", 64));

        assertEquals(0, NavigationLabelSelector.select(snapshot).size());
    }

    @Test public void distanceCapAndSelectedPriorityAreDeterministic() {
        ServerIdentity novus = TestWaypoints.server("Novus", 3726);
        WaypointRecord near = TestWaypoints.staticRecord(
                "30000000-0000-0000-0000-000000000001", "Near",
                "Chamomilo", novus, 1, 0);
        WaypointRecord selected = TestWaypoints.staticRecord(
                "30000000-0000-0000-0000-000000000002", "Selected",
                "Chamomilo", novus, 2, 0);
        WaypointRecord far = TestWaypoints.staticRecord(
                "30000000-0000-0000-0000-000000000003", "Far",
                "Chamomilo", novus, 100, 0);
        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        manager.replaceAll(Arrays.asList(far, selected, near));
        StaticNavigationRegistry registry = new StaticNavigationRegistry();
        NavigationSnapshot snapshot = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        snapshot = registry.selectAndToggleBeam(new NavigationTargetKey(
                novus.getEndpointFingerprint(), selected.getId()));

        List<NavigationTarget> capped = NavigationLabelSelector.select(
                snapshot, 0, 0, 50, 1);
        assertEquals(1, capped.size());
        assertEquals(selected.getId(), capped.get(0).getKey().getWaypointId());

        NavigationSnapshot unselected = new StaticNavigationRegistry().reconcile(
                manager.revisionSnapshot(), new NavigationContext(novus,
                        "Chamomilo", 64));
        List<NavigationTarget> nearest = NavigationLabelSelector.select(
                unselected, 0, 0, 50, 2);
        assertEquals(Arrays.asList(near.getId(), selected.getId()), Arrays.asList(
                nearest.get(0).getKey().getWaypointId(),
                nearest.get(1).getKey().getWaypointId()));
    }

    @Test public void vanillaLabelsBypassUserDistanceAndLabelCaps() {
        ServerIdentity novus = TestWaypoints.server("Novus", 3726);
        WaypointRecord vanilla = WaypointRecord.copyOf(TestWaypoints.staticRecord(
                "40000000-0000-0000-0000-000000000001", "Vanilla Rift",
                "Wurm", novus, 10000, 10000))
                .sourceType(WaypointSourceType.VANILLA_SYSTEM)
                .sourceKey("RIFT").build();
        WaypointRecord ordinary = TestWaypoints.staticRecord(
                "40000000-0000-0000-0000-000000000002", "Ordinary",
                "Chamomilo", novus, 1, 0);
        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        manager.replaceAll(Arrays.asList(vanilla, ordinary));
        NavigationSnapshot snapshot = new StaticNavigationRegistry().reconcile(
                manager.revisionSnapshot(), new NavigationContext(novus,
                        "Chamomilo", 64));

        List<NavigationTarget> selected = NavigationLabelSelector.select(
                snapshot, 0, 0, 10, 1);
        assertEquals(2, selected.size());
        assertEquals(vanilla.getId(), selected.get(0).getKey().getWaypointId());
        assertEquals(ordinary.getId(), selected.get(1).getKey().getWaypointId());
    }
}
