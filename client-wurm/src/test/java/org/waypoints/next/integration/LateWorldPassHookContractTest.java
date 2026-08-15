package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Locks the client adapter to the pinned late-world hook boundary. */
public final class LateWorldPassHookContractTest {
    @Test public void installerAddsOneUnconditionalPassAndClearRecovery()
            throws Exception {
        CtMethod installer = ClassPool.getDefault().get(
                "org.waypoints.next.WurmWaypointerMod")
                .getDeclaredMethod("hookEffectRenderer");

        assertEquals(2, callCount(installer, "getMethod"));
        assertEquals(2, callCount(installer, "insertAfter"));
        assertTrue(hasUtf8(installer,
                "com.wurmonline.client.renderer.effects.EffectRender"));
        assertTrue(hasUtf8(installer,
                "WaypointLatePassBridge.render($1)"));
        assertFalse(hasUtf8(installer, "WorldRender"));
        assertFalse(hasUtf8(installer, "queuePick"));
        assertFalse(hasUtf8(installer, "pipelineWorldForward"));
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

    private static int callCount(CtMethod method, String methodName)
            throws Exception {
        CodeAttribute code = method.getMethodInfo2().getCodeAttribute();
        CodeIterator iterator = code.iterator();
        ConstPool constants = code.getConstPool();
        int count = 0;
        while (iterator.hasNext()) {
            int position = iterator.next();
            int opcode = iterator.byteAt(position);
            if (opcode != Opcode.INVOKEVIRTUAL && opcode != Opcode.INVOKESPECIAL
                    && opcode != Opcode.INVOKESTATIC) continue;
            int reference = iterator.u16bitAt(position + 1);
            if (methodName.equals(constants.getMethodrefName(reference))) count++;
        }
        return count;
    }
}
