package org.waypoints.next.surroundings;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class ScannerProfilesTest {
    @Test public void uniquesMatchesOnlyUniqueCreatures() {
        ScannerProfile profile = ScannerProfiles.find("UNIQUES");

        assertNotNull(profile);
        assertTrue(profile.matches(entry(SurroundingKind.ANIMAL, 1L,
                "red dragon", "model.creature.dragons.red", true)));
        assertFalse(profile.matches(entry(SurroundingKind.ANIMAL, 2L,
                "champion wolf", "model.creature.wolf", false)));
    }

    @Test public void treasureMatchesNameOrCompactModelPath() {
        ScannerProfile profile = ScannerProfiles.find("treasure");

        assertTrue(profile.matches(entry(SurroundingKind.CONTAINER, 3L,
                "large treasure chest", "model.container.chest", false)));
        assertTrue(profile.matches(entry(SurroundingKind.ITEM, 4L,
                "large chest", "model.decoration.treasurechest", false)));
        assertFalse(profile.matches(entry(SurroundingKind.CONTAINER, 5L,
                "large chest", "model.container.chest", false)));
    }

    @Test public void animalsDoesNotMatchGroundItems() {
        ScannerProfile profile = ScannerProfiles.find("animals");

        assertTrue(profile.matches(entry(SurroundingKind.ANIMAL, 6L,
                "horse", "model.creature.horse", false)));
        assertFalse(profile.matches(entry(SurroundingKind.ITEM, 7L,
                "horse shoe", "model.item.horseshoe", false)));
    }

    private static SurroundingEntry entry(SurroundingKind kind, long id,
                                           String name, String model,
                                           boolean unique) {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        return SurroundingEntry.builder().kind(kind).wurmId(id).name(name)
                .shortName(name).modelName(model).category("test")
                .uniqueCreature(unique).position(4.0d, 4.0d, 0.0d)
                .layer(0).firstSeenAt(now).updatedAt(now).build();
    }
}
