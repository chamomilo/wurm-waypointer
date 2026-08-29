package org.waypoints.next.surroundings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SurroundingsMonitoringFiltersTest {
    @Test public void acceptsMultiplePresetsIncludingTheSameKind() {
        SurroundingsMonitoringFilters filters =
                new SurroundingsMonitoringFilters();

        assertTrue(filters.add(query(SurroundingKind.ANIMAL, "unicorn")));
        assertTrue(filters.add(query(SurroundingKind.ANIMAL, "champion")));
        assertTrue(filters.add(query(SurroundingKind.ITEM, "mushroom")));
        assertEquals(3, filters.size());
    }

    @Test public void duplicatePresetIsNotAddedTwice() {
        SurroundingsMonitoringFilters filters =
                new SurroundingsMonitoringFilters();
        filters.add(query(SurroundingKind.ANIMAL, "unicorn"));

        assertFalse(filters.add(query(SurroundingKind.ANIMAL, "unicorn")));
        assertEquals(1, filters.snapshot().size());

        filters.clear();
        assertTrue(filters.isEmpty());
    }

    @Test public void sortChoiceDoesNotCreateASecondEquivalentPreset() {
        SurroundingsMonitoringFilters filters =
                new SurroundingsMonitoringFilters();
        SurroundingsQuery byDistance = SurroundingsQuery.builder()
                .kind(SurroundingKind.CONTAINER).category("Chests")
                .sort(SurroundingsQuery.SortColumn.DISTANCE, true).build();
        SurroundingsQuery byName = SurroundingsQuery.builder()
                .kind(SurroundingKind.CONTAINER).category("Chests")
                .sort(SurroundingsQuery.SortColumn.NAME, true).build();

        assertTrue(filters.add(byDistance));
        assertFalse(filters.add(byName));
    }

    private static SurroundingsQuery query(SurroundingKind kind, String text) {
        return SurroundingsQuery.builder().kind(kind).text(text).build();
    }
}
