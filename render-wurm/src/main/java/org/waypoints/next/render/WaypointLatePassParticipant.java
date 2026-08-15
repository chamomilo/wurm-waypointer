package org.waypoints.next.render;

import com.wurmonline.client.renderer.backend.Queue;

/** A managed navigation primitive rendered by the stable late world pass. */
public interface WaypointLatePassParticipant {
    boolean isLatePassAlive();
    void renderInLateWorldPass(Queue queue);
}
