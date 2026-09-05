package com.wurmonline.client.renderer.gui;

import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKey;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsMonitoringView;
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.surroundings.SurroundingsRow;
import org.waypoints.next.surroundings.SurroundingsSnapshot;
import org.waypoints.next.ui.SurroundingsController;
import org.waypoints.next.ui.SurroundingsScrollState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compact mutually-exclusive view of detections from watched filters. */
final class SurroundingsMonitoringWindow extends WWindow
        implements ButtonListener {
    static final int WINDOW_WIDTH = 440;
    private static final int TABLE_WIDTH = 410;
    private static final int ROW_HEIGHT = 25;
    private static final int MARK_WIDTH = 62;
    private static final int NAME_WIDTH = 230;
    private static final int DETAIL_WIDTH = 118;
    private static final long AUTO_REFRESH_MILLIS = 1000L;
    private static final long SCROLL_SETTLE_MILLIS = 1200L;

    private final SurroundingsController controller;
    private final List<SurroundingsQuery> queries;
    private final Map<WButton, RowAction> rowActions =
            new HashMap<WButton, RowAction>();
    private final SurroundingsScrollState scrollState =
            new SurroundingsScrollState(SCROLL_SETTLE_MILLIS);

    private WurmArrayPanel<FlexComponent> table;
    private SurroundingsScrollPanel scrollPanel;
    private WButton surroundingsButton;
    private WButton refreshButton;
    private WurmLabel countLabel;
    private long displayedRevision = Long.MIN_VALUE;
    private long nextAutoRefreshAt;

    SurroundingsMonitoringWindow(SurroundingsController controller,
                                 List<SurroundingsQuery> queries) {
        super("wurm-waypointer.surroundings-monitoring", true);
        this.controller = controller;
        this.queries = Collections.unmodifiableList(
                new ArrayList<SurroundingsQuery>(queries));
        setTitle("Wurm Waypointer - Monitoring");
        build();
    }

    void refreshFromController() { refreshRows(); }

    boolean mouseWheeledAt(int mouseX, int mouseY, int wheelDelta) {
        return scrollPanel != null && scrollPanel.contains(mouseX, mouseY)
                && scrollPanel.scrollWheel(wheelDelta);
    }

    private void build() {
        WurmBorderPanel root = new WurmBorderPanel(
                "waypointer.surroundings-monitoring.root");
        table = vertical("waypointer.surroundings-monitoring.table");
        scrollPanel = new SurroundingsScrollPanel(
                "waypointer.surroundings-monitoring.scroll",
                table, ROW_HEIGHT,
                new SurroundingsScrollPanel.ScrollListener() {
                    @Override public void userScrolled(int offset,
                                                       long nowMillis) {
                        scrollState.observe(offset, nowMillis);
                    }
                });
        root.setComponent(scrollPanel, WurmBorderPanel.CENTER);
        root.setComponent(actionRow(), WurmBorderPanel.SOUTH);
        setComponent(root);
        refreshRows(0);
    }

    private FlexComponent actionRow() {
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings-monitoring.actions");
        surroundingsButton = button("Surroundings", 122);
        surroundingsButton.setHoverString(
                "Switch back to the full Surroundings filters.");
        refreshButton = button("Refresh", 82);
        countLabel = new WurmLabel("0 detected");
        row.addComponent(surroundingsButton);
        row.addComponent(refreshButton);
        row.addComponent(cell(countLabel, 206));
        return row;
    }

    private void refreshRows() { refreshRows(captureScrollOffset()); }

    private void refreshRows(int scrollOffset) {
        try {
            List<SurroundingsSnapshot> snapshots =
                    new ArrayList<SurroundingsSnapshot>(queries.size());
            for (SurroundingsQuery query : queries) {
                snapshots.add(controller.snapshot(query));
            }
            List<SurroundingsRow> detections =
                    SurroundingsMonitoringView.merge(snapshots);
            List<FlexComponent> components =
                    new ArrayList<FlexComponent>(detections.size() + 1);
            components.add(header());
            rowActions.clear();
            int marked = 0;
            for (SurroundingsRow detection : detections) {
                if (detection.isWaypointEnabled()) marked++;
                components.add(dataRow(detection));
            }
            table.removeAllComponents();
            table.addComponents(components.toArray(
                    new FlexComponent[components.size()]));
            restoreScroll(scrollOffset);
            displayedRevision = controller.revision();
            countLabel.setLabel(detections.size() + " found | "
                    + marked + " marked | " + queries.size() + " filters");
        } catch (Throwable failure) {
            controller.reportFailure("refresh monitoring", failure);
        }
    }

    private FlexComponent header() {
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings-monitoring.header");
        row.addComponent(cell(new WurmLabel("Mark"), MARK_WIDTH));
        row.addComponent(cell(new WurmLabel("Detection"), NAME_WIDTH));
        row.addComponent(cell(new WurmLabel("Type / distance"), DETAIL_WIDTH));
        return row;
    }

    private FlexComponent dataRow(SurroundingsRow data) {
        SurroundingEntry entry = data.getEntry();
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings-monitoring.row." + entry.getKey());
        WButton mark = button(data.isWaypointEnabled() ? "Clear" : "Mark",
                MARK_WIDTH);
        mark.setHoverString(data.isWaypointEnabled()
                ? "Delete this Surroundings waypoint."
                : "Create a standard 15-minute waypoint at this position.");
        rowActions.put(mark, new RowAction(
                entry.getKey(), data.isWaypointEnabled()));
        row.addComponent(mark);
        WurmLabel name = new WurmLabel(entry.getName(),
                entry.getCategory() + "; #" + entry.getWurmId());
        row.addComponent(cell(name, NAME_WIDTH));
        String detail = kindLabel(entry.getKind()) + " / "
                + data.getDistanceMetres() + "m";
        row.addComponent(cell(new WurmLabel(detail), DETAIL_WIDTH));
        return row;
    }

    @Override public void buttonPressed(WButton button) { }

    @Override public void buttonClicked(WButton button) {
        try {
            if (button == surroundingsButton) {
                SurroundingsWindowBridge.showSurroundings(this);
            } else if (button == refreshButton) {
                refreshRows();
            } else if (rowActions.containsKey(button)) {
                RowAction action = rowActions.get(button);
                controller.setWaypoint(action.key, !action.enabled);
                refreshRows();
            }
        } catch (Throwable failure) {
            controller.reportFailure("monitoring button", failure);
        }
    }

    @Override public void gameTick() {
        super.gameTick();
        long now = System.currentTimeMillis();
        if (scrollPanel != null) scrollState.observe(
                Math.max(0, scrollPanel.yo), now);
        if (now < nextAutoRefreshAt || !scrollState.permitsAutoRefresh(now)) return;
        nextAutoRefreshAt = now + AUTO_REFRESH_MILLIS;
        if (controller.revision() != displayedRevision) refreshRows();
    }

    @Override void closePressed() { SurroundingsWindowBridge.closed(this); }

    private int captureScrollOffset() {
        return scrollPanel == null ? 0 : Math.max(0, scrollPanel.yo);
    }

    private void restoreScroll(int requestedOffset) {
        if (scrollPanel == null) return;
        scrollPanel.contentChanged();
        scrollPanel.restoreOffset(requestedOffset);
        scrollState.synchronize(Math.max(0, scrollPanel.yo));
    }

    private WButton button(String label, int width) {
        WButton result = new WButton(label, this);
        result.setInitialSize(width, ROW_HEIGHT, false);
        return result;
    }

    private FlexComponent cell(FlexComponent value, int width) {
        value.setInitialSize(width, ROW_HEIGHT, false);
        return value;
    }

    private WurmArrayPanel<FlexComponent> horizontal(String name) {
        WurmArrayPanel<FlexComponent> result =
                new WurmArrayPanel<FlexComponent>(name, 1);
        result.setInitialSize(TABLE_WIDTH, ROW_HEIGHT, false);
        return result;
    }

    private WurmArrayPanel<FlexComponent> vertical(String name) {
        return new WurmArrayPanel<FlexComponent>(name, 0, true);
    }

    private static String kindLabel(SurroundingKind kind) {
        switch (kind) {
            case ANIMAL: return "Animal";
            case CONTAINER: return "Container";
            case ITEM: return "Item";
            default: return kind.name();
        }
    }

    private static final class RowAction {
        private final SurroundingKey key;
        private final boolean enabled;

        private RowAction(SurroundingKey key, boolean enabled) {
            this.key = key;
            this.enabled = enabled;
        }
    }
}
