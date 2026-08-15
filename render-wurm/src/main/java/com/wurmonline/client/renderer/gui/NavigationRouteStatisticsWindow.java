package com.wurmonline.client.renderer.gui;

import org.waypoints.next.navigation.NavigationRouteStatistics;

/** Always-on compact debug window for the active navigator route. */
final class NavigationRouteStatisticsWindow extends WWindow {
    static final int PREFERRED_WIDTH = 410;
    static final int PREFERRED_HEIGHT = 232;
    private static final int ROW_HEIGHT = 22;
    private static final int CONTENT_WIDTH = PREFERRED_WIDTH - 28;

    private final WurmLabel waypoint = label("Waypoint: -");
    private final WurmLabel points = label("Full plan points: 0");
    private final WurmLabel length = label("Full route length: 0 m");
    private final WurmLabel duration = label("Full estimated time: planning...");
    private final WurmLabel endpoint = label("Full plan end: planning...");
    private final WurmLabel destination = label("Plan: planning...");
    private final WurmLabel lootMap = label("");

    NavigationRouteStatisticsWindow() {
        super("wurm-waypointer.navigation-route-statistics", false);
        setTitle("Wurm Waypointer - Route Statistics");
        resizable = false;
        closeable = false;

        WurmArrayPanel<FlexComponent> rows =
                new WurmArrayPanel<FlexComponent>(
                        "waypointer.route-statistics.rows",
                        WurmArrayPanel.DIR_VERTICAL, true);
        rows.addComponent(waypoint);
        rows.addComponent(points);
        rows.addComponent(length);
        rows.addComponent(duration);
        rows.addComponent(endpoint);
        rows.addComponent(destination);
        rows.addComponent(lootMap);
        rows.addComponent(label("Speed model: off-road 8 / road 16 / Highway 24 km/h"));
        setComponent(rows);
        setInitialSize(PREFERRED_WIDTH, PREFERRED_HEIGHT, false);
    }

    void update(String waypointName, NavigationRouteStatistics statistics,
                String lootMapSummary) {
        waypoint.setLabel("Waypoint: " + clipped(waypointName, 48));
        lootMap.setLabel(clipped(lootMapSummary, 58));
        if (statistics == null) {
            points.setLabel("Full plan points: 0");
            length.setLabel("Full route length: 0 m");
            duration.setLabel("Full estimated time: planning...");
            endpoint.setLabel("Full plan end: planning...");
            destination.setLabel("Plan: planning...");
            return;
        }
        points.setLabel("Full plan points: " + statistics.getPointCount());
        length.setLabel("Full route length: "
                + Math.round(statistics.getLengthMetres()) + " m");
        duration.setLabel("Full estimated time: "
                + NavigationRouteStatistics.formatDuration(
                statistics.getEstimatedDurationSeconds()));
        endpoint.setLabel(statistics.hasEndpoint()
                ? "Full plan end: tile " + statistics.getEndpointTileX()
                + ", " + statistics.getEndpointTileY()
                : "Full plan end: planning...");
        destination.setLabel(statistics.isReachedTarget()
                ? "Plan: COMPLETE TO TARGET"
                : "Plan: PARTIAL (target "
                + statistics.getTargetTileX() + ", "
                + statistics.getTargetTileY() + ")");
    }

    @Override void closePressed() {
        // This debug window follows the active navigator and closes with it.
    }

    private static WurmLabel label(String text) {
        WurmLabel result = new WurmLabel(text);
        result.setInitialSize(CONTENT_WIDTH, ROW_HEIGHT, false);
        return result;
    }

    private static String clipped(String value, int maximum) {
        String clean = value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
        return clean.length() <= maximum ? clean
                : clean.substring(0, maximum - 3) + "...";
    }
}
