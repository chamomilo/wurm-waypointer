package org.waypoints.next.integration;

import org.junit.Test;
import org.waypoints.api.WaypointerApi;
import org.waypoints.api.WaypointerCapability;
import org.waypoints.api.ObjectMarkerType;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.render.BeamProbeConfiguration;
import org.waypoints.next.surroundings.SurroundingKind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WaypointerPublicApiContractTest {
    @Test public void runtimeConfigurationPublishesDiscoverableVersionedApi() {
        WurmWaypointerRuntime.configure(BeamProbeConfiguration.disabled());

        assertTrue(WaypointerApi.isInstalled());
        assertEquals(1, WaypointerApi.apiVersion());
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
}
