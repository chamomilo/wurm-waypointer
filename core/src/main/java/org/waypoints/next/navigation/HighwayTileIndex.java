package org.waypoints.next.navigation;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable bounded raster index parsed from Sklotopolis highways.json. */
public final class HighwayTileIndex {
    public enum Kind { NONE, ROAD, BRIDGE, TUNNEL }

    public static final class Tile {
        private final int kindMask;
        private final int portalMask;

        private Tile(int kindMask, int portalMask) {
            this.kindMask = kindMask;
            this.portalMask = portalMask;
        }

        /** Legacy highest-priority view; layer-aware callers use hasKind. */
        public Kind getKind() {
            if (hasKind(Kind.TUNNEL)) return Kind.TUNNEL;
            if (hasKind(Kind.BRIDGE)) return Kind.BRIDGE;
            if (hasKind(Kind.ROAD)) return Kind.ROAD;
            return Kind.NONE;
        }

        public boolean isPortal() { return isPortal(getKind()); }

        public boolean hasKind(Kind kind) {
            return kind != null && kind != Kind.NONE
                    && (kindMask & bit(kind)) != 0;
        }

        public boolean isPortal(Kind kind) {
            return kind != null && kind != Kind.NONE
                    && (portalMask & bit(kind)) != 0;
        }
    }

    /** One authoritative published segment; crossings are not implicit joins. */
    public static final class Segment {
        private final int startX;
        private final int startY;
        private final int endX;
        private final int endY;
        private final Kind kind;

        private Segment(int startX, int startY, int endX, int endY, Kind kind) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.kind = kind;
        }

        public int getStartX() { return startX; }
        public int getStartY() { return startY; }
        public int getEndX() { return endX; }
        public int getEndY() { return endY; }
        public Kind getKind() { return kind; }

