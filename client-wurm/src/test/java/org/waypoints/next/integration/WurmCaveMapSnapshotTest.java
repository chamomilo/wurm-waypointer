package org.waypoints.next.integration;

import com.wurmonline.mesh.Tiles.Tile;
import com.wurmonline.client.game.CaveDataBuffer;
import com.wurmonline.client.game.World;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public final class WurmCaveMapSnapshotTest {
    private WurmCaveMapSnapshot.Cell cell(Tile type) {
        return new WurmCaveMapSnapshot.Cell(type, null, null,
                (short) 30, (short) 70, (short) 0, false);
    }

    @Test public void paleResourcesHaveSeparateColoursAndPatterns() {
        Set<Integer> colours = new HashSet<Integer>();
        Set<String> patterns = new HashSet<String>();
        for (Tile tile : new Tile[]{Tile.TILE_CAVE_WALL_ORE_ZINC,
                Tile.TILE_CAVE_WALL_ORE_SILVER, Tile.TILE_CAVE_WALL_MARBLE}) {
            colours.add(WurmCaveMapSnapshot.color(tile));
            StringBuilder pattern = new StringBuilder();
            for (int y = 1; y < 6; y++) for (int x = 1; x < 6; x++)
                pattern.append(WurmCaveMapSnapshot.pixel(cell(tile), x, y)
                        == WurmCaveMapSnapshot.color(tile) ? '0' : '1');
            patterns.add(pattern.toString());
        }
        assertEquals(3, colours.size());
        assertEquals(3, patterns.size());
        assertNotEquals(WurmCaveMapSnapshot.pixel(null, 2, 2),
                WurmCaveMapSnapshot.pixel(cell(Tile.TILE_CAVE_WALL), 2, 2));
    }

    @Test public void hoverReportsDecodedFloorWaterAndStructureWithoutInventedQuality() {
        WurmCaveMapSnapshot.Cell cell = new WurmCaveMapSnapshot.Cell(Tile.TILE_CAVE,
                Tile.TILE_CAVE_FLOOR_REINFORCED, null,
                (short) -20, (short) 20, (short) 0, true);
        List<String> lines = cell.details();
        String text = lines.toString();
        assertTrue(text.contains("Reinforced floor"));
        assertTrue(text.contains("Floor / paving:"));
        assertTrue(text.contains("Height: 4.0 m"));
        assertTrue(text.contains("Water depth: 2.0 m"));
        assertTrue(text.contains("Building / structure"));
        assertFalse(text.toLowerCase().contains("quality"));
        assertEquals(Tile.TILE_CAVE_FLOOR_REINFORCED, cell.effectiveType());
    }

    @Test public void nonFloorExtraIsNotInterpretedAsQualityOrTerrain() {
        WurmCaveMapSnapshot.Cell cell = new WurmCaveMapSnapshot.Cell(
                Tile.TILE_CAVE_WALL_ORE_IRON, Tile.TILE_GRASS, null,
                (short) 0, (short) 50, (short) 0, false);
        assertNull(cell.extra);
        assertEquals(Tile.TILE_CAVE_WALL_ORE_IRON, cell.effectiveType());
    }

    @Test public void nativeBufferBoundsPreventAliasedOreAndKeepUnknownSeparate() throws Exception {
        Constructor<CaveDataBuffer> constructor = CaveDataBuffer.class.getDeclaredConstructor(World.class);
        constructor.setAccessible(true);
        // The constructor clears collisions through World; a zero-field test
        // world avoids starting the renderer/audio/network subsystems.
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        World world = (World) unsafe.allocateInstance(World.class);
        CaveDataBuffer buffer = constructor.newInstance(world);
        for (String name : new String[]{"minx", "miny", "maxx", "maxy"}) {
            Field field = CaveDataBuffer.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setShort(buffer, (short) (name.startsWith("min") ? 76 : 123));
        }
        Field floors = CaveDataBuffer.class.getDeclaredField("floors");
        Field ceilings = CaveDataBuffer.class.getDeclaredField("ceilings");
        Field types = CaveDataBuffer.class.getDeclaredField("types");
        floors.setAccessible(true); ceilings.setAccessible(true); types.setAccessible(true);
        int offset = buffer.getOffset(100, 100);
        ((short[]) floors.get(buffer))[offset] = 0;
        ((short[]) ceilings.get(buffer))[offset] = 50;
        ((byte[]) types.get(buffer))[offset] = Tile.TILE_CAVE_WALL_ORE_GOLD.id;
        WurmCaveMapSnapshot current = WurmCaveMapSnapshot.capture(buffer, 100, 100, 1024, null);
        assertEquals(76, current.getOriginX());
        assertTrue(current.hoverLines(100, 100).get(0).toLowerCase().contains("gold"));
        assertTrue(current.hoverLines(101, 100).get(0).contains("Unknown"));
        assertTrue(current.hoverLines(124, 100).get(0).contains("Unknown"));
        // Native getters wrap at 64; a far map coordinate must not reveal that gold.
        WurmCaveMapSnapshot far = WurmCaveMapSnapshot.capture(buffer, 164, 164, 1024, null);
        assertTrue(far.hoverLines(164, 164).get(0).contains("Unknown"));
        assertNotEquals(current.getRevision(), far.getRevision());
        assertEquals(288, current.image().getWidth());
    }
}
