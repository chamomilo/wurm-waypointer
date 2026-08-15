package org.waypoints.next.render;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BeamProbeConfigurationTest {
    @Test
    public void probeIsOffByDefault() {
        BeamProbeConfiguration configuration =
                BeamProbeConfiguration.from(new Properties());
        assertFalse(configuration.isEnabled());
        assertFalse(configuration.isThroughWalls());
        assertEquals("Waypoint 1", configuration.getWaypointName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidWidthIsRejected() {
        Properties properties = new Properties();
        properties.setProperty("phase0BeamProbe", "true");
        properties.setProperty("phase0BeamWidth", "NaN");
        BeamProbeConfiguration.from(properties);
    }

    @Test
    public void explicitProbeCanBeEnabled() {
        Properties properties = new Properties();
        properties.setProperty("phase0BeamProbe", "true");
        properties.setProperty("phase0BeamThroughWalls", "true");
        properties.setProperty("phase0BeamName", "Mine entrance");
        BeamProbeConfiguration configuration = BeamProbeConfiguration.from(properties);
        assertTrue(configuration.isEnabled());
        assertTrue(configuration.isThroughWalls());
        assertEquals("Mine entrance", configuration.getWaypointName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidThroughWallWidthIsRejected() {
        Properties properties = new Properties();
        properties.setProperty("phase0BeamThroughWallWidth", "1.1");
        BeamProbeConfiguration.from(properties);
    }

    @Test
    public void waypointNameIsSingleLineAndBounded() {
        Properties properties = new Properties();
        properties.setProperty("phase0BeamName",
                "  first line\n" + repeat('x', 100) + "  ");
        String name = BeamProbeConfiguration.from(properties).getWaypointName();
        assertFalse(name.contains("\n"));
        assertEquals(80, name.length());
        assertTrue(name.endsWith("\u2026"));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
