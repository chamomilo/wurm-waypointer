package com.wurmonline.client.renderer.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LootMapStatisticsMemoryTest {
    @Test public void unavailableUnfinishedMapKeepsStatisticsByItemId() {
        LootMapStatisticsMemory memory = new LootMapStatisticsMemory(8);

        assertEquals("Loot Map: QL 67.50 | damage 4.25 | readings 3",
                memory.resolve(111L, 67.5f, 4.25f, 3));
        assertEquals("Loot Map: QL 67.50 | damage 4.25 | readings 4",
                memory.resolve(111L, null, null, 4));
    }

    @Test public void statisticsFromDifferentMapIdsNeverBleedTogether() {
        LootMapStatisticsMemory memory = new LootMapStatisticsMemory(8);
        memory.resolve(111L, 67.5f, 4.25f, 3);
        memory.resolve(222L, 88.0f, 1.5f, 1);

        assertEquals("Loot Map: QL 67.50 | damage 4.25 | readings 3",
                memory.resolve(111L, null, null, 3));
        assertEquals("Loot Map: QL 88.00 | damage 1.50 | readings 2",
                memory.resolve(222L, null, null, 2));
        assertEquals("Loot Map: QL ? | damage ? | readings 1",
                memory.resolve(333L, null, null, 1));
    }
}
