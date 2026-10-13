package org.waypoints.next.render;

import java.nio.FloatBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class TileHighlightGeometryTest {
    @Test public void everyFillAndBorderVertexStaysRaisedOnSlopingTerrainWithItsColor() {
        TileHighlightPatch tile=new TileHighlightPatch(100,120,-1,0xff542c,-1,-1,10,12,16,14);
        FloatBuffer out=FloatBuffer.allocate(30*7);TileHighlightGeometry.fill(out,tile);TileHighlightGeometry.edges(out,tile);assertEquals(210,out.position());out.flip();
        while(out.hasRemaining()) {
            float x=out.get(),h=out.get(),y=out.get();
            assertTrue(x>=400&&x<=404&&y>=480&&y<=484);
            assertEquals(10+(x-400)*.5f+(y-480)+TileHighlightGeometry.LIFT,h,.0001f);
            assertEquals(1,out.get(),0);assertEquals(84/255f,out.get(),0);assertEquals(44/255f,out.get(),0);
            assertTrue(out.get()>0);
        }
    }
    @Test public void overlappingRulesKeepDistinctNestedFramesAndOneInteriorFill() {
        int[] colors={0xff0000,0x408cff,0x60e060,0x00ffff};
        TileHighlightPatch tile=new TileHighlightPatch(0,0,colors[0],colors[1],colors[2],colors[3],10,10,10,10);
        FloatBuffer out=FloatBuffer.allocate((6+4*24)*7);TileHighlightGeometry.fill(out,tile);TileHighlightGeometry.edges(out,tile);assertFalse(out.hasRemaining());out.flip();
        float[] previous={-1,-1,5,5};
        for(int part=0;part<5;part++) {
            int vertices=part==0?6:24,rgb=part==0?colors[3]:colors[part-1];
            float[] bounds={5,5,-1,-1};
            for(int v=0;v<vertices;v++) {
                float x=out.get(),h=out.get(),y=out.get();assertEquals(10.5f,h,0);
                bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);
                assertEquals((rgb>>16&255)/255f,out.get(),0);assertEquals((rgb>>8&255)/255f,out.get(),0);assertEquals((rgb&255)/255f,out.get(),0);assertTrue(out.get()>0);
            }
            if(part>0){assertTrue(bounds[0]>previous[0]&&bounds[1]>previous[1]&&bounds[2]<previous[2]&&bounds[3]<previous[3]);previous=bounds;}
            else assertTrue(bounds[0]>.54f&&bounds[2]<3.46f);
        }
        assertFalse(out.hasRemaining());
    }
}
