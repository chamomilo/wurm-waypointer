package org.waypoints.next.render;

import javassist.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.*;
import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.backend.*;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.effects.TileHighlightOverlay;
import static org.junit.Assert.*;

/** Native buffer lifecycle and production overlay; only GL and queue drawing are stubbed. */
public final class TileHighlightOverlayLifecycleTest {
    @Test public void repeatedTerrainUpdatesKeepOneBufferAndBoundEveryDraw() throws Throwable {
        ClassPool pool=new ClassPool(true);String probe=Probe.class.getName();
        CtClass buffer=pool.get("com.wurmonline.client.renderer.backend.VertexBuffer");
        buffer.getClassInitializer().setBody("{ LOCK_STATUS_UNLOCKED=0; LOCK_STATUS_INPROGRESS=1; LOCK_STATUS_LOCKED=2; useMemoryStat=false; memoryStatLock=new Object(); lockedBufferCount=new java.util.concurrent.atomic.AtomicInteger(); deferredDelete=new java.util.ArrayList(); }");
        buffer.getDeclaredMethod("useGPU").setBody("{ return false; }");
        buffer.getDeclaredMethod("lock").insertBefore("{ "+probe+".checkUpload(); "+probe+".locks++; }");
        buffer.getDeclaredMethod("delete").insertBefore("{ "+probe+".checkRetirement(); "+probe+".deletes++; }");
        buffer.getDeclaredMethod("resize").insertBefore("{ "+probe+".resizes++; }");
        CtClass util=pool.get("com.wurmonline.client.util.BufferUtil");util.getClassInitializer().setBody("{}");
        util.getDeclaredMethod("newFloatBuffer").setBody("{ return java.nio.FloatBuffer.allocate($1); }");
        util.getDeclaredMethod("deallocate").setBody("{}");
        CtClass backend=pool.get("com.wurmonline.client.renderer.backend.Backend");backend.getClassInitializer().setBody("{}");backend.getDeclaredMethod("isGLThread").setBody("{ return "+probe+".gl; }");
        CtClass gl=pool.get("com.wurmonline.client.util.GLHelper");gl.getClassInitializer().setBody("{}");gl.getDeclaredMethod("useDeferredShading").setBody("{ return false; }");
        CtClass world=pool.get("com.wurmonline.client.game.World");world.getClassInitializer().setBody("{}");
        world.getDeclaredMethod("getPlayerLayer").setBody("{ return "+probe+".layer; }");
        world.getDeclaredMethod("getRenderOriginX").setBody("{ return 400f; }");world.getDeclaredMethod("getRenderOriginY").setBody("{ return 480f; }");
        CtClass queue=pool.get("com.wurmonline.client.renderer.backend.Queue");queue.getClassInitializer().setBody("{}");
        queue.getDeclaredMethod("reservePrimitive").setBody("{ return new com.wurmonline.client.renderer.backend.Primitive(); }");
        queue.getDeclaredMethod("queue").setBody("{ "+probe+".queued.add($1); }");
        queue.getDeclaredMethod("isReflection").setBody("{ return "+probe+".reflection; }");
        pool.get("org.waypoints.next.render.WaypointWorldTexture").getDeclaredMethod("bindWhite").setBody("{ $1.clearTextures(); }");
        Loader runtime=new Loader(pool);runtime.delegateLoadingOf("org.junit.");runtime.run(probe,new String[0]);
    }
    public static final class Probe {
        public static int locks,resizes,layer,deletes,phase;public static boolean reflection,gl;
        public static final List<Primitive> queued=new ArrayList<Primitive>();
        public static void main(String[] ignored)throws Exception {
            // Reproduce the pinned native API hazard without a real GL context:
            // off-thread resize retires the buffer but leaves its VAO name set.
            VertexBuffer old=VertexBuffer.create(VertexBuffer.Usage.EFFECT,3,true,false,false,true,false,0,0,true,false);
            set(old,"bufferObject",21);set(old,"arrayObject",31);old.resize(30);
            assertEquals(0,field(old,"bufferObject"));assertEquals(31,field(old,"arrayObject"));resizes=0;

            TileHighlightOverlay overlay=new TileHighlightOverlay((World)allocate(World.class),225);
            Queue queue=(Queue)allocate(Queue.class);
            try {
                TileHighlightPatch first=tile(100,0x60e060,-1);overlay.update(Collections.singletonList(first));overlay.renderInLateWorldPass(queue);assertTrue(queued.isEmpty());
                TileHighlightOverlay.flushUploads();assertEquals(0,locks);uploadFrame();overlay.renderInLateWorldPass(queue);
                VertexBuffer stable=queued.get(0).vertex;int capacity=stable.getNumVertex();assertEquals(225*(6+4*24)+3,capacity);verifyDraws(stable,1,1);
                int before=locks;
                for(int i=0;i<200;i++){queued.clear();overlay.update(Collections.singletonList(tile(100,0x60e060,-1)));uploadFrame();overlay.renderInLateWorldPass(queue);verifyDraws(stable,1,1);}
                assertEquals("Unchanged received tiles must not re-upload",before,locks);
                overlay.update(Arrays.asList(tile(100,0x60e060,-1),tile(101,0x60e060,-1)));
                overlay.update(Arrays.asList(tile(100,0x60e060,0xff0000),tile(101,0x60e060,-1),tile(102,-1,0xff0000)));
                queued.clear();overlay.renderInLateWorldPass(queue);verifyDraws(stable,1,1); // Pending data cannot change an in-flight draw.
                int prior=locks;uploadFrame();assertEquals("Coalesce changes at the frame boundary",prior+1,locks);
                queued.clear();overlay.renderInLateWorldPass(queue);verifyDraws(stable,3,4);
                overlay.update(Collections.singletonList(tile(104,0x12ab34,-1)));uploadFrame();queued.clear();overlay.renderInLateWorldPass(queue);verifyDraws(stable,1,1);
                assertEquals("Only live geometry reaches the GPU",(6+24+3)*7,stable.getSystemBuffer().limit());
                FloatBuffer data=stable.getSystemBuffer().duplicate();data.clear();for(int i=(6+24)*7;i<data.capacity();i++)assertEquals("Shrinking snapshots clear old geometry",0f,data.get(i),0f);
                assertEquals(0,resizes);assertEquals(capacity,stable.getNumVertex());
                queued.clear();layer=-1;overlay.renderInLateWorldPass(queue);assertTrue(queued.isEmpty());layer=0;reflection=true;overlay.renderInLateWorldPass(queue);assertTrue(queued.isEmpty());reflection=false;
                overlay.update(Collections.<TileHighlightPatch>emptyList());uploadFrame();overlay.renderInLateWorldPass(queue);assertTrue(queued.isEmpty());
                overlay.update(Collections.singletonList(first));uploadFrame();overlay.renderInLateWorldPass(queue);verifyDraws(stable,1,1);queued.clear();
                List<TileHighlightPatch> excessive=new ArrayList<TileHighlightPatch>();for(int i=0;i<226;i++)excessive.add(first);
                try{overlay.update(excessive);fail("Unbounded snapshot accepted");}catch(IllegalArgumentException expected){}
            }finally{overlay.close();}
            assertFalse(overlay.isLatePassAlive());queued.clear();overlay.renderInLateWorldPass(queue);assertTrue(queued.isEmpty());
            assertEquals(0,deletes);TileHighlightOverlay.finishFrame();assertEquals(0,deletes);
            endFrame();assertEquals(0,deletes);endFrame();assertEquals(1,deletes);overlay.close();endFrame();assertEquals(1,deletes);
        }
        private static TileHighlightPatch tile(int x,int harvest,int blocked){return new TileHighlightPatch(x,120,blocked,-1,harvest,-1,10,10,10,10);}
        private static void verifyDraws(VertexBuffer stable,int tiles,int layers) {
            assertEquals(3,queued.size());
            assertEquals(tiles*6+layers*24,queued.get(0).offset);assertEquals(1,queued.get(0).num);
            assertEquals(0,queued.get(1).offset);assertEquals(tiles*2,queued.get(1).num);
            assertEquals(tiles*6,queued.get(2).offset);assertEquals(layers*8,queued.get(2).num);
            for(Primitive p:queued){assertSame("Native vertex ownership remains stable",stable,p.vertex);assertTrue(p.offset+p.num*3<=stable.getSystemBuffer().limit()/7);assertFalse(p.depthwrite);}
            assertEquals(Primitive.TestFunc.ALWAYS,queued.get(2).depthtest);
        }
        public static void checkUpload(){assertTrue("Buffer writes require the GL frame boundary",gl&&phase==1);}
        public static void checkRetirement(){assertTrue("Deletion must wait for completed frames",gl&&phase==2);}
        private static void uploadFrame(){gl=true;phase=1;try{TileHighlightOverlay.flushUploads();}finally{gl=false;phase=0;}}
        private static void endFrame(){gl=true;phase=2;try{TileHighlightOverlay.finishFrame();}finally{gl=false;phase=0;}}
        private static Object allocate(Class<?> type)throws Exception{Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);return unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);}
        private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
        private static void set(Object object,String name,int value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.setInt(object,value);}
    }
}
