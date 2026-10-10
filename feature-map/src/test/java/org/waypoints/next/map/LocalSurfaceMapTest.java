package org.waypoints.next.map;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LocalSurfaceMapTest {
    @Test public void samplesFiveTilesInEveryDirectionOnceASecondAndReusesUnchangedPixels() {
        LocalSurfaceMap map=new LocalSurfaceMap();
        final int[] calls={0};
        LocalSurfaceMap.Source source=(x,y)->{calls[0]++;assertTrue(x>=95&&x<=105&&y>=95&&y<=105);return new LocalSurfaceMap.Tile(0x293a02,"Tree");};
        assertTrue(map.refresh(0,1024,100,100,source));
        assertEquals(121,calls[0]);
        List<LocalSurfaceMap.Chunk> before=map.snapshot();
        assertFalse(map.refresh(999,1024,100,100,source));assertEquals(121,calls[0]);
        assertFalse(map.refresh(1000,1024,100,100,source));assertEquals(242,calls[0]);
        assertSame(before,map.snapshot());
        assertEquals(0,chunk(map,96,96).image().getRGB(15,15));
    }

    @Test public void felledTreeUpdatesPixelsAndKeepsTheObservedResultAfterMovingAway() {
        LocalSurfaceMap map=new LocalSurfaceMap();
        map.refresh(0,1024,100,100,(x,y)->new LocalSurfaceMap.Tile(0x293a02,"Tree"));
        LocalSurfaceMap.Chunk old=chunk(map,96,96);
        map.refresh(1000,1024,100,100,(x,y)->x==100&&y==100?new LocalSurfaceMap.Tile(0x366503,"Grass"):null);
        LocalSurfaceMap.Chunk current=chunk(map,96,96);
        assertTrue(current.getRevision()>old.getRevision());
        assertEquals(0xff293a02,old.image().getRGB(4,4));
        assertEquals(0xff366503,current.image().getRGB(4,4));
        assertEquals("Grass",map.describe(100,100));
        assertEquals("Tree",map.describe(101,100));
        map.refresh(2000,1024,200,200,(x,y)->new LocalSurfaceMap.Tile(0x4b3f2f,"Dirt"));
        assertEquals("Grass",map.describe(100,100));
        assertSame(current,chunk(map,96,96));
    }

    @Test public void unknownTilesRemainTransparentAndWorldEdgesAreClipped() {
        LocalSurfaceMap map=new LocalSurfaceMap();final int[] calls={0};
        map.refresh(0,16,0,0,(x,y)->{assertTrue(x>=0&&x<=5&&y>=0&&y<=5);calls[0]++;return x==0&&y==0?new LocalSurfaceMap.Tile(0,"Rock"):null;});
        assertEquals(36,calls[0]);
        assertEquals(0xff000000,map.snapshot().get(0).image().getRGB(0,0));
        assertEquals(0,map.snapshot().get(0).image().getRGB(1,0));
        map.clear();assertTrue(map.snapshot().isEmpty());assertEquals("",map.describe(0,0));
        assertTrue(map.refresh(0,16,0,0,(x,y)->new LocalSurfaceMap.Tile(1,"Grass")));
    }

    @Test public void longWalkEvictsOldChunksAndRetainsTheCurrentNeighbourhood() {
        LocalSurfaceMap map=new LocalSurfaceMap();
        for(int i=0;i<150;i++){
            map.refresh(i*1000L,8192,8+i*32,8,(x,y)->new LocalSurfaceMap.Tile(0x366503,"Grass"));
            assertTrue(map.snapshot().size()<=LocalSurfaceMap.MAXIMUM_CHUNKS);
        }
        assertEquals(LocalSurfaceMap.MAXIMUM_CHUNKS,map.snapshot().size());
        assertEquals("",map.describe(8,8));assertEquals("Grass",map.describe(8+149*32,8));
    }
    private static LocalSurfaceMap.Chunk chunk(LocalSurfaceMap map,int x,int y) {
        for(LocalSurfaceMap.Chunk chunk:map.snapshot())if(chunk.getOriginX()==x&&chunk.getOriginY()==y)return chunk;
        throw new AssertionError("Missing chunk");
    }
}
