package org.waypoints.next.render;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class Phase0ProbeProfileTest {
    @Test
    public void libertyGetsIndependentYellowWaypointTwo() {
        Phase0ProbeProfile profile = Phase0ProbeProfile.forServer(
                "176.9.149.249:3726|Liberty",
                BeamProbeConfiguration.from(redFallback()));
        assertEquals("Waypoint 2", profile.getName());
        assertEquals(1.0f, profile.getRed(), 0.0001f);
        assertEquals(1.0f, profile.getGreen(), 0.0001f);
        assertEquals(0.0f, profile.getBlue(), 0.0001f);
    }

    @Test
    public void otherServerKeepsConfiguredProbeStyle() {
        Phase0ProbeProfile profile = Phase0ProbeProfile.forServer(
                "176.9.149.249:3726|Novus",
                BeamProbeConfiguration.from(redFallback()));
        assertEquals("Waypoint 1", profile.getName());
        assertEquals(1.0f, profile.getRed(), 0.0001f);
        assertEquals(0.0f, profile.getGreen(), 0.0001f);
        assertEquals(0.0f, profile.getBlue(), 0.0001f);
    }

    private static Properties redFallback() {
        Properties properties = new Properties();
        properties.setProperty("phase0BeamName", "Waypoint 1");
        properties.setProperty("phase0BeamRed", "1.0");
        properties.setProperty("phase0BeamGreen", "0.0");
        properties.setProperty("phase0BeamBlue", "0.0");
        return properties;
    }
}
