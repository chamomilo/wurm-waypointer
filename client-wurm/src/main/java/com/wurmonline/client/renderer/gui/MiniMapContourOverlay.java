package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.resources.textures.Texture;
import org.waypoints.next.map.MapPoint;
import org.waypoints.next.map.MapViewport;

/** Cached pixels translate with the viewport without another raster or GL upload. */
final class MiniMapContourOverlay {
    final Texture texture;
    final float x,y;
    final int size;
    MiniMapContourOverlay(Texture texture, MiniMapContourCache.Result image, MapViewport viewport) {
        this.texture=texture;size=image.image.getWidth();
        MapPoint center=viewport.mapToScreen(image.centerX,image.centerY);
        x=(float)(center.getX()-size/2d);y=(float)(center.getY()-size/2d);
    }
}
