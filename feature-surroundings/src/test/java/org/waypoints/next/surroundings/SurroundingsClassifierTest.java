package org.waypoints.next.surroundings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SurroundingsClassifierTest {
    @Test public void recognizesContainerModelsWithoutTreatingOrdinaryItemsAsContainers() {
        assertTrue(SurroundingsClassifier.isContainer(
                "large chest", "model.container.chest.large.oak"));
        assertTrue(SurroundingsClassifier.isContainer(
                "Knarr, Freedom", "model.vehicle.boat.knarr"));
        assertFalse(SurroundingsClassifier.isContainer(
                "brown mushroom", "model.food.mushroom.brown"));
        assertFalse(SurroundingsClassifier.isContainer(
                "hitching post", "model.structure.hitching.post"));
    }

    @Test public void assignsUsefulContainerCategories() {
        assertEquals(SurroundingsClassifier.BULK_STORAGE,
                SurroundingsClassifier.containerCategory(
                        "food storage bin", "model.container.bin.food"));
        assertEquals(SurroundingsClassifier.VEHICLES,
                SurroundingsClassifier.containerCategory(
                        "large cart", "model.vehicle.cart.large"));
        assertEquals(SurroundingsClassifier.SHIPS,
                SurroundingsClassifier.containerCategory(
                        "caravel", "model.vehicle.boat.caravel"));
    }

    @Test public void exposesMushroomsAsAFilterableItemCategory() {
        assertEquals(SurroundingsClassifier.MUSHROOMS,
                SurroundingsClassifier.itemCategory(
                        "green mushroom", "model.food.mushroom.green"));
    }

    @Test public void mapsChampionFromThePinnedClientConstant() {
        assertEquals(CreatureModifier.CHAMPION,
                CreatureModifier.fromWurmCode(99));
        assertEquals(CreatureModifier.UNKNOWN,
                CreatureModifier.fromWurmCode(127));
    }

    @Test public void diseasedTraitUsesCodeAndVisibleNameFallback() {
        assertEquals(CreatureModifier.DISEASED,
                CreatureModifier.fromWurmCode(11));
        assertEquals(CreatureModifier.DISEASED,
                CreatureModifier.fromWurmData(0, "diseased old horse"));
        assertEquals(CreatureModifier.NONE,
                CreatureModifier.fromWurmData(0, "old horse"));
    }

    @Test public void recognizesVanillaUniqueTemplatesSeparatelyFromTraits() {
        assertTrue(SurroundingsClassifier.isUniqueCreature(
                "The venerable red dragon", "model.creature.dragon.red"));
        assertTrue(SurroundingsClassifier.isUniqueCreature(
                "Kyklops", "model.creature.humanoid.kyklops"));
        assertTrue(SurroundingsClassifier.isUniqueCreature(
                "Goblin leader", "model.creature.humanoid.goblin.leader"));
        assertFalse(SurroundingsClassifier.isUniqueCreature(
                "champion black wolf", "model.creature.quadraped.wolf.black"));
    }
}
