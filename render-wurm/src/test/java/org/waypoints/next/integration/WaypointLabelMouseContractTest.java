package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Guards the pinned HUD contract that mouse focus must never become null after hit selection. */
public class WaypointLabelMouseContractTest {
    @Test
    public void labelGetComponentAtReturnsItselfInsteadOfNull() throws Exception {
        CtClass label = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.gui.WaypointLabelComponent");
        CodeAttribute code = label.getDeclaredMethod("getComponentAt")
                .getMethodInfo2().getCodeAttribute();
        assertNotNull(code);
        byte[] bytes = code.getCode();
        assertEquals(2, bytes.length);
        assertEquals(Opcode.ALOAD_0, bytes[0] & 0xff);
        assertEquals(Opcode.ARETURN, bytes[1] & 0xff);
    }

    @Test
    public void labelPreparesInitialBoundsBeforeAttachAndNextBoundsAfterPaint()
            throws Exception {
        CtClass label = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.gui.WaypointLabelComponent");

        CodeAttribute attach = label.getDeclaredMethod("attachWithLayout")
                .getMethodInfo2().getCodeAttribute();
        assertNotNull(attach);
        assertTrue(callPosition(attach, "prepareNextFrame", false)
                < callPosition(attach, "attachTo", false));

        CodeAttribute render = label.getDeclaredMethod("renderComponent")
                .getMethodInfo2().getCodeAttribute();
        assertNotNull(render);
        int paint = callPosition(render, "paint", false);
        int finalPrepare = callPosition(render, "prepareNextFrame", true);
        assertTrue(paint >= 0);
        assertTrue(finalPrepare > paint);
    }

    @Test
    public void worldLabelUsesShadowedTextWithoutASolidBackdrop() throws Exception {
        CtClass label = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.gui.WaypointLabelComponent");
        CodeAttribute render = label.getDeclaredMethod("renderComponent")
                .getMethodInfo2().getCodeAttribute();
        assertNotNull(render);
        assertEquals(-1, callPosition(render, "fillRect", false));
        assertEquals(2, callCount(render, "paint"));
    }

    private static int callPosition(CodeAttribute code, String methodName,
                                    boolean last) throws Exception {
        CodeIterator iterator = code.iterator();
        ConstPool constants = code.getConstPool();
        int found = -1;
        while (iterator.hasNext()) {
            int position = iterator.next();
            int opcode = iterator.byteAt(position);
            if (opcode != Opcode.INVOKEVIRTUAL && opcode != Opcode.INVOKESPECIAL
                    && opcode != Opcode.INVOKESTATIC) continue;
            int reference = iterator.u16bitAt(position + 1);
            if (methodName.equals(constants.getMethodrefName(reference))) {
                found = position;
                if (!last) return found;
            }
        }
        return found;
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
