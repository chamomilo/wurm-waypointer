package org.waypoints.next.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WurmSurfaceTileDescriptionTest {
    @Test
    public void onlyNearbyCoordinatesMayUseTheWrappedLiveBuffer() {
        assertTrue(WurmSurfaceTileDescription.withinLiveRange(
                3030, 1076, 3158, 1204));
        assertFalse(WurmSurfaceTileDescription.withinLiveRange(
                3030, 1076, 3401, 1981));
        assertFalse(WurmSurfaceTileDescription.withinLiveRange(
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0));
    }

    @Test public void exactWurmTypesRetainTreeSpecies() {
        assertTrue(WurmSurfaceTileDescription.tileName(
                (byte) 101, (byte) 0).startsWith("Pine tree | Age: "));
        assertEquals("Grass", WurmSurfaceTileDescription.tileName(
                (byte) 2, (byte) 0));
        assertEquals("Clay", WurmSurfaceTileDescription.tileName(
                (byte) 6, (byte) 0));
    }

    @Test public void cropsExposeSpeciesAndOnlyRipeStagesAreHarvestable() {
        for (int age = 0; age < 8; age++) {
            String description = WurmSurfaceTileDescription.tileName(
                    com.wurmonline.mesh.Tiles.Tile.TILE_FIELD.id, (byte) (age << 4));
            assertTrue(description.contains("Crop: Barley"));
            assertTrue(description.contains("Age: "));
            assertEquals(age == 5 || age == 6, description.contains("Harvestable"));
        }
        assertTrue(WurmSurfaceTileDescription.tileName(
                com.wurmonline.mesh.Tiles.Tile.TILE_FIELD2.id, (byte) 0x50)
                .contains("Crop: Tomatoes"));
    }

    @Test public void fruitFlagControlsHarvestableForFruitTrees() {
        byte type = com.wurmonline.mesh.Tiles.Tile.TILE_TREE_APPLE.id;
        assertTrue(WurmSurfaceTileDescription.tileName(type, (byte) 0xC8).contains("Harvestable"));
        assertFalse(WurmSurfaceTileDescription.tileName(type, (byte) 0xC0).contains("Harvestable"));
    }
}
