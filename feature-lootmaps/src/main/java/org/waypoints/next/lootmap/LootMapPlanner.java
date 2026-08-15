package org.waypoints.next.lootmap;

import org.waypoints.next.navigation.NavigationMath;
import org.waypoints.next.source.MapBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Observation-only treasure planner. Objective ordering is lexicographic:
 * expected remaining candidates first, walk distance second.
 */
public final class LootMapPlanner {
    public static final String ALGORITHM_VERSION = "loot-observable-grid-v2-dry-land";
    private static final int MAX_SAMPLE = 4096;
    private static final int MIN_ACTIVE_SAMPLE = 32;
    private static final double READ_FIRST_SLACK = 0.005d;
    private static final double BALANCED_SLACK = 0.03d;
    private static final double BALANCED_MOVE_CAP = 1.25d;

    private LootMapDecision.Mode lockedMode;

    public synchronized LootMapDecision plan(List<LootMapObservation> observations,
                                             MapBounds bounds) {
        if (observations == null || observations.isEmpty()) {
            throw new IllegalArgumentException("at least one observation is required");
        }
        if (bounds == null) throw new IllegalArgumentException("map bounds are required");
        LootMapObservation latest = observations.get(observations.size() - 1);
        Point direct = direct(latest, bounds);
        if (latest.getBand().isFinalPoint()) {
            return simple(latest, direct, LootMapDecision.Mode.FINAL_POINT);
        }
        if (!latest.getBand().isFinite()
                || latest.getBand().getMaximum() > 499) {
            return simple(latest, direct, LootMapDecision.Mode.DIRECT);
        }

        Belief belief = buildBelief(observations, bounds);
        if (belief.feasibleCount == 0 || belief.sample.size() < MIN_ACTIVE_SAMPLE) {
            return new LootMapDecision(direct.x, direct.y,
                    belief.feasibleCount == 0 ? LootMapDecision.Mode.CONFLICT
                            : LootMapDecision.Mode.DIRECT,
                    belief.feasibleCount, belief.sample.size(), Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, distance(latest, direct),
                    Collections.<LootMapDecision.Alternative>emptyList());
        }

        double p100 = probabilityAtLeast100(latest, belief.sample);
        double q75 = distanceQuantile(latest, belief.sample, 0.75d);
        LootMapDecision.Mode mode = lockedMode;
        if (mode == null) {
            mode = initialMode(latest, q75);
            if (mode == LootMapDecision.Mode.READ_FIRST
                    || mode == LootMapDecision.Mode.BALANCED) {
                lockedMode = mode;
            }
        }
        if (mode == LootMapDecision.Mode.DIRECT) {
            return new LootMapDecision(direct.x, direct.y, mode,
                    belief.feasibleCount, belief.sample.size(), p100,
                    q75, Double.NaN, Double.NaN, distance(latest, direct),
                    Collections.<LootMapDecision.Alternative>emptyList());
        }
        return choose(latest, direct, belief, mode, p100, q75, bounds);
    }

    public synchronized LootMapDecision.Mode getLockedMode() { return lockedMode; }

    private static LootMapDecision.Mode initialMode(LootMapObservation latest,
                                                    double q75) {
        LootMapDistanceBand band = latest.getBand();
        if (band.getMaximum() <= 19) return LootMapDecision.Mode.DIRECT;
        // Seeded simulations reject early triangulation in the broad 200-499
        // band. q75 is observable from the reconstructed feasible set.
        if (q75 > 300.0d) return LootMapDecision.Mode.DIRECT;
        // In the ambiguous 50-199 annular sector, 80% of the area is >=100
        // tiles. An extra disambiguation reading loses on the primary metric;
        // information-first therefore starts immediately once q75 is tractable.
        if (band.getMinimum() >= 50) {
            return LootMapDecision.Mode.READ_FIRST;
        }
        return LootMapDecision.Mode.BALANCED;
    }

