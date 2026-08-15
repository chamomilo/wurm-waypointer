package org.waypoints.next.navigation;

import org.waypoints.next.model.MarkerStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Deterministic distance culling and hard cap for managed world effects. */
public final class NavigationEffectSelector {
    private NavigationEffectSelector() { }

    public static List<NavigationTarget> select(NavigationSnapshot snapshot,
                                                double originTileX,
                                                double originTileY,
                                                int maximumDistanceMetres,
                                                int effectCap) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        if (maximumDistanceMetres < 1) throw new IllegalArgumentException(
                "maximum distance must be positive");
        if (effectCap < 0 || effectCap > 1024) throw new IllegalArgumentException(
                "effect cap must be in 0..1024");
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (NavigationTarget target : snapshot.getTargets()) {
            MarkerStyle.WorldStyle worldStyle =
                    target.getMarkerStyle().getWorldStyle();
            if (!target.isWorldBeamVisible()
                    || worldStyle == MarkerStyle.WorldStyle.COMPASS_ONLY
                    || worldStyle == MarkerStyle.WorldStyle.HIDDEN) continue;
            int distance = NavigationMath.distanceMetres(originTileX, originTileY,
                    target.getCoordinate().getTileX(),
                    target.getCoordinate().getTileY());
            if (target.isVanillaSystem() || distance <= maximumDistanceMetres) {
                candidates.add(new Candidate(target, distance));
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
        int count = Math.min(systemCount + effectCap, candidates.size());
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
