package org.waypoints.next.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SurroundingsScrollStateTest {
    @Test public void movingAwayFromTopKeepsLiveRefreshDeferred() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);
        state.synchronize(0);

        assertTrue(state.observe(75, 1000L));
        assertFalse(state.permitsAutoRefresh(2199L));
        assertFalse(state.permitsAutoRefresh(Long.MAX_VALUE));
    }

    @Test public void continuedMovementKeepsTheUserPositionProtected() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);
        state.synchronize(0);

        state.observe(25, 1000L);
        state.observe(50, 1800L);
        assertFalse(state.permitsAutoRefresh(2999L));
        assertFalse(state.permitsAutoRefresh(Long.MAX_VALUE));
    }

    @Test public void programmaticRestoreKeepsUserPositionProtected() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);
        state.synchronize(0);
        state.observe(125, 500L);
        state.synchronize(250);

        assertFalse(state.permitsAutoRefresh(Long.MAX_VALUE));
        assertTrue(state.isUserPositionHeld());
        assertFalse(state.observe(250, 500L));
    }

    @Test public void restoringSavedTabOffsetProtectsItImmediately() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);

        state.synchronize(125);

        assertTrue(state.isUserPositionHeld());
        assertFalse(state.permitsAutoRefresh(Long.MAX_VALUE));
    }

    @Test public void liveRefreshResumesOnlyAfterReturningToTop() {
        SurroundingsScrollState state = new SurroundingsScrollState(1200L);
        state.synchronize(0);

        state.observe(75, 1000L);
        assertFalse(state.permitsAutoRefresh(Long.MAX_VALUE));
        state.observe(0, 2000L);

        assertFalse(state.isUserPositionHeld());
        assertFalse(state.permitsAutoRefresh(3199L));
        assertTrue(state.permitsAutoRefresh(3200L));
    }
}
