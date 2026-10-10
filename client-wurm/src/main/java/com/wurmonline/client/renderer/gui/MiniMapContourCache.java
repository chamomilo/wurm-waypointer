package com.wurmonline.client.renderer.gui;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.render.WaypointRenderProfiler;

/** One in-flight CPU raster, refreshed at most twice a second and anchored to world tiles. */
final class MiniMapContourCache {
    static final long REFRESH_NANOS = 500_000_000L;
    static final int PADDING = 32;
    private static final Executor WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wurm-waypointer-contours");
        thread.setDaemon(true);
        return thread;
    });
    interface Heights { float[] read(int x, int y, int columns, int rows); }
    static final class Result {
        final BufferedImage image;
        final double centerX, centerY, scale;
        final float[] heights;
        final long generation;
        Result(BufferedImage image, double x, double y, double scale, float[] heights, long generation) {
            this.image=image;centerX=x;centerY=y;this.scale=scale;this.heights=heights;this.generation=generation;
        }
    }
    private final Executor worker;
    private volatile Result completed;
    private volatile boolean working;
    private volatile long generation;
    private Result current;
    private long nextRefresh;
    private int size, interval;
    private double scale;
    private boolean cave;
    private String profile="";

    MiniMapContourCache() { this(WORKER); }
    MiniMapContourCache(Executor worker) { this.worker=worker; }

    Result update(MapViewport active, int pixels, int metres, boolean caveView,
                  String profileId, long now, Heights source) {
        if (metres<=0) { if(interval!=0)clear(); return null; }
        if (pixels!=size || metres!=interval || caveView!=cave || !profileId.equals(profile)
                || active.getPixelsPerTile()!=scale) {
            clear();size=pixels;interval=metres;cave=caveView;profile=profileId;scale=active.getPixelsPerTile();
        }
        Result ready=completed;
        if(ready!=null) { completed=null; if(ready.generation==generation)current=ready; }
        if(!working && now>=nextRefresh) {
            nextRefresh=now+REFRESH_NANOS;
            double x=active.getCenterX(), y=active.getCenterY();
            if(current!=null && Math.abs(x-current.centerX)*scale<=PADDING/2
                    && Math.abs(y-current.centerY)*scale<=PADDING/2) {
                x=current.centerX;y=current.centerY;
            }
            final int rasterSize=pixels+2*PADDING;
            final MapViewport raster=new MapViewport(active.getMapWidth(),active.getMapHeight(),
                    rasterSize,rasterSize,x,y,scale);
            int originX=(int)Math.floor(raster.screenToMap(0,0).getX());
            int originY=(int)Math.floor(raster.screenToMap(0,0).getY());
            int columns=(int)Math.ceil(raster.screenToMap(rasterSize,rasterSize).getX())-originX+1;
            int rows=(int)Math.ceil(raster.screenToMap(rasterSize,rasterSize).getY())-originY+1;
            final float[] heights=source.read(originX,originY,columns,rows);
            if(current!=null && raster.getCenterX()==current.centerX && raster.getCenterY()==current.centerY
                    && Arrays.equals(heights,current.heights))return current;
            final long requestedGeneration=generation;
            working=true;
            worker.execute(() -> {
                long start=System.nanoTime();
                try {
                    if(requestedGeneration!=generation)return;
                    BufferedImage image=MiniMapContourImage.render(raster,rasterSize,
                            originX,originY,columns,rows,heights,metres);
                    completed=new Result(image,raster.getCenterX(),raster.getCenterY(),
                            raster.getPixelsPerTile(),heights,requestedGeneration);
                } catch(RuntimeException failure) {
                    java.util.logging.Logger.getLogger("WurmWaypointer.Map").log(
                            java.util.logging.Level.WARNING,"Mini-map contours could not be rasterized",failure);
                } finally {
                    WaypointRenderProfiler.recordContour(System.nanoTime()-start);
                    working=false;
                }
            });
        }
        return current;
    }

    void clear() { generation++;current=null;completed=null;interval=0;nextRefresh=0; }
}
