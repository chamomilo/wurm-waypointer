package org.waypoints.next.model;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WaypointLifetimeTest {
    private final Instant now = Instant.ofEpochMilli(1_700_000_000_000L);

    @Test public void resolvesPermanentPresetAndKeepCurrent() {
        assertNull(WaypointLifetime.resolve(0, null, now, false));
        assertEquals(now.plusSeconds(15L * 60L),
                WaypointLifetime.resolve(15, null, now, false));
        Instant current = now.plusSeconds(60L);
        assertEquals(current, WaypointLifetime.resolve(-1, current, now, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedLifetime() {
        WaypointLifetime.resolve(17, null, now, false);
    }

    @Test public void expiryBoundaryIsInclusive() {
        Instant expiry = now.plusSeconds(60L);
        assertTrue(WaypointLifetime.isExpired(expiry, expiry.toEpochMilli()));
    }
}
