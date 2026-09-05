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

/** Prevents world colours from depending on a server-provided Rift texture. */
public final class WorldPrimitiveTextureContractTest {
    @Test public void everyCustomWorldPrimitiveBindsItsOwnWhiteTexture()
            throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass beam = pool.get(
                "com.wurmonline.client.renderer.effects.WaypointBeamEffect");
        assertEquals(1, calls(beam.getDeclaredMethod("queuePrimitive"),
                "bindWhite"));
        assertEquals(1, calls(beam.getDeclaredMethod("queueStatePrimer"),
                "bindWhite"));
        assertEquals(1, calls(beam.getDeclaredMethod("queueExtraGeometry"),
                "bindWhite"));

        CtClass symbol = pool.get(
                "com.wurmonline.client.renderer.effects.WaypointSymbolEffect");
        assertEquals(1, calls(symbol.getDeclaredMethod("renderWorld"),
                "bindWhite"));
        assertEquals(1, calls(symbol.getDeclaredMethod("queueStatePrimer"),
                "bindWhite"));

        CtClass route = pool.get(
                "com.wurmonline.client.renderer.effects.GroundNavigationRouteEffect");
        assertEquals(1, calls(route.getDeclaredMethod("queueRoutePrimitive"),
                "bindWhite"));
    }

    @Test public void bindingUsesTheBuiltInWhiteTexture() throws Exception {
        CtMethod bind = ClassPool.getDefault().get(
                "org.waypoints.next.render.WaypointWorldTexture")
                .getDeclaredMethod("bindWhite");
        assertEquals(1, calls(bind, "getWhite"));
        assertEquals(1, calls(bind, "clearTextures"));
    }

    private static int calls(CtMethod method, String methodName)
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
