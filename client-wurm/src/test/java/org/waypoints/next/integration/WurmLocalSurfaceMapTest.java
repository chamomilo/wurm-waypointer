package org.waypoints.next.integration;

import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.mesh.Tiles;
import org.junit.Test;
import org.waypoints.next.map.LocalSurfaceMap;
import static org.junit.Assert.*;

public class WurmLocalSurfaceMapTest {
    @Test public void receivedTreeRemovalChangesTheActualMapRasterWithoutAnImageDownload() {
        NearTerrainDataBuffer near=new NearTerrainDataBuffer();
        int[][] terrain=new int[20][20];
        for(int x=0;x<20;x++)for(int y=0;y<20;y++)terrain[x][y]=Tiles.encode((short)100,Tiles.Tile.TILE_TREE_BIRCH.getId(),(byte)0);
        near.tileStrip((short)90,(short)90,(short)20,(short)20,terrain,new short[20][20],new byte[20][20]);
        LocalSurfaceMap map=new LocalSurfaceMap();
        LocalSurfaceMap.Source source=(x,y)->WurmLocalSurfaceMap.sample(near,100,100,1024,x,y);
        assertTrue(map.refresh(0,1024,100,100,source));
        assertEquals(0xff293a02,pixel(map,100,100));
        int[][] changed={{Tiles.encode((short)100,Tiles.Tile.TILE_GRASS.getId(),(byte)0)}};
        near.tileStrip((short)100,(short)100,(short)1,(short)1,changed,new short[1][1],new byte[1][1]);
        assertFalse(map.refresh(999,1024,100,100,source));
        assertTrue(map.refresh(1000,1024,100,100,source));
        assertEquals(0xff366503,pixel(map,100,100));assertTrue(map.describe(100,100).contains("Grass"));
        assertNull(WurmLocalSurfaceMap.sample(near,100,100,1024,612,100));
        assertNull(WurmLocalSurfaceMap.sample(near,100,100,1024,-1,100));
    }
    private static int pixel(LocalSurfaceMap map,int x,int y) {
        for(LocalSurfaceMap.Chunk chunk:map.snapshot())if(x>=chunk.getOriginX()&&x<chunk.getOriginX()+16&&y>=chunk.getOriginY()&&y<chunk.getOriginY()+16)
            return chunk.image().getRGB(x-chunk.getOriginX(),y-chunk.getOriginY());
        throw new AssertionError("Missing received tile");
    }
}
