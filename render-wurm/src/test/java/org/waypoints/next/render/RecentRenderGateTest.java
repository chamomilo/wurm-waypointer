package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecentRenderGateTest {
    @Test
    public void acceptsOnlyRecentMonotonicRenderTimestamp() {
        assertTrue(RecentRenderGate.isFresh(1_500L, 1_000L, 500L));
        assertFalse(RecentRenderGate.isFresh(1_501L, 1_000L, 500L));
        assertFalse(RecentRenderGate.isFresh(999L, 1_000L, 500L));
        assertFalse(RecentRenderGate.isFresh(1_000L, 0L, 500L));
    }
}
