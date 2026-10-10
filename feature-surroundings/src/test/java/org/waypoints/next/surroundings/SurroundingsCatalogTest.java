package org.waypoints.next.surroundings;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SurroundingsCatalogTest {
    @Test public void everyColumnSortsInBothDirectionsAndNoneRestoresArrivalOrder(){
        for(SurroundingKind kind:SurroundingKind.values()){
            SurroundingsCatalog catalog=new SurroundingsCatalog();
            SurroundingEntry z=SurroundingEntry.builder().kind(kind).wurmId(7).name("Zulu").shortName("zebra").category("zebra").material("wood").rarity(3)
                    .creatureModifier(CreatureModifier.RAGING).hostility(CreatureHostility.NEUTRAL).uniqueCreature(true).deedStatus(DeedStatus.ON_DEED).position(40,0,0).build();
            SurroundingEntry a=SurroundingEntry.builder().kind(kind).wurmId(3).name("Alpha").shortName("apple").category("apple").material("iron").rarity(1)
                    .creatureModifier(CreatureModifier.NONE).hostility(CreatureHostility.FRIEND).uniqueCreature(false).deedStatus(DeedStatus.OFF_DEED).position(8,0,0).build();
            catalog.upsert(z);catalog.upsert(a);catalog.setWaypoint(z.getKey(),true);
            for(SurroundingsQuery.SortColumn column:SurroundingsQuery.SortColumn.values()){
                if(column==SurroundingsQuery.SortColumn.NONE)continue;
                assertEquals("Ascending "+kind+" "+column,3L,catalog.snapshot(SurroundingsQuery.builder().kind(kind).sort(column,true).build(),0,0).getRows().get(0).getEntry().getWurmId());
                assertEquals("Descending "+kind+" "+column,7L,catalog.snapshot(SurroundingsQuery.builder().kind(kind).sort(column,false).build(),0,0).getRows().get(0).getEntry().getWurmId());
            }
            SurroundingsQuery original=SurroundingsQuery.builder().kind(kind).sort(SurroundingsQuery.SortColumn.NONE,true).build();
            assertEquals(7L,catalog.snapshot(original,0,0).getRows().get(0).getEntry().getWurmId());
            catalog.upsert(SurroundingEntry.builder().kind(kind).wurmId(7).name("Zulu").category("zebra").position(1,0,0).build());
            assertEquals("Movement preserves original order",7L,catalog.snapshot(original,0,0).getRows().get(0).getEntry().getWurmId());
        }
    }

    @Test public void markedCountIncludesOnlyTheDisplayedKind(){
        SurroundingsCatalog catalog=new SurroundingsCatalog();
        for(SurroundingKind kind:SurroundingKind.values()){
            SurroundingEntry entry=SurroundingEntry.builder().kind(kind).wurmId(kind.ordinal()+1).name("target").category(kind.name()).build();
            catalog.upsert(entry);catalog.setWaypoint(entry.getKey(),true);
        }
        for(SurroundingKind kind:SurroundingKind.values())assertEquals(1,catalog.snapshot(SurroundingsQuery.builder().kind(kind).build(),0,0).getMarkedCount());
    }
    @Test public void filtersAnimalsByChampionModifier() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(animal(1L, "brown bear", CreatureModifier.NONE, 12.0, 0.0));
        catalog.upsert(animal(2L, "black wolf", CreatureModifier.CHAMPION, 20.0, 0.0));

        SurroundingsSnapshot result = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ANIMAL)
                        .modifier(CreatureModifier.CHAMPION).build(), 0.0, 0.0);

        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getFilteredCount());
        assertEquals(2L, result.getRows().get(0).getEntry().getWurmId());
    }

    @Test public void includesMultipleSelectedTraitsAndExcludesTheRest() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(animal(1L, "brown bear", CreatureModifier.NONE, 12.0, 0.0));
        catalog.upsert(animal(2L, "black wolf", CreatureModifier.CHAMPION, 20.0, 0.0));
        catalog.upsert(animal(3L, "cave bug", CreatureModifier.RAGING, 24.0, 0.0));

        SurroundingsSnapshot result = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ANIMAL)
                        .modifiers(Arrays.asList(CreatureModifier.CHAMPION,
                                CreatureModifier.RAGING)).build(), 0.0, 0.0);

        assertEquals(2, result.getFilteredCount());
        assertEquals(2L, result.getRows().get(0).getEntry().getWurmId());
        assertEquals(3L, result.getRows().get(1).getEntry().getWurmId());
    }

    @Test public void diseasedVisibleNameMatchesDiseasedFilterWhenCodeIsZero() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(animal(31L, "diseased old horse", CreatureModifier.NONE,
                12.0, 0.0));
        catalog.upsert(animal(32L, "old horse", CreatureModifier.NONE,
                16.0, 0.0));

        SurroundingsSnapshot result = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ANIMAL)
                        .modifier(CreatureModifier.DISEASED).build(), 0.0, 0.0);

        assertEquals(1, result.getFilteredCount());
        assertEquals(31L, result.getRows().get(0).getEntry().getWurmId());
        assertEquals(CreatureModifier.DISEASED,
                result.getRows().get(0).getEntry().getCreatureModifier());
    }

    @Test public void classifiesAndFiltersAllObjectsByDeedBounds() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(animal(1L, "brown bear", CreatureModifier.NONE, 42.0, 42.0));
        catalog.upsert(item(2L, "iron lump", SurroundingsClassifier.RESOURCES,
                "iron", 80.0, 80.0));
        catalog.updateDeedAreas(Collections.singletonList(
                new DeedArea(10, 12, 10, 12)), true);

        SurroundingsSnapshot onDeed = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ANIMAL)
                        .deedStatuses(Collections.singleton(DeedStatus.ON_DEED))
                        .build(), 0.0, 0.0);
        SurroundingsSnapshot offDeed = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ITEM)
                        .deedStatuses(Collections.singleton(DeedStatus.OFF_DEED))
                        .build(), 0.0, 0.0);

        assertEquals(1, onDeed.getFilteredCount());
        assertEquals(DeedStatus.ON_DEED,
                onDeed.getRows().get(0).getEntry().getDeedStatus());
        assertEquals(1, offDeed.getFilteredCount());
        assertEquals(DeedStatus.OFF_DEED,
                offDeed.getRows().get(0).getEntry().getDeedStatus());
    }

    @Test public void filtersUniqueIndependentlyFromCreatureModifier() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(SurroundingEntry.builder().kind(SurroundingKind.ANIMAL)
                .wurmId(77L).name("red dragon")
                .modelName("model.creature.dragon.red")
                .category(SurroundingsClassifier.ANIMALS).material("flesh")
                .creatureModifier(CreatureModifier.NONE).uniqueCreature(true)
                .position(40.0d, 40.0d, 2.0d).layer(0).build());
        catalog.upsert(animal(78L, "black wolf", CreatureModifier.CHAMPION,
                44.0d, 40.0d));

        SurroundingsSnapshot unique = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ANIMAL)
                        .uniqueStatuses(Collections.singleton(UniqueStatus.UNIQUE))
                        .build(), 0.0d, 0.0d);

        assertEquals(1, unique.getFilteredCount());
        assertEquals(77L, unique.getRows().get(0).getEntry().getWurmId());
        assertEquals(CreatureModifier.NONE,
                unique.getRows().get(0).getEntry().getCreatureModifier());
    }

    @Test public void filtersItemsByMushroomCategoryAndTextFields() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(item(10L, "brown mushroom", SurroundingsClassifier.MUSHROOMS,
                "vegetarian", 4.0, 0.0));
        catalog.upsert(item(11L, "iron lump", SurroundingsClassifier.RESOURCES,
                "iron", 8.0, 0.0));

        SurroundingsSnapshot mushrooms = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ITEM)
                        .category(SurroundingsClassifier.MUSHROOMS).build(), 0.0, 0.0);
        assertEquals(1, mushrooms.getFilteredCount());
        assertEquals("brown mushroom", mushrooms.getRows().get(0).getEntry().getName());

        SurroundingsSnapshot iron = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ITEM)
                        .text("iron").materialContains("iron").build(), 0.0, 0.0);
        assertEquals(1, iron.getFilteredCount());
        assertEquals(11L, iron.getRows().get(0).getEntry().getWurmId());
    }

    @Test public void shortNameCanHideAllDecoratedCatseyeVariants() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(SurroundingEntry.builder().kind(SurroundingKind.ITEM)
                .wurmId(20L).name("rare marble catseye").shortName("catseye")
                .modelName("model.structure.catseye").description("")
                .category(SurroundingsClassifier.DECORATIONS).material("marble")
                .position(4.0, 0.0, 0.0).layer(0).build());
        catalog.upsert(SurroundingEntry.builder().kind(SurroundingKind.ITEM)
                .wurmId(21L).name("stone catseye").shortName("catseye")
                .modelName("model.structure.catseye").description("")
                .category(SurroundingsClassifier.DECORATIONS).material("stone")
                .position(8.0, 0.0, 0.0).layer(0).build());
        catalog.upsert(item(22L, "stone shard", SurroundingsClassifier.RESOURCES,
                "stone", 12.0, 0.0));

        SurroundingsSnapshot hidden = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ITEM)
                        .shortName("catseye",
                                SurroundingsQuery.ShortNameMode.EXCLUDE)
                        .build(), 0.0, 0.0);
        SurroundingsSnapshot only = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ITEM)
                        .shortName("catseye",
                                SurroundingsQuery.ShortNameMode.INCLUDE)
                        .build(), 0.0, 0.0);

        assertEquals(1, hidden.getFilteredCount());
        assertEquals(22L, hidden.getRows().get(0).getEntry().getWurmId());
        assertEquals(2, only.getFilteredCount());
    }

    @Test public void severalMinusNameFiltersHideAnyMatchingName() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(item(30L, "marble catseye",
                SurroundingsClassifier.DECORATIONS, "marble", 4.0, 0.0));
        catalog.upsert(item(31L, "corpse of a troll",
                SurroundingsClassifier.CORPSES, "flesh", 8.0, 0.0));
        catalog.upsert(item(32L, "strange bone",
                SurroundingsClassifier.RESOURCES, "bone", 12.0, 0.0));

        SurroundingsSnapshot result = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ITEM)
                        .excludedNames(Arrays.asList("catseye", "corpse"))
                        .build(), 0.0, 0.0);

        assertEquals(1, result.getFilteredCount());
        assertEquals("strange bone", result.getRows().get(0).getEntry().getName());
    }

    @Test public void groupWaypointSelectionUsesExactlyTheFilteredRows() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        catalog.upsert(item(10L, "red mushroom", SurroundingsClassifier.MUSHROOMS,
                "vegetarian", 4.0, 0.0));
        catalog.upsert(item(11L, "stone shard", SurroundingsClassifier.RESOURCES,
                "stone", 8.0, 0.0));
        SurroundingsSnapshot filtered = catalog.snapshot(
                SurroundingsQuery.builder().kind(SurroundingKind.ITEM)
                        .category(SurroundingsClassifier.MUSHROOMS).build(), 0.0, 0.0);

        catalog.setWaypoints(Arrays.asList(
                filtered.getRows().get(0).getEntry().getKey()), true);

        assertEquals(1, catalog.selectedEntries().size());
        assertTrue(catalog.isWaypointEnabled(
                new SurroundingKey(SurroundingKind.ITEM, 10L)));
        assertFalse(catalog.isWaypointEnabled(
                new SurroundingKey(SurroundingKind.ITEM, 11L)));
    }

    @Test public void selectedMovingEntryKeepsSelectionAndUpdatesPosition() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        SurroundingEntry first = animal(42L, "champion wolf",
                CreatureModifier.CHAMPION, 4.0, 4.0);
        catalog.upsert(first);
        catalog.setWaypoint(first.getKey(), true);
        catalog.upsert(animal(42L, "champion wolf",
                CreatureModifier.CHAMPION, 40.0, 8.0));

        SurroundingEntry moved = catalog.selectedEntries().get(0);
        assertEquals(40.0, moved.getWorldX(), 0.0);
        assertEquals(first.getFirstSeenAt(), moved.getFirstSeenAt());
    }

    @Test public void unloadedWaypointIsNotRenderedAndCanReturnInTheSameSession() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        SurroundingEntry entry = item(7L, "small chest", SurroundingsClassifier.CHESTS,
                "oakenwood", 12.0, 12.0);
        catalog.upsert(entry);
        catalog.setWaypoint(entry.getKey(), true);
        catalog.remove(entry.getKey());
        assertTrue(catalog.selectedEntries().isEmpty());

        catalog.upsert(entry);
        assertEquals(1, catalog.selectedEntries().size());
        catalog.clearSession();
        assertTrue(catalog.selectedEntries().isEmpty());
    }

    @Test public void rendererClearRetainsSelectionUntilTheSessionActuallyEnds() {
        SurroundingsCatalog catalog = new SurroundingsCatalog();
        SurroundingEntry entry = animal(9L, "horse", CreatureModifier.NONE,
                4.0, 4.0);
        catalog.upsert(entry);
        catalog.setWaypoint(entry.getKey(), true);
        catalog.clearEntries();
        assertTrue(catalog.selectedEntries().isEmpty());

        catalog.upsert(entry);
        assertEquals(1, catalog.selectedEntries().size());
        catalog.clearSession();
        catalog.upsert(entry);
        assertTrue(catalog.selectedEntries().isEmpty());
    }

    private static SurroundingEntry animal(long id, String name,
                                            CreatureModifier modifier,
                                            double x, double y) {
        return SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(id)
                .name(name).modelName("model.creature." + name.replace(' ', '.'))
                .category(SurroundingsClassifier.ANIMALS).material("flesh")
                .creatureModifier(modifier).position(x, y, 2.0).layer(0)
                .firstSeenAt(Instant.parse("2026-08-15T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-15T00:00:01Z")).build();
    }

    private static SurroundingEntry item(long id, String name, String category,
                                         String material, double x, double y) {
        return SurroundingEntry.builder().kind(
                category.equals(SurroundingsClassifier.CHESTS)
                        ? SurroundingKind.CONTAINER : SurroundingKind.ITEM)
                .wurmId(id).name(name).modelName("model.item." + name.replace(' ', '.'))
                .category(category).material(material).position(x, y, 0.0)
                .layer(0).firstSeenAt(Instant.parse("2026-08-15T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-15T00:00:01Z")).build();
    }
}
