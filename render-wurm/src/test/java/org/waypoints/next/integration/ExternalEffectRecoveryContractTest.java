package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Locks hook-independent recovery into the ordinary HUD tick path. */
public final class ExternalEffectRecoveryContractTest {
    @Test public void tickDetectsExternallyDeletedOwnedEffects()
            throws Exception {
        CtMethod tick = ClassPool.getDefault().get(
                "org.waypoints.next.render.StaticNavigationController")
                .getDeclaredMethod("tick");

        assertEquals(1, callCount(tick, "anyResourceMatches"));
        assertEquals(1, callCount(tick, "invalidateAfterExternalClear"));
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
