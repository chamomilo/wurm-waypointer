package org.waypoints.next.render;

import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.resources.textures.BuiltinTexture;

/** Complete texture state for vertex-coloured primitives in Wurm's world pass. */
public final class WaypointWorldTexture {
    private WaypointWorldTexture() {
    }

    public static void bindWhite(Primitive primitive) {
        if (primitive == null) throw new IllegalArgumentException(
                "primitive is required");
        primitive.clearTextures();
        // modern/simple.fragment.shader always samples tex0 before multiplying
        // by the vertex colour. Leaving tex0 empty only appeared to work while
        // a vanilla Rift happened to leave its plasma texture bound.
        primitive.texture[0] = BuiltinTexture.getWhite();
        primitive.texenv[0] = Primitive.TexEnv.MODULATE;
    }
}
