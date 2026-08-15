package org.waypoints.next.lootmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Planner output plus compact diagnostics needed by per-hunt training logs. */
public final class LootMapDecision {
    public enum Mode { DIRECT, BALANCED, READ_FIRST, FINAL_POINT, CONFLICT }

    private final double waypointX;
    private final double waypointY;
    private final double plannedWaypointX;
    private final double plannedWaypointY;
    private final Mode mode;
    private final int feasibleTileCount;
    private final int posteriorSampleCount;
    private final double probabilityAtLeast100;
    private final double posteriorQ75Distance;
    private final double informationScore;
    private final double directInformationScore;
    private final double walkTiles;
    private final List<Alternative> alternatives;

    LootMapDecision(double waypointX, double waypointY, Mode mode,
                    int feasibleTileCount, int posteriorSampleCount,
                    double probabilityAtLeast100, double posteriorQ75Distance,
                    double informationScore,
                    double directInformationScore, double walkTiles,
                    List<Alternative> alternatives) {
        this(waypointX, waypointY, waypointX, waypointY, mode,
                feasibleTileCount, posteriorSampleCount,
                probabilityAtLeast100, posteriorQ75Distance,
                informationScore, directInformationScore, walkTiles,
                alternatives);
    }

    private LootMapDecision(double waypointX, double waypointY,
                    double plannedWaypointX, double plannedWaypointY, Mode mode,
                    int feasibleTileCount, int posteriorSampleCount,
                    double probabilityAtLeast100, double posteriorQ75Distance,
                    double informationScore,
                    double directInformationScore, double walkTiles,
                    List<Alternative> alternatives) {
        this.waypointX = waypointX;
        this.waypointY = waypointY;
        this.plannedWaypointX = plannedWaypointX;
        this.plannedWaypointY = plannedWaypointY;
        this.mode = mode;
        this.feasibleTileCount = feasibleTileCount;
        this.posteriorSampleCount = posteriorSampleCount;
        this.probabilityAtLeast100 = probabilityAtLeast100;
        this.posteriorQ75Distance = posteriorQ75Distance;
        this.informationScore = informationScore;
        this.directInformationScore = directInformationScore;
        this.walkTiles = walkTiles;
        this.alternatives = Collections.unmodifiableList(
                new ArrayList<Alternative>(alternatives));
    }

    public double getWaypointX() { return waypointX; }
    public double getWaypointY() { return waypointY; }
    public double getPlannedWaypointX() { return plannedWaypointX; }
    public double getPlannedWaypointY() { return plannedWaypointY; }
    public boolean isLandAdjusted() {
        return Double.compare(waypointX, plannedWaypointX) != 0
                || Double.compare(waypointY, plannedWaypointY) != 0;
    }
    public double getLandAdjustmentTiles() {
        return Math.hypot(waypointX - plannedWaypointX,
                waypointY - plannedWaypointY);
    }
    public Mode getMode() { return mode; }
    public int getFeasibleTileCount() { return feasibleTileCount; }
    public int getPosteriorSampleCount() { return posteriorSampleCount; }
    public double getProbabilityAtLeast100() { return probabilityAtLeast100; }
    public double getPosteriorQ75Distance() { return posteriorQ75Distance; }
    public double getInformationScore() { return informationScore; }
    public double getDirectInformationScore() { return directInformationScore; }
    public double getWalkTiles() { return walkTiles; }
    public List<Alternative> getAlternatives() { return alternatives; }

    LootMapDecision relocatedTo(double x, double y, double adjustedWalkTiles) {
        if (Double.compare(x, waypointX) == 0
                && Double.compare(y, waypointY) == 0) return this;
        return new LootMapDecision(x, y, plannedWaypointX, plannedWaypointY,
                mode, feasibleTileCount, posteriorSampleCount,
                probabilityAtLeast100, posteriorQ75Distance,
                informationScore, directInformationScore, adjustedWalkTiles,
                alternatives);
    }

    public static final class Alternative {
        private final double x;
        private final double y;
        private final double informationScore;
        private final double walkTiles;
        private final boolean direct;

        Alternative(double x, double y, double informationScore,
                    double walkTiles, boolean direct) {
            this.x = x;
            this.y = y;
            this.informationScore = informationScore;
            this.walkTiles = walkTiles;
            this.direct = direct;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getInformationScore() { return informationScore; }
        public double getWalkTiles() { return walkTiles; }
        public boolean isDirect() { return direct; }
    }
}
