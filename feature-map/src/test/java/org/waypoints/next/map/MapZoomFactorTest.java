package org.waypoints.next.map;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MapZoomFactorTest {
    @Test public void cyclesSharedFactorAndScalesAllThreeOriginalWheelSteps() {
        MiniMapState state = new MiniMapState();
        for (int factor : new int[] {1, 2, 4, 1}) {
            assertEquals(factor, state.getZoomFactor());
            assertEquals(-factor, state.fullMapWheelSteps(3), 0.000001);
            assertEquals(factor, state.fullMapWheelSteps(-3), 0.000001);
            assertEquals(-4 * factor, state.fullMapWheelSteps(30), 0.000001);
            state.selectMapView(false);
            assertEquals(8 * factor, state.miniMapWheelTiles(3));
            state.selectMapView(true);
            assertEquals(4 * factor, state.miniMapWheelTiles(-3));
            state.cycleZoomFactor();
        }
    }

    @Test public void fractionalFullMapInputAndZeroInputPreserveBaseBehaviour() {
        MapZoomFactor factor = new MapZoomFactor();
        assertEquals(-1.0 / 3, factor.fullMapWheelSteps(1), 0.000001);
        factor.cycle();
        assertEquals(-2.0 / 3, factor.fullMapWheelSteps(1), 0.000001);
        assertEquals(0, factor.fullMapWheelSteps(0), 0.000001);
        assertEquals(0, factor.miniMapWheelTiles(0, false));
        assertEquals(160, factor.miniMapWheelTiles(Integer.MIN_VALUE, false));
    }
}
