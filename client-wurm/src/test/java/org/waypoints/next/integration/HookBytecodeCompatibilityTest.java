package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import org.junit.Test;
import org.waypoints.next.WurmWaypointerMod;

import java.io.File;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.Assert.assertTrue;

/** Compiles every Javassist insertion against the pinned bytecode without launching Wurm. */
public class HookBytecodeCompatibilityTest {
    @Test
    public void javassistInsertionsCompileAgainstPinnedClient() throws Exception {
        ClassPool pool = pinnedPool();

        invokeInstaller("hookSelectedServer", pool);
        invokeInstaller("hookDirectConnect", pool);
        invokeInstaller("hookConnectionLifecycle", pool);
        invokeInstaller("hookWorldMap", pool);
        invokeInstaller("hookWorldMapWheel", pool);
        invokeInstaller("hookCompass", pool);
        invokeInstaller("hookCompassMarker", pool);
        invokeInstaller("hookEffectRenderer", pool);
        invokeInstaller("hookVanillaLandmarks", pool);
        invokeInstaller("hookConsole", pool);
        invokeInstaller("hookInventoryWindows", pool);

        assertTrue(pool.get("com.wurmonline.client.startup.ServerBrowserFX")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.startup.ServerBrowserDirectConnect")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.comm.SimpleServerConnectionClass")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.renderer.gui.CompassComponent")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.renderer.gui.WorldMap")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.renderer.gui.maps.ClusterMap")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.renderer.gui.WurmComponent")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.renderer.effects.EffectRender")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.comm.ServerConnectionListenerClass")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.console.WurmConsole")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.game.inventory."
                + "InventoryMetaWindowManager").toBytecode().length > 0);
    }

    @Test
    public void vanillaEffectsStayLateBoundUntilAllPreInitHooksFinish()
            throws Exception {
        assertNoClassReference(
                "org/waypoints/next/render/StaticNavigationController.class");
        assertNoClassReference(
                "org/waypoints/next/render/StaticNavigationController$1.class");
    }

    @Test
    public void worldMapHooksDoNotMutateAnAlreadyFrozenWurmComponent()
            throws Exception {
        ClassPool pool = pinnedPool();
        pool.get("com.wurmonline.client.renderer.gui.WurmComponent").freeze();

        invokeInstaller("hookWorldMap", pool);
        invokeInstaller("hookWorldMapWheel", pool);

        assertTrue(pool.get("com.wurmonline.client.renderer.gui.WorldMap")
                .toBytecode().length > 0);
        assertTrue(pool.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay")
                .toBytecode().length > 0);
    }

    private static void assertNoClassReference(String resource) throws Exception {
        InputStream stream = HookBytecodeCompatibilityTest.class.getClassLoader()
                .getResourceAsStream(resource);
        if (stream == null) throw new AssertionError("missing class resource " + resource);
        ClassFile classFile;
        try (DataInputStream input = new DataInputStream(stream)) {
            classFile = new ClassFile(input);
        }
        ConstPool constants = classFile.getConstPool();
        for (int index = 1; index < constants.getSize(); index++) {
            if (constants.getTag(index) != ConstPool.CONST_Class) continue;
            String referenced = constants.getClassInfo(index);
            if ("com.wurmonline.client.renderer.effects.LightBeamEffect"
                    .equals(referenced)
                    || "com.wurmonline.client.renderer.effects.RiftSpawnEffect"
                    .equals(referenced)
                    || "com.wurmonline.client.renderer.light.LightSource"
                    .equals(referenced)) {
                throw new AssertionError("vanilla effect was linked before preInit: "
                        + referenced + " from " + resource);
            }
        }
    }

    private static void invokeInstaller(String name, ClassPool pool) throws Exception {
        Method installer = WurmWaypointerMod.class.getDeclaredMethod(name, ClassPool.class);
        installer.setAccessible(true);
        installer.invoke(null, pool);
    }

    private static ClassPool pinnedPool() throws Exception {
        String directory = System.getProperty("wurmClientLibDir");
        ClassPool pool = new ClassPool(false);
        pool.appendSystemPath();
        pool.appendClassPath(new File(directory, "client-patched.jar").getAbsolutePath());
        pool.appendClassPath(new File(directory, "common.jar").getAbsolutePath());
        pool.appendClassPath(new File(directory, "modlauncher.jar").getAbsolutePath());
        return pool;
    }

}
