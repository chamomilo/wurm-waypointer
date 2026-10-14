package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.backend.Backend;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.VertexBuffer;
import org.waypoints.next.map.MapPoint;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Colored map geometry occupies one HUD primitive, regardless of the number of roads/deeds. */
public final class MapOverlayBatch {
    private static final int FLOATS_PER_VERTEX = 7;
    private static final ConcurrentLinkedQueue<Retired> RETIRED = new ConcurrentLinkedQueue<>();
    private static volatile long frame;
    private final Matrix transform = new Matrix();
    private float[] data = new float[64 * 6 * FLOATS_PER_VERTEX];
    private float[] uploaded = new float[0];
    private VertexBuffer vertex;
    private int used, left, top, right, bottom;

    void begin(int left, int top, int width, int height) {
        used = 0;
        this.left = left; this.top = top;
        right = left + width; bottom = top + height;
    }

    void outline(MapPoint a, MapPoint b, float r, float g, float blue,
                 float alpha, int thickness) {
        int x1 = left + (int) Math.floor(Math.min(a.getX(), b.getX()));
        int y1 = top + (int) Math.floor(Math.min(a.getY(), b.getY()));
        int x2 = left + (int) Math.ceil(Math.max(a.getX(), b.getX()));
        int y2 = top + (int) Math.ceil(Math.max(a.getY(), b.getY()));
        rect(r, g, blue, alpha, x1, y1, x2 - x1, thickness);
        rect(r, g, blue, alpha, x1, y2 - thickness, x2 - x1, thickness);
        rect(r, g, blue, alpha, x1, y1, thickness, y2 - y1);
        rect(r, g, blue, alpha, x2 - thickness, y1, thickness, y2 - y1);
    }

    void rect(float r, float g, float b, float a, int x, int y, int width, int height) {
        int x1 = Math.max(left, x), y1 = Math.max(top, y);
        int x2 = Math.min(right, x + width), y2 = Math.min(bottom, y + height);
        if (x2 <= x1 || y2 <= y1) return;
        quad(x1, y1, x2, y1, x2, y2, x1, y2, r, g, b, a);
    }

    void line(float x1, float y1, float x2, float y2, float width,
              float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.5f) return;
        float nx = -dy / length * width * 0.5f;
        float ny = dx / length * width * 0.5f;
        quad(x1 - nx, y1 - ny, x2 - nx, y2 - ny,
                x2 + nx, y2 + ny, x1 + nx, y1 + ny, r, g, b, a);
    }

    private void quad(float ax, float ay, float bx, float by,
                      float cx, float cy, float dx, float dy,
                      float r, float g, float b, float a) {
        if (used + 6 * FLOATS_PER_VERTEX > data.length)
            data = Arrays.copyOf(data, Math.max(data.length * 2, used + 6 * FLOATS_PER_VERTEX));
        point(ax, ay, r, g, b, a); point(bx, by, r, g, b, a); point(cx, cy, r, g, b, a);
        point(ax, ay, r, g, b, a); point(cx, cy, r, g, b, a); point(dx, dy, r, g, b, a);
    }

    private void point(float x, float y, float r, float g, float b, float a) {
        data[used++] = x; data[used++] = y; data[used++] = 0;
        data[used++] = r; data[used++] = g; data[used++] = b; data[used++] = a;
    }

    void draw(Queue queue) {
        if (changed()) {
            VertexBuffer replacement = null;
            if (used > 0) {
                // Immutable after publication: never resize/overwrite a buffer still used by a queued frame.
                replacement = VertexBuffer.create(VertexBuffer.Usage.GUI, used / FLOATS_PER_VERTEX,
                        true, false, false, true, false, 0, 0, false, false);
                FloatBuffer buffer = replacement.lock();
                try { buffer.clear(); buffer.put(data, 0, used); buffer.flip(); }
                finally { replacement.unlock(); }
            }
            if (vertex != null) RETIRED.add(new Retired(vertex, frame + 2));
            vertex = replacement;
            uploaded = Arrays.copyOf(data, used);
        }
        if (vertex == null) return;
        Primitive p = queue.reservePrimitive();
        p.copyStateFrom(Renderer.stateAlphaBlend);
        p.blendmode = Primitive.BlendMode.ALPHABLEND;
        p.depthtest = Primitive.TestFunc.ALWAYS; p.depthwrite = false;
        p.twosided = true; p.nolight = true; p.nofog = true;
        p.program = null; p.bindings = null; p.materialInstance = null; p.lightManager = null;
        p.instanceCount = 1; p.setColor(1, 1, 1, 1);
        p.clearTextures(); p.texturematrix = null; p.texenv[0] = Primitive.TexEnv.MODULATE;
        p.vertex = vertex; p.index = null; p.destroyBuffers = false;
        p.type = Primitive.Type.TRIANGLES; p.offset = 0; p.num = used / FLOATS_PER_VERTEX / 3;
        p.clipRect = HeadsUpDisplay.scissor.getCurrent();
        queue.queue(p, transform.identity());
    }

    private boolean changed() {
        if (uploaded.length != used) return true;
        for (int i = 0; i < used; i++) if (uploaded[i] != data[i]) return true;
        return false;
    }

    void dispose() {
        if (vertex != null) RETIRED.add(new Retired(vertex, frame + 2));
        vertex = null; uploaded = new float[0]; used = 0;
    }

    /** Called at the same GL frame boundary as the map texture retirement hook. */
    public static void finishFrame() {
        if (!Backend.isGLThread()) return;
        long completed = ++frame;
        for (int remaining = RETIRED.size(); remaining > 0; remaining--) {
            Retired old = RETIRED.poll();
            if (old == null) break;
            if (old.after > completed) RETIRED.add(old);
            else old.vertex.delete();
        }
    }

    private static final class Retired {
        final VertexBuffer vertex;
        final long after;
        Retired(VertexBuffer vertex, long after) { this.vertex = vertex; this.after = after; }
    }
}
