package org.waypoints.next.render;

import com.wurmonline.client.renderer.backend.Queue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class WaypointLatePassBridgeTest {
    @Test public void registrationIsUniqueAndRecoverable() {
        int before = WaypointLatePassBridge.participantCount();
        WaypointLatePassParticipant participant =
                new WaypointLatePassParticipant() {
                    @Override public boolean isLatePassAlive() { return true; }
                    @Override public void renderInLateWorldPass(Queue queue) { }
                };

        try {
            WaypointLatePassBridge.register(participant);
            WaypointLatePassBridge.register(participant);
            assertEquals(before + 1,
                    WaypointLatePassBridge.participantCount());
        } finally {
            WaypointLatePassBridge.unregister(participant);
        }
        assertEquals(before, WaypointLatePassBridge.participantCount());
    }
}
