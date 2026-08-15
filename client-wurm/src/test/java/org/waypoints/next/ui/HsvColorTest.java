package org.waypoints.next.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HsvColorTest {
    @Test public void primaryColorsMapToExpectedHueSectors() {
        float[] hsv = new float[3];
        HsvColor.fromRgb(1.0f, 0.0f, 0.0f, hsv);
        assertEquals(0.0f, hsv[0], 0.0001f);
        HsvColor.fromRgb(0.0f, 1.0f, 0.0f, hsv);
        assertEquals(1.0f / 3.0f, hsv[0], 0.0001f);
        HsvColor.fromRgb(0.0f, 0.0f, 1.0f, hsv);
        assertEquals(2.0f / 3.0f, hsv[0], 0.0001f);
    }

    @Test public void arbitraryColorRoundTripsWithoutAllocationRequirement() {
        float[] hsv = new float[3];
        float[] rgb = new float[3];
        HsvColor.fromRgb(0.18f, 0.63f, 0.91f, hsv);
        HsvColor.toRgb(hsv[0], hsv[1], hsv[2], rgb);
        assertEquals(0.18f, rgb[0], 0.0001f);
        assertEquals(0.63f, rgb[1], 0.0001f);
        assertEquals(0.91f, rgb[2], 0.0001f);
    }

    @Test public void grayscaleHasZeroSaturationAndPreservesValue() {
        float[] hsv = new float[3];
        HsvColor.fromRgb(0.42f, 0.42f, 0.42f, hsv);
        assertEquals(0.0f, hsv[1], 0.0001f);
        assertEquals(0.42f, hsv[2], 0.0001f);
    }
}
