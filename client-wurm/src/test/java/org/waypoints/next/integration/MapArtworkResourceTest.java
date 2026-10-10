package org.waypoints.next.integration;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class MapArtworkResourceTest {
    @Test
    public void mapChromeUsesOnlyTheCompactStandardKit() throws Exception {
        assertEquals(null, MapArtworkResourceTest.class.getResource("/org/waypoints/next/map/main-map-frame.png"));
        BufferedImage frame = resource("/org/chamomilo/wurm/ui/v1/frame.png");
        assertEquals(176, frame.getWidth());assertEquals(176, frame.getHeight());
    }

    @Test
    public void galleryUsesFiveSmallSquarePreviews() throws Exception {
        for (String name : new String[] {
                "liberty", "novus", "caza", "infinity-r5", "old-infinity"
        }) {
            BufferedImage image = resource(
                    "/org/waypoints/next/map/gallery/" + name + ".png");
            assertEquals(name, 256, image.getWidth());
            assertEquals(name, 256, image.getHeight());
        }
    }

    private static BufferedImage resource(String name) throws Exception {
        InputStream input = MapArtworkResourceTest.class.getResourceAsStream(name);
        assertNotNull("missing resource " + name, input);
        try {
            BufferedImage image = ImageIO.read(input);
            assertNotNull("invalid image " + name, image);
            return image;
        } finally {
            input.close();
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getColorModel().getAlpha(image.getRaster()
                .getDataElements(x, y, null));
    }
}
