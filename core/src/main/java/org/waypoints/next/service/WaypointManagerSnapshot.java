package org.waypoints.next.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One immutable manager refresh, including rows and dynamic filter options. */
public final class WaypointManagerSnapshot {
    private final int totalCount;
    private final List<WaypointManagerRow> rows;
    private final List<WaypointFilterOption> users;
    private final List<WaypointFilterOption> servers;

    WaypointManagerSnapshot(int totalCount, List<WaypointManagerRow> rows,
                            List<WaypointFilterOption> users,
                            List<WaypointFilterOption> servers) {
        this.totalCount = totalCount;
        this.rows = immutable(rows);
        this.users = immutable(users);
        this.servers = immutable(servers);
    }

    public int getTotalCount() { return totalCount; }
    public int getFilteredCount() { return rows.size(); }
    public List<WaypointManagerRow> getRows() { return rows; }
    public List<WaypointFilterOption> getUsers() { return users; }
    public List<WaypointFilterOption> getServers() { return servers; }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
