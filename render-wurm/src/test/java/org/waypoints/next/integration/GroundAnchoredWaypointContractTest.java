package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Prevents omitted waypoint Z from reverting to a fixed login-time player Z. */
public final class GroundAnchoredWaypointContractTest {
    @Test public void everyWorldPresentationResolvesGroundAtRenderTime()
            throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass beam = pool.get(
                "com.wurmonline.client.renderer.effects.WaypointBeamEffect");
        CtClass symbol = pool.get(
                "com.wurmonline.client.renderer.effects.WaypointSymbolEffect");
        CtClass label = pool.get(
                "com.wurmonline.client.renderer.gui.WaypointLabelComponent");

        assertEquals(1, callCount(beam.getDeclaredMethod("renderWorld"),
                "resolve"));
        assertEquals(1, callCount(symbol.getDeclaredMethod("renderWorld"),
                "resolve"));
        assertEquals(1, callCount(label.getDeclaredMethod("measureForLayout"),
                "resolve"));
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
