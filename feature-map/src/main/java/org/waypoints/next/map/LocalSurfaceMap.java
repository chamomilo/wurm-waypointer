package org.waypoints.next.map;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded session observations layered over the published map, with a one-second sampling budget. */
public final class LocalSurfaceMap {
    public static final int RADIUS = 5;
    public static final int CHUNK_SIZE = 16;
    public static final int MAXIMUM_CHUNKS = 64;
    public static final long REFRESH_MILLIS = 1000;

    public interface Source { Tile read(int tileX, int tileY); }

    public static final class Tile {
        final int argb;
        final String description;
        public Tile(int rgb, String description) {
            argb = 0xff000000 | (rgb & 0xffffff);
            this.description = Objects.requireNonNull(description, "description");
        }
    }

    /** Immutable pixels; a new revision is published only when a received tile changes colour. */
    public static final class Chunk {
        private final int originX, originY;
        private final long key, revision;
        private final int[] pixels;
        private Chunk(MutableChunk chunk) {
            originX=chunk.originX; originY=chunk.originY;
            key=chunk.key; revision=chunk.revision;
            pixels=chunk.pixels.clone();
        }
        public int getOriginX() { return originX; }
        public int getOriginY() { return originY; }
        public long getKey() { return key; }
        public long getRevision() { return revision; }
        public BufferedImage image() {
            BufferedImage image=new BufferedImage(CHUNK_SIZE,CHUNK_SIZE,BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0,0,CHUNK_SIZE,CHUNK_SIZE,pixels,0,CHUNK_SIZE);
            return image;
        }
    }

    private final Map<Long,MutableChunk> chunks=new LinkedHashMap<Long,MutableChunk>(16,.75f,true);
    private List<Chunk> snapshot=Collections.emptyList();
    private long lastRefresh=Long.MIN_VALUE, revision;

    public boolean refresh(long nowMillis, int worldSize, int playerX, int playerY, Source source) {
        if(source==null||worldSize<1||playerX<0||playerY<0||playerX>=worldSize||playerY>=worldSize)return false;
        if(lastRefresh!=Long.MIN_VALUE&&nowMillis>=lastRefresh&&nowMillis-lastRefresh<REFRESH_MILLIS)return false;
        lastRefresh=nowMillis;
        boolean changed=false;
        for(int y=Math.max(0,playerY-RADIUS);y<=Math.min(worldSize-1,playerY+RADIUS);y++)
            for(int x=Math.max(0,playerX-RADIUS);x<=Math.min(worldSize-1,playerX+RADIUS);x++) {
                Tile tile=source.read(x,y);
                if(tile==null)continue; // A missing packet must not erase previously received terrain.
                long key=key(x,y);
                MutableChunk chunk=chunks.get(key);
                if(chunk==null){chunk=new MutableChunk(key,x/CHUNK_SIZE*CHUNK_SIZE,y/CHUNK_SIZE*CHUNK_SIZE);chunks.put(key,chunk);changed=true;}
                int index=x-chunk.originX+(y-chunk.originY)*CHUNK_SIZE;
                chunk.tiles[index]=tile;
                if(chunk.pixels[index]!=tile.argb){chunk.pixels[index]=tile.argb;chunk.revision=++revision;chunk.snapshot=null;changed=true;}
            }
        while(chunks.size()>MAXIMUM_CHUNKS){chunks.remove(chunks.keySet().iterator().next());changed=true;}
        if(changed){
            List<Chunk> next=new ArrayList<Chunk>(chunks.size());
            for(MutableChunk chunk:chunks.values()){
                if(chunk.snapshot==null)chunk.snapshot=new Chunk(chunk);
                next.add(chunk.snapshot);
            }
            snapshot=Collections.unmodifiableList(next);
        }
        return changed;
    }

    public List<Chunk> snapshot() { return snapshot; }
    public String describe(int tileX,int tileY) {
        if(tileX<0||tileY<0)return "";
        MutableChunk chunk=chunks.get(key(tileX,tileY));
        if(chunk==null)return "";
        Tile tile=chunk.tiles[tileX-chunk.originX+(tileY-chunk.originY)*CHUNK_SIZE];
        return tile==null?"":tile.description;
    }
    public void clear() { chunks.clear();snapshot=Collections.emptyList();lastRefresh=Long.MIN_VALUE; }
    private static long key(int x,int y) { return ((long)(x/CHUNK_SIZE)<<32)|(y/CHUNK_SIZE&0xffffffffL); }
    private static final class MutableChunk {
        final long key;
        final int originX,originY;
        final int[] pixels=new int[CHUNK_SIZE*CHUNK_SIZE];
        final Tile[] tiles=new Tile[pixels.length];
        long revision;
        Chunk snapshot;
        MutableChunk(long key,int x,int y){this.key=key;originX=x;originY=y;}
    }
}
