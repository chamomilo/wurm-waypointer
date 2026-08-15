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

/** Guards marker-click-to-close ordering before any picker reopen/toggle action. */
public final class ClusterPickerToggleContractTest {
    @Test public void openPickerConsumesCompassMarkerClickBeforeTargetAction()
            throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass runtime = pool.get(
                "org.waypoints.next.integration.WurmWaypointerRuntime");
        CodeAttribute code = runtime.getDeclaredMethod(
                "compassWaypointMarkerClicked",
                new CtClass[]{pool.get("java.lang.Object")})
                .getMethodInfo2().getCodeAttribute();
        int close = callPosition(code, "closeIfOpen");
        int open = callPosition(code, "openClusterPicker");
        int toggle = callPosition(code, "selectAndToggle");
        assertTrue(close >= 0);
        assertTrue(open > close);
        assertTrue(toggle > close);
    }

    @Test public void openingClusterPickerDoesNotDetachWaypointManager()
            throws Exception {
        CtClass runtime = ClassPool.getDefault().get(
                "org.waypoints.next.integration.WurmWaypointerRuntime");
        CodeAttribute code = runtime.getDeclaredMethod("openClusterPicker")
                .getMethodInfo2().getCodeAttribute();
        assertEquals(-1, callPosition(code, "detach"));
    }

    private static int callPosition(CodeAttribute code, String methodName)
            throws Exception {
        CodeIterator iterator = code.iterator();
        ConstPool constants = code.getConstPool();
        while (iterator.hasNext()) {
            int position = iterator.next();
            int opcode = iterator.byteAt(position);
            if (opcode != Opcode.INVOKEVIRTUAL && opcode != Opcode.INVOKESPECIAL
                    && opcode != Opcode.INVOKESTATIC) continue;
            int reference = iterator.u16bitAt(position + 1);
            if (methodName.equals(constants.getMethodrefName(reference))) return position;
        }
        return -1;
    }
}
