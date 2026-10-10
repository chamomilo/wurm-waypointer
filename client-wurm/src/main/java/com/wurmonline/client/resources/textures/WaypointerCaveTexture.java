package com.wurmonline.client.resources.textures;

import com.wurmonline.client.renderer.backend.Backend;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.waypoints.next.render.WaypointRenderProfiler;

/** CPU snapshots on the HUD thread; uploads and retirement only at GL frame boundaries. */
public final class WaypointerCaveTexture {
    private static final ConcurrentLinkedQueue<WaypointerCaveTexture> UPDATES = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Retired> RETIRED = new ConcurrentLinkedQueue<>();
    private static volatile long frame;
    private ImageTexture texture;
    private PreProcessedTextureData pending;
    private boolean queued;

    public ImageTexture update(BufferedImage image) {
        PreProcessedTextureData data = TextureLoader.preprocessImage(image, false);
        synchronized (this) {
            if (texture == null) {
                texture = new ImageTexture(-1, data);
                texture.deferInit(data, TextureLoader.Filter.NEAREST, false, false, false);
            }
            pending = data; // Coalesce changes while the GL thread is busy.
            if (!queued) { queued = true; UPDATES.add(this); }
            return texture;
        }
    }

    public synchronized ImageTexture get() { return texture; }

    public synchronized void dispose() {
        if (texture != null) RETIRED.add(new Retired(texture,frame+2));
        texture = null;
        pending = null;
    }

    /** Injected before queue rendering, before Wurm establishes its cached GL state. */
    public static void flushUploads() {
        if (!Backend.isGLThread()) return;
        WaypointerCaveTexture owner;
        while ((owner = UPDATES.poll()) != null) owner.upload();
    }

    private synchronized void upload() {
        queued = false;
        PreProcessedTextureData data = pending;
        pending = null;
        if (texture == null || data == null) return;
        long start = System.nanoTime();
        if (!texture.isValid() || texture.needReinit()
                || texture.getWidth() != data.getWidth() || texture.getHeight() != data.getHeight()
                || texture.hasAlpha() != data.hasAlpha()) {
            texture.setData(data);
            texture.deferInit(data, TextureLoader.Filter.NEAREST, false, false, false);
            texture.reinit();
        } else {
            // Stable storage: glTexSubImage2D, without glTexImage2D reallocation each tick.
            TextureLoader.updateGifTexture(texture, data, 0);
        }
        WaypointRenderProfiler.recordMapUpload(System.nanoTime() - start);
    }

    /** Injected after the frame, when its queued draws no longer refer to retired textures. */
    public static void finishFrame() {
        if (!Backend.isGLThread()) return;
        long completed=++frame;
        for(int remaining=RETIRED.size();remaining>0;remaining--) {
            Retired value=RETIRED.poll();
            if(value==null)break;
            if(value.afterFrame>completed)RETIRED.add(value);
            else if(value.texture.getId()>=0)ImageTextureLoader.deleteTexture(value.texture);
        }
    }
    private static final class Retired {
        final ImageTexture texture;
        final long afterFrame;
        Retired(ImageTexture texture,long afterFrame) { this.texture=texture;this.afterFrame=afterFrame; }
    }
}
