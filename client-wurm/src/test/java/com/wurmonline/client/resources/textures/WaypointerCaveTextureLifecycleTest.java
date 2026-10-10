package com.wurmonline.client.resources.textures;

import javassist.*;
import org.junit.Test;
import java.awt.image.BufferedImage;
import static org.junit.Assert.*;

/** Real texture lifecycle bytecode with GL entry points replaced by a thread-checking driver. */
public final class WaypointerCaveTextureLifecycleTest {
    @Test public void snapshotsCoalesceAndGlWorkStaysOnFrameBoundaries() throws Throwable {
        ClassPool pool=new ClassPool(true);
        String probe=Probe.class.getName();
        CtClass backend=pool.get("com.wurmonline.client.renderer.backend.Backend");
        backend.getClassInitializer().setBody("{}");
        backend.getDeclaredMethod("isGLThread").setBody("{ return "+probe+".gl; }");
        CtClass loader=pool.get("com.wurmonline.client.resources.textures.TextureLoader");
        loader.getClassInitializer().setBody("{}");
        loader.getDeclaredMethod("preprocessImage").setBody("{ return new com.wurmonline.client.resources.textures.PreProcessedTextureData(new byte[$1.getWidth()*$1.getHeight()*4],$1.getWidth(),$1.getHeight(),true); }");
        loader.getDeclaredMethod("initTexture").setBody("{ "+probe+".checkGl(); "+probe+".allocations++; return true; }");
        loader.getDeclaredMethod("updateGifTexture").setBody("{ "+probe+".checkGl(); "+probe+".updates++; if($1.getWidth()!=$2.getWidth()||$1.getHeight()!=$2.getHeight())throw new IllegalStateException(\"GL storage size mismatch\"); return true; }");
        pool.get("com.wurmonline.client.resources.textures.ImageTextureLoader").getDeclaredMethod("deleteTexture")
                .setBody("{ "+probe+".checkGl(); "+probe+".deletes++; }");
        pool.get("com.wurmonline.client.resources.textures.ImageTexture").getDeclaredMethod("reinit")
                .instrument(new javassist.expr.ExprEditor() {
                    public void edit(javassist.expr.MethodCall call)throws CannotCompileException {
                        if(call.getMethodName().equals("glGenTextures"))call.replace("{ "+probe+".checkGl(); $_=++"+probe+".ids; }");
                    }
                });
        Loader runtime=new Loader(pool);runtime.delegateLoadingOf("org.junit.");
        runtime.run(probe,new String[0]);
    }
    public static final class Probe {
        public static boolean gl;
        public static int allocations,updates,deletes,ids;
        public static void checkGl() { if(!gl)throw new AssertionError("GL call from HUD thread"); }
        public static void main(String[] ignored) {
            WaypointerCaveTexture owner=new WaypointerCaveTexture();
            owner.update(image(16));owner.update(image(32));owner.update(image(32));
            assertEquals(0,allocations);assertEquals(0,updates);
            WaypointerCaveTexture.flushUploads();assertEquals(0,allocations);
            gl=true;WaypointerCaveTexture.flushUploads();assertEquals(1,allocations);
            assertEquals(32,owner.get().getWidth());assertEquals(1,ids);
            gl=false;owner.update(image(32));owner.update(image(32));
            gl=true;WaypointerCaveTexture.flushUploads();assertEquals(1,updates);assertEquals(1,allocations);
            gl=false;owner.update(image(48));
            gl=true;WaypointerCaveTexture.flushUploads();assertEquals(2,allocations);assertEquals(1,ids);
            gl=false;owner.dispose();assertNull(owner.get());assertEquals(0,deletes);
            WaypointerCaveTexture.finishFrame();assertEquals(0,deletes);
            gl=true;WaypointerCaveTexture.finishFrame();assertEquals(0,deletes);
            WaypointerCaveTexture.finishFrame();assertEquals(1,deletes);
            gl=false;owner.update(image(16));owner.dispose();
            gl=true;WaypointerCaveTexture.flushUploads();WaypointerCaveTexture.finishFrame();WaypointerCaveTexture.finishFrame();
            assertEquals(2,allocations);assertEquals(1,deletes); // Never-created GL names need no deletion.
            gl=false;owner.update(image(16));
            gl=true;WaypointerCaveTexture.flushUploads();assertEquals(2,ids);
            owner.dispose();WaypointerCaveTexture.finishFrame();WaypointerCaveTexture.finishFrame();assertEquals(2,deletes);
        }
        private static BufferedImage image(int size) { return new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB); }
    }
}
