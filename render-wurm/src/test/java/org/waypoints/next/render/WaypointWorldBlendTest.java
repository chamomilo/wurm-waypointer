package org.waypoints.next.render;

import com.wurmonline.client.renderer.backend.Primitive;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class WaypointWorldBlendTest {
    @Test public void luminousEffectsUseAlphaWeightedAdditiveBlend() {
        assertSame(Primitive.BlendMode.ALPHAADD, WaypointWorldBlend.luminous());
    }

    @Test public void blackLightUsesStandardAlphaAndAStableDarkTint() {
        assertSame(Primitive.BlendMode.ALPHABLEND, WaypointWorldBlend.blackLight());
        assertEquals(0.12f, WaypointWorldBlend.blackLightChannel(0.0f), 0.0001f);
        assertEquals(0.02f, WaypointWorldBlend.blackLightChannel(1.0f), 0.0001f);
    }
}
