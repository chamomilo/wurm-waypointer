package org.waypoints.next.integration;

import org.junit.Test;
import org.waypoints.api.WaypointerApi;
import org.waypoints.api.WaypointerCapability;
import org.waypoints.api.ObjectMarkerType;
import org.waypoints.api.WurmObjectKind;
import org.waypoints.api.WurmObjectRef;
import org.waypoints.api.WurmObjectSnapshot;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.render.BeamProbeConfiguration;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WaypointerPublicApiContractTest {
    @Test public void runtimeConfigurationPublishesDiscoverableVersionedApi() {
        WurmWaypointerRuntime.configure(BeamProbeConfiguration.disabled());

        assertTrue(WaypointerApi.isInstalled());
        assertEquals(2, WaypointerApi.apiVersion());
        assertTrue(WaypointerApi.capabilities().contains(
                WaypointerCapability.OBJECT_MARKS));
        assertTrue(WaypointerApi.capabilities().contains(
                WaypointerCapability.SUBJECT_LIFECYCLE));
        assertTrue(WaypointerApi.capabilities().contains(
                WaypointerCapability.NAVIGATION));
    }

    @Test public void alertMarkerIsHighContrastInsteadOfContainerCyan() {
        MarkerStyle style = WurmWaypointerRuntime.externalMarkerStyle(
                ObjectMarkerType.ALERT, SurroundingKind.CONTAINER);

        assertEquals(MarkerStyle.WorldStyle.EXCLAMATION,
                style.getWorldStyle());
        assertEquals(1.0f, style.getRed(), 0.0001f);
        assertEquals(0.12f, style.getGreen(), 0.0001f);
        assertEquals(0.055f, style.getBlue(), 0.0001f);
        assertEquals(1.0f, style.getAlpha(), 0.0001f);
        assertTrue(style.getMarkerSize() >= 15.0f);
    }

    @Test public void callerSnapshotProjectsUncataloguedHedgeAsStaticItem() {
        SurroundingEntry entry = WurmWaypointerRuntime.externalSnapshotEntry(
                new WurmObjectRef(WurmObjectKind.AUTO, 77L),
                new WurmObjectSnapshot("Oleander hedge",
                        120.0d, 240.0d, 3.5d, 0),
                java.time.Instant.parse("2026-08-30T00:00:00Z"));

        assertEquals(SurroundingKind.ITEM, entry.getKind());
        assertEquals(77L, entry.getWurmId());
        assertEquals("Oleander hedge", entry.getName());
        assertEquals(120.0d, entry.getWorldX(), 0.0d);
        assertEquals(240.0d, entry.getWorldY(), 0.0d);
        assertEquals(3.5d, entry.getHeight(), 0.0d);
    }
}
