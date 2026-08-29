package org.waypoints.next.integration;

import com.wurmonline.shared.constants.PlayerAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WurmPlayerActionNameTest {
    @Test public void builtInOpenFallsBackFromMissingNameToBind() {
        assertNull(PlayerAction.OPEN.getName());
        assertEquals("OPEN", WurmPlayerActionName.resolve(PlayerAction.OPEN));
    }

    @Test public void serverSuppliedActionKeepsItsDisplayName() {
        PlayerAction action = new PlayerAction((short) -32000, 0,
                "Read map", false);

        assertEquals("Read map", WurmPlayerActionName.resolve(action));
    }
}
