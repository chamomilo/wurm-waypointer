package org.waypoints.next.surroundings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Session-local collection of distinct immutable Monitoring filter presets. */
public final class SurroundingsMonitoringFilters {
    private final List<SurroundingsQuery> filters =
            new ArrayList<SurroundingsQuery>();

    public boolean add(SurroundingsQuery query) {
        if (query == null) return false;
        for (SurroundingsQuery existing : filters) {
            if (sameFilter(existing, query)) return false;
        }
        filters.add(query);
        return true;
    }

    public void clear() { filters.clear(); }
    public boolean isEmpty() { return filters.isEmpty(); }
    public int size() { return filters.size(); }

    public List<SurroundingsQuery> snapshot() {
        return Collections.unmodifiableList(
                new ArrayList<SurroundingsQuery>(filters));
    }

    private static boolean sameFilter(SurroundingsQuery left,
                                      SurroundingsQuery right) {
        return left.getKind() == right.getKind()
                && left.getText().equalsIgnoreCase(right.getText())
                && left.getShortName().equalsIgnoreCase(right.getShortName())
                && left.getShortNameMode() == right.getShortNameMode()
                && left.getExcludedNames().equals(right.getExcludedNames())
                && left.getCategories().equals(right.getCategories())
                && left.getMaterials().equals(right.getMaterials())
                && left.getModifiers().equals(right.getModifiers())
                && left.getRarities().equals(right.getRarities())
                && left.getLayers().equals(right.getLayers())
                && left.getMarks().equals(right.getMarks())
                && left.getDeedStatuses().equals(right.getDeedStatuses())
                && left.getUniqueStatuses().equals(right.getUniqueStatuses());
    }
}
