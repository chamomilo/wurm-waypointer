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
import org.chamomilo.wurm.ui.v1.UiDensity;
import org.chamomilo.wurm.ui.v1.UiColor;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts;

/** Compact companion showing detections from the current browser filter. */
final class SurroundingsMonitoringWindow extends WaypointerUiWindow
        implements ButtonListener {
    static final int WINDOW_WIDTH = 440;
    private static final int TABLE_WIDTH = 410;
    private static final int ROW_HEIGHT = 28;
    private static final int FOOTER_GAP = 8;
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
    private WurmArrayPanel<FlexComponent> tableHeader;
    private WurmBorderPanel tablePanel;
    private SurroundingsScrollPanel scrollPanel;
    private WButton surroundingsButton;
    private WButton refreshButton;
    private WaypointerLabel countLabel;
    private long displayedRevision = Long.MIN_VALUE;
    private long nextAutoRefreshAt;
    private int minimumTableWidth = TABLE_WIDTH;
    private final WaypointerButtonGroup rowMarks;
    private final int markWidth;

    SurroundingsMonitoringWindow(SurroundingsController controller,
                                 List<SurroundingsQuery> queries) {
        super("wurm-waypointer.surroundings-monitoring", true);
        this.controller = controller;
        this.queries = Collections.unmodifiableList(
                new ArrayList<SurroundingsQuery>(queries));
        String[] captions=org.waypoints.next.i18n.Messages.texts(new String[]{"Mark","Clear"});
        markWidth=Math.max(MARK_WIDTH,Math.max(WaypointerButtonGroup.width(captions[0],16,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false),
                WaypointerButtonGroup.width(captions[1],16,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false)));
        rowMarks=new WaypointerButtonGroup("monitoring.row-marks",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,
                WaypointerTableActionCell.BUTTON_HEIGHT,16,false,captions,new int[]{markWidth,markWidth});
        setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer - Monitoring"));
        setTitleFont(ChamomiloUiV1Fonts.caption(16,true,UiDensity.HIGH));
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
        scrollPanel.useWholeRowOffsets();
        tablePanel = new WholeRowsPanel();
        tablePanel.setComponent(scrollPanel, WurmBorderPanel.CENTER);
        root.setComponent(tablePanel, WurmBorderPanel.CENTER);
        root.setComponent(actionRow(), WurmBorderPanel.SOUTH);
        setComponent(root);
        refreshRows(0);
    }

    private FlexComponent actionRow() {
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings-monitoring.actions");
        String[] captions=org.waypoints.next.i18n.Messages.texts(new String[]{"Back to Waypointer","Refresh","{0} found; {1} tracked; {2} filters"});
        captions[2]=org.waypoints.next.i18n.Messages.format("{0} found; {1} tracked; {2} filters",0,0,0);
        int[] widths=new int[captions.length];
        for(int i=0;i<widths.length;i++)widths[i]=WaypointerButtonGroup.width(captions[i],16,UiDensity.HIGH,false);
        WaypointerButtonGroup group=new WaypointerButtonGroup("monitoring.footer",UiDensity.HIGH,ROW_HEIGHT,16,false,captions,widths);
        surroundingsButton = button("Back to Waypointer", widths[0]);
        surroundingsButton.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Return to the Waypointer window."));
        refreshButton = button("Refresh", widths[1]);
        group.apply((ChamomiloUiV1Button)surroundingsButton,widths[0]);
        group.apply((ChamomiloUiV1Button)refreshButton,widths[1]);
        countLabel = new CounterLabel(group);
        row.componentWidthOffset=8;
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
                    new ArrayList<FlexComponent>(detections.size());
            tableHeader = header();
            tablePanel.setComponent(tableHeader, WurmBorderPanel.NORTH);
            rowActions.clear();
            int marked = 0;
            for (SurroundingsRow detection : detections) {
                if (detection.isWaypointEnabled()) marked++;
                components.add(dataRow(detection));
            }
            table.removeAllComponents();
            table.addComponents(components.toArray(
                    new FlexComponent[components.size()]));
            countLabel.setLabel(org.waypoints.next.i18n.Messages.format("{0} found; {1} tracked; {2} filters",detections.size(),marked,queries.size()));
            countLabel.setSize(Math.max(206, countLabel.textWidth()+8), ROW_HEIGHT);
            fitColumns();
            setSize(width, height);
            restoreScroll(scrollOffset);
            displayedRevision = controller.revision();
        } catch (Throwable failure) {
            controller.reportFailure("refresh monitoring", failure);
        }
    }

    private WurmArrayPanel<FlexComponent> header() {
        WurmArrayPanel<FlexComponent> row = new WaypointerTableHeader.Row("monitoring.headers");
        String[] titles={"Mark","Detection","Type / distance"};
        int[] widths={MARK_WIDTH,NAME_WIDTH,DETAIL_WIDTH};
        String[] hints={"Mark follows the object by ID. Unmark removes its waypoint.","Full object name received from the client.","Straight-line distance from your current position, in metres."};
        int baseline=WaypointerTableHeader.baseline(titles);
        for(int i=0;i<titles.length;i++)row.addComponent(new WaypointerTableHeader("monitoring.headers",titles[i],Math.max(widths[i],WaypointerTableHeader.minimumWidth(titles[i])),0,baseline,false,null,hints[i]));
        return row;
    }

    private FlexComponent dataRow(SurroundingsRow data) {
        SurroundingEntry entry = data.getEntry();
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings-monitoring.row." + entry.getKey());
        WButton mark = button(data.isWaypointEnabled() ? "Clear" : "Mark",
                markWidth);
        rowMarks.apply((ChamomiloUiV1Button)mark,markWidth);
        WaypointerButtonGroup.active(mark,data.isWaypointEnabled());
        mark.setHoverString(org.waypoints.next.i18n.Messages.text(data.isWaypointEnabled()
                ? "Delete this Surroundings waypoint."
                : "Create a standard tracked waypoint at this position."));
        rowActions.put(mark, new RowAction(
                entry.getKey(), data.isWaypointEnabled()));
        row.addComponent(new WaypointerTableActionCell(mark));
        WurmLabel name = new WaypointerTableLabel(entry.getName(),
                entry.getCategory() + "; #" + entry.getWurmId(),false);
        row.addComponent(cell(name, NAME_WIDTH));
        String detail = kindLabel(entry.getKind()) + " / "
                + data.getDistanceMetres() + "m";
        row.addComponent(cell(new WaypointerTableLabel(detail), DETAIL_WIDTH));
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

    @Override void setSize(int requestedWidth, int requestedHeight) {
        int minimum = Math.max(WINDOW_WIDTH, minimumTableWidth + 48);
        if (surroundingsButton != null && countLabel != null) minimum = Math.max(minimum,
                surroundingsButton.width + refreshButton.width + countLabel.width + 64);
        super.setSize(Math.max(minimum, requestedWidth), minimized?requestedHeight:Math.max(220, requestedHeight));
    }

    private void fitColumns() {
        WurmArrayPanel<?> header = tableHeader;
        List<FlexComponent> layoutRows = new ArrayList<FlexComponent>(table.components);
        layoutRows.add(0, tableHeader);
        int[] widths = {MARK_WIDTH, NAME_WIDTH, DETAIL_WIDTH};
        for (int i=0; i<widths.length; i++) {
            widths[i] = Math.max(widths[i], WaypointerTableHeader.minimumWidth(((WButton)header.components.get(i)).getLabel()));
            for (FlexComponent child : layoutRows) {
                FlexComponent cell = ((WurmArrayPanel<?>) child).components.get(i);
                if (cell instanceof WaypointerTableActionCell) widths[i] = Math.max(widths[i], cell.width);
                else if (cell instanceof WButton) widths[i] = Math.max(widths[i], WaypointerUi.captionWidth((WButton) cell));
            }
        }
        minimumTableWidth = 0;for (int value : widths) minimumTableWidth += value;
        for (FlexComponent child : layoutRows) {
            WurmArrayPanel<?> row = (WurmArrayPanel<?>) child;
            for(int i=0;i<widths.length;i++){
                FlexComponent value=row.components.get(i);
                if(value instanceof WaypointerTableActionCell)((WaypointerTableActionCell)value).resizeColumn(widths[i]);
                else if(value instanceof WaypointerTableHeader)((WaypointerTableHeader)value).resize(widths[i],ROW_HEIGHT);
                else if(value instanceof ChamomiloUiV1Button)((ChamomiloUiV1Button)value).resize(widths[i],ROW_HEIGHT);
                else value.setSize(widths[i],value.height);
            }
            row.componentResized();
        }
        table.componentResized();
        tablePanel.componentResized();
    }

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
        WButton result = WaypointerUi.button(org.waypoints.next.i18n.Messages.text(label), this, 90);
        result.setInitialSize(Math.max(width, WaypointerUi.captionWidth(result)), ROW_HEIGHT, false);
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

    /** Reserve the incomplete final row as space above the footer. */
    private final class WholeRowsPanel extends WurmBorderPanel {
        private final WurmPanel footerGap = new WurmPanel(1, FOOTER_GAP, false);
        WholeRowsPanel() {
            super("waypointer.surroundings-monitoring.table-panel");
            setComponent(footerGap, SOUTH);
        }
        @Override void performLayout() {
            int available = Math.max(0, height - ROW_HEIGHT - FOOTER_GAP);
            footerGap.height = FOOTER_GAP + available % ROW_HEIGHT;
            super.performLayout();
        }
    }

    private static final class CounterLabel extends WaypointerLabel {
        private final int baseline;
        private String caption="";
        CounterLabel(WaypointerButtonGroup group) {
            super("","",false,false);
            text=ChamomiloUiV1Fonts.caption(group.fontPixels,false,UiDensity.HIGH);
            baseline=group.baseline;
        }
        @Override void setLabel(String value) { super.setLabel(value); caption=value; }
        @Override protected void renderComponent(Queue queue,float ignoredAlpha) {
            text.moveTo(x+8,y+baseline);
            text.paint(queue,caption,UiColor.TEXT.red,UiColor.TEXT.green,UiColor.TEXT.blue,1f);
        }
    }
}
