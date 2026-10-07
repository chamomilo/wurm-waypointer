package org.waypoints.next.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiniMapStateTest {
    @Test public void topographicIntervalSupportsButtonsAndTwoDigitInput() {
        MiniMapState state = new MiniMapState();
        assertEquals(0, state.getTopographicIntervalMetres());
        assertEquals(0, state.changeTopographicInterval(-1));
        assertEquals(1, state.changeTopographicInterval(1));
        assertTrue(state.setTopographicInput("25"));
        assertEquals(25, state.getTopographicIntervalMetres());
        assertFalse(state.setTopographicInput("-1"));
        assertFalse(state.setTopographicInput("2x"));
        assertFalse(state.setTopographicInput("100"));
        assertEquals(25, state.getTopographicIntervalMetres());
        state.selectMapView(true);
        assertEquals(25, state.getTopographicIntervalMetres());
        assertTrue(state.setTopographicInput("99"));
        assertEquals(99, state.changeTopographicInterval(1));
        assertTrue(state.setTopographicInput("0"));
        assertEquals(0, state.getTopographicIntervalMetres());
        assertTrue(state.setTopographicInput(""));
    }

    @Test public void tileBordersDisappearOnTheFirstZoomOutInBothLayers() {
        MiniMapState state = new MiniMapState();
        assertFalse(state.areTileBordersVisible());
        for (boolean cave : new boolean[] {false, true}) {
            state.selectMapView(cave);
            state.zoomIn(1000);
            assertTrue(state.areTileBordersVisible());
            state.zoomOut(1);
            assertFalse(state.areTileBordersVisible());
            state.zoomOut(1000);
            assertFalse(state.areTileBordersVisible());
            state.zoomIn(1000);
            assertTrue(state.areTileBordersVisible());
        }
    }

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

    @Test public void automaticSwitchRunsOncePerTransitionAndHonoursManualOverride() {
        MiniMapState state = new MiniMapState();
        assertTrue(state.observePlayerLayer(false));
        assertTrue(state.observePlayerLayer(true));
        assertTrue(state.isCaveView());
        assertEquals(17, state.getVisibleTiles());
        state.zoomOut(12);
        assertFalse(state.observePlayerLayer(true));
        assertEquals(29, state.getVisibleTiles());
        state.toggleMapView();
        assertFalse(state.observePlayerLayer(true));
        assertFalse(state.isCaveView());
        state.toggleMapView();
        assertTrue(state.observePlayerLayer(false));
        assertFalse(state.isCaveView());
        assertTrue(state.observePlayerLayer(true));
        assertEquals(17, state.getVisibleTiles());
    }

    @Test public void caveZoomHasIndependentBoundsAndGroundRangeIsRestored() {
        MiniMapState state = new MiniMapState(120, true);
        state.toggleMapView();
        assertEquals(17, state.zoomIn(100));
        assertEquals(48, state.zoomOut(100));
        assertEquals(256.0d / 48, state.pixelsPerTile(256), 0.000001d);
        state.toggleMapView();
        assertEquals(120, state.getVisibleTiles());
        state.zoomIn(8);
        state.toggleMapView();
        assertEquals(17, state.getVisibleTiles());
        state.toggleMapView();
        assertEquals(112, state.getVisibleTiles());
    }

    @Test public void reconnectStartsInTheActualLayerEvenAfterManualOverride() {
        MiniMapState state = new MiniMapState();
        state.observePlayerLayer(true);
        state.toggleMapView();
        state.resetPlayerLayerObservation();
        assertTrue(state.observePlayerLayer(true));
        assertTrue(state.isCaveView());
        assertEquals(17, state.getVisibleTiles());
    }
}
