package org.waypoints.next.integration;

import com.wurmonline.shared.util.MulticolorLineSegment;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class EventSegmentTextTest {
    @Test public void joinsTimestampAndColoredMessageWithoutChangingText() {
        assertEquals("[21:01:13] Reading details from the report, Haven looks "
                        + "like it may have been nearby to the northeast.",
                EventSegmentText.join(Arrays.asList(
                        new MulticolorLineSegment("[21:01:13] ", (byte) 0),
                        new MulticolorLineSegment(
                                "Reading details from the report, Haven looks ",
                                (byte) 4),
                        new MulticolorLineSegment(
                                "like it may have been nearby to the northeast.",
                                (byte) 8))));
    }
}