    private static LootMapDecision choose(LootMapObservation latest, Point direct,
                                          Belief belief, LootMapDecision.Mode mode,
                                          double p100, double q75, MapBounds bounds) {
        List<Point> candidates = candidates(latest, direct, belief.sample, bounds);
        double directDistance = distance(latest, direct);
        List<Scored> scored = new ArrayList<Scored>();
        for (Point candidate : candidates) {
            double walk = distance(latest, candidate);
            if (mode == LootMapDecision.Mode.BALANCED
                    && walk > Math.max(1.0d, directDistance * BALANCED_MOVE_CAP) + 1.0e-9d) {
                continue;
            }
            scored.add(new Scored(candidate, informationScore(candidate, belief.sample),
                    walk, candidate.equals(direct)));
        }
        if (scored.isEmpty()) {
            return simple(latest, direct, LootMapDecision.Mode.DIRECT);
        }
        double bestInformation = Double.POSITIVE_INFINITY;
        double directScore = Double.NaN;
        for (Scored value : scored) {
            bestInformation = Math.min(bestInformation, value.information);
            if (value.direct) directScore = value.information;
        }
        double slack = mode == LootMapDecision.Mode.READ_FIRST
                ? READ_FIRST_SLACK : BALANCED_SLACK;
        double limit = bestInformation * (1.0d + slack) + 0.25d;
        Scored chosen = null;
        for (Scored value : scored) {
            if (value.information > limit) continue;
            if (chosen == null || value.walk < chosen.walk
                    || (value.walk == chosen.walk
                    && value.information < chosen.information)) chosen = value;
        }
        Collections.sort(scored, new Comparator<Scored>() {
            @Override public int compare(Scored left, Scored right) {
                int information = Double.compare(left.information, right.information);
                return information != 0 ? information : Double.compare(left.walk, right.walk);
            }
        });
        List<LootMapDecision.Alternative> alternatives =
                new ArrayList<LootMapDecision.Alternative>();
        for (int i = 0; i < Math.min(8, scored.size()); i++) {
            Scored value = scored.get(i);
            alternatives.add(new LootMapDecision.Alternative(value.point.x,
                    value.point.y, value.information, value.walk, value.direct));
        }
        return new LootMapDecision(chosen.point.x, chosen.point.y, mode,
                belief.feasibleCount, belief.sample.size(), p100,
                q75, chosen.information, directScore, chosen.walk, alternatives);
    }

    private static LootMapDecision simple(LootMapObservation latest, Point point,
                                          LootMapDecision.Mode mode) {
        return new LootMapDecision(point.x, point.y, mode, 0, 0, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, distance(latest, point),
                Collections.<LootMapDecision.Alternative>emptyList());
    }

