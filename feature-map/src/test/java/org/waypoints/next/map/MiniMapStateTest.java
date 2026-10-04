package org.waypoints.next.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiniMapStateTest {
    @Test public void defaultsToEightyTilesWithSharedLayersVisible() {
        MiniMapState state = new MiniMapState();
        assertEquals(80, state.getVisibleTiles());
        assertTrue(state.areDeedsVisible());
        assertTrue(state.areRoadsVisible());
        assertTrue(state.isNavigationLineVisible());
    }

    @Test public void sizeChangesOneTileAtATimeAndClampsToBounds() {
        MiniMapState state = new MiniMapState(20, true);
        assertEquals(20, state.decreaseVisibleTiles());
        assertEquals(21, state.increaseVisibleTiles());

        state = new MiniMapState(160, true);
        assertEquals(160, state.increaseVisibleTiles());
        assertEquals(159, state.decreaseVisibleTiles());
    }

    @Test public void constructorClampsAndScaleUsesVisibleTileSpan() {
        MiniMapState small = new MiniMapState(-100, false);
        MiniMapState large = new MiniMapState(1000, true);
        assertEquals(20, small.getVisibleTiles());
        assertEquals(160, large.getVisibleTiles());
        assertEquals(15.0d, small.pixelsPerTile(300), 0.000001d);
        assertFalse(small.areDeedsVisible());
        assertTrue(small.toggleDeeds());
        assertTrue(small.areRoadsVisible());
        assertFalse(small.toggleRoads());
        assertFalse(small.toggleNavigationLine());
    }

    @Test public void wheelZoomChangesOneTilePerStepAndClamps() {
        MiniMapState state = new MiniMapState();
        assertEquals(79, state.zoomIn(1));
        assertEquals(82, state.zoomOut(3));
        assertEquals(20, state.zoomIn(1000));
        assertEquals(160, state.zoomOut(1000));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyViewport() {
        new MiniMapState().pixelsPerTile(0);
    }
}
