package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.VertexBuffer;
import javassist.*;
import org.junit.Test;
import org.waypoints.next.map.*;
import org.waypoints.next.navigation.HighwayTileIndex;
import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import static org.junit.Assert.*;

/** Exercise production overlays and Wurm's actual bounded HUD queue without a GL context. */
public final class MapOverlayBatchTest {
    @Test public void denseOverviewKeepsHudCapacityAndImmutableBuffers() throws Throwable {
        ClassPool pool = new ClassPool(true);
        String probe = Probe.class.getName();
        CtClass backend = pool.get("com.wurmonline.client.renderer.backend.Backend");
        backend.getClassInitializer().setBody("{}");
        backend.getDeclaredMethod("isGLThread").setBody("{ return " + probe + ".gl; }");
        pool.get("com.wurmonline.client.options.Options").getClassInitializer().setBody("{}");
        CtClass vertex = pool.get("com.wurmonline.client.renderer.backend.VertexBuffer");
        vertex.getDeclaredMethod("useGPU").setBody("{ return false; }");
        vertex.getClassInitializer().instrument(new javassist.expr.ExprEditor() {
            @Override public void edit(javassist.expr.MethodCall call) throws CannotCompileException {
                if (call.getMethodName().equals("value")) call.replace("{ $_ = false; }");
            }
        });
        CtClass buffers = pool.get("com.wurmonline.client.util.BufferUtil");
        if (buffers.getClassInitializer() != null) buffers.getClassInitializer().setBody("{}");
        buffers.getDeclaredMethod("newFloatBuffer").setBody("{ return java.nio.FloatBuffer.allocate($1); }");
        buffers.getDeclaredMethod("deallocate").setBody("{}");
        pool.get("com.wurmonline.client.renderer.backend.Primitive").getClassInitializer()
                .setBody("{ defaultState = new com.wurmonline.client.renderer.backend.RenderState(); }");
        pool.get("com.wurmonline.client.renderer.gui.Renderer").getClassInitializer()
                .setBody("{ stateAlphaBlend = new com.wurmonline.client.renderer.backend.RenderState(); }");
        pool.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay").getClassInitializer()
                .setBody("{ scissor = new com.wurmonline.client.renderer.backend.ScissorControl(); }");
        pool.get("com.wurmonline.client.renderer.gui.WurmComponent").getClassInitializer().setBody("{}");
        CtClass fonts = pool.get("com.wurmonline.client.renderer.gui.text.WaypointerMiniMapFonts");
        if (fonts.getClassInitializer() != null) fonts.getClassInitializer().setBody("{}");
        fonts.getDeclaredMethod("healthbarTitle").setBody("{ return null; }");
        Loader loader = new Loader(pool); loader.delegateLoadingOf("org.junit.");
        loader.run(probe, new String[0]);
    }

    public static final class Probe {
        public static boolean gl;
        private static final Matrix IDENTITY = new Matrix();
        private static VertexBuffer square;

