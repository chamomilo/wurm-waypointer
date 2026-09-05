package org.waypoints.api;

/**
 * Optional live position supplied by an integration for objects that are not
 * present in Waypointer's creature/item catalog (for example fences or hedges).
 */
public final class WurmObjectSnapshot {
    private final String name;
    private final double worldX;
    private final double worldY;
    private final double height;
    private final int layer;

    public WurmObjectSnapshot(String name, double worldX, double worldY,
                              double height, int layer) {
        this.name = required(name);
        this.worldX = finiteNonNegative(worldX, "world X");
        this.worldY = finiteNonNegative(worldY, "world Y");
        this.height = finite(height, "height");
        this.layer = layer;
    }

    public String getName() { return name; }
    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public double getHeight() { return height; }
    public int getLayer() { return layer; }

    private static String required(String value) {
        String clean = value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(
                "snapshot name is required");
        return clean.length() <= 160 ? clean : clean.substring(0, 160);
    }

    private static double finite(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return value;
    }

    private static double finiteNonNegative(double value, String label) {
        double result = finite(value, label);
        if (result < 0.0d) throw new IllegalArgumentException(
                label + " must not be negative");
        return result;
    }
}
