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

    @Test public void caveTextureHasNoBakedTileBorders() {
        for (Tile tile : new Tile[] {Tile.TILE_CAVE, Tile.TILE_CAVE_WALL,
                Tile.TILE_CAVE_WALL_ORE_GOLD}) {
            for (int offset = 0; offset < 6; offset++) {
                assertEquals(WurmCaveMapSnapshot.color(tile),
                        WurmCaveMapSnapshot.pixel(cell(tile), offset, 0));
                assertEquals(WurmCaveMapSnapshot.color(tile),
                        WurmCaveMapSnapshot.pixel(cell(tile), 0, offset));
                assertEquals(WurmCaveMapSnapshot.color(tile),
                        WurmCaveMapSnapshot.pixel(cell(tile), offset, 5));
                assertEquals(WurmCaveMapSnapshot.color(tile),
                        WurmCaveMapSnapshot.pixel(cell(tile), 5, offset));
            }
        }
    }

    @Test public void reinforcementAndStructuresUseTwoShortEdgesWithClearCorners() {
        for (Tile tile : new Tile[] {Tile.TILE_CAVE_WALL_REINFORCED, Tile.TILE_CAVE_FLOOR_REINFORCED}) {
            assertCellPattern(cell(tile),
                    "......", ".....O", ".....O", ".....O", ".....O", ".OOOO.");
        }
        WurmCaveMapSnapshot.Cell structure = new WurmCaveMapSnapshot.Cell(Tile.TILE_CAVE,
                null, null, (short) 30, (short) 70, (short) 0, true);
        assertCellPattern(structure,
                ".PPPP.", "P.....", "P.....", "P.....", "P.....", "......");
    }

    @Test public void structureOnReinforcementShowsBothBorderColours() {
        WurmCaveMapSnapshot.Cell both = new WurmCaveMapSnapshot.Cell(Tile.TILE_CAVE,
                Tile.TILE_CAVE_FLOOR_REINFORCED, null,
                (short) 30, (short) 70, (short) 0, true);
        assertCellPattern(both,
                ".PPPP.", "P....O", "P....O", "P....O", "P....O", ".OOOO.");
    }

    private static void assertCellPattern(WurmCaveMapSnapshot.Cell cell, String... rows) {
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
            char mark = rows[y].charAt(x);
            int expected = mark == 'O' ? 0xdda24a : mark == 'P' ? 0xb690e4
                    : WurmCaveMapSnapshot.color(cell.effectiveType());
            assertEquals("Status mark at " + x + "," + y, expected,
                    WurmCaveMapSnapshot.pixel(cell, x, y));
        }
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
        WurmCaveTileCoverage.beforeStrip(buffer, 100, 100, 1, 1);
        WurmCaveTileCoverage.afterStrip(buffer, 100, 100, 1, 1);
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

    @Test public void receivedUnexcavatedOresAndRockRenderWithoutFloorHeights() throws Exception {
        CaveDataBuffer buffer = emptyBuffer();
        setBounds(buffer, 60, 123);
        Tile[] resources = {Tile.TILE_CAVE_WALL_ORE_IRON, Tile.TILE_CAVE_WALL_ORE_COPPER,
                Tile.TILE_CAVE_WALL_ORE_TIN, Tile.TILE_CAVE_WALL_ORE_GOLD,
                Tile.TILE_CAVE_WALL_ORE_SILVER, Tile.TILE_CAVE_WALL_ORE_ZINC,
                Tile.TILE_CAVE_WALL_ORE_LEAD, Tile.TILE_CAVE_WALL_ORE_ADAMANTINE,
                Tile.TILE_CAVE_WALL_ORE_GLIMMERSTEEL, Tile.TILE_CAVE_WALL_MARBLE,
                Tile.TILE_CAVE_WALL_SLATE, Tile.TILE_CAVE_WALL_SANDSTONE,
                Tile.TILE_CAVE_WALL_ROCKSALT, Tile.TILE_CAVE_WALL};
        Field types = field("types");
        for (int i = 0; i < resources.length; i++)
            ((byte[]) types.get(buffer))[buffer.getOffset(90 + i, 100)] = resources[i].id;
        // clear() leaves both floor and ceiling at -100, exactly as in the
        // server's unexcavated vein encoding. Receipt must still reveal the type.
        WurmCaveTileCoverage.beforeStrip(buffer, 76, 76, 48, 48);
        WurmCaveTileCoverage.afterStrip(buffer, 76, 76, 48, 48);
        WurmCaveMapSnapshot snapshot = WurmCaveMapSnapshot.capture(buffer, 100, 100, 1024, null);
        java.awt.image.BufferedImage image = snapshot.image();
        for (int i = 0; i < resources.length; i++) {
            List<String> lines = snapshot.hoverLines(90 + i, 100);
            assertTrue(lines.get(0), lines.get(0).contains(resources[i].getName()));
            assertTrue(lines.toString(), lines.toString().contains("not formed"));
            assertFalse(lines.toString(), lines.toString().contains("Height: 0.0 m"));
            assertEquals(resources[i].name(), WurmCaveMapSnapshot.color(resources[i]),
                    image.getRGB((90 + i - 76) * 6 + 2, (100 - 76) * 6 + 3) & 0xffffff);
        }
        WurmCaveMapSnapshot edge = WurmCaveMapSnapshot.capture(buffer, 84, 100, 1024, null);
        assertTrue(edge.hoverLines(60, 100).get(0).contains("Unknown"));
        WurmCaveTileCoverage.clear(buffer);
        assertTrue(WurmCaveMapSnapshot.capture(buffer, 100, 100, 1024, null)
                .hoverLines(100, 100).get(0).contains("Unknown"));
    }

    @Test public void receivedTunnelAtMinusTenMetresRetainsItsActualGeometry() throws Exception {
        CaveDataBuffer buffer = emptyBuffer();
        setBounds(buffer, 37, 100);
        int offset = buffer.getOffset(100, 100);
        ((byte[]) field("types").get(buffer))[offset] = Tile.TILE_CAVE.id;
        ((short[]) field("ceilings").get(buffer))[offset] = -70;
        WurmCaveTileCoverage.beforeStrip(buffer, 100, 100, 1, 1);
        WurmCaveTileCoverage.afterStrip(buffer, 100, 100, 1, 1);
        String text = WurmCaveMapSnapshot.capture(buffer, 100, 100, 1024, null)
                .hoverLines(100, 100).toString();
        assertFalse(text, text.contains("Unknown"));
        assertTrue(text, text.contains("Floor (NW): -10.0 m"));
        assertTrue(text, text.contains("Height: 3.0 m"));
    }

    @Test public void solidOreIsNotReplacedByFloorExtras() {
        WurmCaveMapSnapshot.Cell ore = new WurmCaveMapSnapshot.Cell(
                Tile.TILE_CAVE_WALL_ORE_GOLD, Tile.TILE_CAVE_FLOOR_REINFORCED, null,
                (short) -100, (short) -100, (short) 0, false);
        assertNull(ore.extra);
        assertEquals(Tile.TILE_CAVE_WALL_ORE_GOLD, ore.effectiveType());
        assertEquals(WurmCaveMapSnapshot.color(Tile.TILE_CAVE_WALL_ORE_GOLD),
                WurmCaveMapSnapshot.pixel(ore, 2, 2));
    }

    @Test public void topographicFloorUsesReceivedFloorsIncludingMinusTenMetres() throws Exception {
        CaveDataBuffer buffer = emptyBuffer();
        setBounds(buffer, 60, 123);
        ((byte[]) field("types").get(buffer))[buffer.getOffset(100, 100)] = Tile.TILE_CAVE.id;
        ((short[]) field("floors").get(buffer))[buffer.getOffset(100, 100)] = -100;
        ((byte[]) field("types").get(buffer))[buffer.getOffset(101, 100)] = Tile.TILE_CAVE_WALL_ORE_IRON.id;
        WurmCaveTileCoverage.beforeStrip(buffer, 100, 100, 2, 1);
        WurmCaveTileCoverage.afterStrip(buffer, 100, 100, 2, 1);
        WurmCaveMapSnapshot snapshot = WurmCaveMapSnapshot.capture(buffer, 100, 100, 1024, null);
        assertEquals(-10, snapshot.floorHeightMetres(100, 100), 0.00001);
        assertTrue(Float.isNaN(snapshot.floorHeightMetres(101, 100)));
        assertTrue(Float.isNaN(snapshot.floorHeightMetres(102, 100)));
        assertTrue(Float.isNaN(snapshot.floorHeightMetres(500, 500)));
    }

    private static Field field(String name) throws Exception {
        Field field = CaveDataBuffer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static CaveDataBuffer emptyBuffer() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Constructor<CaveDataBuffer> constructor = CaveDataBuffer.class.getDeclaredConstructor(World.class);
        constructor.setAccessible(true);
        return constructor.newInstance((World) unsafe.allocateInstance(World.class));
    }

    private static void setBounds(CaveDataBuffer buffer, int minimum, int maximum) throws Exception {
        for (String name : new String[]{"minx", "miny", "maxx", "maxy"})
            field(name).setShort(buffer, (short) (name.startsWith("min") ? minimum : maximum));
    }
}
