package org.waypoints.next.render;

import com.wurmonline.client.renderer.backend.Primitive;

/** Alpha-aware Wurm blend policy for persisted waypoint opacity. */
public final class WaypointWorldBlend {
    private WaypointWorldBlend() {
    }

    public static Primitive.BlendMode luminous() {
        return Primitive.BlendMode.ALPHAADD;
    }

    public static Primitive.BlendMode blackLight() {
        return Primitive.BlendMode.ALPHABLEND;
    }

    /** Dark tint used by Black light while standard alpha controls its strength. */
    public static float blackLightChannel(float selectedChannel) {
        if (Float.isNaN(selectedChannel) || Float.isInfinite(selectedChannel)
                || selectedChannel < 0.0f || selectedChannel > 1.0f) {
            throw new IllegalArgumentException("selected channel must be in 0..1");
        }
        return 0.02f + (1.0f - selectedChannel) * 0.10f;
    }
}
