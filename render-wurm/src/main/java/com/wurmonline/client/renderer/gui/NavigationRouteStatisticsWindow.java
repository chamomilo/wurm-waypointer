package com.wurmonline.client.renderer.gui;

import org.waypoints.next.navigation.NavigationRouteStatistics;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;
import org.waypoints.next.render.StaticNavigationController;

/** Compact statistics window for the active navigator route. */
final class NavigationRouteStatisticsWindow extends WWindow
        implements ButtonListener {
    static final int PREFERRED_WIDTH = 410;
    static final int PREFERRED_HEIGHT = 254;
    private static final int ROW_HEIGHT = 22;
    private static final int CONTENT_WIDTH = PREFERRED_WIDTH - 28;

    private final WurmLabel waypoint = label("Waypoint: -");
    private final WurmLabel points = label("Full plan points: 0");
    private final WurmLabel length = label("Full route length: 0 m");
    private final WurmLabel duration = label("Full estimated time: planning...");
    private final WurmLabel endpoint = label("Full plan end: planning...");
    private final WurmLabel destination = label("Plan: planning...");
    private final WurmLabel lootMap = label("");
    private final StaticNavigationController navigation;
    private final WButton pulse;
    private final WButton solid;
    private final WButton moving;
    private NavigationRouteVisualStyle visualStyle;

    NavigationRouteStatisticsWindow(StaticNavigationController navigation) {
        super("wurm-waypointer.navigation-route-statistics", false);
        if (navigation == null) throw new IllegalArgumentException(
                "navigation controller is required");
        this.navigation = navigation;
        setTitle("Wurm Waypointer - Route Statistics");
        resizable = false;
        closeable = true;

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
        WurmArrayPanel<FlexComponent> signal =
                new WurmArrayPanel<FlexComponent>(
                        "waypointer.route-statistics.signal", 1);
        signal.setInitialSize(CONTENT_WIDTH, ROW_HEIGHT, false);
        WurmLabel signalLabel = new WurmLabel("Navigation signal:");
        signalLabel.setInitialSize(126, ROW_HEIGHT, false);
        signal.addComponent(signalLabel);
        pulse = button("Pulse", 82,
                "Send a recurring light pulse along the route.");
        solid = button("Solid", 82,
                "Show a continuous line above the route tiles.");
        moving = button("Moving", 82,
                "Show moving dashed light along the route.");
        signal.addComponent(pulse);
        signal.addComponent(solid);
        signal.addComponent(moving);
        rows.addComponent(signal);
        rows.addComponent(label("Speed model: off-road 8 / road 16 / Highway 24 km/h"));
        setComponent(rows);
        setInitialSize(PREFERRED_WIDTH, PREFERRED_HEIGHT, false);
        updateVisualStyle(navigation.getNavigationRouteVisualStyle());
    }

    void updateVisualStyle(NavigationRouteVisualStyle style) {
        NavigationRouteVisualStyle selected = style == null
                ? NavigationRouteVisualStyle.MOVING_DASHES : style;
        if (visualStyle == selected) return;
        visualStyle = selected;
        pulse.setLabel(optionLabel("Pulse",
                selected == NavigationRouteVisualStyle.PULSE), false);
        solid.setLabel(optionLabel("Solid",
                selected == NavigationRouteVisualStyle.SOLID), false);
        moving.setLabel(optionLabel("Moving",
                selected == NavigationRouteVisualStyle.MOVING_DASHES), false);
    }

    @Override public void buttonPressed(WButton button) {
    }

    @Override public void buttonClicked(WButton button) {
        NavigationRouteVisualStyle selected = button == pulse
                ? NavigationRouteVisualStyle.PULSE
                : button == solid ? NavigationRouteVisualStyle.SOLID
                : button == moving ? NavigationRouteVisualStyle.MOVING_DASHES
                : null;
        if (selected == null) return;
        navigation.selectNavigationRouteVisualStyle(selected);
        updateVisualStyle(navigation.getNavigationRouteVisualStyle());
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
        NavigationRouteStatisticsWindowBridge.closed(this);
    }

    private static WurmLabel label(String text) {
        WurmLabel result = new WurmLabel(text);
        result.setInitialSize(CONTENT_WIDTH, ROW_HEIGHT, false);
        return result;
    }

    private WButton button(String text, int width, String hover) {
        WButton result = new WButton(text, this);
        result.setInitialSize(width, ROW_HEIGHT, false);
        result.setHoverString(hover + " The choice is saved in Waypointer settings.");
        return result;
    }

    private static String optionLabel(String text, boolean selected) {
        return selected ? "[" + text + "]" : text;
    }

    private static String clipped(String value, int maximum) {
        String clean = value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
        return clean.length() <= maximum ? clean
                : clean.substring(0, maximum - 3) + "...";
    }
}
