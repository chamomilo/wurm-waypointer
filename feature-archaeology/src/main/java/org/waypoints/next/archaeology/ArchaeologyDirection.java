package org.waypoints.next.archaeology;

import java.util.Locale;

/** Eight absolute Wurm map directions. Map Y grows southward. */
public enum ArchaeologyDirection {
    NORTH(0, -1), NORTH_EAST(1, -1), EAST(1, 0), SOUTH_EAST(1, 1),
    SOUTH(0, 1), SOUTH_WEST(-1, 1), WEST(-1, 0), NORTH_WEST(-1, -1);

    private final int deltaX;
    private final int deltaY;

    ArchaeologyDirection(int deltaX, int deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    public int getDeltaX() { return deltaX; }
    public int getDeltaY() { return deltaY; }
    public boolean isDiagonal() { return deltaX != 0 && deltaY != 0; }

    public static ArchaeologyDirection parse(String value) {
        if (value == null) return null;
        String clean = value.trim().toLowerCase(Locale.ENGLISH)
                .replace('-', ' ').replaceAll("\\s+", " ");
        if ("north".equals(clean)) return NORTH;
        if ("north east".equals(clean) || "northeast".equals(clean)) return NORTH_EAST;
        if ("east".equals(clean)) return EAST;
        if ("south east".equals(clean) || "southeast".equals(clean)) return SOUTH_EAST;
        if ("south".equals(clean)) return SOUTH;
        if ("south west".equals(clean) || "southwest".equals(clean)) return SOUTH_WEST;
        if ("west".equals(clean)) return WEST;
        if ("north west".equals(clean) || "northwest".equals(clean)) return NORTH_WEST;
        return null;
    }

    /** Classifies a target delta into the nearest 45-degree absolute sector. */
    public static ArchaeologyDirection fromDelta(double deltaX, double deltaY) {
        if (deltaX == 0.0d && deltaY == 0.0d) return null;
        double clockwiseFromNorth = Math.toDegrees(Math.atan2(deltaX, -deltaY));
        if (clockwiseFromNorth < 0.0d) clockwiseFromNorth += 360.0d;
        int index = ((int) Math.floor((clockwiseFromNorth + 22.5d) / 45.0d)) & 7;
        return values()[index];
    }
}
