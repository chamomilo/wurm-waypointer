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
import static org.junit.Assert.assertFalse;

/** Prevents Loot Map visuals from regressing to a vanilla Rift-owned path. */
public final class LootMapRenderContractTest {
    @Test public void lootMapUsesTerrainTileOutlineWithoutCentralGuide()
            throws Exception {
        CtClass symbol = ClassPool.getDefault().get(
                "com.wurmonline.client.renderer.effects.WaypointSymbolEffect");

        assertEquals(0, callCount(symbol.getDeclaredMethod("render"),
                "renderWorld"));
        assertEquals(1, callCount(symbol.getDeclaredMethod(
                "renderInLateWorldPass"), "renderWorld"));
        assertEquals(0, callCount(symbol.getDeclaredMethod(
                "writeLootMapScroll"), "writeNavigationGuide"));
        assertEquals(1, callCount(symbol.getDeclaredMethod(
                "writeLootMapScroll"), "writeLootMapGroundOutlineOrHidden"));
        assertEquals(1, callCount(symbol.getDeclaredMethod(
                "writeLootMapGroundOutlineOrHidden"),
                "writeLootMapGroundOutline"));
        assertEquals(1, callCount(symbol.getDeclaredMethod(
                "writeLootMapGroundOutlineOrHidden"),
                "writeHiddenLootMapGroundOutline"));
        assertFalse(referencesClass(symbol,
                "com.wurmonline.client.renderer.effects.RiftSpawnEffect"));
    }

    private static boolean referencesClass(CtClass type, String className) {
        ConstPool constants = type.getClassFile2().getConstPool();
        for (int index = 1; index < constants.getSize(); index++) {
            if (constants.getTag(index) == ConstPool.CONST_Class
                    && className.equals(constants.getClassInfo(index))) return true;
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
