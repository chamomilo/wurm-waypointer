package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BeamMarkerScaleTest {
    @Test public void minimumRemovesDecorativeFieldButDefaultIsUnchanged() {
        assertEquals(0.0f, BeamMarkerScale.fieldScale(1.0f), 0.0001f);
        assertEquals(1.0f, BeamMarkerScale.fieldScale(9.0f), 0.0001f);
    }

    @Test public void maximumIsExactlyTwiceThePreviousBeam() {
        assertEquals(2.0f, BeamMarkerScale.fieldScale(30.0f), 0.0001f);
        assertEquals(250.0f, BeamMarkerScale.circleRadius(30.0f), 0.0001f);
        assertEquals(800.0f, BeamMarkerScale.height(400.0f, 30.0f), 0.0001f);
    }

    @Test public void heightRunsFromHorizonLineThroughOldDefaultToRenderLimit() {
        assertEquals(64.0f, BeamMarkerScale.height(400.0f, 1.0f), 0.0001f);
        assertEquals(400.0f, BeamMarkerScale.height(400.0f, 9.0f), 0.0001f);
    }

    @Test public void persistedOutOfRangeValuesAreSafelyClamped() {
        assertEquals(0.0f, BeamMarkerScale.fieldScale(0.25f), 0.0001f);
        assertEquals(2.0f, BeamMarkerScale.fieldScale(100.0f), 0.0001f);
        assertEquals(1.0f, BeamMarkerScale.fieldScale(Float.NaN), 0.0001f);
    }
}
