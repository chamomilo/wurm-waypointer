package org.waypoints.next.map;

/** One shared wheel-speed factor, preserving the original 1X zoom steps. */
public final class MapZoomFactor {
    private int multiplier = 1;

    public int getMultiplier() { return multiplier; }

    public int cycle() {
        multiplier = multiplier == 4 ? 1 : multiplier * 2;
        return multiplier;
    }

    public double fullMapWheelSteps(int wheelDelta) {
        double base = Math.max(-4.0d, Math.min(4.0d, -wheelDelta / 3.0d));
        return base * multiplier;
    }

    public int miniMapWheelTiles(int wheelDelta, boolean cave) {
        if (wheelDelta == 0) return 0;
        long base = Math.max(1L, Math.abs((long) wheelDelta) / 3) * (cave ? 4 : 8);
        return (int) Math.min(MiniMapState.MAXIMUM_VISIBLE_TILES, base * multiplier);
    }
}
