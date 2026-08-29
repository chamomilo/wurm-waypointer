package org.waypoints.next.surroundings;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SurroundingsMonitoringViewTest {
    @Test public void overlappingFiltersAreMergedAndSortedByDistance() {
        SurroundingsRow ram = row(SurroundingKind.ANIMAL, 1L, "ram", 48);
        SurroundingsRow unicorn = row(
                SurroundingKind.ANIMAL, 2L, "unicorn", 9);
        SurroundingsRow chest = row(
                SurroundingKind.CONTAINER, 3L, "large chest", 24);

        SurroundingsSnapshot animals = snapshot(ram, unicorn);
        SurroundingsSnapshot overlapping = snapshot(ram, chest);

        assertEquals(Arrays.asList(unicorn, chest, ram),
                SurroundingsMonitoringView.merge(
                        Arrays.asList(animals, overlapping)));
    }

    @Test public void absentFiltersProduceAnEmptyView() {
        assertTrue(SurroundingsMonitoringView.merge(
                Collections.<SurroundingsSnapshot>emptyList()).isEmpty());
    }

    private static SurroundingsSnapshot snapshot(SurroundingsRow... rows) {
        return new SurroundingsSnapshot(1L, rows.length, rows.length, 0,
                Arrays.asList(rows));
    }

    private static SurroundingsRow row(SurroundingKind kind, long id,
                                       String name, int distance) {
        SurroundingEntry entry = SurroundingEntry.builder().kind(kind)
                .wurmId(id).name(name).category(kind.name())
                .position(100.0d, 100.0d, 0.0d).build();
        return new SurroundingsRow(entry, false, distance);
    }
}