        public static void main(String[] ignored) throws Exception {
            HeadsUpDisplay.scissor.reset(1024, 768);
            square = VertexBuffer.create(VertexBuffer.Usage.GUI, 6, true, false, false, true, false, 0, 0, false, false);
            Queue queue = new Queue(128, false);
            reserve(queue, 9000);
            assertEquals("Native HUD silently discards reservations beyond its hard cap", 8192, queue.getQueueCount());
            queue.clear();

            MapViewport viewport = new MapViewport(4096, 4096, 920, 620, 2048, 2048);
            List<Deed> deeds = new ArrayList<>();
            Map<String, String> extents = new HashMap<>();
            for (String side : new String[]{"North", "South", "East", "West"}) extents.put("tiles" + side, "12");
            for (int i = 0; i < 2000; i++) deeds.add(Deed.fromProviderData("deed" + i, "Deed " + i,
                    32 + i % 50 * 80, 32 + i / 50 * 96, extents));
            StringBuilder roads = new StringBuilder("[");
            for (int i = 0; i < 3000; i++) {
                if (i > 0) roads.append(',');
                int x = 32 + i % 50 * 80, y = 32 + i / 50 * 64;
                roads.append("{\"startX\":").append(x).append(",\"startY\":").append(y)
                        .append(",\"endX\":").append(x + 8).append(",\"endY\":").append(y).append(",\"type\":2}");
            }
            HighwayTileIndex index = HighwayTileIndex.parse(roads.append(']').toString(), 4096, 4096);
            Class<?> stateType = Class.forName(ServerMapWindowBridge.class.getName() + "$State");
            Constructor<?> constructor = stateType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            Object state = constructor.newInstance("sklotopolis-novus", viewport, new MapOverlayVisibility(true, true, true));
            Method highways = ServerMapWindowBridge.class.getDeclaredMethod("drawHighways", Queue.class, MapViewport.class,
                    int.class, int.class, int.class, int.class, HighwayTileIndex.class);
            Method drawDeeds = ServerMapWindowBridge.class.getDeclaredMethod("drawDeeds", WorldMap.class, Queue.class,
                    MapViewport.class, int.class, int.class, List.class, stateType);
            highways.setAccessible(true); drawDeeds.setAccessible(true);
            reserve(queue, 7000); // Other native windows may already have a large HUD.
            highways.invoke(null, queue, viewport, 3, 21, 920, 620, index);
            drawDeeds.invoke(null, null, queue, viewport, 3, 21, deeds, state);
            assertEquals("All roads/deeds consume only two HUD slots", 7002, queue.getQueueCount());
            Primitive[] submitted = submitted(queue);
            Primitive road = submitted[7000], deed = submitted[7001];
            assertEquals("No road segments are dropped", 3000 * 2, road.num);
            assertEquals("Every deed keeps its four borders and two marker squares", 2000 * 6 * 2, deed.num);
            assertHudState(road); assertHudState(deed);
            VertexBuffer first = deed.vertex;
            float[] firstGeometry = geometry(first);
            reserve(queue, 1000); // Frame, toolbar, labels and later windows all still fit.
            assertEquals(8002, queue.getQueueCount());
            assertTrue(submitted(queue)[8001].queued);
            queue.clear();
            drawDeeds.invoke(null, null, queue, viewport, 3, 21, deeds, state);
            assertSame("Stationary map reuses uploaded geometry", first, submitted(queue)[0].vertex);
            Field hovered = stateType.getDeclaredField("hoveredDeed"); hovered.setAccessible(true); hovered.set(state, deeds.get(0));
            queue.clear();
            drawDeeds.invoke(null, null, queue, viewport, 3, 21, deeds, state);
            assertNotSame("Hover publishes a fresh buffer", first, submitted(queue)[0].vertex);
            assertArrayEquals("Pending frame geometry is never overwritten", firstGeometry, geometry(first), 0);
            MapOverlayBatch.finishFrame(); assertNotNull(first.getSystemBuffer());
            gl = true; MapOverlayBatch.finishFrame(); assertNotNull(first.getSystemBuffer());
            MapOverlayBatch.finishFrame(); assertNull("Old buffer retires only after two completed GL frames", first.getSystemBuffer());
            gl = false;
            clippingAndReplacement(queue);
        }

        private static void clippingAndReplacement(Queue queue) {
            queue.clear();
            MapOverlayBatch batch = new MapOverlayBatch();
            batch.begin(10, 20, 100, 50);
            batch.rect(.2f, .4f, .8f, .55f, 0, 0, 40, 40);
            batch.rect(1, 1, 1, 1, 200, 200, 20, 20); // Fully outside.
            batch.line(20, 30, 80, 30, 2, 1, .7f, .2f, .92f);
            batch.draw(queue);
            Primitive primitive = submitted(queue)[0];
            assertEquals(4, primitive.num);
            float[] data = geometry(primitive.vertex);
            assertEquals(10, data[0], 0); assertEquals(20, data[1], 0); assertEquals(.55f, data[6], 0);
            assertEquals(40, data[7], 0); assertEquals(20, data[8], 0);
            assertEquals(20, data[42], 0); assertEquals(29, data[43], .0001);
            assertEquals(80, data[49], 0); assertEquals(29, data[50], .0001);
            VertexBuffer old = primitive.vertex;
            queue.clear(); batch.begin(10, 20, 100, 50); batch.draw(queue);
            assertEquals("Empty layer submits no stale geometry", 0, queue.getQueueCount());
            batch.dispose(); gl = true; MapOverlayBatch.finishFrame(); assertNotNull(old.getSystemBuffer());
            MapOverlayBatch.finishFrame(); assertNull(old.getSystemBuffer());
        }

        private static void assertHudState(Primitive p) {
            assertEquals(Primitive.Type.TRIANGLES, p.type);
            assertEquals(Primitive.BlendMode.ALPHABLEND, p.blendmode);
            assertEquals(Primitive.TestFunc.ALWAYS, p.depthtest);
            assertFalse(p.depthwrite); assertTrue(p.nolight); assertTrue(p.nofog);
            assertNull(p.program); assertNull(p.texturematrix); assertNull(p.texture[0]);
            assertEquals(1, p.instanceCount); assertEquals(1, p.a, 0); assertFalse(p.destroyBuffers);
            assertSame(HeadsUpDisplay.scissor.getCurrent(), p.clipRect);
        }
        private static Primitive[] submitted(Queue queue) {
            try { Field field = Queue.class.getDeclaredField("queue"); field.setAccessible(true); return (Primitive[]) field.get(queue); }
            catch (Exception e) { throw new AssertionError(e); }
        }
        private static float[] geometry(VertexBuffer vertex) {
            FloatBuffer buffer = vertex.getSystemBuffer().duplicate();
            float[] result = new float[buffer.remaining()]; buffer.get(result); return result;
        }
        private static void reserve(Queue queue, int count) {
            for (int i = 0; i < count; i++) {
                Primitive p = queue.reservePrimitive(); p.vertex = square; p.num = 2;
                queue.queue(p, IDENTITY);
            }
        }
    }
}
