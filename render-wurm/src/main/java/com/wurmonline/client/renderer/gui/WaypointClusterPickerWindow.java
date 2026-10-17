package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.game.World;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.render.WaypointDistanceLabel;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compact native selector shown when several waypoint bearings overlap. */
final class WaypointClusterPickerWindow extends WaypointerUiWindow implements ButtonListener {
    private static final int ROW_HEIGHT = 32;
    private static final int MIN_CONTENT_WIDTH = 330;
    private static final int MAX_CONTENT_WIDTH = 520;
    private final Map<WButton, NavigationTargetKey> choices =
            new LinkedHashMap<WButton, NavigationTargetKey>();
    private final int preferredWidth;
    private final int preferredHeight;
    private WButton cancel;
    private final WaypointerVirtualTable rows;

    WaypointClusterPickerWindow(List<NavigationTarget> targets, World world) {
        this(targets,world.getPlayerPosX(),world.getPlayerPosY());
    }

    WaypointClusterPickerWindow(List<NavigationTarget> targets, float playerX, float playerY) {
        super("wurm-waypointer.cluster-picker", false);
        setTitle("Wurm Waypointer - Choose Waypoint");
        resizable = false;

        WurmLabel prompt = new WaypointerLabel(targets.size()
                + " overlapping waypoints. Choose one:");
        int contentWidth = Math.max(MIN_CONTENT_WIDTH, prompt.width);
        rows = new WaypointerVirtualTable("waypointer.cluster.rows", ROW_HEIGHT, choices::clear, null);
        List<String> labels = new ArrayList<String>();
        for (NavigationTarget target : targets) {
            int distance = WaypointDistanceLabel.roundedMeters(
                    playerX, playerY,
                    (float) target.getCoordinate().worldX(),
                    (float) target.getCoordinate().worldY());
            boolean compassOnly = target.getMarkerStyle().getWorldStyle()
                    == MarkerStyle.WorldStyle.COMPASS_ONLY;
            String label = (target.isSelected() ? "> " : "")
                    + WaypointDistanceLabel.format(target.getName(), distance)
                    + (compassOnly ? " - Compass only"
                    : target.isWorldBeamVisible() ? " - World on" : " - World off");
            labels.add(label);
        }
        contentWidth = Math.min(MAX_CONTENT_WIDTH, contentWidth);
        prompt.setInitialSize(contentWidth, ROW_HEIGHT, false);

        WurmBorderPanel root = new WurmBorderPanel("waypointer.cluster.root");
        root.setComponent(prompt, WurmBorderPanel.NORTH);
        ChamomiloUiV1ScrollPanel scroll = new ChamomiloUiV1ScrollPanel(
                "waypointer.cluster.scroll", rows, false, true);
        rows.bind(scroll);
        final int rowWidth = contentWidth;
        rows.setRows(targets.size(),0,index->{
            NavigationTarget target=targets.get(index);
            WButton button=WaypointerUi.button(labels.get(index),this,rowWidth);
            button.setInitialSize(rowWidth,ROW_HEIGHT,false);
            button.setHoverString(target.getMarkerStyle().getWorldStyle()==MarkerStyle.WorldStyle.COMPASS_ONLY
                    ? "Select this compass-only waypoint." : "Select this waypoint and toggle only its world marker.");
            choices.put(button,target.getKey());return button;
        });
        root.setComponent(scroll,WurmBorderPanel.CENTER);
        cancel = WaypointerUi.button(org.waypoints.next.i18n.Messages.text("Cancel"),this,330);
        cancel.setInitialSize(contentWidth, ROW_HEIGHT, false);
        root.setComponent(cancel, WurmBorderPanel.SOUTH);
        setComponent(root);

        preferredWidth = contentWidth + 48;
        preferredHeight = Math.min(360,
                90 + ROW_HEIGHT * (targets.size() + 1));
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
