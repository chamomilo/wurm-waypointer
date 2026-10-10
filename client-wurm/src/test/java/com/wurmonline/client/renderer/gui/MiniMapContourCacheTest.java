package com.wurmonline.client.renderer.gui;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.waypoints.next.map.MapViewport;
import static org.junit.Assert.*;

public final class MiniMapContourCacheTest {
    private final Queue<Runnable> jobs=new ArrayDeque<>();
    private final Executor worker=jobs::add;
    private final MiniMapContourCache cache=new MiniMapContourCache(worker);
    private int reads;
    private float slope=4;
    private final MiniMapContourCache.Heights terrain=(x,y,columns,rows) -> {
        reads++;
        float[] data=new float[columns*rows];
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++)data[col+row*columns]=(col%2)*slope;
        return data;
    };

    @Test public void movingEveryFrameDoesNotResampleOrRasterEveryFrame() {
        MapViewport viewport=viewport();
        assertNull(update(viewport,0));assertEquals(1,jobs.size());
        for(int frame=1;frame<100;frame++) {
            viewport.centerOn(1000+frame*.02,2000);
            assertNull(update(viewport,frame*1_000_000));
        }
        assertEquals(1,reads);assertEquals(1,jobs.size());
        jobs.remove().run();
        MiniMapContourCache.Result image=update(viewport,100_000_000);
        assertNotNull(image);
        viewport.centerOn(1003,2000);
        assertSame(image,update(viewport,499_000_000));
        MiniMapContourOverlay overlay=new MiniMapContourOverlay(null,image,viewport);
        assertEquals(-38,overlay.x,0);
        assertEquals(-32,overlay.y,0);
        assertEquals(320,overlay.size);
        assertEquals(1,reads);
        assertSame(image,update(viewport,500_000_000));
        assertEquals(2,reads);assertTrue(jobs.isEmpty()); // Stable world pixels translate with the player.
    }

    @Test public void changedTerrainReplacesPixelsAndRecenterKeepsBoundedCadence() {
        MapViewport viewport=viewport();update(viewport,0);jobs.remove().run();
        MiniMapContourCache.Result first=update(viewport,1);
        slope=8;update(viewport,500_000_000);assertEquals(1,jobs.size());jobs.remove().run();
        MiniMapContourCache.Result second=update(viewport,500_000_001);
        assertNotSame(first,second);
        viewport.centerOn(1100,2100);
        assertSame(second,update(viewport,999_999_999));
        update(viewport,1_000_000_000);assertEquals(1,jobs.size());jobs.remove().run();
        assertEquals(1100,update(viewport,1_000_000_001).centerX,0);
    }

    @Test public void disablingOrChangingLayerDropsAnInFlightRaster() {
        MapViewport viewport=viewport();update(viewport,0);
        assertNull(cache.update(viewport,256,0,false,"server",1,terrain));
        jobs.remove().run();
        assertNull(cache.update(viewport,256,4,true,"server",2,terrain));
        assertEquals(1,jobs.size());jobs.remove().run();
        assertNotNull(cache.update(viewport,256,4,true,"server",3,terrain));
        cache.clear();
        assertNull(cache.update(viewport,256,4,true,"server",4,terrain));
    }

    private MiniMapContourCache.Result update(MapViewport viewport,long now) {
        return cache.update(viewport,256,4,false,"server",now,terrain);
    }
    private static MapViewport viewport() { return new MapViewport(4096,4096,256,256,1000,2000,2); }
}
