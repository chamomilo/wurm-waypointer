package org.waypoints.next.navigation;

import org.junit.Before;
import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.service.WaypointManager;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StaticNavigationRegistryTest {
    private ServerIdentity novus;
    private ServerIdentity liberty;
    private WaypointManager manager;
    private StaticNavigationRegistry registry;

    @Before public void setUp() {
        novus = TestWaypoints.server("Novus", 3726);
        liberty = TestWaypoints.server("Liberty", 3725);
        manager = new WaypointManager(new WaypointRecordValidator());
        registry = new StaticNavigationRegistry();
    }

    @Test public void compassOnlySelectionDoesNotToggleMeaninglessWorldState() {
        WaypointRecord base = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000090", "Compass",
                "Chamomilo", novus, 10, 20);
        MarkerStyle compassOnly = new MarkerStyle(
                MarkerStyle.WorldStyle.COMPASS_ONLY, 1.0f, 0.75f, 0.15f,
                0.9f, 9.0f, 2.0f, true, true);
        manager.add(WaypointRecord.copyOf(base).markerStyle(compassOnly).build());
        NavigationSnapshot snapshot = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));

        NavigationTargetKey key = snapshot.getTargets().get(0).getKey();
        NavigationTarget selected = registry.selectAndToggleBeam(key).find(key);
        assertTrue(selected.isSelected());
        assertTrue(selected.isWorldBeamVisible());
    }

    @Test public void eligibilityIsStrictlyEndpointUserEnabledAndExact() {
        WaypointRecord accepted = record("00000000-0000-0000-0000-000000000001",
                "Accepted", "Chamomilo", novus, 10, 20);
        WaypointRecord otherServer = record("00000000-0000-0000-0000-000000000002",
                "Other server", "Chamomilo", liberty, 11, 20);
        WaypointRecord otherUser = record("00000000-0000-0000-0000-000000000003",
                "Other user", "Alt", novus, 12, 20);
        WaypointRecord disabled = WaypointRecord.copyOf(record(
                "00000000-0000-0000-0000-000000000004", "Disabled",
                "Chamomilo", novus, 13, 20)).enabled(false).build();
        WaypointRecord pending = WaypointRecord.copyOf(record(
                "00000000-0000-0000-0000-000000000005", "Pending",
                "Chamomilo", novus, 14, 20))
                .sourceType(WaypointSourceType.PLAYER).sourceKey("pending-player")
                .coordinate(null).resolution(WaypointResolution.PENDING).build();
        ServerIdentity unresolved = ServerIdentity.of(novus.getEndpoint(),
                "Sklotopolis - Novus", "Novus",
                ServerIdentity.Resolution.UNRESOLVED_NAME_MISMATCH);
        WaypointRecord unsafe = WaypointRecord.copyOf(record(
                "00000000-0000-0000-0000-000000000006", "Unsafe",
                "Chamomilo", novus, 15, 20)).serverIdentity(unresolved).build();
        manager.replaceAll(Arrays.asList(accepted, otherServer, otherUser,
                disabled, pending, unsafe));

        NavigationSnapshot snapshot = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "chamomilo", 64));
        assertEquals(1, snapshot.getTargets().size());
        assertEquals(accepted.getId(), snapshot.getTargets().get(0).getKey().getWaypointId());
        assertTrue(snapshot.getTargets().get(0).isSelected());
    }

    @Test public void surroundingsMarksReachThePersistentRenderSnapshot() {
        WaypointRecord animal = WaypointRecord.copyOf(record(
                "00000000-0000-0000-0000-000000000071", "Marked wolf",
                "Chamomilo", novus, 10, 20))
                .sourceType(WaypointSourceType.MANAGED_ANIMAL)
                .sourceKey("ANIMAL:42")
                .markerStyle(new MarkerStyle(
                        MarkerStyle.WorldStyle.TARGET_CROSSHAIR,
                        1.0f, 0.28f, 0.16f, 0.92f,
                        13.0f, 2.4f, true, true))
                .build();
        WaypointRecord item = WaypointRecord.copyOf(record(
                "00000000-0000-0000-0000-000000000072", "Marked mushroom",
                "Chamomilo", novus, 11, 20))
                .sourceType(WaypointSourceType.MANAGED_ITEM)
                .sourceKey("ITEM:43")
                .markerStyle(new MarkerStyle(MarkerStyle.WorldStyle.DIAMOND,
                        0.98f, 0.84f, 0.20f, 0.90f,
                        10.0f, 2.0f, true, true))
                .build();
        manager.replaceAll(Arrays.asList(animal, item));

        NavigationSnapshot snapshot = registry.reconcile(
                manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));

        assertEquals(2, snapshot.getTargets().size());
        assertEquals(MarkerStyle.WorldStyle.EXCLAMATION,
                snapshot.find(new NavigationTargetKey(
                        novus.getEndpointFingerprint(), animal.getId()))
                        .getMarkerStyle().getWorldStyle());
        assertEquals(MarkerStyle.WorldStyle.EXCLAMATION,
                snapshot.find(new NavigationTargetKey(
                        novus.getEndpointFingerprint(), item.getId()))
                        .getMarkerStyle().getWorldStyle());
    }

    @Test public void deterministicCapPromotesSelection() {
        WaypointRecord first = record("00000000-0000-0000-0000-000000000001",
                "First", "Chamomilo", novus, 10, 20);
        WaypointRecord second = record("00000000-0000-0000-0000-000000000002",
                "Second", "Chamomilo", novus, 11, 20);
        WaypointRecord third = record("00000000-0000-0000-0000-000000000003",
                "Third", "Chamomilo", novus, 12, 20);
        manager.replaceAll(Arrays.asList(third, second, first));
        NavigationSnapshot all = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 3));
        assertEquals(first.getId(), all.getTargets().get(0).getKey().getWaypointId());
        NavigationTargetKey selected = all.getTargets().get(2).getKey();
        registry.selectAndToggleBeam(selected);

        NavigationSnapshot capped = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 1));
        assertEquals(1, capped.getTargets().size());
        assertEquals(selected, capped.getTargets().get(0).getKey());
        assertTrue(capped.getTargets().get(0).isSelected());
        assertFalse(capped.getTargets().get(0).isWorldBeamVisible());
    }

    @Test public void beamStateIsSeparateForEveryUuidAndEndpoint() {
        WaypointRecord novusTarget = record("00000000-0000-0000-0000-000000000001",
                "Novus", "Chamomilo", novus, 10, 20);
        WaypointRecord libertyTarget = record("00000000-0000-0000-0000-000000000002",
                "Liberty", "Chamomilo", liberty, 10, 20);
        manager.replaceAll(Arrays.asList(novusTarget, libertyTarget));
        NavigationSnapshot novusView = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        registry.selectAndToggleBeam(novusView.getTargets().get(0).getKey());

        NavigationSnapshot libertyView = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(liberty, "Chamomilo", 64));
        assertTrue(libertyView.getTargets().get(0).isWorldBeamVisible());
        assertEquals(libertyView.getTargets().get(0).getKey(), registry.selectedKey());

        NavigationSnapshot returned = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        assertFalse(returned.getTargets().get(0).isWorldBeamVisible());

        manager.setEnabled(novusTarget.getId(), false, Instant.now());
        registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        manager.setEnabled(novusTarget.getId(), true, Instant.now());
        NavigationSnapshot reenabled = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        assertTrue(reenabled.getTargets().get(0).isWorldBeamVisible());
    }

    @Test public void managerEnableRestoresCompassHiddenWorldEffect() {
        WaypointRecord target = record("00000000-0000-0000-0000-000000000001",
                "Target", "Chamomilo", novus, 10, 20);
        manager.add(target);
        NavigationSnapshot initial = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTargetKey key = initial.getTargets().get(0).getKey();
        assertFalse(registry.selectAndToggleBeam(key).find(key)
                .isWorldBeamVisible());

        assertTrue(registry.setWorldEffectVisible(target.getId(), true).find(key)
                .isWorldBeamVisible());
    }

    @Test public void navigatorIsExclusiveAndInheritsTheActiveWaypointStyle() {
        MarkerStyle firstStyle = new MarkerStyle(
                MarkerStyle.WorldStyle.COLORED_BEAM, 0.15f, 0.35f, 0.75f,
                0.55f, 12.0f, 3.0f, true, true);
        MarkerStyle secondStyle = new MarkerStyle(
                MarkerStyle.WorldStyle.PLUS, 0.8f, 0.25f, 0.1f,
                0.7f, 9.0f, 2.0f, true, true);
        WaypointRecord first = WaypointRecord.copyOf(record(
                "00000000-0000-0000-0000-000000000011", "First",
                "Chamomilo", novus, 10, 20)).markerStyle(firstStyle).build();
        WaypointRecord second = WaypointRecord.copyOf(record(
                "00000000-0000-0000-0000-000000000012", "Second",
                "Chamomilo", novus, 30, 40)).markerStyle(secondStyle).build();
        manager.replaceAll(Arrays.asList(first, second));
        NavigationSnapshot initial = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTargetKey firstKey = new NavigationTargetKey(
                novus.getEndpointFingerprint(), first.getId());
        NavigationTargetKey secondKey = new NavigationTargetKey(
                novus.getEndpointFingerprint(), second.getId());

        NavigationSnapshot firstActive = registry.toggleNavigator(firstKey);
        assertEquals(firstKey, firstActive.getActiveNavigator().getKey());
        assertEquals(firstStyle,
                firstActive.getActiveNavigator().getMarkerStyle());

        NavigationSnapshot secondActive = registry.toggleNavigator(secondKey);
        assertFalse(secondActive.find(firstKey).isNavigatorActive());
        assertTrue(secondActive.find(secondKey).isNavigatorActive());
        assertEquals(secondKey, registry.navigatorKey());

        assertNull(registry.toggleNavigator(secondKey).getActiveNavigator());
        assertNull(registry.navigatorKey());
        assertEquals(2, initial.getTargets().size());
    }

    @Test public void automaticNavigatorStartIsIdempotentAndExactStopKeepsOtherOwners() {
        WaypointRecord first = record(
                "00000000-0000-0000-0000-000000000013", "First",
                "Chamomilo", novus, 10, 20);
        WaypointRecord second = record(
                "00000000-0000-0000-0000-000000000014", "Second",
                "Chamomilo", novus, 30, 40);
        manager.replaceAll(Arrays.asList(first, second));
        registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTargetKey firstKey = new NavigationTargetKey(
                novus.getEndpointFingerprint(), first.getId());
        NavigationTargetKey secondKey = new NavigationTargetKey(
                novus.getEndpointFingerprint(), second.getId());

        assertEquals(firstKey, registry.activateNavigator(firstKey)
                .getActiveNavigator().getKey());
        assertEquals(firstKey, registry.activateNavigator(firstKey)
                .getActiveNavigator().getKey());
        assertEquals(secondKey, registry.activateNavigator(secondKey)
                .getActiveNavigator().getKey());

        registry.deactivateNavigator(firstKey);
        assertEquals(secondKey, registry.navigatorKey());
        assertNull(registry.deactivateNavigator(secondKey).getActiveNavigator());
    }

    @Test public void navigatorPromotesAnyEligibleWaypointThroughTheMarkerCap() {
        WaypointRecord first = record(
                "00000000-0000-0000-0000-000000000021", "First",
                "Chamomilo", novus, 10, 20);
        WaypointRecord second = record(
                "00000000-0000-0000-0000-000000000022", "Second",
                "Chamomilo", novus, 30, 40);
        manager.replaceAll(Arrays.asList(first, second));
        registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 1));
        NavigationTargetKey hiddenByCap = new NavigationTargetKey(
                novus.getEndpointFingerprint(), second.getId());

        NavigationSnapshot activated = registry.toggleNavigator(hiddenByCap);

        assertEquals(1, activated.getTargets().size());
        assertEquals(hiddenByCap, activated.getActiveNavigator().getKey());
    }

    @Test public void activeNavigatorSurvivesSelectionAndLaterCapChanges() {
        WaypointRecord first = record(
                "00000000-0000-0000-0000-000000000025", "First",
                "Chamomilo", novus, 10, 20);
        WaypointRecord second = record(
                "00000000-0000-0000-0000-000000000026", "Second",
                "Chamomilo", novus, 30, 40);
        manager.replaceAll(Arrays.asList(first, second));
        NavigationSnapshot initial = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 2));
        NavigationTargetKey firstKey = new NavigationTargetKey(
                novus.getEndpointFingerprint(), first.getId());
        NavigationTargetKey secondKey = new NavigationTargetKey(
                novus.getEndpointFingerprint(), second.getId());
        registry.toggleNavigator(secondKey);
        registry.selectAndToggleBeam(firstKey);

        NavigationSnapshot capped = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 1));

        assertEquals(1, capped.getTargets().size());
        assertEquals(secondKey, capped.getActiveNavigator().getKey());
        assertEquals(2, initial.getTargets().size());
    }

    @Test public void disablingOrRemovingTheTargetClearsItsNavigator() {
        WaypointRecord target = record(
                "00000000-0000-0000-0000-000000000031", "Target",
                "Chamomilo", novus, 10, 20);
        manager.add(target);
        NavigationSnapshot initial = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        registry.toggleNavigator(initial.getTargets().get(0).getKey());

        manager.setEnabled(target.getId(), false, Instant.now());
        NavigationSnapshot disabled = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));

        assertNull(disabled.getActiveNavigator());
        assertNull(registry.navigatorKey());

        manager.setEnabled(target.getId(), true, Instant.now());
        NavigationSnapshot reenabled = registry.reconcile(
                manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        registry.activateNavigator(reenabled.getTargets().get(0).getKey());
        manager.delete(target.getId());
        NavigationSnapshot removed = registry.reconcile(
                manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));

        assertNull(removed.getActiveNavigator());
        assertNull(registry.navigatorKey());
    }

    @Test public void arrivalCanHideOnlyTheWorldEffect() {
        WaypointRecord target = record("00000000-0000-0000-0000-000000000001",
                "Target", "Chamomilo", novus, 10, 20);
        manager.add(target);
        NavigationSnapshot initial = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTargetKey key = initial.getTargets().get(0).getKey();

        NavigationTarget hidden = registry.setWorldEffectVisible(
                target.getId(), false).find(key);
        assertFalse(hidden.isWorldBeamVisible());
        assertTrue(hidden.isCompassVisible());
        assertTrue(hidden.getMarkerStyle().isShowLabel());
        assertTrue(hidden.getMarkerStyle().isShowDistance());
    }

    @Test public void freshViewForgetsSessionOnlyCompassHides() {
        WaypointRecord target = record("00000000-0000-0000-0000-000000000001",
                "Target", "Chamomilo", novus, 10, 20);
        manager.add(target);
        NavigationSnapshot initial = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTargetKey key = initial.getTargets().get(0).getKey();
        assertFalse(registry.selectAndToggleBeam(key).find(key)
                .isWorldBeamVisible());

        registry.clearView();
        NavigationSnapshot restored = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        assertTrue(restored.find(key).isWorldBeamVisible());
    }

    @Test public void selectedLabelOwnerIsReplacedWhenActiveRecordDisappears() {
        WaypointRecord first = record("00000000-0000-0000-0000-000000000001",
                "First", "Chamomilo", novus, 10, 20);
        WaypointRecord second = record("00000000-0000-0000-0000-000000000002",
                "Second", "Chamomilo", novus, 11, 20);
        manager.replaceAll(Arrays.asList(first, second));
        NavigationSnapshot initial = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));
        NavigationTargetKey selected = registry.selectedKey();
        assertTrue(initial.find(selected).isSelected());

        manager.setEnabled(selected.getWaypointId(), false, Instant.now());
        NavigationSnapshot replacement = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64));

        assertEquals(1, replacement.getTargets().size());
        assertTrue(replacement.getTargets().get(0).isSelected());
        assertFalse(selected.equals(replacement.getTargets().get(0).getKey()));
    }

    @Test public void revisionSnapshotReconcilesCrudWithoutStoreReads() {
        manager.replaceAll(Collections.<WaypointRecord>emptyList());
        long emptyRevision = manager.revisionSnapshot().getRevision();
        WaypointRecord added = record("00000000-0000-0000-0000-000000000001",
                "Added", "Chamomilo", novus, 10, 20);
        manager.add(added);
        assertTrue(manager.revision() > emptyRevision);
        assertEquals(1, registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64)).getTargets().size());
        manager.delete(added.getId());
        assertEquals(0, registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64)).getTargets().size());
    }

    @Test public void vanillaLandmarksBypassUserOwnershipAndMarkerCap() {
        WaypointRecord white = systemRecord(
                "30000000-0000-0000-0000-000000000001", "Vanilla White Light",
                "WHITE_LIGHT", 10);
        WaypointRecord black = systemRecord(
                "30000000-0000-0000-0000-000000000002", "Vanilla Black Light",
                "BLACK_LIGHT", 20);
        WaypointRecord rift = systemRecord(
                "30000000-0000-0000-0000-000000000003", "Vanilla Rift",
                "RIFT", 30);
        WaypointRecord ordinary = record(
                "30000000-0000-0000-0000-000000000004", "Ordinary",
                "Not Wurm", novus, 40, 20);
        manager.replaceAll(Arrays.asList(white, black, rift, ordinary));

        NavigationSnapshot snapshot = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Not Wurm", 1));

        assertEquals(4, snapshot.getTargets().size());
        assertEquals("White Light", snapshot.getTargets().get(0).getName());
        assertEquals("Black Light", snapshot.getTargets().get(1).getName());
        assertEquals("Rift", snapshot.getTargets().get(2).getName());
        for (int i = 0; i < 3; i++) {
            NavigationTarget target = snapshot.getTargets().get(i);
            assertFalse(target.isCompassVisible());
            assertFalse(target.isSelected());
        }
        assertTrue(snapshot.getTargets().get(3).isCompassVisible());
        assertTrue(snapshot.getTargets().get(3).isSelected());
        NavigationTarget first = snapshot.getTargets().get(0);
        NavigationTargetKey ordinaryKey = snapshot.getTargets().get(3).getKey();
        registry.selectAndToggleBeam(first.getKey());
        assertTrue(registry.snapshot().find(first.getKey()).isWorldBeamVisible());
        assertEquals(ordinaryKey, registry.selectedKey());
    }

    @Test public void legacyUserVanillaStyleRendersAsColoredBeam() {
        WaypointRecord base = record(
                "30000000-0000-0000-0000-000000000004", "Old Rift",
                "Chamomilo", novus, 10, 20);
        MarkerStyle oldRift = new MarkerStyle(MarkerStyle.WorldStyle.RIFT,
                0.7f, 0.1f, 0.2f, 0.8f, 9.0f, 2.0f, true, true);
        manager.add(WaypointRecord.copyOf(base).markerStyle(oldRift).build());

        NavigationTarget target = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64))
                .getTargets().get(0);

        assertEquals(MarkerStyle.WorldStyle.COLORED_BEAM,
                target.getMarkerStyle().getWorldStyle());
        assertEquals(0.7f, target.getMarkerStyle().getRed(), 0.0f);
        assertTrue(target.isCompassVisible());
    }

    @Test public void arrivalRadiusReachesTheRenderSafeProjection() {
        Instant expiry = Instant.ofEpochMilli(1_700_003_600_000L);
        WaypointRecord record = WaypointRecord.copyOf(record(
                "50000000-0000-0000-0000-000000000001", "Arrival",
                "Chamomilo", novus, 10, 20)).arrivalRadiusMetres(36)
                .expiresAt(expiry).build();
        manager.add(record);

        NavigationTarget target = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64))
                .getTargets().get(0);

        assertEquals(36, target.getArrivalRadiusMetres());
        assertEquals(expiry.toEpochMilli(), target.getExpiresAtEpochMillis());
    }

    @Test public void recordExtensionsReachTheImmutableRenderProjection() {
        WaypointRecord record = WaypointRecord.copyOf(record(
                "50000000-0000-0000-0000-000000000002", "Loot metadata",
                "Chamomilo", novus, 10, 20)).extensions(
                Collections.singletonMap("lootmap.readingCount",
                        Collections.singletonList("3"))).build();
        manager.add(record);

        NavigationTarget target = registry.reconcile(manager.revisionSnapshot(),
                new NavigationContext(novus, "Chamomilo", 64))
                .getTargets().get(0);

        assertEquals("3", target.getExtension("lootmap.readingCount"));
        assertEquals(Collections.singletonList("3"), target.getExtensions()
                .get("lootmap.readingCount"));
        try {
            target.getExtensions().put("changed", Collections.singletonList("1"));
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("render projection extensions must be immutable");
    }

    private static WaypointRecord record(String id, String name, String user,
                                         ServerIdentity server, double x, double y) {
        return TestWaypoints.staticRecord(id, name, user, server, x, y);
    }

    private WaypointRecord systemRecord(String id, String name, String key,
                                        double x) {
        MarkerStyle.WorldStyle worldStyle = "WHITE_LIGHT".equals(key)
                ? MarkerStyle.WorldStyle.WHITE_LIGHT
                : "BLACK_LIGHT".equals(key)
                ? MarkerStyle.WorldStyle.BLACK_LIGHT
                : MarkerStyle.WorldStyle.RIFT;
        MarkerStyle style = new MarkerStyle(worldStyle, 1.0f, 1.0f, 1.0f,
                1.0f, 9.0f, 2.0f, true, true);
        return WaypointRecord.copyOf(record(id, name, "Wurm", novus, x, 20))
                .sourceType(WaypointSourceType.VANILLA_SYSTEM)
                .sourceKey(key).markerStyle(style).build();
    }
}