        public float lengthTiles() {
            return (float) Math.hypot(endX - startX, endY - startY);
        }
    }

    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]+)\\}");
    private static final Pattern FIELD = Pattern.compile(
            "\\\"(startX|startY|endX|endY|type)\\\"\\s*:\\s*(?:\\\"(-?\\d+)\\\"|(-?\\d+))");
    private static final int MAXIMUM_INPUT_CHARACTERS = 5_000_000;
    private static final int MAXIMUM_SEGMENTS = 20_000;
    private static final int MAXIMUM_RASTER_TILES = 1_000_000;
    private static final Tile NONE = new Tile(0, 0);
    private static final HighwayTileIndex EMPTY = new HighwayTileIndex(
            Collections.<Long, Tile>emptyMap(),
            Collections.<Segment>emptyList(), 0);

    private final Map<Long, Tile> tiles;
    private final List<Segment> segments;
    private final int segmentCount;

    private HighwayTileIndex(Map<Long, Tile> tiles, List<Segment> segments,
                             int segmentCount) {
        this.tiles = Collections.unmodifiableMap(new HashMap<Long, Tile>(tiles));
        this.segments = Collections.unmodifiableList(
                new ArrayList<Segment>(segments));
        this.segmentCount = segmentCount;
    }

    public static HighwayTileIndex empty() { return EMPTY; }

    public static HighwayTileIndex parse(String source, int mapWidth,
                                         int mapHeight) {
        if (source == null || source.length() > MAXIMUM_INPUT_CHARACTERS) {
            throw new IllegalArgumentException("highway input is missing or oversized");
        }
        if (mapWidth < 1 || mapHeight < 1) throw new IllegalArgumentException(
                "map bounds must be positive");
        int arrayStart = source.indexOf('[');
        int arrayEnd = source.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            throw new IllegalArgumentException("highway array is missing");
        }
        String array = source.substring(arrayStart + 1, arrayEnd);
        Matcher objects = OBJECT.matcher(array);
        Map<Long, MutableTile> mutable = new HashMap<Long, MutableTile>();
        List<Segment> published = new ArrayList<Segment>();
        int segments = 0;
        int rasterTiles = 0;
        while (objects.find()) {
            if (++segments > MAXIMUM_SEGMENTS) throw new IllegalArgumentException(
                    "too many highway segments");
            Integer startX = null;
            Integer startY = null;
            Integer endX = null;
            Integer endY = null;
            int type = 2;
            Matcher fields = FIELD.matcher(objects.group(1));
            while (fields.find()) {
                int value = Integer.parseInt(fields.group(2) == null
                        ? fields.group(3) : fields.group(2));
                String name = fields.group(1);
                if ("startX".equals(name)) startX = Integer.valueOf(value);
                else if ("startY".equals(name)) startY = Integer.valueOf(value);
                else if ("endX".equals(name)) endX = Integer.valueOf(value);
                else if ("endY".equals(name)) endY = Integer.valueOf(value);
                else if ("type".equals(name)) type = value;
            }
            if (startX == null || startY == null || endX == null || endY == null) {
                throw new IllegalArgumentException("incomplete highway segment");
            }
            requireBounds(startX.intValue(), startY.intValue(), mapWidth, mapHeight);
            requireBounds(endX.intValue(), endY.intValue(), mapWidth, mapHeight);
            Kind kind = kind(type);
            if (kind == Kind.NONE) continue;
            published.add(new Segment(startX.intValue(), startY.intValue(),
                    endX.intValue(), endY.intValue(), kind));
            int x = startX.intValue();
            int y = startY.intValue();
            int targetX = endX.intValue();
            int targetY = endY.intValue();
            int dx = Math.abs(targetX - x);
            int dy = Math.abs(targetY - y);
            int stepX = x < targetX ? 1 : -1;
            int stepY = y < targetY ? 1 : -1;
            int error = dx - dy;
            int sideOffsetX = corridorSideOffsetX(dx, dy);
            int sideOffsetY = corridorSideOffsetY(dx, dy);
            while (true) {
                boolean endpoint = (x == startX.intValue() && y == startY.intValue())
                        || (x == targetX && y == targetY);
                merge(mutable, x, y, kind,
                        endpoint && (kind == Kind.BRIDGE || kind == Kind.TUNNEL));
                if (++rasterTiles > MAXIMUM_RASTER_TILES) {
                    throw new IllegalArgumentException("highway raster is oversized");
                }
                int sideX = x + sideOffsetX;
                int sideY = y + sideOffsetY;
                if (inside(sideX, sideY, mapWidth, mapHeight)) {
                    // Published coordinates describe the boundary between two
                    // highway tiles, not a one-tile centreline.  The second
                    // side deliberately receives the identical kind/cost.
                    // Special portal flags are copied after normalisation so
                    // joins between consecutive bridge/tunnel spans do not
                    // become false ramps or tunnel entrances.
                    merge(mutable, sideX, sideY, kind, false);
                    if (++rasterTiles > MAXIMUM_RASTER_TILES) {
                        throw new IllegalArgumentException(
                                "highway raster is oversized");
                    }
                }
                if (x == targetX && y == targetY) break;
                int doubled = error * 2;
                int previousX = x;
                int previousY = y;
                if (doubled > -dy) {
                    error -= dy;
                    x += stepX;
                }
                if (doubled < dx) {
                    error += dx;
                    y += stepY;
                }
                if (x != previousX && y != previousY) {
                    // A diagonal published line represents a staircase of
                    // tile boundaries. At its cardinal direction change the
                    // protected turn is the full 2x2 square touching that
                    // grid corner. Choosing the corner by the lower X makes
                    // the result independent of segment direction.
                    int turnX;
                    int turnY;
                    if (previousX < x) {
                        turnX = previousX;
                        turnY = y;
                    } else {
                        turnX = x;
                        turnY = previousY;
                    }
                    for (int turnDx = -1; turnDx <= 0; turnDx++) {
                        for (int turnDy = -1; turnDy <= 0; turnDy++) {
                            int turnTileX = turnX + turnDx;
                            int turnTileY = turnY + turnDy;
                            if (!inside(turnTileX, turnTileY, mapWidth,
                                    mapHeight)) continue;
                            merge(mutable, turnTileX, turnTileY, kind,
                                    false);
                            if (++rasterTiles > MAXIMUM_RASTER_TILES) {
                                throw new IllegalArgumentException(
                                        "highway raster is oversized");
                            }
                        }
                    }
                }
            }
        }
        fillAdjacentEndpointSquares(mutable, published, mapWidth, mapHeight);
        normaliseSpecialPortals(mutable, published);
        copyPortalsToSecondCorridorSide(mutable, published, mapWidth,
                mapHeight);
        Map<Long, Tile> immutable = new HashMap<Long, Tile>(mutable.size());
        for (Map.Entry<Long, MutableTile> entry : mutable.entrySet()) {
            MutableTile value = entry.getValue();
            immutable.put(entry.getKey(), new Tile(
                    value.kindMask, value.portalMask));
        }
        return new HighwayTileIndex(immutable, published, segments);
    }

    public Tile get(int tileX, int tileY) {
        Tile tile = tiles.get(Long.valueOf(key(tileX, tileY)));
        return tile == null ? NONE : tile;
    }

    public int getSegmentCount() { return segmentCount; }
    public List<Segment> getSegments() { return segments; }
    public int getTileCount() { return tiles.size(); }
    public boolean isEmpty() { return tiles.isEmpty(); }

    private static void merge(Map<Long, MutableTile> target, int x, int y,
                              Kind kind, boolean portal) {
        Long key = Long.valueOf(key(x, y));
        MutableTile existing = target.get(key);
        if (existing == null) {
            target.put(key, new MutableTile(kind, portal));
            return;
        }
        existing.kindMask |= bit(kind);
        if (portal) existing.portalMask |= bit(kind);
    }

    private static Kind kind(int type) {
        if (type == 0) return Kind.BRIDGE;
        if (type == 1) return Kind.TUNNEL;
        if (type == 3) return Kind.NONE;
        return Kind.ROAD;
    }

    private static int bit(Kind kind) {
        return 1 << kind.ordinal();
    }

    /**
     * A published segment endpoint is not necessarily a physical portal: the
     * server commonly splits one bridge or tunnel into touching chunks. Only
     * a terminal endpoint of that same-kind network is an entry/exit portal.
     */
    private static void normaliseSpecialPortals(
            Map<Long, MutableTile> tiles, List<Segment> segments) {
        Map<Long, int[]> degree = new HashMap<Long, int[]>();
        Map<Long, List<Incident>> incidents =
                new HashMap<Long, List<Incident>>();
        for (Segment segment : segments) {
            if (!special(segment.kind)) continue;
            increment(degree, segment.startX, segment.startY, segment.kind);
            increment(degree, segment.endX, segment.endY, segment.kind);
            addIncident(incidents, segment.startX, segment.startY,
                    segment.endX - segment.startX,
                    segment.endY - segment.startY, segment.kind);
            addIncident(incidents, segment.endX, segment.endY,
                    segment.startX - segment.endX,
                    segment.startY - segment.endY, segment.kind);
        }
        List<Long> endpointCoordinates = new ArrayList<Long>(degree.keySet());
        for (Long coordinate : endpointCoordinates) {
            int x = (int) (coordinate.longValue() >> 32);
            int y = (int) coordinate.longValue();
            int[] sourceDegree = degree.get(coordinate);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Long adjacentKey = Long.valueOf(key(x + dx, y + dy));
                    if (adjacentKey.longValue() <= coordinate.longValue()) {
                        continue;
                    }
                    int[] adjacentDegree = degree.get(adjacentKey);
                    if (adjacentDegree == null) continue;
                    for (Kind kind : new Kind[] { Kind.BRIDGE, Kind.TUNNEL }) {
                        int ordinal = kind.ordinal();
                        if (sourceDegree[ordinal] > 0
                                && adjacentDegree[ordinal] > 0) {
                            sourceDegree[ordinal]++;
                            adjacentDegree[ordinal]++;
                        }
                    }
                }
            }
        }
        int specialMask = bit(Kind.BRIDGE) | bit(Kind.TUNNEL);
        for (Map.Entry<Long, MutableTile> entry : tiles.entrySet()) {
            MutableTile tile = entry.getValue();
            int candidates = tile.portalMask & specialMask;
            tile.portalMask &= ~specialMask;
            int[] endpointDegree = degree.get(entry.getKey());
            if (endpointDegree == null) continue;
            for (Kind kind : new Kind[] { Kind.BRIDGE, Kind.TUNNEL }) {
                if ((candidates & bit(kind)) != 0
                        && (endpointDegree[kind.ordinal()] <= 1
                        || isTurningJunction(incidents.get(entry.getKey()),
                        kind))) {
                    tile.portalMask |= bit(kind);
                }
            }
        }
    }

    private static void addIncident(Map<Long, List<Incident>> incidents,
                                    int x, int y, int dx, int dy,
                                    Kind kind) {
        Long coordinate = Long.valueOf(key(x, y));
        List<Incident> values = incidents.get(coordinate);
        if (values == null) {
            values = new ArrayList<Incident>();
            incidents.put(coordinate, values);
        }
        values.add(new Incident(dx, dy, kind));
    }

    /** A straight pair is a technical span join; a corner/T/cross is an access node. */
    private static boolean isTurningJunction(List<Incident> incidents,
                                             Kind kind) {
        if (incidents == null) return false;
        List<Incident> sameKind = new ArrayList<Incident>();
        for (Incident incident : incidents) {
            if (incident.kind == kind) sameKind.add(incident);
        }
        if (sameKind.size() < 2) return false;
        if (sameKind.size() != 2) return true;
        Incident first = sameKind.get(0);
        Incident second = sameKind.get(1);
        long cross = (long) first.dx * second.dy
                - (long) first.dy * second.dx;
        long dot = (long) first.dx * second.dx
                + (long) first.dy * second.dy;
        return cross != 0L || dot >= 0L;
    }

    private static void increment(Map<Long, int[]> degree, int x, int y,
                                  Kind kind) {
        Long coordinate = Long.valueOf(key(x, y));
        int[] values = degree.get(coordinate);
        if (values == null) {
            values = new int[Kind.values().length];
            degree.put(coordinate, values);
        }
        values[kind.ordinal()]++;
    }

    private static boolean special(Kind kind) {
        return kind == Kind.BRIDGE || kind == Kind.TUNNEL;
    }

    /**
     * Inclusive Sklotopolis segments frequently meet one coordinate apart.
     * Their graph connection is a real junction, so its protected footprint
     * must be the same filled 2x2 square as an exact-coordinate turn/T/cross.
     */
    private static void fillAdjacentEndpointSquares(
            Map<Long, MutableTile> tiles, List<Segment> segments,
            int mapWidth, int mapHeight) {
        Map<Long, List<Endpoint>> endpoints =
                new HashMap<Long, List<Endpoint>>();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            addEndpoint(endpoints, new Endpoint(i, segment.startX,
                    segment.startY, segment.kind));
            addEndpoint(endpoints, new Endpoint(i, segment.endX,
                    segment.endY, segment.kind));
        }
        for (Map.Entry<Long, List<Endpoint>> entry : endpoints.entrySet()) {
            List<Endpoint> source = entry.getValue();
            for (int i = 0; i < source.size(); i++) {
                for (int j = i + 1; j < source.size(); j++) {
                    Endpoint left = source.get(i);
                    Endpoint right = source.get(j);
                    if (left.segmentIndex == right.segmentIndex
                            || !junctionCompatible(left.kind, right.kind)) {
                        continue;
                    }
                    fillTwoByTwo(tiles, left.x, left.y, left.kind,
                            mapWidth, mapHeight);
                    fillTwoByTwo(tiles, left.x, left.y, right.kind,
                            mapWidth, mapHeight);
                }
            }
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = dx == 0 ? 1 : -1; dy <= 1; dy++) {
                    int otherX = source.get(0).x + dx;
                    int otherY = source.get(0).y + dy;
                    List<Endpoint> target = endpoints.get(Long.valueOf(key(
                            otherX, otherY)));
                    if (target == null) continue;
                    for (Endpoint left : source) {
                        for (Endpoint right : target) {
                            if (left.segmentIndex == right.segmentIndex
                                    || !junctionCompatible(left.kind,
                                    right.kind)) continue;
                            int cornerX = Math.max(left.x, right.x);
                            int cornerY = Math.max(left.y, right.y);
                            fillTwoByTwo(tiles, cornerX, cornerY, left.kind,
                                    mapWidth, mapHeight);
                            fillTwoByTwo(tiles, cornerX, cornerY, right.kind,
                                    mapWidth, mapHeight);
                        }
                    }
                }
            }
        }
    }

    private static void addEndpoint(Map<Long, List<Endpoint>> endpoints,
                                    Endpoint endpoint) {
        Long coordinate = Long.valueOf(key(endpoint.x, endpoint.y));
        List<Endpoint> values = endpoints.get(coordinate);
        if (values == null) {
            values = new ArrayList<Endpoint>();
            endpoints.put(coordinate, values);
        }
        values.add(endpoint);
    }

    private static boolean junctionCompatible(Kind left, Kind right) {
        if (left == right) return true;
        return (left == Kind.ROAD && right == Kind.BRIDGE)
                || (left == Kind.BRIDGE && right == Kind.ROAD);
    }

    private static void fillTwoByTwo(Map<Long, MutableTile> tiles,
                                     int cornerX, int cornerY, Kind kind,
                                     int mapWidth, int mapHeight) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                int x = cornerX + dx;
                int y = cornerY + dy;
                if (inside(x, y, mapWidth, mapHeight)) {
                    merge(tiles, x, y, kind, false);
                }
            }
        }
    }

    /**
     * Returns the tile offset on the other side of a published boundary.
     * Horizontal boundaries separate y-1/y; vertical boundaries separate
     * x-1/x. Diagonal/stair-step data uses the dominant axis; every diagonal
     * direction change is additionally filled as a 2x2 turn above.
     */
    private static int corridorSideOffsetX(int absoluteDx, int absoluteDy) {
        return absoluteDx >= absoluteDy ? 0 : -1;
    }

    private static int corridorSideOffsetY(int absoluteDx, int absoluteDy) {
        return absoluteDx >= absoluteDy ? -1 : 0;
    }

    private static void copyPortalsToSecondCorridorSide(
            Map<Long, MutableTile> tiles, List<Segment> segments,
            int mapWidth, int mapHeight) {
        for (Segment segment : segments) {
            if (!special(segment.kind)) continue;
            int dx = Math.abs(segment.endX - segment.startX);
            int dy = Math.abs(segment.endY - segment.startY);
            int sideOffsetX = corridorSideOffsetX(dx, dy);
            int sideOffsetY = corridorSideOffsetY(dx, dy);
            copyPortal(tiles, segment.startX, segment.startY,
                    sideOffsetX, sideOffsetY, segment.kind, mapWidth,
                    mapHeight);
            copyPortal(tiles, segment.endX, segment.endY,
                    sideOffsetX, sideOffsetY, segment.kind, mapWidth,
                    mapHeight);
        }
    }

    private static void copyPortal(Map<Long, MutableTile> tiles,
                                   int boundaryX, int boundaryY,
                                   int sideOffsetX, int sideOffsetY,
                                   Kind kind, int mapWidth, int mapHeight) {
        MutableTile boundary = tiles.get(Long.valueOf(key(boundaryX,
                boundaryY)));
        if (boundary == null || (boundary.portalMask & bit(kind)) == 0) {
            return;
        }
        int sideX = boundaryX + sideOffsetX;
        int sideY = boundaryY + sideOffsetY;
        if (!inside(sideX, sideY, mapWidth, mapHeight)) return;
        MutableTile side = tiles.get(Long.valueOf(key(sideX, sideY)));
        if (side != null && (side.kindMask & bit(kind)) != 0) {
            side.portalMask |= bit(kind);
        }
    }

    private static boolean inside(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static void requireBounds(int x, int y, int width, int height) {
        if (!inside(x, y, width, height)) {
            throw new IllegalArgumentException("highway coordinate is outside map bounds");
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static final class MutableTile {
        private int kindMask;
        private int portalMask;

        private MutableTile(Kind kind, boolean portal) {
            this.kindMask = bit(kind);
            this.portalMask = portal ? bit(kind) : 0;
        }
    }

    private static final class Endpoint {
        private final int segmentIndex;
        private final int x;
        private final int y;
        private final Kind kind;

        private Endpoint(int segmentIndex, int x, int y, Kind kind) {
            this.segmentIndex = segmentIndex;
            this.x = x;
            this.y = y;
            this.kind = kind;
        }
    }

    private static final class Incident {
        private final int dx;
        private final int dy;
        private final Kind kind;

        private Incident(int dx, int dy, Kind kind) {
            this.dx = dx;
            this.dy = dy;
            this.kind = kind;
        }
    }
}