    private static Belief buildBelief(List<LootMapObservation> observations,
                                      MapBounds bounds) {
        LootMapObservation latest = observations.get(observations.size() - 1);
        int radius = latest.getBand().getMaximum() + 1;
        int minX = Math.max(0, (int) Math.floor(latest.getOriginX()) - radius);
        int maxX = Math.min(bounds.getWidth() - 1,
                (int) Math.ceil(latest.getOriginX()) + radius);
        int minY = Math.max(0, (int) Math.floor(latest.getOriginY()) - radius);
        int maxY = Math.min(bounds.getHeight() - 1,
                (int) Math.ceil(latest.getOriginY()) + radius);
        List<Point> reservoir = new ArrayList<Point>(MAX_SAMPLE);
        long seed = 0xcbf29ce484222325L;
        for (LootMapObservation observation : observations) {
            seed ^= Double.doubleToLongBits(observation.getOriginX()); seed *= 1099511628211L;
            seed ^= Double.doubleToLongBits(observation.getOriginY()); seed *= 1099511628211L;
            seed ^= observation.getBand().ordinal() * 31L
                    + Math.round(observation.getAbsoluteSectorDegrees());
        }
        Random random = new Random(seed);
        int feasible = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                boolean accepted = true;
                for (LootMapObservation observation : observations) {
                    if (!observation.accepts(x, y)) { accepted = false; break; }
                }
                if (!accepted) continue;
                feasible++;
                Point point = new Point(x, y);
                if (reservoir.size() < MAX_SAMPLE) reservoir.add(point);
                else {
                    int replacement = random.nextInt(feasible);
                    if (replacement < MAX_SAMPLE) reservoir.set(replacement, point);
                }
            }
        }
        return new Belief(feasible, reservoir);
    }

    private static List<Point> candidates(LootMapObservation latest, Point direct,
                                          List<Point> belief, MapBounds bounds) {
        Map<Long, Point> result = new LinkedHashMap<Long, Point>();
        add(result, direct, bounds);
        double step = distance(latest, direct);
        double center = latest.getAbsoluteSectorDegrees();
        double[] scales = {0.35d, 0.6d, 0.85d, 1.0d, 1.2d, 1.5d};
        int[] offsets = {-90, -60, -45, -30, -15, 0, 15, 30, 45, 60, 90};
        for (double scale : scales) for (int offset : offsets) {
            add(result, fromBearing(latest.getOriginX(), latest.getOriginY(),
                    center + offset, step * scale), bounds);
        }
        double meanX = 0.0d, meanY = 0.0d;
        for (Point point : belief) { meanX += point.x; meanY += point.y; }
        meanX /= belief.size(); meanY /= belief.size();
        Point centroid = new Point(meanX, meanY);
        add(result, centroid, bounds);
        double spreadSquared = 0.0d;
        for (Point point : belief) {
            double dx = point.x - meanX, dy = point.y - meanY;
            spreadSquared += dx * dx + dy * dy;
        }
        double spread = Math.sqrt(spreadSquared / belief.size());
        for (double scale : new double[]{0.25d, 0.5d}) {
            for (int direction = 0; direction < 8; direction++) {
                add(result, fromBearing(meanX, meanY, direction * 45.0d,
                        spread * scale), bounds);
            }
        }
        final double[] quantiles = {0.15d, 0.35d, 0.5d, 0.65d, 0.85d};
        for (int axisIndex = 0; axisIndex < 8; axisIndex++) {
            final double bearing = axisIndex * 22.5d;
            final double radians = Math.toRadians(bearing);
            final double axisX = Math.sin(radians);
            final double axisY = -Math.cos(radians);
            List<Point> ordered = new ArrayList<Point>(belief);
            Collections.sort(ordered, new Comparator<Point>() {
                @Override public int compare(Point left, Point right) {
                    return Double.compare(left.x * axisX + left.y * axisY,
                            right.x * axisX + right.y * axisY);
                }
            });
            for (double quantile : quantiles) {
                int index = (int) Math.round(quantile * (ordered.size() - 1));
                add(result, ordered.get(index), bounds);
            }
        }
        return new ArrayList<Point>(result.values());
    }

    private static double informationScore(Point candidate, List<Point> belief) {
        int[] counts = new int[LootMapDistanceBand.values().length * 8];
        for (Point target : belief) {
            int signature = signature(candidate, target);
            if (signature / 8 != LootMapDistanceBand.EXACT.ordinal()) counts[signature]++;
        }
        long sum = 0L;
        for (int count : counts) sum += (long) count * count;
        return (double) sum / belief.size();
    }

    static int signature(double originX, double originY, double targetX, double targetY) {
        double dx = targetX - originX, dy = targetY - originY;
        LootMapDistanceBand band = LootMapDistanceBand.forDistance(
                Math.sqrt(dx * dx + dy * dy));
        if (band == LootMapDistanceBand.EXACT) return 0;
        double bearing = NavigationMath.absoluteBearingDegrees(
                originX, originY, targetX, targetY);
        int direction = ((int) Math.floor((bearing + 22.5d) / 45.0d)) & 7;
        return band.ordinal() * 8 + direction;
    }

    private static int signature(Point origin, Point target) {
        return signature(origin.x, origin.y, target.x, target.y);
    }

    private static double probabilityAtLeast100(LootMapObservation latest,
                                                List<Point> belief) {
        int count = 0;
        for (Point target : belief) {
            double dx = target.x - latest.getOriginX();
            double dy = target.y - latest.getOriginY();
            if (Math.sqrt(dx * dx + dy * dy) >= 100.0d) count++;
        }
        return (double) count / belief.size();
    }

    private static double distanceQuantile(LootMapObservation latest,
                                           List<Point> belief, double quantile) {
        List<Double> distances = new ArrayList<Double>(belief.size());
        for (Point target : belief) {
            distances.add(Double.valueOf(Math.hypot(
                    target.x - latest.getOriginX(), target.y - latest.getOriginY())));
        }
        Collections.sort(distances);
        int index = (int) Math.round(quantile * (distances.size() - 1));
        return distances.get(index).doubleValue();
    }

    private static Point direct(LootMapObservation latest, MapBounds bounds) {
        return clamp(fromBearing(latest.getOriginX(), latest.getOriginY(),
                latest.getAbsoluteSectorDegrees(), latest.getBand().getDirectStep()), bounds);
    }

    private static Point fromBearing(double x, double y, double bearing,
                                     double distance) {
        double radians = Math.toRadians(bearing);
        return new Point(Math.rint(x + Math.sin(radians) * distance),
                Math.rint(y - Math.cos(radians) * distance));
    }

    private static void add(Map<Long, Point> points, Point point, MapBounds bounds) {
        Point clean = clamp(point, bounds);
        long x = Math.round(clean.x), y = Math.round(clean.y);
        points.put((x << 32) ^ (y & 0xffffffffL), new Point(x, y));
    }

    private static Point clamp(Point point, MapBounds bounds) {
        return new Point(Math.max(0.0d, Math.min(bounds.getWidth() - 1.0d, point.x)),
                Math.max(0.0d, Math.min(bounds.getHeight() - 1.0d, point.y)));
    }

    private static double distance(LootMapObservation origin, Point target) {
        return Math.hypot(target.x - origin.getOriginX(), target.y - origin.getOriginY());
    }

    private static final class Belief {
        private final int feasibleCount;
        private final List<Point> sample;
        private Belief(int feasibleCount, List<Point> sample) {
            this.feasibleCount = feasibleCount;
            this.sample = sample;
        }
    }

    private static final class Scored {
        private final Point point;
        private final double information;
        private final double walk;
        private final boolean direct;
        private Scored(Point point, double information, double walk, boolean direct) {
            this.point = point; this.information = information;
            this.walk = walk; this.direct = direct;
        }
    }

    private static final class Point {
        private final double x;
        private final double y;
        private Point(double x, double y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Point)) return false;
            Point that = (Point) other;
            return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0;
        }
        @Override public int hashCode() {
            long xb = Double.doubleToLongBits(x), yb = Double.doubleToLongBits(y);
            return (int) (xb ^ (xb >>> 32) ^ yb ^ (yb >>> 32));
        }
    }
}
