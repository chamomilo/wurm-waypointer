package org.waypoints.next.lootmap;

/** Server-visible TreasureHunting distance buckets. Distance is floored Euclidean tiles. */
public enum LootMapDistanceBand {
    EXACT(0, 0, 0.0d),
    ONE_TO_THREE(1, 3, 1.0d),
    FOUR_TO_FIVE(4, 5, 4.0d),
    SIX_TO_NINE(6, 9, 7.2d),
    TEN_TO_NINETEEN(10, 19, 12.0d),
    TWENTY_TO_FORTY_NINE(20, 49, 24.0d),
    FIFTY_TO_ONE_NINETY_NINE(50, 199, 60.0d),
    TWO_HUNDRED_TO_FOUR_NINETY_NINE(200, 499, 240.0d),
    FIVE_HUNDRED_TO_NINE_NINETY_NINE(500, 999, 500.0d),
    ONE_THOUSAND_TO_NINETEEN_NINETY_NINE(1000, 1999, 1000.0d),
    TWO_THOUSAND_PLUS(2000, Integer.MAX_VALUE, 2000.0d);

    private final int minimum;
    private final int maximum;
    private final double directStep;

    LootMapDistanceBand(int minimum, int maximum, double directStep) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.directStep = directStep;
    }

    public int getMinimum() { return minimum; }
    public int getMaximum() { return maximum; }
    public double getDirectStep() { return directStep; }
    public boolean isFinite() { return maximum != Integer.MAX_VALUE; }
    public boolean isFinalPoint() { return maximum <= 5; }

    /** Human-readable server-reported range; never implies exact distance. */
    public String displayRangeTiles() {
        if (this == EXACT) return "0 tiles";
        if (!isFinite()) return minimum + "+ tiles";
        return minimum + "-" + maximum + " tiles";
    }

    public boolean containsFlooredDistance(int distance) {
        return distance >= minimum && distance <= maximum;
    }

    public static LootMapDistanceBand forDistance(double distance) {
        int floored = Math.max(0, (int) Math.floor(distance));
        for (LootMapDistanceBand band : values()) {
            if (band.containsFlooredDistance(floored)) return band;
        }
        return TWO_THOUSAND_PLUS;
    }
}
