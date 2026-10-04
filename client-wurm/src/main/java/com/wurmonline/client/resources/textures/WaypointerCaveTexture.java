package com.wurmonline.client.resources.textures;

import java.awt.image.BufferedImage;

/** Updates one nearest-filtered texture, without allocating a GL texture per tick. */
public final class WaypointerCaveTexture {
    private ImageTexture texture;

    public ImageTexture update(BufferedImage image) {
        if (texture == null) texture = ImageTextureLoader.loadNowrapNearestTexture(image, false);
        else texture.deferInit(TextureLoader.preprocessImage(image, false),
                TextureLoader.Filter.NEAREST, false, false, false);
        return texture;
    }

    public ImageTexture get() { return texture; }

    public void dispose() {
        try {
            if (texture != null) ImageTextureLoader.deleteTexture(texture);
        } finally {
            texture = null;
        }
    }
}
