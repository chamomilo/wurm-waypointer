package com.wurmonline.client.renderer.gui;

import org.junit.Test;
import org.waypoints.next.ui.SurroundingsScrollGesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SurroundingsScrollPanelTest {
    @Test public void wheelMovesThreeWholeRowsInEitherDirection() {
        assertEquals(175, SurroundingsScrollGesture.wheelTargetOffset(
                100, 3, 25));
        assertEquals(25, SurroundingsScrollGesture.wheelTargetOffset(
                100, -3, 25));
    }

    @Test public void wheelUsesDirectionInsteadOfPlatformDeltaMagnitude() {
        assertEquals(75, SurroundingsScrollGesture.wheelTargetOffset(
                0, 120, 25));
        assertEquals(75, SurroundingsScrollGesture.wheelTargetOffset(
                0, 1, 25));
    }

    @Test public void consecutiveWheelEventsKeepAdvancing() {
        int offset = 0;
        offset = SurroundingsScrollGesture.wheelTargetOffset(offset, 3, 25);
        offset = SurroundingsScrollGesture.wheelTargetOffset(offset, 3, 25);
        offset = SurroundingsScrollGesture.wheelTargetOffset(offset, 3, 25);
        assertEquals(225, offset);
    }

    @Test public void draggingDownMovesTowardTheTop() {
        assertEquals(60, SurroundingsScrollGesture.dragTargetOffset(
                100, 200, 240));
        assertEquals(0, SurroundingsScrollGesture.dragTargetOffset(
                20, 200, 240));
    }

    @Test public void contentDragNeverCapturesNativeScrollbars() {
        assertTrue(SurroundingsScrollGesture.startsContentDrag(true,
                false, false));
        assertFalse(SurroundingsScrollGesture.startsContentDrag(true,
                true, false));
        assertFalse(SurroundingsScrollGesture.startsContentDrag(true,
                false, true));
        assertFalse(SurroundingsScrollGesture.startsContentDrag(false,
                false, false));
    }

}
