package org.waypoints.next.render;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CompassArtworkResourceTest {
    private static final String BASE =
            "/org/waypoints/next/compass/compass-hud-base.png";
    private static final String NEEDLE =
            "/org/waypoints/next/compass/compass-hud-needle.png";

    @Test
    public void layersAreSquareTransparentAndCentered() throws IOException {
        BufferedImage base = read(BASE);
        BufferedImage needle = read(NEEDLE);
        assertLayer(base);
        assertLayer(needle);
        assertTrue("base must cover the dial center",
                alpha(base.getRGB(512, 512)) > 200);
        assertTrue("needle pivot must sit on the exact rotation center",
                alpha(needle.getRGB(512, 512)) > 200);
    }

    @Test
    public void northHalfOfNeedleContainsReadableRedTip() throws IOException {
        BufferedImage needle = read(NEEDLE);
        int redPixels = 0;
        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < needle.getWidth(); x++) {
                int argb = needle.getRGB(x, y);
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;
                if (alpha(argb) > 96 && red > 110
                        && red > green * 3 / 2 && red > blue * 3 / 2) {
                    redPixels++;
                }
            }
        }
        assertTrue("north tip must contain a substantial red enamel area",
                redPixels > 5_000);
    }

    private static void assertLayer(BufferedImage image) {
        assertNotNull(image);
        assertEquals(1024, image.getWidth());
        assertEquals(1024, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(0, alpha(image.getRGB(0, 0)));
        assertEquals(0, alpha(image.getRGB(1023, 1023)));
    }

    private static BufferedImage read(String path) throws IOException {
        InputStream stream = CompassArtworkResourceTest.class
                .getResourceAsStream(path);
        assertNotNull("missing test resource " + path, stream);
        try {
            return ImageIO.read(stream);
        } finally {
            stream.close();
        }
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xff;
    }
}
