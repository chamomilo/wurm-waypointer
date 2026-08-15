package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationViewReconcileStateTest {
    @Test public void unchangedFramesDoNotRequireCollectionReconciliation() {
        NavigationViewReconcileState state = new NavigationViewReconcileState();
        assertTrue(state.requires(7L, 10, 20, 1920));
        state.applied(7L, 10, 20, 1920);
        assertFalse(state.requires(7L, 10, 20, 1920));
    }

    @Test public void everySelectionInputAndResetInvalidatesTheGate() {
        NavigationViewReconcileState state = new NavigationViewReconcileState();
        state.applied(7L, 10, 20, 1920);
        assertTrue(state.requires(8L, 10, 20, 1920));
        assertTrue(state.requires(7L, 11, 20, 1920));
        assertTrue(state.requires(7L, 10, 21, 1920));
        assertTrue(state.requires(7L, 10, 20, 1280));
        state.reset();
        assertTrue(state.requires(7L, 10, 20, 1920));
    }
}
