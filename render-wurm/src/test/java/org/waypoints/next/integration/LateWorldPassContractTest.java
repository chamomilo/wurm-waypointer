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
import static org.junit.Assert.assertNotNull;

/** Locks every custom world primitive to one cave-independent late queue. */
public final class LateWorldPassContractTest {
    @Test public void beamAndSymbolHaveNoNativeEffectQueueDraw()
            throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass beam = pool.get(
                "com.wurmonline.client.renderer.effects.WaypointBeamEffect");
        CtClass symbol = pool.get(
                "com.wurmonline.client.renderer.effects.WaypointSymbolEffect");

        assertEquals(0, callCount(beam.getDeclaredMethod("render"),
                "renderWorld"));
        assertEquals(0, callCount(symbol.getDeclaredMethod("render"),
                "renderWorld"));
        assertEquals(1, callCount(beam.getDeclaredMethod(
                "renderInLateWorldPass"), "renderWorld"));
        assertEquals(1, callCount(symbol.getDeclaredMethod(
                "renderInLateWorldPass"), "renderWorld"));
    }

    @Test public void groundRouteHasTheSameSingleLateOwner()
            throws Exception {
        CtClass route = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.effects.GroundNavigationRouteEffect");

        assertEquals(0, callCount(route.getDeclaredMethod("render"),
                "renderRoute"));
        assertEquals(1, callCount(route.getDeclaredMethod(
                "renderInLateWorldPass"), "renderRoute"));
    }

    @Test public void pinnedEffectRendererExposesTheConfiguredQueueBoundary()
            throws Exception {
        CtMethod boundary = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.effects.EffectRender")
                .getDeclaredMethod("render");
        assertNotNull(boundary);
        assertEquals("(Lcom/wurmonline/client/renderer/backend/Queue;)V",
                boundary.getSignature());
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
