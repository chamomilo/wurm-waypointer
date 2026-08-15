package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RendererStabilizationGateTest {
    @Test
    public void scheduledRefreshFiresOnceAtDeadline() {
        RendererStabilizationGate gate = new RendererStabilizationGate();
        gate.schedule(100L, 20L);
        assertTrue(gate.isPending());
        assertFalse(gate.takeIfDue(119L));
        assertTrue(gate.takeIfDue(120L));
        assertFalse(gate.takeIfDue(121L));
    }

    @Test
    public void laterScheduleReplacesPreviousDeadlineAndCancelClearsIt() {
        RendererStabilizationGate gate = new RendererStabilizationGate();
        gate.schedule(100L, 20L);
        gate.schedule(110L, 30L);
        assertFalse(gate.takeIfDue(120L));
        gate.cancel();
        assertFalse(gate.takeIfDue(200L));
    }
}
