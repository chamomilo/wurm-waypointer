package org.waypoints.next.navigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Removes completed excursions when independently planned legs overlap. */
public final class NavigationRouteAssembly {
    private NavigationRouteAssembly() { }

    /** Joins a received approach wherever it actually meets the selected highway. */
    public static List<GroundRouteTrace.Point> joinHighway(
            List<GroundRouteTrace.Point> approach,
            List<GroundRouteTrace.Point> highway,
            List<GroundRouteTrace.Point> tail) {
        Map<Long, Integer> positions = new HashMap<Long, Integer>();
        for (int i = 0; i < highway.size(); i++) {
            positions.put(Long.valueOf(positionKey(highway.get(i))), Integer.valueOf(i));
        }
        int approachJoin = -1, highwayJoin = -1;
        for (int i = 0; i < approach.size(); i++) {
            Integer match = positions.get(Long.valueOf(positionKey(approach.get(i))));
            if (match != null && match.intValue() >= highwayJoin) {
                approachJoin = i;
                highwayJoin = match.intValue();
            }
        }
        if (approachJoin < 0) return new ArrayList<GroundRouteTrace.Point>();
        List<GroundRouteTrace.Point> result = new ArrayList<GroundRouteTrace.Point>(
                approach.subList(0, approachJoin));
        result.addAll(highway.subList(highwayJoin, highway.size()));
        if (!tail.isEmpty() && positionKey(tail.get(0))
                == positionKey(highway.get(highway.size() - 1))) result.addAll(tail);
        return withoutLoops(result);
    }

    public static List<GroundRouteTrace.Point> withoutLoops(
            List<GroundRouteTrace.Point> source) {
        List<GroundRouteTrace.Point> result = new ArrayList<GroundRouteTrace.Point>();
        Map<Long, Integer> visited = new HashMap<Long, Integer>();
        for (GroundRouteTrace.Point point : source) {
            Long key = Long.valueOf(positionKey(point));
            Integer previous = visited.get(key);
            if (previous != null) {
                while (result.size() > previous.intValue() + 1) {
                    GroundRouteTrace.Point removed = result.remove(result.size() - 1);
                    visited.remove(Long.valueOf(positionKey(removed)));
                }
                result.set(previous.intValue(), point);
            } else {
                visited.put(key, Integer.valueOf(result.size()));
                result.add(point);
            }
        }
        return result;
    }

    public static long positionKey(GroundRouteTrace.Point point) {
        int layer = point.getHeightSource() == GroundRouteTrace.HeightSource.CAVE
                || point.getHighwayKind() == HighwayTileIndex.Kind.TUNNEL ? 1
                : point.getHighwayKind() == HighwayTileIndex.Kind.BRIDGE ? 2 : 0;
        return (((long) point.getTileX() << 32)
                ^ (point.getTileY() & 0xffffffffL)) | ((long) layer << 62);
    }
}
