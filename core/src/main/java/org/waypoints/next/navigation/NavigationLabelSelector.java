package org.waypoints.next.navigation;

import org.waypoints.next.model.MarkerStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Deterministic distance culling and hard cap for persistent HUD labels. */
public final class NavigationLabelSelector {
    private NavigationLabelSelector() { }

    /** Compatibility helper for callers that intentionally request no culling. */
    public static List<NavigationTarget> select(NavigationSnapshot snapshot) {
        return select(snapshot, 0.0d, 0.0d, Integer.MAX_VALUE, 1024);
    }

    public static List<NavigationTarget> select(NavigationSnapshot snapshot,
                                                double originTileX,
                                                double originTileY,
                                                int maximumDistanceMetres,
                                                int labelCap) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        if (maximumDistanceMetres < 1) throw new IllegalArgumentException(
                "maximum distance must be positive");
        if (labelCap < 0 || labelCap > 1024) throw new IllegalArgumentException(
                "label cap must be in 0..1024");
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (NavigationTarget target : snapshot.getTargets()) {
            if (target.getMarkerStyle().isShowLabel()
                    && target.getMarkerStyle().getWorldStyle()
                    != MarkerStyle.WorldStyle.COMPASS_ONLY) {
                int distance = NavigationMath.distanceMetres(originTileX, originTileY,
                        target.getCoordinate().getTileX(),
                        target.getCoordinate().getTileY());
                if (target.isVanillaSystem() || distance <= maximumDistanceMetres) {
                    candidates.add(new Candidate(target, distance));
                }
            }
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override public int compare(Candidate left, Candidate right) {
                if (left.target.isVanillaSystem()
                        != right.target.isVanillaSystem()) {
                    return left.target.isVanillaSystem() ? -1 : 1;
                }
                if (left.target.isSelected() != right.target.isSelected()) {
                    return left.target.isSelected() ? -1 : 1;
                }
                int distance = Integer.compare(left.distance, right.distance);
                return distance != 0 ? distance
                        : left.target.getKey().compareTo(right.target.getKey());
            }
        });
        int systemCount = 0;
        for (Candidate candidate : candidates) {
            if (candidate.target.isVanillaSystem()) systemCount++;
        }
        int count = Math.min(systemCount + labelCap, candidates.size());
        List<NavigationTarget> result = new ArrayList<NavigationTarget>(count);
        for (int i = 0; i < count; i++) result.add(candidates.get(i).target);
        return Collections.unmodifiableList(result);
    }

    private static final class Candidate {
        private final NavigationTarget target;
        private final int distance;

        private Candidate(NavigationTarget target, int distance) {
            this.target = target;
            this.distance = distance;
        }
    }
}
