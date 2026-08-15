package org.waypoints.next.integration;

import com.wurmonline.shared.util.MulticolorLineSegment;

import java.util.List;

/** Reconstructs the plain Event text carried by Wurm's colored overload. */
final class EventSegmentText {
    private static final int MAX_JOINED_TEXT = 4096;

    private EventSegmentText() { }

    static String join(List<MulticolorLineSegment> segments) {
        if (segments == null || segments.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (MulticolorLineSegment segment : segments) {
            if (segment == null || segment.getText() == null) continue;
            int remaining = MAX_JOINED_TEXT - text.length();
            if (remaining <= 0) break;
            String value = segment.getText();
            text.append(value, 0, Math.min(remaining, value.length()));
        }
        return text.toString();
    }
}
