package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtMethod;
import javassist.bytecode.ConstPool;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Locks Scanner rendering to Wurm's native bounded picked-object pass. */
public final class ScannerOutlineHookContractTest {
    @Test public void scannerHookUsesNativePickStatesAndDedicatedBridge()
            throws Exception {
        CtMethod installer = ClassPool.getDefault().get(
                "org.waypoints.next.WurmWaypointerMod")
                .getDeclaredMethod("hookScannerOutlines");

        assertTrue(hasUtf8(installer,
                "com.wurmonline.client.renderer.WorldRender"));
        assertTrue(hasUtf8(installer, "ScannerOutlineRenderBridge"));
        assertTrue(hasUtf8(installer, "customPickFill"));
        assertTrue(hasUtf8(installer, "customPickOutline"));
        assertTrue(hasUtf8(installer, "customPickFillDepth"));
        assertTrue(hasUtf8(installer, "Primitive.TestFunc.ALWAYS"));
        assertTrue(hasUtf8(installer, "renderPickedItem"));
    }

    private static boolean hasUtf8(CtMethod method, String fragment) {
        ConstPool constants = method.getMethodInfo2().getConstPool();
        for (int index = 1; index < constants.getSize(); index++) {
            if (constants.getTag(index) != ConstPool.CONST_Utf8) continue;
            String value = constants.getUtf8Info(index);
            if (value != null && value.contains(fragment)) return true;
        }
        return false;
    }
}
