package org.waypoints.next.render;

/** Maps Wurm's player layer to the render pass that can actually reach the camera. */
public final class NavigationRenderLayer {
    private NavigationRenderLayer() { }

    public static int forPlayer(int playerLayer) {
        return playerLayer < 0 ? -1 : 0;
    }
}
