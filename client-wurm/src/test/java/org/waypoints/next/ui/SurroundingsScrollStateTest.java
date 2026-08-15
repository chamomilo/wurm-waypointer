package org.waypoints.next.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SurroundingsScrollStateTest {
    @Test public void movingScrollDefersLiveRefreshUntilItSettles() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);
        state.synchronize(0);

        assertTrue(state.observe(75, 1000L));
        assertFalse(state.permitsAutoRefresh(2199L));
        assertTrue(state.permitsAutoRefresh(2200L));
    }

    @Test public void continuedMovementRestartsQuietPeriod() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);
        state.synchronize(0);

        state.observe(25, 1000L);
        state.observe(50, 1800L);
        assertFalse(state.permitsAutoRefresh(2999L));
        assertTrue(state.permitsAutoRefresh(3000L));
    }

    @Test public void programmaticRestoreDoesNotDelayRefresh() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);
        state.synchronize(0);
        state.observe(125, 500L);
        state.synchronize(250);

        assertTrue(state.permitsAutoRefresh(0L));
        assertFalse(state.observe(250, 500L));
    }
}
