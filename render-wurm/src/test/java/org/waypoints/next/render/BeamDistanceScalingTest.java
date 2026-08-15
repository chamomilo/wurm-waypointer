package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BeamDistanceScalingTest {
    @Test
    public void nearMarkerKeepsConfiguredDimensions() {
        assertEquals(0.15f,
                BeamDistanceScaling.throughWallWidth(0.15f, 10.0f, 1920, 75.0f),
                0.0001f);
        assertEquals(400.0f, BeamDistanceScaling.halfHeight(400.0f, 10.0f), 0.0001f);
        assertEquals(10.0f, BeamDistanceScaling.geometryDistance(10.0f), 0.0001f);
    }

    @Test
    public void distantMarkerMaintainsPixelsAndFullVerticalSpan() {
        float width = BeamDistanceScaling.throughWallWidth(
                0.15f, 2000.0f, 1920, 75.0f);
        assertTrue(width > 6.0f);
        double projectedPixels = width * 1920.0d
                / (2.0d * 2000.0d * Math.tan(Math.toRadians(75.0d / 2.0d)));
        assertEquals(4.0d, projectedPixels, 0.0001d);
        assertEquals(4000.0f,
                BeamDistanceScaling.halfHeight(400.0f, 2000.0f), 0.0001f);
    }

    @Test
    public void reportedCaveDistancesKeepNearWidthAndMakeFarBeamRasterizable() {
        float nearWidth = BeamDistanceScaling.throughWallWidth(
                0.15f, 40.0f, 1920, 80.0f);
        float farWidth = BeamDistanceScaling.throughWallWidth(
                0.15f, 195.0f, 1920, 80.0f);

        assertEquals(0.15f, nearWidth, 0.0001f);
        assertTrue(farWidth > 0.68f);
        double farProjectedPixels = farWidth * 1920.0d
                / (2.0d * 195.0d * Math.tan(Math.toRadians(80.0d / 2.0d)));
        assertEquals(4.0d, farProjectedPixels, 0.0001d);
    }

    @Test
    public void kilometreRangeMarkerUsesSameRayInsideCameraFarPlane() {
        assertEquals(256.0f, BeamDistanceScaling.geometryDistance(10111.0f), 0.0001f);
        float projection = BeamDistanceScaling.geometryDistance(10111.0f) / 10111.0f;
        assertTrue(projection > 0.0f);
        assertTrue(projection < 0.026f);
    }

    @Test
    public void caveTargetSymbolsStayInsideTheShortProjectionOnEitherPlayerLayer() {
        assertEquals(20.0f,
                BeamDistanceScaling.symbolGeometryDistance(20.0f, -1), 0.0001f);
        assertEquals(24.0f,
                BeamDistanceScaling.symbolGeometryDistance(40.0f, -1), 0.0001f);
        assertEquals(40.0f,
                BeamDistanceScaling.symbolGeometryDistance(40.0f, 0), 0.0001f);
        assertEquals(256.0f,
                BeamDistanceScaling.symbolGeometryDistance(10000.0f, 0), 0.0001f);
    }

    @Test
    public void geometryBeyondOrdinaryRenderSpaceUsesOnlyNavigationLine() {
        assertTrue(!BeamDistanceScaling.beamRequiresLineOnlyFallback(256.0f));
        assertTrue(BeamDistanceScaling.beamRequiresLineOnlyFallback(256.01f));
        assertTrue(BeamDistanceScaling.beamRequiresLineOnlyFallback(10111.0f));
    }

    @Test
    public void distantLineSpendsItsHeightAboveTheHorizon() {
        assertEquals(128.0f, BeamDistanceScaling.farLineAboveTarget(64.0f),
                0.0001f);
        assertEquals(512.0f, BeamDistanceScaling.farLineAboveTarget(512.0f),
                0.0001f);
        assertEquals(0.0f, BeamDistanceScaling.farLineBelowTarget(512.0f),
                0.0001f);
    }
}
