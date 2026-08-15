package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NavigationRenderLayerTest {
    @Test public void effectUsesCurrentPlayerPassOnSurfaceAndInCaves() {
        assertEquals(0, NavigationRenderLayer.forPlayer(0));
        assertEquals(-1, NavigationRenderLayer.forPlayer(-1));
    }
}
