package org.waypoints.next.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class WurmSurfaceTileDescriptionTest {
    @Test public void exactWurmTypesRetainTreeSpecies() {
        assertEquals("Pine tree", WurmSurfaceTileDescription.tileName(
                (byte) 101, (byte) 0));
        assertEquals("Grass", WurmSurfaceTileDescription.tileName(
                (byte) 2, (byte) 0));
        assertEquals("Clay", WurmSurfaceTileDescription.tileName(
                (byte) 6, (byte) 0));
    }
}
