package org.waypoints.next.map;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MapOverlayVisibilityTest {
    @Test public void eachMapLayerTogglesIndependently() {
        MapOverlayVisibility visibility = new MapOverlayVisibility(
                true, true, true);

        visibility.toggle(MapOverlayVisibility.Layer.DEEDS);
        assertFalse(visibility.isVisible(MapOverlayVisibility.Layer.DEEDS));
        assertTrue(visibility.isVisible(MapOverlayVisibility.Layer.HIGHWAYS));
        assertTrue(visibility.isVisible(MapOverlayVisibility.Layer.WAYPOINTS));

        visibility.toggle(MapOverlayVisibility.Layer.HIGHWAYS);
        visibility.toggle(MapOverlayVisibility.Layer.WAYPOINTS);
        assertFalse(visibility.isVisible(MapOverlayVisibility.Layer.HIGHWAYS));
        assertFalse(visibility.isVisible(MapOverlayVisibility.Layer.WAYPOINTS));
    }

    @Test public void configuredDefaultsArePreserved() {
        MapOverlayVisibility visibility = new MapOverlayVisibility(
                false, true, false);
        assertFalse(visibility.isVisible(MapOverlayVisibility.Layer.DEEDS));
        assertTrue(visibility.isVisible(MapOverlayVisibility.Layer.HIGHWAYS));
        assertFalse(visibility.isVisible(MapOverlayVisibility.Layer.WAYPOINTS));
    }
}
