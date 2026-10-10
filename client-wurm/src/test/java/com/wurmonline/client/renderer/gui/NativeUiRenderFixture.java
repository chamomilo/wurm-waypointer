package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.resources.ResourceUrl;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.Texture;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import javassist.*;

/** Run the real SDK adapter and inspect its submitted native primitives before GPU execution. */
public final class NativeUiRenderFixture {
    private static final Map<Texture, String> resources = new IdentityHashMap<Texture, String>();
    private static Queue queue;
    private static int submitted;
    private static List<Float> frameAlpha;
    public static void beginAlphaFrame() { frameAlpha = new ArrayList<Float>(); }
    public static void recordAlpha(float alpha) { if (frameAlpha != null) frameAlpha.add(alpha); }
    public static List<Float> endAlphaFrame() { List<Float> result=frameAlpha;frameAlpha=null;return result; }
    public static void instrument(ClassPool pool) throws Exception {
        String name = NativeUiRenderFixture.class.getName();
        for (CtMethod method : pool.get("com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts").getDeclaredMethods("caption")) {
            String density = method.getParameterTypes().length == 3 ? ",$3" : "";
            method.setBody("{ return new com.wurmonline.client.renderer.gui.WaypointerLayoutProbe.ProbeFont(org.chamomilo.wurm.ui.v1.UiTypography.font($1,$2" + density + ")); }");
        }
        pool.get("com.wurmonline.client.renderer.gui.ChamomiloUiV1Canvas").getDeclaredMethod("begin")
                .setBody("{ this.queue = " + name + ".queue(); return this; }");
        CtClass textures = pool.get("com.wurmonline.client.resources.textures.ResourceTextureLoader");
        if (textures.getClassInitializer() != null) textures.getClassInitializer().setBody("{}");
        for (CtMethod method : textures.getDeclaredMethods("getInternalTexture")) {
            CtClass[] parameters = method.getParameterTypes();
            if (parameters.length == 5 && parameters[0].getName().equals("com.wurmonline.client.resources.ResourceUrl"))
                method.setBody("{ return " + name + ".texture($1); }");
        }
        pool.get("com.wurmonline.client.renderer.backend.Primitive").getClassInitializer().setBody("{}");
        CtClass nativeQueue = pool.get("com.wurmonline.client.renderer.backend.Queue");
        nativeQueue.getDeclaredMethod("reservePrimitive").setBody("{ return " + name + ".reserve(); }");
        nativeQueue.getDeclaredMethod("queue").setBody("{ " + name + ".submit($1,$2); }");
    }
    public static Queue queue() throws Exception {
        if (queue == null) queue = (Queue) WaypointerLayoutProbe.allocate(Queue.class);
        return queue;
    }
    public static ResourceTexture texture(ResourceUrl url) throws Exception {
        ResourceTexture texture = (ResourceTexture) WaypointerLayoutProbe.allocate(ResourceTexture.class);
        resources.put(texture, url.getFilePath());
        return texture;
    }
    public static Primitive reserve() throws Exception {
        Primitive primitive = (Primitive) WaypointerLayoutProbe.allocate(Primitive.class);
        primitive.texture = new Texture[8];
        primitive.texenv = new Primitive.TexEnv[8];
        // A pooled primitive must not leak these previous world-render settings into the HUD.
        primitive.blendmode = Primitive.BlendMode.OPAQUE;
        primitive.depthtest = Primitive.TestFunc.LESS;
        primitive.depthwrite = true;
        primitive.instanceCount = 7;
        primitive.texturematrix = new Matrix().fromTranslationAndNonUniformScale(9, 9, 0, 9, 9, 1);
        return primitive;
    }
    public static void submit(Primitive primitive, Matrix model) throws Exception {
        recordAlpha(primitive.a);
        require(primitive.blendmode == Primitive.BlendMode.ALPHABLEND, "HUD uses source alpha blending");
        require(!primitive.depthwrite && primitive.depthtest == Primitive.TestFunc.ALWAYS, "HUD does not compete with world depth");
        require(primitive.nolight && primitive.nofog, "HUD is independent of world lighting and fog");
        require(primitive.program == null && primitive.instanceCount == 1, "HUD clears pooled shader/instance state");
        require(primitive.a >= 0 && primitive.a <= 1, "HUD alpha is bounded");
        float[] target = new float[16]; model.get(target);
        int x = Math.round(target[12]), y = Math.round(target[13]);
        int width = Math.round(target[0]), height = Math.round(target[5]);
        if (primitive.texture[0] == null) {
            require(primitive.texturematrix == null, "Solid fill clears previous texture transform");
            WaypointerLayoutProbe.rect(primitive.r, primitive.g, primitive.b, primitive.a, x, y, width, height);
        } else {
            String resource = resources.get(primitive.texture[0]);
            require(resource != null, "Texture originates from the actual SDK resource adapter");
            float[] uv = new float[16]; primitive.texturematrix.get(uv);
            require(uv[12] >= 0 && uv[13] >= 0 && uv[0] > 0 && uv[5] > 0
                    && uv[12] + uv[0] <= 1.00001f && uv[13] + uv[5] <= 1.00001f,
                    "Atlas sample uses extents within one source crop: " + resource);
            WaypointerLayoutProbe.kitTexture(resource, primitive.r, primitive.a, x, y, width, height,
                    uv[12], uv[13], uv[12] + uv[0], uv[13] + uv[5]);
        }
        submitted++;
    }
    public static void verified() { require(submitted > 100, "Production previews passed through the native SDK adapter"); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
