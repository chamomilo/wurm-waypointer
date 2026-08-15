package org.waypoints.next.map;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compact broad terrain labels decoded from a published flat map image. */
public final class SurfaceTileIndex {
    private static final String[] LABELS = {
            "", "Water", "Grass", "Tree / bush", "Rock", "Paved road",
            "Steppe", "Sand", "Tundra", "Dirt / packed dirt", "Field",
            "Stone paving", "Peat", "Enchanted grass", "Gravel", "Moss",
            "Clay", "Wooden planks", "Tar", "Enchanted tree / bush",
            "Marsh", "Mycelium", "Lava", "Cliff", "Snow",
            "Infected tree / bush"
    };

    private final int width;
    private final int height;
    private final byte[] labels;

    private SurfaceTileIndex(int width, int height, byte[] labels) {
        this.width = width;
        this.height = height;
        this.labels = labels;
    }

    public static SurfaceTileIndex load(Path path, int expectedWidth,
                                        int expectedHeight) throws IOException {
        if (path == null) throw new IllegalArgumentException("image path is required");
        BufferedImage image;
        try (InputStream input = Files.newInputStream(path)) {
            image = ImageIO.read(input);
        }
        if (image == null) throw new IOException("surface image is not readable");
        if (image.getWidth() != expectedWidth
                || image.getHeight() != expectedHeight) {
            throw new IOException("surface image dimensions " + image.getWidth()
                    + "x" + image.getHeight() + " do not match map "
                    + expectedWidth + "x" + expectedHeight);
        }
        return fromImage(image);
    }

    public String describe(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
            return "";
        }
        return LABELS[labels[tileY * width + tileX] & 0xff];
    }

    static SurfaceTileIndex fromImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] labels = new byte[width * height];
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                labels[offset + x] = classify(row[x] & 0x00ffffff);
            }
        }
        return new SurfaceTileIndex(width, height, labels);
    }

    static String describeRgb(int rgb) {
        return LABELS[classify(rgb & 0x00ffffff) & 0xff];
    }

    private static byte classify(int rgb) {
        switch (rgb) {
            case 0x366503: return 2;  // grass, lawn, reed or kelp
            case 0x293a02: return 3;  // ordinary tree or bush
            case 0x726e6b: return 4;  // rock
            case 0x5c5349: return 5;  // cobblestone and brick road families
            case 0x727543: return 6;  // steppe
            case 0xa0936d: return 7;  // sand
            case 0x76876d: return 8;  // tundra
            case 0x4b3f2f: return 9;  // dirt and packed dirt
            case 0x473c2f: return 10; // field
            case 0x636363: return 11; // slabs and prepared paving
            case 0x362720: return 12; // peat
            case 0x2d5d2b: return 13; // enchanted grass
            case 0x4f4a40: return 14; // gravel
            case 0x6a8e38: return 15; // moss
            case 0x717c76: return 16; // clay
            case 0x726650: return 17; // wooden planks
            case 0x121528: return 18; // tar (before the blue-water heuristic)
            case 0x1a3418: return 19; // enchanted tree or bush
            case 0x2b6548: return 20; // marsh
            case 0x470233: return 21; // mycelium
            case 0xd7331e: return 22; // lava
            case 0x9b9794: return 23; // cliff
            case 0xffffff: return 24; // snow
            case 0xdd0229: return 25; // infected tree or bush
            default:
                int red = rgb >>> 16 & 0xff;
                int green = rgb >>> 8 & 0xff;
                int blue = rgb & 0xff;
                return blue >= red + 18 && blue >= green + 12 ? (byte) 1 : 0;
        }
    }
}
