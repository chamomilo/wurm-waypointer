package org.waypoints.next.render;

import com.wurmonline.client.renderer.Frustum;
import com.wurmonline.client.renderer.backend.Queue;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns Waypointer world primitives after Wurm's native effect traversal.
 *
 * Wurm's ordinary effect queue changes behaviour when cave interiors stop
 * participating in frame composition. Native Waypointer Effect callbacks are
 * intentionally empty; this bridge appends every live participant once to the
 * already configured main effect queue without consulting cave-layer flags.
 */
public final class WaypointLatePassBridge {
    private static final Logger LOGGER = Logger.getLogger(
            "WurmWaypointer.LateWorldPass");
    private static final CopyOnWriteArrayList<WaypointLatePassParticipant>
            PARTICIPANTS =
            new CopyOnWriteArrayList<WaypointLatePassParticipant>();
    private static volatile boolean firstFrameLogged;

    private WaypointLatePassBridge() {
    }

    public static void register(WaypointLatePassParticipant participant) {
        if (participant != null) PARTICIPANTS.addIfAbsent(participant);
    }

    public static void unregister(WaypointLatePassParticipant participant) {
        if (participant != null) PARTICIPANTS.remove(participant);
    }

    /** Appends one cave-independent copy to Wurm's configured main queue. */
    public static void render(Queue queue) {
        if (queue == null || queue.getFrustum() != Frustum.mainFrustum) return;
        boolean rendered = false;
        for (WaypointLatePassParticipant participant : PARTICIPANTS) {
            if (participant == null || !participant.isLatePassAlive()) {
                PARTICIPANTS.remove(participant);
                continue;
            }
            try {
                participant.renderInLateWorldPass(queue);
                rendered = true;
            } catch (Throwable failure) {
                LOGGER.log(Level.WARNING,
                        "Waypoint late-world primitive failed open", failure);
            }
        }
        if (rendered && !firstFrameLogged) {
            firstFrameLogged = true;
            LOGGER.info("Waypoint late world pass rendered its first frame");
        }
    }

    static int participantCount() {
        return PARTICIPANTS.size();
    }
}
