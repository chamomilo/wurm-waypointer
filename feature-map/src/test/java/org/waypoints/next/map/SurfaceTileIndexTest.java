package org.waypoints.next.map;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;

public final class SurfaceTileIndexTest {
    @Test public void publishedMapColorsHaveUsefulBroadLabels() {
        assertEquals("Grass", SurfaceTileIndex.describeRgb(0x366503));
        assertEquals("Tree / bush", SurfaceTileIndex.describeRgb(0x293a02));
        assertEquals("Clay", SurfaceTileIndex.describeRgb(0x717c76));
        assertEquals("Paved road", SurfaceTileIndex.describeRgb(0x5c5349));
    }

    @Test public void depthShadedBluePixelsRemainWaterButTarDoesNot() {
        assertEquals("Water", SurfaceTileIndex.describeRgb(0x373f6f));
        assertEquals("Water", SurfaceTileIndex.describeRgb(0x334767));
        assertEquals("Tar", SurfaceTileIndex.describeRgb(0x121528));
    }

    @Test public void compactIndexUsesTileCoordinatesDirectly() {
        BufferedImage image = new BufferedImage(2, 2,
                BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x366503);
        image.setRGB(1, 0, 0x293a02);
        image.setRGB(0, 1, 0x726e6b);
        image.setRGB(1, 1, 0x717c76);
        SurfaceTileIndex index = SurfaceTileIndex.fromImage(image);
        assertEquals("Grass", index.describe(0, 0));
        assertEquals("Tree / bush", index.describe(1, 0));
        assertEquals("Rock", index.describe(0, 1));
        assertEquals("Clay", index.describe(1, 1));
        assertEquals("", index.describe(2, 1));
    }
}
