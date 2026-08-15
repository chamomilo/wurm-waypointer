package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.game.World;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.render.WaypointDistanceLabel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact native selector shown when several waypoint bearings overlap. */
final class WaypointClusterPickerWindow extends WWindow implements ButtonListener {
    private static final int ROW_HEIGHT = 25;
    private static final int MIN_CONTENT_WIDTH = 330;
    private static final int MAX_CONTENT_WIDTH = 520;
    private final Map<WButton, NavigationTargetKey> choices =
            new LinkedHashMap<WButton, NavigationTargetKey>();
    private final int preferredWidth;
    private final int preferredHeight;
    private WButton cancel;

    WaypointClusterPickerWindow(List<NavigationTarget> targets, World world) {
        super("wurm-waypointer.cluster-picker", false);
        setTitle("Wurm Waypointer - Choose Waypoint");
        resizable = false;

        WurmLabel prompt = new WurmLabel(targets.size()
                + " overlapping waypoints. Choose one:");
        int contentWidth = Math.max(MIN_CONTENT_WIDTH, prompt.width);
        WurmArrayPanel<FlexComponent> rows = new WurmArrayPanel<FlexComponent>(
                "waypointer.cluster.rows", WurmArrayPanel.DIR_VERTICAL, true);
        for (NavigationTarget target : targets) {
            int distance = WaypointDistanceLabel.roundedMeters(
                    world.getPlayerPosX(), world.getPlayerPosY(),
                    (float) target.getCoordinate().worldX(),
                    (float) target.getCoordinate().worldY());
            boolean compassOnly = target.getMarkerStyle().getWorldStyle()
                    == MarkerStyle.WorldStyle.COMPASS_ONLY;
            String label = (target.isSelected() ? "> " : "")
                    + WaypointDistanceLabel.format(target.getName(), distance)
                    + (compassOnly ? " - Compass only"
                    : target.isWorldBeamVisible() ? " - World on" : " - World off");
            WButton button = new WButton(label, this);
            button.setHoverString(compassOnly
                    ? "Select this compass-only waypoint."
                    : "Select this waypoint and toggle only its world marker.");
            choices.put(button, target.getKey());
            rows.addComponent(button);
            contentWidth = Math.max(contentWidth, button.width);
        }
        contentWidth = Math.min(MAX_CONTENT_WIDTH, contentWidth);
        prompt.setInitialSize(contentWidth, ROW_HEIGHT, false);
        for (WButton button : choices.keySet()) {
            button.setInitialSize(contentWidth, ROW_HEIGHT, false);
        }

        WurmBorderPanel root = new WurmBorderPanel("waypointer.cluster.root");
        root.setComponent(prompt, WurmBorderPanel.NORTH);
        root.setComponent(new WurmScrollPanel(
                "waypointer.cluster.scroll", rows, false, true),
                WurmBorderPanel.CENTER);
        cancel = new WButton("Cancel", this);
        cancel.setInitialSize(contentWidth, ROW_HEIGHT, false);
        root.setComponent(cancel, WurmBorderPanel.SOUTH);
        setComponent(root);

        preferredWidth = contentWidth + 24;
        preferredHeight = Math.min(360,
                58 + ROW_HEIGHT * (targets.size() + 1));
        setInitialSize(preferredWidth, preferredHeight, false);
    }

    int preferredWidth() {
        return preferredWidth;
    }

    int preferredHeight() {
        return preferredHeight;
    }

    @Override public void buttonPressed(WButton button) {
        // Selection happens only after a completed native click.
    }

    @Override public void buttonClicked(WButton button) {
        if (button == cancel) {
            WaypointClusterPickerWindowBridge.closed(this);
            return;
        }
        NavigationTargetKey key = choices.get(button);
        if (key != null) WaypointClusterPickerWindowBridge.chosen(this, key);
    }

    @Override void closePressed() {
        WaypointClusterPickerWindowBridge.closed(this);
    }
}
