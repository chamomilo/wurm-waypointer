package org.waypoints.next.integration;

import javassist.*;
import org.junit.Test;
import com.wurmonline.client.game.*;
import com.wurmonline.mesh.Tiles;
import org.waypoints.next.tiles.TileHighlightSettings;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Received terrain changes use the actual runtime with a resource-counting overlay. */
public final class TileHighlightRuntimeLifecycleTest {
    @Test public void movementAcrossFlatTerrainDoesNotDestroyAndRecreateTheOverlay()throws Throwable {
        ClassPool pool=new ClassPool(true);String probe=Probe.class.getName();
        CtClass world=pool.get("com.wurmonline.client.game.World");world.getClassInitializer().setBody("{}");
        world.getDeclaredMethod("getPlayerLayer").setBody("{ return 0; }");world.getDeclaredMethod("getWorldSize").setBody("{ return 1024; }");
        world.getDeclaredMethod("getPlayerCurrentTileX").setBody("{ return "+probe+".x; }");world.getDeclaredMethod("getPlayerCurrentTileY").setBody("{ return 100; }");
        world.getDeclaredMethod("getNearTerrainBuffer").setBody("{ return "+probe+".near; }");
        CtClass overlay=pool.get("com.wurmonline.client.renderer.effects.TileHighlightOverlay");
        overlay.getClassInitializer().setBody("{}");for(CtConstructor ctor:overlay.getDeclaredConstructors())ctor.setBody("{ "+probe+".created++; }");
        overlay.getDeclaredMethod("update").setBody("{ "+probe+".matches=$1.size(); }");overlay.getDeclaredMethod("close").setBody("{ "+probe+".closed++; }");
        Loader loader=new Loader(pool);loader.delegateLoadingOf("org.junit.");loader.run(probe,new String[0]);
    }
    public static final class Probe {
        public static int created,closed,matches,x=100;
        public static NearTerrainDataBuffer near=new NearTerrainDataBuffer();
        public static void main(String[] ignored)throws Exception {
            Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);World world=(World)unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),World.class);
            TileHighlightRuntime runtime=new TileHighlightRuntime();Properties p=new Properties();p.setProperty(TileHighlightSettings.NON_RIDEABLE,"true");runtime.configure(p);
            int[][] data=new int[18][18];for(int i=0;i<18;i++)for(int j=0;j<18;j++)data[i][j]=Tiles.encode((short)100,Tiles.Tile.TILE_GRASS.id,(byte)0);
            received(data);runtime.tick(world,0);assertEquals(0,created);
            data[8][8]=Tiles.encode((short)200,Tiles.Tile.TILE_GRASS.id,(byte)0);received(data);runtime.tick(world,500);assertEquals(1,created);assertTrue(matches>0);
            data[8][8]=Tiles.encode((short)100,Tiles.Tile.TILE_GRASS.id,(byte)0);received(data);runtime.tick(world,1000);assertEquals(1,created);assertEquals(0,closed);assertEquals(0,matches);
            data[8][8]=Tiles.encode((short)200,Tiles.Tile.TILE_GRASS.id,(byte)0);received(data);runtime.tick(world,1500);assertEquals(1,created);assertEquals(0,closed);assertTrue(matches>0);
            x=110;runtime.tick(world,1501);assertEquals(1,created);assertEquals(0,closed);assertEquals(0,matches);
            x=100;runtime.tick(world,1502);assertEquals(1,created);assertTrue(matches>0);
            p.setProperty(TileHighlightSettings.NON_RIDEABLE,"false");runtime.configure(p);assertEquals(1,closed);runtime.tick(world,2000);assertEquals(1,created);
        }
        private static void received(int[][] data){near.tileStrip((short)92,(short)92,(short)18,(short)18,data,new short[18][18],new byte[18][18]);}
    }
}
