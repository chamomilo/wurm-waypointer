package org.waypoints.next.integration;

import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.mesh.*;
import org.waypoints.next.render.TileHighlightPatch;
import org.waypoints.next.tiles.TileHighlightSettings;
import org.junit.Test;
import java.util.Properties;
import static org.junit.Assert.*;

public class WurmTileHighlightSamplerTest {
    @Test public void radiusIncludesSevenTilesAlongEachAxisAndRejectsReceivedTilesBeyondIt() {
        NearTerrainDataBuffer near=new NearTerrainDataBuffer();int[][] data=new int[18][18];
        for(int x=0;x<18;x++)for(int y=0;y<18;y++)data[x][y]=Tiles.encode((short)100,Tiles.Tile.TILE_FIELD.id,(byte)(5<<4));
        near.tileStrip((short)92,(short)92,(short)18,(short)18,data,new short[18][18],new byte[18][18]);
        Properties prefs=new Properties();prefs.setProperty(TileHighlightSettings.HARVESTABLE,"true");TileHighlightSettings settings=new TileHighlightSettings(prefs);assertEquals(7,settings.radius());
        int matches=0;for(int x=92;x<=108;x++)for(int y=92;y<=108;y++) {
            TileHighlightPatch tile=WurmTileHighlightSampler.sample(near,100,100,1024,x,y,settings);
            assertEquals(Math.abs(x-100)<=7&&Math.abs(y-100)<=7,tile!=null);if(tile!=null)matches++;
        }
        assertEquals(225,matches);prefs.setProperty(TileHighlightSettings.RADIUS,"1");settings=new TileHighlightSettings(prefs);
        assertNotNull(WurmTileHighlightSampler.sample(near,100,100,1024,101,101,settings));assertNull(WurmTileHighlightSampler.sample(near,100,100,1024,102,100,settings));
    }
    @Test public void terrainUsesSlopeAndDepthInsteadOfMarkingOrdinaryTileTypes() {
        for(Tiles.Tile tile:new Tiles.Tile[]{Tiles.Tile.TILE_GRASS,Tiles.Tile.TILE_CLAY,Tiles.Tile.TILE_MARSH,Tiles.Tile.TILE_ROCK}) {
            assertFalse(WurmTileHighlightSampler.nonRideable(tile,10,14,10,10,0));
            assertTrue(WurmTileHighlightSampler.nonRideable(tile,10,14.1f,10,10,0));
            assertFalse(WurmTileHighlightSampler.nonRideable(tile,0,0,0,0,.7f));
            assertTrue(WurmTileHighlightSampler.nonRideable(tile,0,0,0,0,.8f));
        }
        assertTrue(WurmTileHighlightSampler.nonRideable(Tiles.Tile.TILE_HOLE,10,10,10,10,0));
        assertTrue(WurmTileHighlightSampler.nonRideable(Tiles.Tile.TILE_LAVA,10,10,10,10,0));
    }
    @Test public void sevenNativeFlowersIgnoreGrowthBitsAndUnrelatedTileData() {
        int[] colors={0xffe233,0xff542c,0xb45aff,0xffffff,0x408cff,0xc4df48,0xf0f0e4};
        for(int i=1;i<=7;i++) {
            byte data=GrassData.encodeGrassTileData(GrassData.GrowthStage.TALL,GrassData.FlowerType.fromInt(i));
            assertEquals(colors[i-1],WurmTileHighlightSampler.flowerColor(Tiles.Tile.TILE_GRASS,data));
            assertEquals(-1,WurmTileHighlightSampler.flowerColor(Tiles.Tile.TILE_TREE_BIRCH,data));
            assertEquals(-1,WurmTileHighlightSampler.flowerColor(Tiles.Tile.TILE_FIELD,data));
        }
        assertEquals(-1,WurmTileHighlightSampler.flowerColor(Tiles.Tile.TILE_GRASS,(byte)0));
        assertEquals(-1,WurmTileHighlightSampler.flowerColor(Tiles.Tile.TILE_GRASS,(byte)15));
    }
    @Test public void receivedChangesUnloadEdgesAndWrappedCoordinatesNeverLeaveStaleTiles() {
        NearTerrainDataBuffer near=new NearTerrainDataBuffer();Properties prefs=new Properties();prefs.setProperty(TileHighlightSettings.FLOWERS,"true");prefs.setProperty(TileHighlightSettings.NON_RIDEABLE,"true");
        TileHighlightSettings settings=new TileHighlightSettings(prefs);
        assertNull(WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings));
        int[][] data=new int[3][3];for(int x=0;x<3;x++)for(int y=0;y<3;y++)data[x][y]=Tiles.encode((short)100,Tiles.Tile.TILE_GRASS.id,(byte)5);
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        TileHighlightPatch tile=WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings);assertNotNull(tile);assertEquals(0x408cff,tile.flowerRgb);assertEquals(-1,tile.nonRideableRgb);assertEquals(10,tile.nw,0);
        assertNull(WurmTileHighlightSampler.sample(near,100,100,1024,612,100,settings));
        assertNull(WurmTileHighlightSampler.sample(near,100,100,1024,102,102,settings));
        data[1][0]=Tiles.encode((short)150,Tiles.Tile.TILE_GRASS.id,(byte)5);
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        tile=WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings);
        assertEquals(0xff0000,tile.nonRideableRgb);assertEquals(0x408cff,tile.flowerRgb);assertEquals(2,tile.layerCount());
        prefs.setProperty(TileHighlightSettings.NON_RIDEABLE,"false");
        tile=WurmTileHighlightSampler.sample(near,100,100,1024,100,100,new TileHighlightSettings(prefs));assertEquals(-1,tile.nonRideableRgb);assertEquals(0x408cff,tile.flowerRgb);
        data[1][0]=Tiles.encode((short)100,Tiles.Tile.TILE_GRASS.id,(byte)0);data[0][0]=data[1][0];
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        assertNull(WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings));
    }
    @Test public void harvestUsesRipeFieldStagesAndReceivedFruitFlagWithoutConfusingSpeciesBits() {
        for(Tiles.Tile tile:new Tiles.Tile[]{Tiles.Tile.TILE_FIELD,Tiles.Tile.TILE_FIELD2})for(int age=0;age<8;age++) {
            assertEquals(age==5||age==6,WurmTileHighlightSampler.harvestable(tile,(byte)(age<<4)));
            assertEquals(age==5||age==6,WurmTileHighlightSampler.harvestable(tile,(byte)((age<<4)|128|1)));
        }
        byte fruit=(byte)(FoliageAge.OLD_ONE.encodeAsData()|8);
        assertTrue(WurmTileHighlightSampler.harvestable(Tiles.Tile.TILE_TREE_APPLE,fruit));
        assertTrue(WurmTileHighlightSampler.harvestable(Tiles.Tile.TILE_BUSH_GRAPE,fruit));
        assertFalse(WurmTileHighlightSampler.harvestable(Tiles.Tile.TILE_TREE_APPLE,FoliageAge.OLD_ONE.encodeAsData()));
        assertTrue(WurmTileHighlightSampler.harvestable(Tiles.Tile.TILE_TREE_PINE,fruit)); // Native pine-cone harvest flag.
        assertFalse(WurmTileHighlightSampler.harvestable(Tiles.Tile.TILE_TREE,fruit));
        assertFalse(WurmTileHighlightSampler.harvestable(Tiles.Tile.TILE_GRASS,fruit));
        for(FoliageAge age:FoliageAge.values())assertEquals(age.getAgeId()>3&&age.getAgeId()<14,WurmTileHighlightSampler.harvestable(Tiles.Tile.TILE_TREE_APPLE,(byte)(age.encodeAsData()|8)));
        for(int id=0;id<256;id++){Tiles.Tile tile=Tiles.getTile((byte)id);if(tile!=null&&tile.isTree()&&!tile.isNormal())assertFalse(WurmTileHighlightSampler.harvestable(tile,fruit));}
    }
    @Test public void overagedIncludesShrivelledTreesButNotYoungerAgesOrBushes() {
        for(FoliageAge age:FoliageAge.values()) {
            boolean expected=age==FoliageAge.OVERAGED||age==FoliageAge.SHRIVELLED;
            for(int id=0;id<256;id++){Tiles.Tile tile=Tiles.getTile((byte)id);if(tile!=null&&tile.isTree()) {
                assertEquals(tile+" "+age,expected,WurmTileHighlightSampler.overaged(tile,age.encodeAsData()));
                assertEquals(expected,WurmTileHighlightSampler.overaged(tile,(byte)(age.encodeAsData()|15)));
            }}
            assertFalse(WurmTileHighlightSampler.overaged(Tiles.Tile.TILE_BUSH_GRAPE,age.encodeAsData()));
            assertFalse(WurmTileHighlightSampler.overaged(Tiles.Tile.TILE_FIELD,age.encodeAsData()));
        }
    }
    @Test public void harvestAndTreeAgeLayersRefreshIndependentlyWhenNativeDataChanges() {
        NearTerrainDataBuffer near=new NearTerrainDataBuffer();Properties prefs=new Properties();
        prefs.setProperty(TileHighlightSettings.NON_RIDEABLE,"true");prefs.setProperty(TileHighlightSettings.HARVESTABLE,"true");prefs.setProperty(TileHighlightSettings.OVERAGED,"true");
        prefs.setProperty(TileHighlightSettings.HARVESTABLE_COLOR,"#12AB34");prefs.setProperty(TileHighlightSettings.OVERAGED_COLOR,"#AABBCC");
        TileHighlightSettings settings=new TileHighlightSettings(prefs);
        int[][] data=new int[3][3];for(int x=0;x<3;x++)for(int y=0;y<3;y++)data[x][y]=Tiles.encode((short)100,Tiles.Tile.TILE_GRASS.id,(byte)0);
        data[1][0]=Tiles.encode((short)150,Tiles.Tile.TILE_GRASS.id,(byte)0);
        data[0][0]=Tiles.encode((short)100,Tiles.Tile.TILE_TREE_APPLE.id,(byte)(FoliageAge.OLD_ONE.encodeAsData()|8));
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        TileHighlightPatch tile=WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings);
        assertEquals(2,tile.layerCount());assertEquals(0xff0000,tile.nonRideableRgb);assertEquals(0x12ab34,tile.harvestableRgb);assertEquals(-1,tile.overagedRgb);assertEquals(-1,tile.flowerRgb);
        data[0][0]=Tiles.encode((short)100,Tiles.Tile.TILE_TREE_APPLE.id,(byte)(FoliageAge.OVERAGED.encodeAsData()|8));
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        tile=WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings);
        assertEquals(2,tile.layerCount());assertEquals(-1,tile.harvestableRgb);assertEquals(0xaabbcc,tile.overagedRgb);
        data[1][0]=Tiles.encode((short)100,Tiles.Tile.TILE_GRASS.id,(byte)0);
        data[0][0]=Tiles.encode((short)100,Tiles.Tile.TILE_TREE_APPLE.id,FoliageAge.SHRIVELLED.encodeAsData());
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        tile=WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings);
        assertEquals(1,tile.layerCount());assertEquals(-1,tile.harvestableRgb);assertEquals(0xaabbcc,tile.overagedRgb);
        data[0][0]=Tiles.encode((short)100,Tiles.Tile.TILE_TREE_APPLE.id,FoliageAge.VERY_OLD.encodeAsData());
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        assertNull(WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings));
        data[0][0]=Tiles.encode((short)100,Tiles.Tile.TILE_FIELD.id,(byte)(5<<4));
        near.tileStrip((short)100,(short)100,(short)3,(short)3,data,new short[3][3],new byte[3][3]);
        tile=WurmTileHighlightSampler.sample(near,100,100,1024,100,100,settings);assertEquals(1,tile.layerCount());assertEquals(0x12ab34,tile.harvestableRgb);
    }
}
