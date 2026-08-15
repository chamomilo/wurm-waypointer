package org.waypoints.next.integration;

import com.wurmonline.client.renderer.CreatureData;
import org.junit.Test;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsClassifier;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class SurroundingsRenderableAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Test public void mobileWagonIsProjectedAsVehicleContainer() throws Exception {
        SurroundingEntry entry = project(
                701L, "large wagon", "model.vehicle.wagon.large", true);

        assertEquals(SurroundingKind.CONTAINER, entry.getKind());
        assertEquals(SurroundingsClassifier.VEHICLES, entry.getCategory());
    }

    @Test public void hitchingPostIsProjectedAsItemInsteadOfAnimal() throws Exception {
        SurroundingEntry entry = project(702L, "hitching post",
                "model.structure.hitching.post", true);

        assertEquals(SurroundingKind.ITEM, entry.getKind());
        assertEquals(SurroundingsClassifier.OTHER_ITEMS, entry.getCategory());
    }

    @Test public void ordinaryCreatureRemainsAnAnimal() throws Exception {
        SurroundingEntry entry = project(703L, "old horse",
                "model.creature.quadraped.horse", false);

        assertEquals(SurroundingKind.ANIMAL, entry.getKind());
        assertEquals(SurroundingsClassifier.ANIMALS, entry.getCategory());
    }

    private static SurroundingEntry project(long id, String name, String model,
                                            boolean item) throws Exception {
        CreatureData data = new CreatureData(id, model, name, (byte) 0,
                128.0f, 256.0f, 4.0f, 0.0f, 0, false,
                (byte) 0, 0L, (byte) 0);
        return SurroundingsRenderableAdapter.projectCreatureData(
                data, item, id, name, 0, 128.0d, 256.0d, 4.0d, NOW);
    }
}
