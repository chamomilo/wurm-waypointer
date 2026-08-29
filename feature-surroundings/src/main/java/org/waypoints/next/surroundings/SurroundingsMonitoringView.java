package org.waypoints.next.surroundings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Combines several monitored filters into one compact, distance-sorted view. */
public final class SurroundingsMonitoringView {
    private SurroundingsMonitoringView() { }

    public static List<SurroundingsRow> merge(
            Collection<SurroundingsSnapshot> snapshots) {
        Map<SurroundingKey, SurroundingsRow> unique =
                new LinkedHashMap<SurroundingKey, SurroundingsRow>();
        if (snapshots != null) for (SurroundingsSnapshot snapshot : snapshots) {
            if (snapshot == null) continue;
            for (SurroundingsRow row : snapshot.getRows()) {
                if (row != null && !unique.containsKey(row.getEntry().getKey())) {
                    unique.put(row.getEntry().getKey(), row);
                }
            }
        }
        List<SurroundingsRow> result =
                new ArrayList<SurroundingsRow>(unique.values());
        Collections.sort(result, new Comparator<SurroundingsRow>() {
            @Override public int compare(SurroundingsRow left,
                                         SurroundingsRow right) {
                int distance = Integer.compare(left.getDistanceMetres(),
                        right.getDistanceMetres());
                if (distance != 0) return distance;
                int name = left.getEntry().getName().compareToIgnoreCase(
                        right.getEntry().getName());
                return name != 0 ? name : left.getEntry().getKey().compareTo(
                        right.getEntry().getKey());
            }
        });
        return Collections.unmodifiableList(result);
    }
}
