package org.waypoints.next.integration;

import com.wurmonline.client.game.World;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.Tiles.Tile;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javassist.ClassPool;
import javassist.CtClass;
import org.junit.Test;
import org.waypoints.next.WurmWaypointerMod;
import static org.junit.Assert.*;

public final class WurmCaveTileCoverageTest {
    private static void receive(Object buffer, int x, int y, int width, int height) {
        WurmCaveTileCoverage.beforeStrip(buffer, x, y, width, height);
        WurmCaveTileCoverage.afterStrip(buffer, x, y, width, height);
    }

    @Test public void initialStripDoesNotMakeTheUnfilledRingAreaKnown() {
        Object buffer = new Object();
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        receive(buffer, 76, 76, 48, 48);
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 76, 76));
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 123, 123));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 60, 76));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 76, 60));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 124, 100));
    }

    @Test public void unfinishedAndOverwrittenSlotsStayUnknown() {
        Object buffer = new Object();
        receive(buffer, 76, 76, 48, 48);
        WurmCaveTileCoverage.beforeStrip(buffer, 100, 100, 1, 1);
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 99, 100));
        WurmCaveTileCoverage.afterStrip(buffer, 100, 100, 1, 1);
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        receive(buffer, 164, 100, 1, 1);
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 164, 100));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        // Moving bounds back does not restore the overwritten slot for (100,100).
        receive(buffer, 76, 76, 1, 1);
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 164, 100));
    }

    @Test public void movementInBothAxesRejectsDistantAliases() {
        Object buffer = new Object();
        receive(buffer, 76, 76, 48, 48);
        receive(buffer, 124, 76, 1, 48);
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 124, 100));
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        receive(buffer, 100, 164, 1, 1);
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 100, 164));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 164, 164));
    }

    @Test public void clearAndDifferentWorldBuffersNeverShareCoverage() {
        Object first = new Object(), second = new Object();
        receive(first, 76, 76, 48, 48);
        assertFalse(WurmCaveTileCoverage.isReceived(second, 100, 100));
        WurmCaveTileCoverage.clear(first);
        assertFalse(WurmCaveTileCoverage.isReceived(first, 100, 100));
        receive(first, 100, 100, 1, 1);
        assertTrue(WurmCaveTileCoverage.isReceived(first, 100, 100));
        assertFalse(WurmCaveTileCoverage.isReceived(first, 101, 100));
    }

    @Test public void installedHooksTrackNativeStripDecodingAndClearAtRuntime() throws Exception {
        ClassPool pool = new ClassPool(false);
        pool.appendSystemPath();
        String directory = System.getProperty("wurmClientLibDir");
        pool.appendClassPath(new File(directory, "client-patched.jar").getAbsolutePath());
        pool.appendClassPath(new File(directory, "common.jar").getAbsolutePath());
        Method installer = WurmWaypointerMod.class.getDeclaredMethod("hookCaveTileCoverage", ClassPool.class);
        installer.setAccessible(true);
        installer.invoke(null, pool);
        CtClass nativeBuffer = pool.get("com.wurmonline.client.game.CaveDataBuffer");
        // Only collision/structure refreshing requires a running game. Keep the
        // native constructor, decoder, bounds updates and installed hooks intact.
        nativeBuffer.getDeclaredMethod("refreshCollision").setBody("{}");
        nativeBuffer.setName("org.waypoints.next.integration.PatchedCaveBufferProbe");
        Class<?> patched = nativeBuffer.toClass(new ClassLoader(getClass().getClassLoader()) { },
                getClass().getProtectionDomain());
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Constructor<?> constructor = patched.getDeclaredConstructor(World.class);
        constructor.setAccessible(true);
        Object buffer = constructor.newInstance((World) unsafe.allocateInstance(World.class));
        Method strip = patched.getMethod("tileStrip", short.class, short.class,
                short.class, short.class, int[][].class, short[][].class, byte[][].class);
        int[][] terrain = new int[48][48];
        for (int x = 0; x < 48; x++) for (int y = 0; y < 48; y++)
            terrain[x][y] = Tiles.encode((short) -100, Tile.TILE_CAVE_WALL_ORE_GOLD.id, (byte) 0);
        strip.invoke(buffer, (short) 76, (short) 76, (short) 48, (short) 48,
                terrain, new short[48][48], new byte[48][48]);
        assertEquals((short) -100, patched.getMethod("getRawFloor", int.class, int.class)
                .invoke(buffer, 100, 100));
        assertEquals(Tile.TILE_CAVE_WALL_ORE_GOLD,
                patched.getMethod("getTileType", int.class, int.class).invoke(buffer, 100, 100));
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 60, 100));
        strip.invoke(buffer, (short) 164, (short) 100, (short) 1, (short) 1,
                new int[][]{{terrain[0][0]}}, new short[1][1], new byte[1][1]);
        assertTrue(WurmCaveTileCoverage.isReceived(buffer, 164, 100));
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 100, 100));
        patched.getMethod("clear").invoke(buffer);
        assertFalse(WurmCaveTileCoverage.isReceived(buffer, 164, 100));
    }
}
