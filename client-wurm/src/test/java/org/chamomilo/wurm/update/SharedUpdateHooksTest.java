package org.chamomilo.wurm.update;

import javassist.ClassPool;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.assertTrue;

public class SharedUpdateHooksTest {
    @Test public void nativeHudCanBeInstrumentedAndEmittedWithSharedHooks() throws Exception {
        ClassPool pool = new ClassPool(true);
        String configured = System.getProperty("wurmClientLibDir", "C:/projects/wurm/libs");
        File[] files = new File(configured).listFiles();
        if (files == null) throw new AssertionError("Pinned client libraries missing");
        for (File file : files) if (file.getName().endsWith(".jar")) pool.appendClassPath(file.getAbsolutePath());
        pool.insertClassPath(new javassist.ClassClassPath(SharedUpdateHooks.class));
        SharedUpdateHooks.install(pool);
        SharedUpdateHooks.install(pool);
        assertTrue(pool.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay").toBytecode().length > 1000);
    }
}
