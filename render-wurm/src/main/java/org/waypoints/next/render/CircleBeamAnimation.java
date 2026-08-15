package org.waypoints.next.render;

/** Slow, allocation-free rotation policy for the Circle beam wall. */
public final class CircleBeamAnimation {
    private static final float ROTATION_RADIANS_PER_SECOND = 0.08f;

    private CircleBeamAnimation() {
    }

    public static float rotationRadians(float seconds, float phase) {
        return seconds * ROTATION_RADIANS_PER_SECOND + phase;
    }
}
