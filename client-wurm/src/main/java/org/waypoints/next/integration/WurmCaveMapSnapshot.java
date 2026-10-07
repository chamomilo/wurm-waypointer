package org.waypoints.next.integration;

import com.wurmonline.client.game.CaveDataBuffer;
import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.structures.StructureTiles;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.Tiles.Tile;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A bounded copy of received cave terrain; unknown cells never become rock. */
public final class WurmCaveMapSnapshot {
    public static final int SIZE = 48;
    private static final int CELL_PIXELS = 6;
    private final int originX;
    private final int originY;
    private final Cell[] cells = new Cell[SIZE * SIZE];
    private long revision = 1;

    private WurmCaveMapSnapshot(int x, int y) { originX = x; originY = y; }

    public static WurmCaveMapSnapshot capture(World world) {
        if (world == null) return null;
        return capture(world.getCaveBuffer(), world.getPlayerCurrentTileX(),
                world.getPlayerCurrentTileY(), world.getWorldSize(), world.getStructureTiles());
    }

    static WurmCaveMapSnapshot capture(CaveDataBuffer cave, int playerX, int playerY,
                                        int worldSize, StructureTiles structures) {
        WurmCaveMapSnapshot result = new WurmCaveMapSnapshot(
                playerX - 24, playerY - 24);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int tx = result.originX + x, ty = result.originY + y;
            Cell cell = null;
            try {
                // Unexcavated rock/ore also has floor=-100. Only strip receipt
                // and the slot's absolute coordinates establish that it is known.
                if (cave != null && tx >= 0 && ty >= 0
                        && tx < worldSize && ty < worldSize
                        && WurmCaveTileCoverage.isReceived(cave, tx, ty)) {
                    Tile type = cave.getTileType(tx, ty);
                    Tile extra = Tiles.getTile(cave.getExtra(tx, ty));
                    Tile temporary = cave.hasTempType(tx, ty)
                            ? cave.getTileTempType(tx, ty) : null;
                    boolean structure = structures != null
                            && structures.covers(tx, ty, (byte) -1);
                    if (type != null) cell = new Cell(type, extra, temporary,
                            cave.getRawFloor(tx, ty), cave.getRawCeiling(tx, ty),
                            cave.getWaterHeight(tx, ty), structure);
                }
            } catch (RuntimeException refreshing) { /* Keep this cell unknown. */ }
            result.cells[x + y * SIZE] = cell;
            result.revision = result.revision * 31 + (cell == null ? 0 : cell.signature());
        }
        return result;
    }

    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
    public long getRevision() { return revision; }

    public float floorHeightMetres(int tileX, int tileY) {
        int x = tileX - originX, y = tileY - originY;
        Cell cell = x < 0 || y < 0 || x >= SIZE || y >= SIZE ? null : cells[x + y * SIZE];
        // Receipt and terrain type distinguish a real -10 m floor from the
        // same raw sentinel in unknown or unexcavated rock.
        return cell == null || cell.type.isSolidCave() ? Float.NaN : cell.floor / 10.0f;
    }

    public List<String> hoverLines(int tileX, int tileY) {
        List<String> lines = new ArrayList<String>();
        int x = tileX - originX, y = tileY - originY;
        Cell cell = x < 0 || y < 0 || x >= SIZE || y >= SIZE
                ? null : cells[x + y * SIZE];
        lines.add("X=" + tileX + " Y=" + tileY + " | Cave: "
                + (cell == null ? "Unknown (not received)" : cell.type.getName()));
        if (cell != null) lines.addAll(cell.details());
        return lines;
    }

    public BufferedImage image() {
        BufferedImage image = new BufferedImage(SIZE * CELL_PIXELS,
                SIZE * CELL_PIXELS, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            Cell cell = cells[x + y * SIZE];
            for (int py = 0; py < CELL_PIXELS; py++)
                for (int px = 0; px < CELL_PIXELS; px++) {
                    image.setRGB(x * CELL_PIXELS + px, y * CELL_PIXELS + py,
                            pixel(cell, px, py));
                }
        }
        return image;
    }

    static int pixel(Cell cell, int x, int y) {
        if (cell == null) return (x + y) % 3 == 0 ? 0x262231 : 0x14121c;
        Tile type = cell.effectiveType();
        int color = color(type);
        if (!type.isSolidCave() && cell.water > cell.floor) color = 0x2475ba;
        // Keep the status marks separate and short, leaving every corner clear:
        // reinforcement uses right/bottom, structures use top/left.
        boolean reinforced = type.isReinforcedCave() || type.isReinforcedFloor();
        boolean horizontalSegment = x > 0 && x < CELL_PIXELS - 1;
        boolean verticalSegment = y > 0 && y < CELL_PIXELS - 1;
        if (reinforced && (x == CELL_PIXELS - 1 && verticalSegment
                || y == CELL_PIXELS - 1 && horizontalSegment)) return 0xdda24a;
        if (cell.structure && (y == 0 && horizontalSegment
                || x == 0 && verticalSegment)) return 0xb690e4;
        if (type == Tile.TILE_CAVE_WALL_ORE_ZINC && x == 3 && y == 3) return 0x243c5d;
        if (type == Tile.TILE_CAVE_WALL_ORE_SILVER && x == 3) return 0xe8f0ff;
        if (type == Tile.TILE_CAVE_WALL_MARBLE && x == y) return 0x8d775a;
        return color;
    }

    static int color(Tile tile) {
        if (tile == Tile.TILE_CAVE_WALL_ORE_IRON) return 0x8b1f24;
        if (tile == Tile.TILE_CAVE_WALL_ORE_COPPER) return 0x3c9a58;
        if (tile == Tile.TILE_CAVE_WALL_ORE_TIN) return 0xb9c6a4;
        if (tile == Tile.TILE_CAVE_WALL_ORE_GOLD) return 0xffd33d;
        if (tile == Tile.TILE_CAVE_WALL_ORE_SILVER) return 0x8995a9;
        if (tile == Tile.TILE_CAVE_WALL_ORE_ZINC) return 0xb4e5ff;
        if (tile == Tile.TILE_CAVE_WALL_ORE_LEAD) return 0x8574b5;
        if (tile == Tile.TILE_CAVE_WALL_ORE_ADAMANTINE) return 0x43dd74;
        if (tile == Tile.TILE_CAVE_WALL_ORE_GLIMMERSTEEL) return 0xffed9b;
        if (tile == Tile.TILE_CAVE_WALL_MARBLE) return 0xf1dfbc;
        if (tile == Tile.TILE_CAVE_WALL_SLATE) return 0x506982;
        if (tile == Tile.TILE_CAVE_WALL_SANDSTONE) return 0xba9854;
        if (tile == Tile.TILE_CAVE_WALL_ROCKSALT) return 0xecb5cf;
        if (tile == Tile.TILE_CAVE_WALL_LAVA) return 0xff4030;
        if (tile == Tile.TILE_CAVE_EXIT) return 0x8bd870;
        if (tile.isSolidCave()) return 0x494347;
        if (tile.isRoad()) return 0x9aa2a1;
        return 0xcac0ac;
    }

    static final class Cell {
        final Tile type, extra, temporary;
        final short floor, ceiling, water;
        final boolean structure;
        Cell(Tile type, Tile extra, Tile temporary, short floor,
             short ceiling, short water, boolean structure) {
            this.type = type == null ? Tile.TILE_CAVE_WALL : type;
            // Cave extras encode a floor/paving type, not ore quality.
            this.extra = !this.type.isSolidCave() && extra != null && (extra.isReinforcedFloor()
                    || extra.isRoad() || extra.isReinforcedCave()) ? extra : null;
            this.temporary = temporary;
            this.floor = floor; this.ceiling = ceiling;
            this.water = water; this.structure = structure;
        }
        Tile effectiveType() { return extra == null ? type : extra; }
        long signature() {
            long hash = type.getIntId();
            hash = hash * 257 + (extra == null ? 0 : extra.getIntId());
            hash = hash * 257 + (temporary == null ? 0 : temporary.getIntId());
            hash = hash * 65537 + floor;
            hash = hash * 65537 + ceiling;
            hash = hash * 65537 + water;
            return hash * 2 + (structure ? 1 : 0);
        }
        List<String> details() {
            List<String> lines = new ArrayList<String>();
            Tile effective = effectiveType();
            if (extra != null && extra != type) lines.add("Floor / paving: " + extra.getName());
            if (temporary != null && temporary != type)
                lines.add("Pending terrain: " + temporary.getName());
            if (effective.isReinforcedCave() || effective.isReinforcedFloor())
                lines.add(effective.isSolidCave() ? "Reinforced wall" : "Reinforced floor");
            if (type.name().contains("PART_")) lines.add("Partially clad wall");
            if (type == Tile.TILE_CAVE_EXIT) lines.add("Cave entrance / exit");
            if (type.isSolidCave() && floor == -100 && ceiling == -100) {
                lines.add("Floor / ceiling: not formed (solid rock)");
            } else {
                lines.add(String.format(Locale.ROOT,
                        "Floor (NW): %.1f m | Ceiling: %.1f m | Height: %.1f m",
                        floor / 10.0d, ceiling / 10.0d, (ceiling - floor) / 10.0d));
            }
            if (!type.isSolidCave() && water > floor)
                lines.add(String.format(Locale.ROOT, "Water depth: %.1f m",
                        (water - floor) / 10.0d));
            if (structure) lines.add("Building / structure on this cave tile");
            return Collections.unmodifiableList(lines);
        }
    }
}
