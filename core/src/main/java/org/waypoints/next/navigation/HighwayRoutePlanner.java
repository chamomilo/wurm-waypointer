package org.waypoints.next.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Global shortest-time search on the published highway graph.
 *
 * <p>Published-Highway distance costs one third of off-road distance. The virtual start and
 * target connect to every ordinary road segment and published endpoint;
 * bridge and tunnel
 * segments remain atomic, so they can only be entered at published portals.</p>
 */
public final class HighwayRoutePlanner {
    public static final String ALGORITHM_VERSION = "highway-graph-a-star-v9";
    private static final float HIGHWAY_TIME_PER_TILE = 1.0f / 3.0f;
    private static final float CONFIRMED_SPECIAL_MAXIMUM_PROJECTION_TILES =
            1.5f;
    private HighwayTileIndex compiledIndex;
    private List<Node> compiledNodes = Collections.emptyList();
    private Map<Long, Integer> compiledNodeByCoordinate =
            Collections.emptyMap();

    private enum NodeLayer { SURFACE, TUNNEL }

    /** Selects the published network that is physically available to the player. */
    public enum NetworkLayer {
        ALL,
        ROAD,
        SURFACE,
        BRIDGE,
        BRIDGE_ONLY,
        TUNNEL,
        MULTI_LAYER;

        boolean allows(HighwayTileIndex.Kind kind) {
            if (kind == null || kind == HighwayTileIndex.Kind.NONE) return false;
            if (this == ALL || this == MULTI_LAYER) return true;
            if (this == TUNNEL) return kind == HighwayTileIndex.Kind.TUNNEL;
            if (this == BRIDGE_ONLY) {
                return kind == HighwayTileIndex.Kind.BRIDGE;
            }
            if (this == ROAD) return kind == HighwayTileIndex.Kind.ROAD;
            return kind == HighwayTileIndex.Kind.ROAD
                    || kind == HighwayTileIndex.Kind.BRIDGE;
        }

        boolean allowsOccupiedSpecial(HighwayTileIndex.Kind kind) {
            if (this == ALL || this == MULTI_LAYER) return true;
            if (this == TUNNEL) {
                return kind == HighwayTileIndex.Kind.TUNNEL;
            }
            return (this == BRIDGE || this == BRIDGE_ONLY)
                    && kind == HighwayTileIndex.Kind.BRIDGE;
        }

        boolean allowsConfirmedAdjacentProjection(
                HighwayTileIndex.Kind kind) {
            return (this == BRIDGE || this == BRIDGE_ONLY)
                    && kind == HighwayTileIndex.Kind.BRIDGE;
        }
    }

    public static final class TileStep {
        private final int tileX;
        private final int tileY;
        private final HighwayTileIndex.Kind kind;
        private final boolean portal;

        private TileStep(int tileX, int tileY, HighwayTileIndex.Kind kind,
                         boolean portal) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.kind = kind;
            this.portal = portal;
        }

