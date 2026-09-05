package org.waypoints.next.surroundings;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ScannerSessionTest {
    @Test public void activationSeedsWithoutAppearanceSpamThenTracksTransitions() {
        ScannerSession session = new ScannerSession();
        SurroundingEntry horse = animal(1L, "horse", false);
        assertEquals(1, session.activate(ScannerProfiles.find("animals"),
                Collections.singletonList(horse)));
        assertNull(session.observeUpsert(horse));

        SurroundingEntry wolf = animal(2L, "wolf", false);
        ScannerEvent appeared = session.observeUpsert(wolf);
        assertEquals(ScannerEvent.Type.APPEARED, appeared.getType());
        assertEquals(2, session.getMatchCount());

        ScannerEvent disappeared = session.observeRemoved(wolf);
        assertEquals(ScannerEvent.Type.DISAPPEARED, disappeared.getType());
        assertEquals(1, session.getMatchCount());
        assertNull(session.observeRemoved(wolf));
    }

    @Test public void multipleGenericNameExclusionsAreAppliedTogether() {
        ScannerSession session = new ScannerSession();
        session.addExcludedName("catseye");
        session.addExcludedName("corpse");
        assertEquals(1, session.activate(ScannerProfiles.find("animals"),
                Arrays.asList(animal(1L, "wild catseye mimic", false),
                        animal(2L, "corpse eater", false),
                        animal(3L, "horse", false))));
        assertTrue(session.matches(animal(3L, "horse", false)));
        assertFalse(session.matches(animal(2L, "corpse eater", false)));

        assertTrue(session.removeExcludedName("CATSEYE"));
        assertFalse(session.removeExcludedName("missing"));
    }

    @Test public void notificationToggleDoesNotDisableMatchingOrOutlineState() {
        ScannerSession session = new ScannerSession();
        session.activate(ScannerProfiles.find("uniques"),
                Collections.<SurroundingEntry>emptyList());
        session.setNotificationsEnabled(false);

        SurroundingEntry dragon = animal(4L, "red dragon", true);
        assertNull(session.observeUpsert(dragon));
        assertEquals(1, session.getMatchCount());
        assertTrue(session.matches(dragon));
    }

    private static SurroundingEntry animal(long id, String name,
                                            boolean unique) {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        return SurroundingEntry.builder().kind(SurroundingKind.ANIMAL)
                .wurmId(id).name(name).shortName(name)
                .modelName("model.creature." + name.replace(' ', '.'))
                .category(SurroundingsClassifier.ANIMALS)
                .uniqueCreature(unique).position(4.0d, 4.0d, 0.0d)
                .layer(0).firstSeenAt(now).updatedAt(now).build();
    }
}
