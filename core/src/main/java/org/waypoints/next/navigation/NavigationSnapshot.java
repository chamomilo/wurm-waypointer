package org.waypoints.next.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable navigation view published atomically to client render adapters. */
public final class NavigationSnapshot {
    private final long sourceRevision;
    private final long generation;
    private final List<NavigationTarget> targets;

    NavigationSnapshot(long sourceRevision, long generation,
                       List<NavigationTarget> targets) {
        this.sourceRevision = sourceRevision;
        this.generation = generation;
        this.targets = Collections.unmodifiableList(
                new ArrayList<NavigationTarget>(targets));
    }

    public static NavigationSnapshot empty() {
        return new NavigationSnapshot(-1L, 0L,
                Collections.<NavigationTarget>emptyList());
    }

    public long getSourceRevision() { return sourceRevision; }
    public long getGeneration() { return generation; }
    public List<NavigationTarget> getTargets() { return targets; }

    public NavigationTarget find(NavigationTargetKey key) {
        if (key == null) return null;
        for (NavigationTarget target : targets) {
            if (key.equals(target.getKey())) return target;
        }
        return null;
    }

    /** At most one target can own the session navigation route. */
    public NavigationTarget getActiveNavigator() {
        for (NavigationTarget target : targets) {
            if (target.isNavigatorActive()) return target;
        }
        return null;
    }
}
