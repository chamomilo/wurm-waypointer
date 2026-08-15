package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CircleBeamAnimationTest {
    @Test public void wallCompletesOneSlowTurnInAboutSeventyNineSeconds() {
        assertEquals(0.5f, CircleBeamAnimation.rotationRadians(0.0f, 0.5f),
                0.0001f);
        assertEquals(0.5f + (float) (Math.PI * 2.0d),
                CircleBeamAnimation.rotationRadians(
                        (float) (Math.PI * 2.0d / 0.08d), 0.5f),
                0.0001f);
    }
}
