package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Locks the live distinction between distant beams and distant pictograms. */
public final class FarWorldPresentationContractTest {
    @Test public void onlyBeamsCollapseToLineWhileSymbolsKeepTheirGeometry()
            throws Exception {
        CtClass beam = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.effects.WaypointBeamEffect");
        CtClass symbol = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.effects.WaypointSymbolEffect");
        CodeAttribute beamRender = beam.getDeclaredMethod("renderWorld")
                .getMethodInfo2().getCodeAttribute();
        CodeAttribute symbolRender = symbol.getDeclaredMethod("renderWorld")
                .getMethodInfo2().getCodeAttribute();

        assertEquals(1, callCount(beamRender, "beamRequiresLineOnlyFallback"));
        assertEquals(0, callCount(symbolRender, "beamRequiresLineOnlyFallback"));
        assertTrue(callCount(symbolRender, "adaptiveRadius") > 0);
    }

    private static int callCount(CodeAttribute code, String methodName)
            throws Exception {
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
