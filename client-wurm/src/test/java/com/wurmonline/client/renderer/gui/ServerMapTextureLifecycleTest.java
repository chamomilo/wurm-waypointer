package com.wurmonline.client.renderer.gui;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;
import javassist.Modifier;
import org.junit.Test;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.ServerMapSnapshot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Exercises reconnect and replacement against the real bridge with a controlled native loader. */
public class ServerMapTextureLifecycleTest {
    @Test public void reconnectReusesAssetsAndReplacedJobsReleaseTheirNativeRequests() throws Throwable {
        ClassPool pool=new ClassPool(true);
        String probe=Probe.class.getName();
        CtClass fonts=pool.get("com.wurmonline.client.renderer.gui.text.WaypointerMiniMapFonts");
        if(fonts.getClassInitializer()!=null)fonts.getClassInitializer().setBody("{}");
        fonts.getDeclaredMethod("healthbarTitle").setBody("{ return null; }");
        CtClass nativeLoader=pool.get("com.wurmonline.client.resources.textures.ResourceTextureLoader");
        nativeLoader.getClassInitializer().setBody("{}");
        nativeLoader.getDeclaredMethod("prepareTexture").setBody("{ "+probe+".prepare($2); }");
        for(javassist.CtMethod method:nativeLoader.getDeclaredMethods("getPreparedTexture")) {
            if(method.getParameterTypes().length==2)method.setBody("{ "+probe+".consume($2); return null; }");
        }
        CtClass bridge=pool.get(ServerMapWindowBridge.class.getName());
        bridge.getDeclaredField("TEXTURE_WORKER").setModifiers(Modifier.PRIVATE|Modifier.STATIC);
        Loader loader=new Loader(pool);loader.delegateLoadingOf("org.junit.");
        loader.run(probe,new String[0]);
    }

    public static final class Probe {
        private static final ControlledWorker worker=new ControlledWorker();
        private static final Set<Object> requests=new HashSet<>();
        private static int reads,consumed;
        private static boolean inWorker;
        private static Runnable duringRead;
        public static void prepare(Object request) {
            assertTrue("Image decoding must stay off HUD",inWorker);
            reads++;requests.add(request);
            if(duringRead!=null){Runnable callback=duringRead;duringRead=null;callback.run();}
        }
        public static void consume(Object request) {
            assertTrue("Native preparation must be consumed on the worker",inWorker);
            assertTrue(requests.remove(request));consumed++;
        }
        public static void main(String[] ignored)throws Exception {
            Field workerField=ServerMapWindowBridge.class.getDeclaredField("TEXTURE_WORKER");
            workerField.setAccessible(true);workerField.set(null,worker);
            ServerMapSnapshot first=snapshot("sklotopolis-novus",1);
            Object asset=prepare(first);
            ServerMapWindowBridge.resetAll();
            assertSame(asset,prepare(first));assertEquals(1,worker.tasks.size());
            worker.runNext();assertEquals(1,reads);assertEquals(1,consumed);assertTrue(requests.isEmpty());
            Field indexScheduled=asset.getClass().getDeclaredField("indexScheduled");
            indexScheduled.setAccessible(true);indexScheduled.setBoolean(asset,true);
            ServerMapWindowBridge.resetAll();
            assertSame(asset,prepare(first));assertTrue(indexScheduled.getBoolean(asset));
            assertEquals(0,worker.tasks.size());

            Object stale=prepare(snapshot("sklotopolis-novus",2));
            Object newer=prepare(snapshot("sklotopolis-novus",3));assertNotSame(stale,newer);
            worker.runNext();assertEquals(1,reads); // A queued superseded image is never decoded.
            worker.runNext();assertEquals(2,reads);assertTrue(requests.isEmpty());

            prepare(snapshot("sklotopolis-novus",4));
            duringRead=()->{try { prepare(snapshot("sklotopolis-liberty",4)); }catch(Exception e){throw new AssertionError(e);}};
            worker.runNext(); // Even a request superseded while decoding is consumed.
            assertEquals(3,reads);assertEquals(3,consumed);assertTrue(requests.isEmpty());
            worker.runNext();assertEquals(4,reads);assertEquals(4,consumed);assertTrue(requests.isEmpty());
        }
        private static Object prepare(ServerMapSnapshot snapshot)throws Exception {
            Method method=ServerMapWindowBridge.class.getDeclaredMethod("prepare",ServerMapSnapshot.class);
            method.setAccessible(true);return method.invoke(null,snapshot);
        }
        private static ServerMapSnapshot snapshot(String id,long revision)throws Exception {
            Constructor<ServerMapProfile> profile=ServerMapProfile.class.getDeclaredConstructor(
                    String.class,String.class,int.class,int.class,int.class,String.class);
            profile.setAccessible(true);
            Constructor<ServerMapSnapshot> snapshot=ServerMapSnapshot.class.getDeclaredConstructor(
                    ServerMapProfile.class,Path.class,long.class,List.class,long.class,long.class);
            snapshot.setAccessible(true);
            return snapshot.newInstance(profile.newInstance(id,id,1,4096,4096,"https://example.invalid"),
                    Paths.get("surface.png"),revision,Collections.emptyList(),0L,revision);
        }
        private static final class ControlledWorker extends AbstractExecutorService {
            final Deque<Runnable> tasks=new ArrayDeque<>();
            public void execute(Runnable task){tasks.add(task);}
            void runNext(){inWorker=true;try{tasks.remove().run();}finally{inWorker=false;}}
            public void shutdown(){}
            public List<Runnable> shutdownNow(){return Collections.emptyList();}
            public boolean isShutdown(){return false;}
            public boolean isTerminated(){return false;}
            public boolean awaitTermination(long timeout,TimeUnit unit){return true;}
        }
    }
}
