package com.wurmonline.client.resources.textures;

/** Applies map-friendly filtering before a prepared texture reaches OpenGL. */
public final class WaypointerTextureFilters {
    private WaypointerTextureFilters() { }

    public static ResourceTexture useCrispMagnification(
            ResourceTexture texture) {
        if (texture != null && !texture.isValid()) {
            texture.filter = TextureLoader.Filter.MIPMAPNEAREST;
        }
        return texture;
    }
}
