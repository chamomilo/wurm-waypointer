package org.waypoints.next.navigation;

import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointLayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Stateful outside-to-inside arrival detector. It creates state only after a
 * navigation generation change and performs no allocation on unchanged ticks.
 */
public final class WaypointArrivalTracker {
    public interface Listener {
        void arrived(NavigationTarget target, int distanceMetres);
        void exited(NavigationTarget target, int distanceMetres);
    }

    private final Map<NavigationTargetKey, State> states =
            new HashMap<NavigationTargetKey, State>();
    private long generation = Long.MIN_VALUE;

    public void update(NavigationSnapshot snapshot, double playerTileX,
                       double playerTileY, WaypointLayer playerLayer,
                       Listener listener) {
        if (snapshot == null) throw new IllegalArgumentException(
                "snapshot is required");
        if (playerLayer == null) throw new IllegalArgumentException(
                "player layer is required");
        if (listener == null) throw new IllegalArgumentException(
                "arrival listener is required");
        if (generation != snapshot.getGeneration()) {
            for (Iterator<NavigationTargetKey> keys = states.keySet().iterator();
                 keys.hasNext();) {
                if (!contains(snapshot, keys.next())) keys.remove();
            }
            generation = snapshot.getGeneration();
        }
        for (NavigationTarget target : snapshot.getTargets()) {
            int radius = target.getArrivalRadiusMetres();
            if (target.isVanillaSystem() || radius <= WaypointArrival.DISABLED) {
                continue;
            }
            boolean sameLayer = target.getCoordinate().getLayer() == playerLayer;
            int distance = NavigationMath.distanceMetres(playerTileX, playerTileY,
                    target.getCoordinate().getTileX(),
                    target.getCoordinate().getTileY());
            boolean inside = sameLayer && distance <= radius;
            State state = states.get(target.getKey());
            if (state == null || !state.matches(target)) {
                // Loading while already inside is not an arrival transition.
                states.put(target.getKey(), new State(target, !inside, inside));
            } else if (inside && state.armed) {
                state.armed = false;
                state.inside = true;
                listener.arrived(target, distance);
            } else {
                if (!inside && state.inside) {
                    state.inside = false;
                    listener.exited(target, distance);
                } else if (inside) {
                    state.inside = true;
                }
                if (!state.armed && (!sameLayer || distance >= radius
                        + WaypointArrival.REARM_HYSTERESIS_METRES)) {
                    state.armed = true;
                }
            }
        }
    }

    public void reset() {
        states.clear();
        generation = Long.MIN_VALUE;
    }

    private static boolean contains(NavigationSnapshot snapshot,
                                    NavigationTargetKey key) {
        for (NavigationTarget target : snapshot.getTargets()) {
            if (target.getKey().equals(key)
                    && !target.isVanillaSystem()
                    && target.getArrivalRadiusMetres() > WaypointArrival.DISABLED) {
                return true;
            }
        }
        return false;
    }

    private static final class State {
        private final int radius;
        private final WaypointLayer layer;
        private boolean armed;
        private boolean inside;

        private State(NavigationTarget target, boolean armed, boolean inside) {
            radius = target.getArrivalRadiusMetres();
            layer = target.getCoordinate().getLayer();
            this.armed = armed;
            this.inside = inside;
        }

        private boolean matches(NavigationTarget target) {
            return radius == target.getArrivalRadiusMetres()
                    && layer == target.getCoordinate().getLayer();
        }
    }
}