        public int getTileX() { return tileX; }
        public int getTileY() { return tileY; }
        public HighwayTileIndex.Kind getKind() { return kind; }
        public boolean isPortal() { return portal; }
    }

    public static final class Plan {
        private final List<TileStep> highwaySteps;
        private final int entryX;
        private final int entryY;
        private final int exitX;
        private final int exitY;
        private final float estimatedTimeTiles;
        private final float directOffroadTimeTiles;
        private final int expandedNodes;

        private Plan(List<TileStep> highwaySteps, int entryX, int entryY,
                     int exitX, int exitY, float estimatedTimeTiles,
                     float directOffroadTimeTiles, int expandedNodes) {
            this.highwaySteps = Collections.unmodifiableList(
                    new ArrayList<TileStep>(highwaySteps));
            this.entryX = entryX;
            this.entryY = entryY;
            this.exitX = exitX;
            this.exitY = exitY;
            this.estimatedTimeTiles = estimatedTimeTiles;
            this.directOffroadTimeTiles = directOffroadTimeTiles;
            this.expandedNodes = expandedNodes;
        }

        public List<TileStep> getHighwaySteps() { return highwaySteps; }
        public int getEntryX() { return entryX; }
        public int getEntryY() { return entryY; }
        public int getExitX() { return exitX; }
        public int getExitY() { return exitY; }
        public float getEstimatedTimeTiles() { return estimatedTimeTiles; }
        public float getDirectOffroadTimeTiles() { return directOffroadTimeTiles; }
        public int getExpandedNodes() { return expandedNodes; }
        public boolean usesHighway() { return !highwaySteps.isEmpty(); }
    }

    private static final class Edge {
        private final int to;
        private final HighwayTileIndex.Kind kind;
        private final float lengthTiles;
        private final boolean layerTransition;

        private Edge(int to, HighwayTileIndex.Segment segment) {
            this.to = to;
            this.kind = segment.getKind();
            this.lengthTiles = segment.lengthTiles();
            this.layerTransition = false;
        }

        private Edge(int to, HighwayTileIndex.Kind kind, float lengthTiles) {
            this(to, kind, lengthTiles, false);
        }

        private Edge(int to, HighwayTileIndex.Kind kind, float lengthTiles,
                     boolean layerTransition) {
            this.to = to;
            this.kind = kind;
            this.lengthTiles = lengthTiles;
            this.layerTransition = layerTransition;
        }
    }

    private static final class Node {
        private final int x;
        private final int y;
        private final NodeLayer layer;
        private final List<Edge> edges = new ArrayList<Edge>();
        private int endpointKindMask;
        private boolean publishedEndpoint;

        private Node(int x, int y) {
            this(x, y, NodeLayer.SURFACE);
        }

        private Node(int x, int y, NodeLayer layer) {
            this.x = x;
            this.y = y;
            this.layer = layer;
        }

        private void addEndpointKind(HighwayTileIndex.Kind kind) {
            if (kind != null && kind != HighwayTileIndex.Kind.NONE) {
                endpointKindMask |= kindBit(kind);
            }
        }

        private boolean hasEndpointKind(HighwayTileIndex.Kind kind) {
            return kind != null && kind != HighwayTileIndex.Kind.NONE
                    && (endpointKindMask & kindBit(kind)) != 0;
        }
    }

    private static final class Candidate {
        private final int node;
        private final float cost;
        private final int accessX;
        private final int accessY;
        private final int routeX;
        private final int routeY;
        private final HighwayTileIndex.Kind kind;

        private Candidate(int node, float cost, int accessX, int accessY,
                          HighwayTileIndex.Kind kind) {
            this(node, cost, accessX, accessY, accessX, accessY, kind);
        }

        private Candidate(int node, float cost, int accessX, int accessY,
                          int routeX, int routeY,
                          HighwayTileIndex.Kind kind) {
            this.node = node;
            this.cost = cost;
            this.accessX = accessX;
            this.accessY = accessY;
            this.routeX = routeX;
            this.routeY = routeY;
            this.kind = kind;
        }
    }

    private static final class DirectChoice {
        private final List<TileStep> steps;
        private final float cost;

        private DirectChoice(List<TileStep> steps, float cost) {
            this.steps = steps;
            this.cost = cost;
        }
    }

    private static final class Open {
        private final int node;
        private final float g;
        private final float f;

        private Open(int node, float g, float f) {
            this.node = node;
            this.g = g;
            this.f = f;
        }
    }

    private static final class BridgeEnd {
        private final int x;
        private final int y;
        private final int outX;
        private final int outY;

        private BridgeEnd(int x, int y, int outX, int outY) {
            this.x = x;
            this.y = y;
            this.outX = outX;
            this.outY = outY;
        }
    }

    public Plan plan(int startX, int startY, int targetX, int targetY,
                     HighwayTileIndex index) {
        return plan(startX, startY, targetX, targetY, index,
                NetworkLayer.ALL);
    }

    public Plan plan(int startX, int startY, int targetX, int targetY,
                     HighwayTileIndex index, NetworkLayer layer) {
        return plan(startX, startY, targetX, targetY, index, true,
                layer == null ? NetworkLayer.ALL : layer);
    }

    /**
     * Returns the best published route even when it is a geometric detour.
     * Callers use this only after terrain A* proves the direct alternative
     * impassable; a bridge can be necessary without looking faster than a
     * straight line that crosses water.
     */
    public Plan planIncludingNecessaryDetours(int startX, int startY,
                                               int targetX, int targetY,
                                               HighwayTileIndex index) {
        return planIncludingNecessaryDetours(startX, startY, targetX,
                targetY, index, NetworkLayer.ALL);
    }

    public Plan planIncludingNecessaryDetours(int startX, int startY,
                                               int targetX, int targetY,
                                               HighwayTileIndex index,
                                               NetworkLayer layer) {
        return plan(startX, startY, targetX, targetY, index, false,
                layer == null ? NetworkLayer.ALL : layer);
    }

    /**
     * Compares the complete published route across explicit surface/tunnel
     * transitions. Identical map coordinates on different layers remain
     * separate graph nodes; only verified tunnel entrances connect them.
     */
    public Plan planAcrossLayers(int startX, int startY,
                                 boolean startUnderground,
                                 int targetX, int targetY,
                                 boolean targetUnderground,
                                 HighwayTileIndex index,
                                 boolean requireMeaningfulSaving) {
        NetworkLayer startAccess = startUnderground
                ? NetworkLayer.TUNNEL : NetworkLayer.SURFACE;
        NetworkLayer goalAccess = targetUnderground
                ? NetworkLayer.TUNNEL : NetworkLayer.SURFACE;
        return plan(startX, startY, targetX, targetY, index,
                requireMeaningfulSaving, NetworkLayer.MULTI_LAYER,
                startAccess, goalAccess, false);
    }

    /** Returns the part that can be rendered before entering a tunnel. */
    public Plan leadingSurfaceStage(Plan complete) {
        return leadingStage(complete, false);
    }

    /** Returns the occupied tunnel part up to its selected surface exit. */
    public Plan leadingTunnelStage(Plan complete) {
        return leadingStage(complete, true);
    }

    public static boolean containsKind(Plan plan,
                                       HighwayTileIndex.Kind kind) {
        if (plan == null || kind == null) return false;
        for (TileStep step : plan.highwaySteps) {
            if (step.kind == kind) return true;
        }
        return false;
    }

    /**
     * Plans from a known underground target back to a real tunnel entrance
     * near the surface position. Goal candidates are terminal tunnel portals
     * with an adjacent published surface road; an arbitrary tunnel endpoint
     * under a mountain is not treated as a surface entrance.
     */
    public Plan planTunnelToSurfacePortal(int tunnelX, int tunnelY,
                                          int surfaceX, int surfaceY,
                                          HighwayTileIndex index) {
        return plan(tunnelX, tunnelY, surfaceX, surfaceY, index, false,
                NetworkLayer.TUNNEL, true);
    }

    /**
     * Chooses the correct ramp using the complete surface road/bridge graph,
     * then returns only the currently occupied bridge component and one
     * surface hand-off tile.  Looking only at the nearest ramp is incorrect
     * for bridge chains and bridge junctions: the geometrically closest ramp
     * can lead away from the destination.
     */
    public Plan planBridgeToSurfacePortal(int bridgeX, int bridgeY,
                                          int towardX, int towardY,
                                          HighwayTileIndex index) {
        Plan complete = planFromOccupiedBridgeToTarget(bridgeX, bridgeY,
                towardX, towardY, index);
        Plan stage = leadingBridgeStage(complete);
        if (stage.usesHighway()) return stage;

        // A disconnected or incomplete publication can still describe the
        // occupied span. Keep the old bridge-only escape as a safe fallback.
        return plan(bridgeX, bridgeY, towardX, towardY, index, false,
                NetworkLayer.BRIDGE_ONLY, false);
    }

    /** Full mixed-layer route, with start access restricted to the live deck. */
    public Plan planFromOccupiedBridgeToTarget(int bridgeX, int bridgeY,
                                                int targetX, int targetY,
                                                HighwayTileIndex index) {
        return plan(bridgeX, bridgeY, targetX, targetY, index,
                false, NetworkLayer.SURFACE, NetworkLayer.BRIDGE_ONLY,
                NetworkLayer.SURFACE, false);
    }

    private Plan plan(int startX, int startY, int targetX, int targetY,
                       HighwayTileIndex index, boolean requireMeaningfulSaving,
                       NetworkLayer layer) {
        return plan(startX, startY, targetX, targetY, index,
                requireMeaningfulSaving, layer, false);
    }

    private Plan plan(int startX, int startY, int targetX, int targetY,
                      HighwayTileIndex index, boolean requireMeaningfulSaving,
                      NetworkLayer layer,
                      boolean tunnelGoalMustTouchSurfaceRoad) {
        return plan(startX, startY, targetX, targetY, index,
                requireMeaningfulSaving, layer, layer, layer,
                tunnelGoalMustTouchSurfaceRoad);
    }

    private Plan plan(int startX, int startY, int targetX, int targetY,
                      HighwayTileIndex index, boolean requireMeaningfulSaving,
                      NetworkLayer graphLayer, NetworkLayer startAccessLayer,
                      NetworkLayer goalAccessLayer,
                      boolean tunnelGoalMustTouchSurfaceRoad) {
        float direct = distance(startX, startY, targetX, targetY);
        if (index == null || index.isEmpty() || index.getSegments().isEmpty()) {
            return empty(direct);
        }
        List<Node> nodes = graph(index);
        if (nodes.isEmpty()) return empty(direct);

        List<Candidate> starts = accesses(index, startX, startY,
                startAccessLayer,
                false);
        List<Candidate> goals = accesses(index, targetX, targetY,
                goalAccessLayer,
                tunnelGoalMustTouchSurfaceRoad);
        Map<Integer, Candidate> goalConnector =
                new HashMap<Integer, Candidate>();
        for (Candidate candidate : goals) {
            Candidate previous = goalConnector.get(Integer.valueOf(candidate.node));
            if (previous == null || candidate.cost < previous.cost) {
                goalConnector.put(Integer.valueOf(candidate.node),
                        candidate);
            }
        }

        float[] g = new float[nodes.size()];
        int[] parent = new int[nodes.size()];
        Edge[] parentEdge = new Edge[nodes.size()];
        Candidate[] startAccess = new Candidate[nodes.size()];
        java.util.Arrays.fill(g, Float.POSITIVE_INFINITY);
        java.util.Arrays.fill(parent, -1);
        PriorityQueue<Open> open = new PriorityQueue<Open>(64,
                new Comparator<Open>() {
                    @Override public int compare(Open left, Open right) {
                        return Float.compare(left.f, right.f);
                    }
                });
        for (Candidate candidate : starts) {
            if (candidate.cost < g[candidate.node]) {
                g[candidate.node] = candidate.cost;
                startAccess[candidate.node] = candidate;
                open.add(new Open(candidate.node, candidate.cost,
                        candidate.cost + heuristic(nodes.get(candidate.node),
                                 targetX, targetY)));
            }
        }
        int bestGoal = -1;
        Candidate bestGoalAccess = null;
        DirectChoice directChoice = startAccessLayer.allows(
                HighwayTileIndex.Kind.ROAD)
                && goalAccessLayer.allows(HighwayTileIndex.Kind.ROAD)
                ? directRoadChoice(index, startX, startY, targetX, targetY,
                graphLayer) : null;
        float bestTotal = directChoice == null
                ? Float.POSITIVE_INFINITY : directChoice.cost;
        int expanded = 0;
        while (!open.isEmpty()) {
            Open current = open.poll();
            if (current.g != g[current.node]) continue;
            if (current.f >= bestTotal) break;
            expanded++;
            Candidate connector = goalConnector.get(Integer.valueOf(current.node));
            if (connector != null) {
                float total = current.g + connector.cost;
                if (total < bestTotal) {
                    bestTotal = total;
                    bestGoal = current.node;
                    bestGoalAccess = connector;
                }
            }
            for (Edge edge : nodes.get(current.node).edges) {
                if (!graphLayer.allows(edge.kind)) continue;
                if (edge.layerTransition
                        && graphLayer != NetworkLayer.MULTI_LAYER) continue;
                float nextG = current.g + edge.lengthTiles
                        * HIGHWAY_TIME_PER_TILE;
                if (nextG >= g[edge.to]) continue;
                g[edge.to] = nextG;
                parent[edge.to] = current.node;
                parentEdge[edge.to] = edge;
                open.add(new Open(edge.to, nextG, nextG
                        + heuristic(nodes.get(edge.to), targetX, targetY)));
            }
        }
        // A road route must be materially faster, not merely a tiny numerical win.
        if (requireMeaningfulSaving && bestTotal >= direct * 0.95f) {
            return empty(direct);
        }
        if (bestGoal < 0) {
            if (directChoice == null || directChoice.steps.isEmpty()) {
                return empty(direct);
            }
            TileStep first = directChoice.steps.get(0);
            TileStep last = directChoice.steps.get(directChoice.steps.size() - 1);
            return new Plan(directChoice.steps, first.tileX, first.tileY,
                    last.tileX, last.tileY, bestTotal, direct, expanded);
        }

        List<Integer> reversed = new ArrayList<Integer>();
        int cursor = bestGoal;
        while (cursor >= 0) {
            reversed.add(Integer.valueOf(cursor));
            cursor = parent[cursor];
        }
        Collections.reverse(reversed);
        List<TileStep> steps = new ArrayList<TileStep>();
        Candidate firstAccess = startAccess[reversed.get(0).intValue()];
        if (firstAccess != null) {
            if (firstAccess.routeX != firstAccess.accessX
                    || firstAccess.routeY != firstAccess.accessY) {
                appendRaster(steps,
                        new Node(firstAccess.routeX, firstAccess.routeY),
                        new Node(firstAccess.accessX, firstAccess.accessY),
                        firstAccess.kind);
            }
            appendRaster(steps,
                    new Node(firstAccess.accessX, firstAccess.accessY),
                    nodes.get(reversed.get(0).intValue()), firstAccess.kind);
        }
        for (int i = 1; i < reversed.size(); i++) {
            int fromIndex = reversed.get(i - 1).intValue();
            int toIndex = reversed.get(i).intValue();
            Edge edge = parentEdge[toIndex];
            if (edge.layerTransition) {
                appendLayerTransition(steps, nodes.get(fromIndex),
                        nodes.get(toIndex), index);
            } else {
                appendRaster(steps, nodes.get(fromIndex), nodes.get(toIndex),
                        edge.kind);
            }
        }
        if (bestGoalAccess != null) {
            appendRaster(steps, nodes.get(bestGoal),
                    new Node(bestGoalAccess.accessX, bestGoalAccess.accessY),
                    bestGoalAccess.kind);
            if (bestGoalAccess.routeX != bestGoalAccess.accessX
                    || bestGoalAccess.routeY != bestGoalAccess.accessY) {
                appendRaster(steps,
                        new Node(bestGoalAccess.accessX,
                                bestGoalAccess.accessY),
                        new Node(bestGoalAccess.routeX,
                                bestGoalAccess.routeY),
                        bestGoalAccess.kind);
            }
        }
        if (steps.isEmpty()) return empty(direct);
        applyAuthoritativePortals(steps, index);
        TileStep first = steps.get(0);
        TileStep last = steps.get(steps.size() - 1);
        return new Plan(steps, first.tileX, first.tileY, last.tileX, last.tileY,
                bestTotal, direct, expanded);
    }

    private static Plan leadingBridgeStage(Plan complete) {
        if (complete == null || complete.highwaySteps.isEmpty()) {
            return empty(complete == null ? 0.0f
                    : complete.directOffroadTimeTiles);
        }
        List<TileStep> stage = new ArrayList<TileStep>();
        boolean bridgeSeen = false;
        for (TileStep step : complete.highwaySteps) {
            if (!bridgeSeen && step.kind != HighwayTileIndex.Kind.BRIDGE) {
                return empty(complete.directOffroadTimeTiles);
            }
            stage.add(step);
            if (step.kind == HighwayTileIndex.Kind.BRIDGE) {
                bridgeSeen = true;
                continue;
            }
            // One ordinary-road tile makes the direction of the ramp visible
            // even when the cart already occupies the terminal bridge tile.
            break;
        }
        if (!bridgeSeen || stage.isEmpty()) {
            return empty(complete.directOffroadTimeTiles);
        }
        TileStep first = stage.get(0);
        TileStep last = stage.get(stage.size() - 1);
        return new Plan(stage, first.tileX, first.tileY, last.tileX,
                last.tileY, complete.estimatedTimeTiles,
                complete.directOffroadTimeTiles, complete.expandedNodes);
    }

    private static Plan leadingStage(Plan complete, boolean tunnel) {
        if (complete == null || complete.highwaySteps.isEmpty()) {
            return empty(complete == null ? 0.0f
                    : complete.directOffroadTimeTiles);
        }
        List<TileStep> stage = new ArrayList<TileStep>();
        for (TileStep step : complete.highwaySteps) {
            boolean tunnelStep = step.kind == HighwayTileIndex.Kind.TUNNEL;
            if (tunnelStep != tunnel) break;
            stage.add(step);
        }
        if (stage.isEmpty()) return empty(complete.directOffroadTimeTiles);
        TileStep first = stage.get(0);
        TileStep last = stage.get(stage.size() - 1);
        return new Plan(stage, first.tileX, first.tileY, last.tileX,
                last.tileY, complete.estimatedTimeTiles,
                complete.directOffroadTimeTiles, complete.expandedNodes);
    }

    private static int node(List<Node> nodes, Map<Long, Integer> index,
                            int x, int y, NodeLayer layer) {
        Long key = Long.valueOf(graphKey(x, y, layer));
        Integer existing = index.get(key);
        if (existing != null) return existing.intValue();
        int created = nodes.size();
        nodes.add(new Node(x, y, layer));
        index.put(key, Integer.valueOf(created));
        return created;
    }

    private List<Candidate> accesses(HighwayTileIndex index, int x, int y,
                                     NetworkLayer layer,
                                     boolean tunnelMustTouchSurfaceRoad) {
        List<Candidate> candidates = new ArrayList<Candidate>(
                index.getSegments().size() * 2);
        for (HighwayTileIndex.Segment segment : index.getSegments()) {
            if (!layer.allows(segment.getKind())) continue;
            NodeLayer nodeLayer = nodeLayer(segment.getKind());
            int startNode = compiledNodeByCoordinate.get(Long.valueOf(
                    graphKey(segment.getStartX(), segment.getStartY(),
                            nodeLayer))).intValue();
            int endNode = compiledNodeByCoordinate.get(Long.valueOf(
                    graphKey(segment.getEndX(), segment.getEndY(),
                            nodeLayer))).intValue();
            if (segment.getKind() == HighwayTileIndex.Kind.ROAD) {
                int[] access = projection(segment, x, y);
                float offroad = distance(x, y, access[0], access[1]);
                candidates.add(new Candidate(startNode, offroad + distance(
                        access[0], access[1], segment.getStartX(),
                        segment.getStartY()) * HIGHWAY_TIME_PER_TILE,
                        access[0], access[1], segment.getKind()));
                candidates.add(new Candidate(endNode, offroad + distance(
                        access[0], access[1], segment.getEndX(),
                        segment.getEndY()) * HIGHWAY_TIME_PER_TILE,
                        access[0], access[1], segment.getKind()));
            } else {
                int[] access = projection(segment, x, y);
                HighwayTileIndex.Tile occupied = index.get(x, y);
                if (!tunnelMustTouchSurfaceRoad
                        && layer.allowsOccupiedSpecial(segment.getKind())
                        && (occupied.hasKind(segment.getKind())
                        || layer.allowsConfirmedAdjacentProjection(
                        segment.getKind()))
                        && distance(x, y, access[0], access[1])
                        <= CONFIRMED_SPECIAL_MAXIMUM_PROJECTION_TILES) {
                    float ontoSegment = distance(x, y, access[0], access[1]);
                    candidates.add(new Candidate(startNode, ontoSegment
                            + distance(access[0], access[1],
                            segment.getStartX(), segment.getStartY())
                            * HIGHWAY_TIME_PER_TILE,
                            access[0], access[1], x, y,
                            segment.getKind()));
                    candidates.add(new Candidate(endNode, ontoSegment
                            + distance(access[0], access[1],
                            segment.getEndX(), segment.getEndY())
                            * HIGHWAY_TIME_PER_TILE,
                            access[0], access[1], x, y,
                            segment.getKind()));
                    continue;
                }
                if (allowsDirectSpecialAccess(index, startNode,
                        segment.getStartX(), segment.getStartY(),
                        segment.getKind(), layer)
                        && (!tunnelMustTouchSurfaceRoad
                        || touchesSurfaceRoad(index, segment.getStartX(),
                        segment.getStartY()))) {
                    candidates.add(new Candidate(startNode, distance(x, y,
                            segment.getStartX(), segment.getStartY()),
                            segment.getStartX(), segment.getStartY(),
                            segment.getKind()));
                }
                if (allowsDirectSpecialAccess(index, endNode,
                        segment.getEndX(), segment.getEndY(),
                        segment.getKind(), layer)
                        && (!tunnelMustTouchSurfaceRoad
                        || touchesSurfaceRoad(index, segment.getEndX(),
                        segment.getEndY()))) {
                    candidates.add(new Candidate(endNode, distance(x, y,
                            segment.getEndX(), segment.getEndY()),
                            segment.getEndX(), segment.getEndY(),
                            segment.getKind()));
                }
            }
        }
        return candidates;
    }

    private static boolean touchesSurfaceRoad(HighwayTileIndex index,
                                              int tileX, int tileY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (index.get(tileX + dx, tileY + dy).hasKind(
                        HighwayTileIndex.Kind.ROAD)) return true;
            }
        }
        return false;
    }

    private boolean allowsDirectSpecialAccess(HighwayTileIndex index,
                                              int nodeIndex,
                                              int tileX, int tileY,
                                              HighwayTileIndex.Kind kind,
                                              NetworkLayer layer) {
        if (index.get(tileX, tileY).isPortal(kind)) return true;
        Node node = compiledNodes.get(nodeIndex);
        int specialConnections = 0;
        for (Edge edge : node.edges) {
            if (!layer.allows(edge.kind)) continue;
            if (edge.kind == HighwayTileIndex.Kind.ROAD) return false;
            if (edge.kind == kind) specialConnections++;
        }
        // Internal joins in a segmented bridge/tunnel are not real portals.
        return specialConnections <= 1;
    }

    private static DirectChoice directRoadChoice(HighwayTileIndex index,
                                                   int startX, int startY,
                                                   int targetX, int targetY,
                                                   NetworkLayer layer) {
        if (!layer.allows(HighwayTileIndex.Kind.ROAD)) return null;
        DirectChoice best = null;
        for (HighwayTileIndex.Segment segment : index.getSegments()) {
            if (segment.getKind() != HighwayTileIndex.Kind.ROAD) continue;
            int[] entry = projection(segment, startX, startY);
            int[] exit = projection(segment, targetX, targetY);
            float cost = distance(startX, startY, entry[0], entry[1])
                    + distance(entry[0], entry[1], exit[0], exit[1])
                    * HIGHWAY_TIME_PER_TILE
                    + distance(exit[0], exit[1], targetX, targetY);
            if (best != null && cost >= best.cost) continue;
            List<TileStep> steps = new ArrayList<TileStep>();
            appendRaster(steps, new Node(entry[0], entry[1]),
                    new Node(exit[0], exit[1]), HighwayTileIndex.Kind.ROAD);
            best = new DirectChoice(steps, cost);
        }
        return best;
    }

    private static int[] projection(HighwayTileIndex.Segment segment,
                                    int x, int y) {
        double dx = segment.getEndX() - segment.getStartX();
        double dy = segment.getEndY() - segment.getStartY();
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared <= 0.0d ? 0.0d
                : ((x - segment.getStartX()) * dx
                + (y - segment.getStartY()) * dy) / lengthSquared;
        t = Math.max(0.0d, Math.min(1.0d, t));
        return new int[] {
                (int) Math.round(segment.getStartX() + t * dx),
                (int) Math.round(segment.getStartY() + t * dy)
        };
    }

    /** Cache the immutable topology; only start/target costs change per replan. */
    private synchronized List<Node> graph(HighwayTileIndex index) {
        if (compiledIndex == index) return compiledNodes;
        List<Node> nodes = new ArrayList<Node>();
        Map<Long, Integer> nodeByCoordinate = new HashMap<Long, Integer>();
        // Create all authoritative endpoints first. Road segments can then be
        // split where a T-branch endpoint touches their middle.
        for (HighwayTileIndex.Segment segment : index.getSegments()) {
            NodeLayer layer = nodeLayer(segment.getKind());
            int from = node(nodes, nodeByCoordinate, segment.getStartX(),
                    segment.getStartY(), layer);
            int to = node(nodes, nodeByCoordinate, segment.getEndX(),
                    segment.getEndY(), layer);
            nodes.get(from).addEndpointKind(segment.getKind());
            nodes.get(to).addEndpointKind(segment.getKind());
            nodes.get(from).publishedEndpoint = true;
            nodes.get(to).publishedEndpoint = true;
        }
        java.util.Set<Long> roadCrossings = roadCrossings(
                index.getSegments());
        for (HighwayTileIndex.Segment segment : index.getSegments()) {
            if (segment.getKind() == HighwayTileIndex.Kind.ROAD) {
                connectRoadThroughJunctions(segment, nodes,
                        nodeByCoordinate, roadCrossings);
            } else {
                int from = nodeByCoordinate.get(Long.valueOf(graphKey(
                        segment.getStartX(), segment.getStartY(),
                        nodeLayer(segment.getKind())))).intValue();
                int to = nodeByCoordinate.get(Long.valueOf(graphKey(
                        segment.getEndX(), segment.getEndY(),
                        nodeLayer(segment.getKind())))).intValue();
                nodes.get(from).edges.add(new Edge(to, segment));
                nodes.get(to).edges.add(new Edge(from, segment));
            }
        }
        connectAdjacentPublishedEndpoints(nodes, nodeByCoordinate);
        connectShortBridgePlatformGaps(index.getSegments(), nodes,
                nodeByCoordinate);
        connectTunnelEntrances(index, nodes, nodeByCoordinate);
        compiledIndex = index;
        compiledNodes = Collections.unmodifiableList(nodes);
        compiledNodeByCoordinate = Collections.unmodifiableMap(
                new HashMap<Long, Integer>(nodeByCoordinate));
        return compiledNodes;
    }

    /**
     * Sklotopolis segments use inclusive tile coordinates. Consequently a
     * continuous published way commonly ends at x and resumes at x + 1. Only
     * published endpoints are joined here, so a geometric crossing in the
     * middle of two segments still does not create an implicit intersection.
     */
    private static void connectAdjacentPublishedEndpoints(
            List<Node> nodes, Map<Long, Integer> nodeByCoordinate) {
        for (int from = 0; from < nodes.size(); from++) {
            Node source = nodes.get(from);
            if (!source.publishedEndpoint) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Integer adjacent = nodeByCoordinate.get(Long.valueOf(
                            graphKey(source.x + dx, source.y + dy,
                                    source.layer)));
                    if (adjacent == null || adjacent.intValue() <= from) continue;
                    Node target = nodes.get(adjacent.intValue());
                    if (!target.publishedEndpoint) continue;
                    float length = (float) Math.hypot(dx, dy);
                    connectAdjacentKinds(source, from, target,
                            adjacent.intValue(), length);
                }
            }
        }
    }

    /**
     * Some server maps omit a very short, flat Highway platform between two
     * bridge ramps.  Infer only the conservative case: collinear bridge ends
     * whose open ends face each other with at most three intervening tiles.
     * The inferred edge is ROAD, so live layer changes still split rendering
     * at the real deck boundary.
     */
    private static void connectShortBridgePlatformGaps(
            List<HighwayTileIndex.Segment> segments, List<Node> nodes,
            Map<Long, Integer> nodeByCoordinate) {
        List<BridgeEnd> ends = new ArrayList<BridgeEnd>();
        for (HighwayTileIndex.Segment segment : segments) {
            if (segment.getKind() != HighwayTileIndex.Kind.BRIDGE) continue;
            ends.add(new BridgeEnd(segment.getStartX(), segment.getStartY(),
                    direction(segment.getStartX() - segment.getEndX()),
                    direction(segment.getStartY() - segment.getEndY())));
            ends.add(new BridgeEnd(segment.getEndX(), segment.getEndY(),
                    direction(segment.getEndX() - segment.getStartX()),
                    direction(segment.getEndY() - segment.getStartY())));
        }
        for (int i = 0; i < ends.size(); i++) {
            BridgeEnd left = ends.get(i);
            for (int j = i + 1; j < ends.size(); j++) {
                BridgeEnd right = ends.get(j);
                int dx = right.x - left.x;
                int dy = right.y - left.y;
                int distance = Math.abs(dx) + Math.abs(dy);
                if ((dx != 0 && dy != 0) || distance < 2 || distance > 4) {
                    continue;
                }
                int directionX = direction(dx);
                int directionY = direction(dy);
                if (left.outX != directionX || left.outY != directionY
                        || right.outX != -directionX
                        || right.outY != -directionY) continue;
                Integer from = nodeByCoordinate.get(Long.valueOf(graphKey(
                        left.x, left.y, NodeLayer.SURFACE)));
                Integer to = nodeByCoordinate.get(Long.valueOf(graphKey(
                        right.x, right.y, NodeLayer.SURFACE)));
                if (from == null || to == null) continue;
                connect(nodes.get(from.intValue()), from.intValue(),
                        nodes.get(to.intValue()), to.intValue(),
                        HighwayTileIndex.Kind.ROAD, distance);
            }
        }
    }

    /**
     * Adds the only legal surface/tunnel layer changes. A terminal tunnel
     * endpoint must face a published surface-road endpoint at the same or an
     * adjacent coordinate. Mid-span geometric crossings therefore never
     * become junctions, even when both rasters occupy the same tile.
     */
    private static void connectTunnelEntrances(HighwayTileIndex index,
                                               List<Node> nodes,
                                               Map<Long, Integer> nodeByCoordinate) {
        for (int tunnelIndex = 0; tunnelIndex < nodes.size(); tunnelIndex++) {
            Node tunnel = nodes.get(tunnelIndex);
            if (tunnel.layer != NodeLayer.TUNNEL
                    || !tunnel.hasEndpointKind(HighwayTileIndex.Kind.TUNNEL)
                    || !index.get(tunnel.x, tunnel.y).isPortal(
                    HighwayTileIndex.Kind.TUNNEL)) continue;
            Edge inwardEdge = onlyTunnelEdge(tunnel);
            if (inwardEdge == null) continue;
            Node inward = nodes.get(inwardEdge.to);
            int inwardX = direction(inward.x - tunnel.x);
            int inwardY = direction(inward.y - tunnel.y);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Integer surfaceIndex = nodeByCoordinate.get(Long.valueOf(
                            graphKey(tunnel.x + dx, tunnel.y + dy,
                                    NodeLayer.SURFACE)));
                    if (surfaceIndex == null) continue;
                    Node surface = nodes.get(surfaceIndex.intValue());
                    if (!surface.hasEndpointKind(HighwayTileIndex.Kind.ROAD)) {
                        continue;
                    }
                    if (!facesOutOfTunnel(surface, nodes, dx, dy,
                            inwardX, inwardY)) continue;
                    float length = Math.max(1.0f,
                            (float) Math.hypot(dx, dy));
                    connectLayerTransition(tunnel, tunnelIndex, surface,
                            surfaceIndex.intValue(), length);
                }
            }
        }
    }

    private static Edge onlyTunnelEdge(Node node) {
        Edge found = null;
        for (Edge edge : node.edges) {
            if (edge.layerTransition
                    || edge.kind != HighwayTileIndex.Kind.TUNNEL) continue;
            if (found != null) return null;
            found = edge;
        }
        return found;
    }

    private static boolean facesOutOfTunnel(Node surface, List<Node> nodes,
                                            int portalToSurfaceX,
                                            int portalToSurfaceY,
                                            int inwardX, int inwardY) {
        if (portalToSurfaceX != 0 || portalToSurfaceY != 0) {
            return portalToSurfaceX * inwardX
                    + portalToSurfaceY * inwardY <= 0;
        }
        for (Edge edge : surface.edges) {
            if (edge.layerTransition
                    || edge.kind != HighwayTileIndex.Kind.ROAD) continue;
            Node other = nodes.get(edge.to);
            int roadX = direction(other.x - surface.x);
            int roadY = direction(other.y - surface.y);
            if (roadX * inwardX + roadY * inwardY < 0) return true;
        }
        return false;
    }

    private static void connectLayerTransition(Node tunnel, int tunnelIndex,
                                               Node surface, int surfaceIndex,
                                               float length) {
        tunnel.edges.add(new Edge(surfaceIndex, HighwayTileIndex.Kind.TUNNEL,
                length, true));
        surface.edges.add(new Edge(tunnelIndex, HighwayTileIndex.Kind.TUNNEL,
                length, true));
    }

    private static int direction(int value) {
        return value == 0 ? 0 : value < 0 ? -1 : 1;
    }

    /**
     * Finds same-layer surface intersections without turning the complete
     * raster into an expensive graph. Only shared road coordinates become
     * split nodes; bridges and tunnels remain atomic and never acquire an
     * implicit connection where one layer merely passes over another.
     */
    private static java.util.Set<Long> roadCrossings(
            List<HighwayTileIndex.Segment> segments) {
        Map<Long, Integer> owner = new HashMap<Long, Integer>();
        java.util.Set<Long> crossings = new java.util.HashSet<Long>();
        for (int segmentIndex = 0; segmentIndex < segments.size();
             segmentIndex++) {
            HighwayTileIndex.Segment segment = segments.get(segmentIndex);
            if (segment.getKind() != HighwayTileIndex.Kind.ROAD) continue;
            for (Long coordinate : rasterCoordinates(segment)) {
                Integer previous = owner.get(coordinate);
                if (previous == null) {
                    owner.put(coordinate, Integer.valueOf(segmentIndex));
                } else if (previous.intValue() != segmentIndex) {
                    crossings.add(coordinate);
                }
            }
        }
        return crossings;
    }

    private static void connectRoadThroughJunctions(
            HighwayTileIndex.Segment segment, List<Node> nodes,
            Map<Long, Integer> nodeByCoordinate,
            java.util.Set<Long> roadCrossings) {
        List<Long> raster = rasterCoordinates(segment);
        int previousNode = -1;
        for (int i = 0; i < raster.size(); i++) {
            Long coordinate = raster.get(i);
            Integer existing = nodeByCoordinate.get(Long.valueOf(graphKey(
                    (int) (coordinate.longValue() >> 32),
                    (int) coordinate.longValue(), NodeLayer.SURFACE)));
            boolean endpoint = i == 0 || i == raster.size() - 1;
            boolean publishedBranch = existing != null
                    && nodes.get(existing.intValue()).publishedEndpoint;
            if (!endpoint && !publishedBranch
                    && !roadCrossings.contains(coordinate)) continue;
            int x = (int) (coordinate.longValue() >> 32);
            int y = (int) coordinate.longValue();
            int currentNode = node(nodes, nodeByCoordinate, x, y,
                    NodeLayer.SURFACE);
            if (previousNode >= 0 && previousNode != currentNode) {
                Node from = nodes.get(previousNode);
                Node to = nodes.get(currentNode);
                float length = distance(from.x, from.y, to.x, to.y);
                connect(from, previousNode, to, currentNode,
                        HighwayTileIndex.Kind.ROAD, length);
            }
            previousNode = currentNode;
        }
    }

    private static List<Long> rasterCoordinates(
            HighwayTileIndex.Segment segment) {
        List<Long> result = new ArrayList<Long>();
        int x = segment.getStartX();
        int y = segment.getStartY();
        int targetX = segment.getEndX();
        int targetY = segment.getEndY();
        int dx = Math.abs(targetX - x);
        int dy = Math.abs(targetY - y);
        int stepX = x < targetX ? 1 : -1;
        int stepY = y < targetY ? 1 : -1;
        int error = dx - dy;
        while (true) {
            result.add(Long.valueOf(key(x, y)));
            if (x == targetX && y == targetY) break;
            int doubled = error * 2;
            if (doubled > -dy) { error -= dy; x += stepX; }
            if (doubled < dx) { error += dx; y += stepY; }
        }
        return result;
    }

    private static void connectAdjacentKinds(Node source, int sourceIndex,
                                             Node target, int targetIndex,
                                             float length) {
        if (source.hasEndpointKind(HighwayTileIndex.Kind.TUNNEL)
                && target.hasEndpointKind(HighwayTileIndex.Kind.TUNNEL)) {
            connect(source, sourceIndex, target, targetIndex,
                    HighwayTileIndex.Kind.TUNNEL, length);
        }
        boolean sourceBridge = source.hasEndpointKind(
                HighwayTileIndex.Kind.BRIDGE);
        boolean targetBridge = target.hasEndpointKind(
                HighwayTileIndex.Kind.BRIDGE);
        if (sourceBridge && targetBridge) {
            connect(source, sourceIndex, target, targetIndex,
                    HighwayTileIndex.Kind.BRIDGE, length);
        }
        boolean sourceRoad = source.hasEndpointKind(HighwayTileIndex.Kind.ROAD);
        boolean targetRoad = target.hasEndpointKind(HighwayTileIndex.Kind.ROAD);
        if ((sourceRoad && (targetRoad || targetBridge))
                || (targetRoad && sourceBridge)) {
            // The one-coordinate published gap between a surface road and a
            // bridge ramp is rendered and validated as ordinary Highway.
            connect(source, sourceIndex, target, targetIndex,
                    HighwayTileIndex.Kind.ROAD, length);
        }
    }

    private static void connect(Node source, int sourceIndex, Node target,
                                int targetIndex, HighwayTileIndex.Kind kind,
                                float length) {
        source.edges.add(new Edge(targetIndex, kind, length));
        target.edges.add(new Edge(sourceIndex, kind, length));
    }

    private static int kindBit(HighwayTileIndex.Kind kind) {
        return 1 << kind.ordinal();
    }

    private static void appendRaster(List<TileStep> target, Node from, Node to,
                                     HighwayTileIndex.Kind kind) {
        int x = from.x;
        int y = from.y;
        int dx = Math.abs(to.x - x);
        int dy = Math.abs(to.y - y);
        int sx = x < to.x ? 1 : -1;
        int sy = y < to.y ? 1 : -1;
        int error = dx - dy;
        while (true) {
            boolean endpoint = (x == from.x && y == from.y)
                    || (x == to.x && y == to.y);
            boolean portal = endpoint && special(kind);
            if (target.isEmpty() || target.get(target.size() - 1).tileX != x
                    || target.get(target.size() - 1).tileY != y) {
                target.add(new TileStep(x, y, kind, portal));
            } else {
                int lastIndex = target.size() - 1;
                TileStep existing = target.get(lastIndex);
                HighwayTileIndex.Kind merged = special(kind) ? kind
                        : existing.kind;
                target.set(lastIndex, new TileStep(x, y, merged,
                        existing.portal || portal));
            }
            if (x == to.x && y == to.y) break;
            int doubled = error * 2;
            if (doubled > -dy) { error -= dy; x += sx; }
            if (doubled < dx) { error += dx; y += sy; }
        }
    }

    private static void appendLayerTransition(List<TileStep> target,
                                              Node from, Node to,
                                              HighwayTileIndex index) {
        HighwayTileIndex.Kind destinationKind = to.layer == NodeLayer.TUNNEL
                ? HighwayTileIndex.Kind.TUNNEL
                : HighwayTileIndex.Kind.ROAD;
        boolean portal = destinationKind == HighwayTileIndex.Kind.TUNNEL
                && index.get(to.x, to.y).isPortal(
                HighwayTileIndex.Kind.TUNNEL);
        if (target.isEmpty()) {
            HighwayTileIndex.Kind sourceKind = from.layer == NodeLayer.TUNNEL
                    ? HighwayTileIndex.Kind.TUNNEL
                    : HighwayTileIndex.Kind.ROAD;
            target.add(new TileStep(from.x, from.y, sourceKind,
                    sourceKind == HighwayTileIndex.Kind.TUNNEL
                            && index.get(from.x, from.y).isPortal(sourceKind)));
        }
        TileStep last = target.get(target.size() - 1);
        if (last.tileX != to.x || last.tileY != to.y
                || last.kind != destinationKind) {
            target.add(new TileStep(to.x, to.y, destinationKind, portal));
        }
    }

    private static boolean special(HighwayTileIndex.Kind kind) {
        return kind == HighwayTileIndex.Kind.BRIDGE
                || kind == HighwayTileIndex.Kind.TUNNEL;
    }

    private static void applyAuthoritativePortals(
            List<TileStep> steps, HighwayTileIndex index) {
        for (int i = 0; i < steps.size(); i++) {
            TileStep step = steps.get(i);
            boolean portal = special(step.kind)
                    && index.get(step.tileX, step.tileY).isPortal(step.kind);
            if (portal != step.portal) {
                steps.set(i, new TileStep(step.tileX, step.tileY, step.kind,
                        portal));
            }
        }
    }

    private static float heuristic(Node node, int x, int y) {
        return distance(node.x, node.y, x, y) * HIGHWAY_TIME_PER_TILE;
    }

    private static float distance(int x1, int y1, int x2, int y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static long graphKey(int x, int y, NodeLayer layer) {
        long coordinate = key(x, y);
        return layer == NodeLayer.TUNNEL
                ? coordinate | Long.MIN_VALUE : coordinate;
    }

    private static NodeLayer nodeLayer(HighwayTileIndex.Kind kind) {
        return kind == HighwayTileIndex.Kind.TUNNEL
                ? NodeLayer.TUNNEL : NodeLayer.SURFACE;
    }

    private static Plan empty(float direct) {
        return new Plan(Collections.<TileStep>emptyList(), 0, 0, 0, 0,
                direct, direct, 0);
    }
}
