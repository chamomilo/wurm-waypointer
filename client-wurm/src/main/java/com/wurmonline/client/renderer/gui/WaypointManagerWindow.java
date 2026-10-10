package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.PickData;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.UserMarkerStyles;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointLifetime;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.service.WaypointFilter;
import org.waypoints.next.service.WaypointDistance;
import org.waypoints.next.service.WaypointFilterOption;
import org.waypoints.next.service.WaypointManagerQuery;
import org.waypoints.next.service.WaypointManagerRow;
import org.waypoints.next.service.WaypointManagerSnapshot;
import org.waypoints.next.service.WaypointShareCodec;
import org.waypoints.next.source.ParsedCoordinate;
import org.waypoints.next.ui.WaypointEditData;
import org.waypoints.next.ui.WaypointManagerContext;
import org.waypoints.next.ui.WaypointManagerController;
import org.waypoints.next.ui.WaypointManagerHelpText;
import org.waypoints.next.ui.MarkerStyleEditorState;
import org.waypoints.next.ui.MarkerStyleControlProfile;
import org.waypoints.next.ui.WaypointManagerTableLayout;
import org.waypoints.next.ui.WaypointManagerWindowSizing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Dense native Wurm-style Phase 1 manager and static source editor. */
final class WaypointManagerWindow extends WaypointerContentPanel
        implements ButtonListener, InputFieldListener {
    private static final int ROW_HEIGHT = 32;
    private static final int CONTROL_GAP = 6;
    private static final int BUTTON_FONT_PIXELS = 20;
    private static final int TABLE_WIDTH = 1096;
    private static final int FORM_MINIMUM_WIDTH = 620;
    private static final int SOURCE_MINIMUM_WIDTH = 520;
    private static final int ON_WIDTH = 44;
    private static final int NAV_WIDTH = 58;
    private static final int NAME_WIDTH = 208;
    private static final int TYPE_WIDTH = 74;
    private static final int SERVER_WIDTH = 108;
    private static final int USER_WIDTH = 92;
    private static final int STATUS_WIDTH = 104;
    private static final int DISTANCE_WIDTH = 72;
    private static final int STYLE_WIDTH = 106;
    private static final int EDIT_WIDTH = 52;
    private static final int SHARE_WIDTH = 58;
    private static final int COPY_WIDTH = 52;
    private static final int DELETE_WIDTH = 68;
    private static final long DISTANCE_REFRESH_INTERVAL_MILLIS = 250L;
    private static final long DISTANCE_ERROR_REPORT_INTERVAL_MILLIS = 30000L;
    private static final double DISTANCE_POSITION_EPSILON_TILES = 0.01d;

    private final WaypointManagerController controller;
    private final WaypointerButtonGroup commandGroup;
    private final WaypointerButtonGroup tableActionGroup;
    private final Map<WButton, RowAction> rowActions =
            new HashMap<WButton, RowAction>();
    private final Map<WButton, WaypointManagerQuery.SortColumn> sortActions =
            new HashMap<WButton, WaypointManagerQuery.SortColumn>();
    private final Map<FlexComponent, String> hoverTexts =
            new HashMap<FlexComponent, String>();
    private final List<LiveDistanceCell> liveDistanceCells =
            new ArrayList<LiveDistanceCell>();
    private final List<TableLayoutRow> tableLayoutRows =
            new ArrayList<TableLayoutRow>();
    private final List<ResponsiveRow> responsiveRows =
            new ArrayList<ResponsiveRow>();
    private final Map<UUID, LiveRow> liveRows = new HashMap<UUID, LiveRow>();

    private WurmArrayPanel<FlexComponent> table;
    private WurmArrayPanel<FlexComponent> tableHeader;
    private WurmBorderPanel listTable;
    private WurmBorderPanel listRoot;
    private WurmArrayPanel<FlexComponent> activeContent;
    private WurmScrollPanel listScroll;
    private WurmDropDown serverFilter;
    private WurmDropDown userFilter;
    private WurmDropDown typeFilter;
    private WurmDropDown statusFilter;
    private String[] serverValues = new String[0];
    private String[] userValues = new String[0];
    private WurmLabel countLabel;
    private String countCaption = "";
    private WButton applyFilters;
    private WButton refreshButton;
    private WButton addButton;
    private WButton enableFiltered;
    private WButton disableFiltered;
    private WButton exportButton;
    private WButton importButton;
    private WButton pasteSharedButton;
    private WButton surroundingsButton;
    private List<UUID> filteredIds = new ArrayList<UUID>();
    private WaypointManagerQuery.SortColumn sortColumn =
            WaypointManagerQuery.SortColumn.NAME;
    private boolean sortAscending = true;
    private String appliedServer = "current", appliedUser = "current";
    private int appliedType, appliedStatus;

    private WurmInputField nameInput;
    private WurmInputField coordinateInput;
    private WurmDropDown worldStyleInput;
    private MarkerStyleEditorState styleEditor;
    private WaypointColorPicker colorPicker;
    private WurmArrayPanel<FlexComponent> styleSliderPanel;
    private WaypointStyleSlider alphaSlider;
    private WaypointStyleSlider markerSizeSlider;
    private WaypointStyleSlider beamWidthSlider;
    private WaypointStyleSlider arrivalRadiusSlider;
    private WurmLabel arrivalRadiusLabel;
    private int arrivalRadiusMetres = WaypointArrival.DEFAULT_RADIUS_METRES;
    private WurmDropDown lifetimeInput;
    private int[] lifetimeValues = new int[0];
    private final List<ResponsiveRow> styleSliderResponsiveRows =
            new ArrayList<ResponsiveRow>();
    private final List<FlexComponent> styleSliderHoverComponents =
            new ArrayList<FlexComponent>();
    private int observedWorldStyle = -1;
    private WurmInputField preferredInput;
    private WurmLabel coordinateStatusLabel;
    private WButton sourceHere;
    private WButton sourceCoordinates;
    private WButton clipboardButton;
    private WButton saveButton;
    private WButton cancelButton;
    private UUID editingId;
    private String previewedInput = "";
    private WaypointCoordinate previewedCoordinate;
    private int listWidth;
    private int listHeight;
    private long nextDistanceRefreshAt;
    private long nextCatalogueRefresh, catalogueRevision=-1;
    private long nextHerePreviewAt;
    private long nextDistanceErrorReportAt;
    private double lastDistanceOriginX = Double.NaN;
    private double lastDistanceOriginY = Double.NaN;
    private int lastResponsiveWidth = -1;
    private ViewMode viewMode = ViewMode.LIST;

    WaypointManagerWindow(WaypointManagerController controller) {
        super("wurm-waypointer.manager", true);
        this.controller = controller;
        commandGroup = commandTypography();
        tableActionGroup = tableActionTypography();
        setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer"));
        showList();
    }

    void refreshFromController() {
        // Every explicit compass/console open returns to the authoritative list.
        // This also discards an incomplete editor after a shard/HUD transition.
        showList();
    }

    private void showList() {
        rememberListSize();
        clearEditorState();
        viewMode = ViewMode.LIST;
        responsiveRows.clear();
        tableLayoutRows.clear();
        liveRows.clear();
        hoverTexts.clear();
        activeContent = null;
        lastResponsiveWidth = -1;
        setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer - Waypoints"));
        WaypointManagerContext context = controller.context();
        WaypointManagerSnapshot options = controller.snapshot(
                WaypointManagerQuery.builder().allServers()
                        .currentContext(context.getServer()).build());

        WurmBorderPanel root = new WurmBorderPanel("waypointer.manager.root");
        listRoot = root;
        WurmArrayPanel<FlexComponent> top = new WurmArrayPanel<FlexComponent>(
                "waypointer.manager.filters", 0, true);
        top.addComponent(summaryRow());
        top.addComponent(gap(1, CONTROL_GAP));
        top.addComponent(filterRowTwo(options, context));
        top.addComponent(gap(1, CONTROL_GAP));
        root.setComponent(top, WurmBorderPanel.NORTH);

        table = new WurmArrayPanel<FlexComponent>("waypointer.manager.table", 0);
        WurmScrollPanel scroll = new ChamomiloUiV1ScrollPanel(
                "waypointer.manager.scroll", table, false, true);
        listScroll=scroll;
        listTable = new WurmBorderPanel("waypointer.manager.table-panel");
        listTable.setComponent(scroll, WurmBorderPanel.CENTER);
        root.setComponent(listTable, WurmBorderPanel.CENTER);
        WurmBorderPanel footer = new WurmBorderPanel("waypointer.actions.spacing");
        footer.setComponent(gap(1, CONTROL_GAP), NORTH);
        footer.setComponent(actionRow(), CENTER);
        root.setComponent(footer, WurmBorderPanel.SOUTH);
        setComponent(root);
        restoreListSize();
        refreshRows();
        applyResponsiveLayout();
    }

    private FlexComponent summaryRow() {
        WurmArrayPanel<FlexComponent> row=horizontal("waypointer.filters.count");
        addButton=button("Add...",82);row.addComponent(addButton);row.addComponent(gap(CONTROL_GAP,ROW_HEIGHT));
        addButton.setHoverString(org.waypoints.next.i18n.Messages.text("Create a waypoint at your position or from coordinates/map link."));
        refreshButton=button("Refresh",100);row.addComponent(refreshButton);row.addComponent(gap(CONTROL_GAP,ROW_HEIGHT));
        refreshButton.setHoverString(org.waypoints.next.i18n.Messages.text("Refresh the waypoint list using the applied filters."));
        WaypointerButtonGroup.peers("all-waypoints.top-actions",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,ROW_HEIGHT,addButton,refreshButton);
        countLabel=new WaypointerLabel("0 waypoints");row.addComponent(cell(countLabel,350));
        registerHover(countLabel,"Waypoints matching the applied filters / all saved waypoints.");
        countCaption = "";
        return row;
    }

    private FlexComponent filterRowTwo(WaypointManagerSnapshot options,
                                       WaypointManagerContext context) {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.filters.two");

        row.addComponent(cell(new WaypointerLabel("Server"), 50));
        List<String> serverLabels = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        serverLabels.add("Current"); values.add("current");
        serverLabels.add("All"); values.add("all");
        serverLabels.add("Unassigned"); values.add("unassigned");
        for (WaypointFilterOption option : options.getServers()) {
            serverLabels.add(option.getLabel());
            values.add("specific:" + option.getValue());
        }
        serverValues = values.toArray(new String[values.size()]);
        serverFilter = WaypointerUi.dropDown("waypointer.filter.server", Math.max(0, values.indexOf(appliedServer)),
                serverLabels.toArray(new String[serverLabels.size()]));
        registerHover(serverFilter, "Show the current server, every server, unassigned records, or one server.");
        row.addComponent(cell(serverFilter, 205));

        row.addComponent(cell(new WaypointerLabel("User"), 38));
        List<String> userLabels = new ArrayList<String>();
        values = new ArrayList<String>();
        userLabels.add("Current (" + context.getUser() + ")"); values.add("current");
        userLabels.add("All"); values.add("all");
        for (WaypointFilterOption option : options.getUsers()) {
            if (option.getValue().equalsIgnoreCase(context.getUser())) continue;
            userLabels.add(option.getLabel()); values.add(option.getValue());
        }
        userValues = values.toArray(new String[values.size()]);
        userFilter = WaypointerUi.dropDown("waypointer.filter.user", Math.max(0, values.indexOf(appliedUser)),
                userLabels.toArray(new String[userLabels.size()]));
        registerHover(userFilter, "Filter by the character that owns the waypoint.");
        row.addComponent(cell(userFilter, 180));

        row.addComponent(cell(new WaypointerLabel("Type"), 36));
        String[] types = new String[WaypointSourceType.values().length + 1];
        types[0] = "All types";
        for (int i = 0; i < WaypointSourceType.values().length; i++) {
            types[i + 1] = title(WaypointSourceType.values()[i].name());
        }
        typeFilter = WaypointerUi.dropDown("waypointer.filter.type", appliedType, types);
        registerHover(typeFilter, "Filter by waypoint source type.");
        row.addComponent(cell(typeFilter, 140));

        row.addComponent(cell(new WaypointerLabel("Status"), 45));
        String[] statuses = new String[WaypointResolution.values().length + 1];
        statuses[0] = "All statuses";
        for (int i = 0; i < WaypointResolution.values().length; i++) {
            statuses[i + 1] = title(WaypointResolution.values()[i].name());
        }
        statusFilter = WaypointerUi.dropDown("waypointer.filter.status", appliedStatus, statuses);
        registerHover(statusFilter, "Filter by coordinate resolution status.");
        row.addComponent(cell(statusFilter, 160));
        applyFilters = button("Apply filters", 110);
        applyFilters.setHoverString(org.waypoints.next.i18n.Messages.text("Apply the server, user, type and status filters."));
        row.addComponent(applyFilters);
        registerResponsive(row,
                new int[]{50, 130, 38, 120, 36, 100, 45, 110, 110},
                1, 3, 5, 7);
        return row;
    }

    private FlexComponent actionRow() {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.actions");
        enableFiltered = button("Enable filtered", 122);
        disableFiltered = button("Disable filtered", 126);
        exportButton = button("Export", 82);
        importButton = button("Import", 82);
        pasteSharedButton = button("Paste shared", 112);
        surroundingsButton = button("Surroundings", 112);
        addButton.setHoverString(org.waypoints.next.i18n.Messages.text("Create a waypoint at your position or from coordinates/map link."));
        enableFiltered.setHoverString(org.waypoints.next.i18n.Messages.text("Enable every waypoint currently matched by the filters."));
        disableFiltered.setHoverString(org.waypoints.next.i18n.Messages.text("Disable every waypoint currently matched by the filters."));
        exportButton.setHoverString(org.waypoints.next.i18n.Messages.text("Export all waypoint records to the transfer file."));
        importButton.setHoverString(org.waypoints.next.i18n.Messages.text("Import waypoint records from the transfer file."));
        pasteSharedButton.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Import one WWP1 waypoint copied from chat. It stays inactive until you visit its destination server."));
        surroundingsButton.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Open the live catalog of animals, containers and items loaded around you."));
        row.addComponent(enableFiltered);
        row.addComponent(disableFiltered);
        row.addComponent(exportButton);
        row.addComponent(importButton);
        row.addComponent(pasteSharedButton);

        WaypointerButtonGroup.peers("all-waypoints.footer",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,ROW_HEIGHT,
                enableFiltered,disableFiltered,exportButton,importButton,pasteSharedButton);

        registerResponsive(row, new int[]{122, 126, 82, 82, 112});
        return row;
    }

    private void refreshRows() {
        refreshRows(true);
    }

    private void refreshRows(boolean explicitOrder) {
        // Stale native input callbacks can arrive while an editor view owns the
        // window and deliberately has no table. Ignore them instead of letting
        // one delayed search/Enter event break the Manager refresh path.
        if (table == null) return;
        try {
            WaypointManagerContext context = controller.context();
            WaypointManagerQuery query = query(context);
            WaypointManagerSnapshot snapshot = controller.snapshot(query);
            if (!explicitOrder && canUpdateLiveRows(snapshot)) {
                for (WaypointManagerRow data : snapshot.getRows()) liveRows.get(data.getId()).update(data);
                updateCount(snapshot);
                catalogueRevision = controller.revision();
                return;
            }
            int previousOffset = listScroll.yo;
            List<UUID> previousIds = new ArrayList<UUID>(filteredIds);
            List<WaypointManagerRow> displayRows = new ArrayList<WaypointManagerRow>(snapshot.getRows());
            if (!explicitOrder && !previousIds.isEmpty()) {
                Map<UUID, Integer> order = new HashMap<UUID, Integer>();
                for (UUID id : previousIds) order.put(id, order.size());
                for (WaypointManagerRow data : displayRows) if (!order.containsKey(data.getId())) order.put(data.getId(), order.size());
                displayRows.sort((left, right) -> Integer.compare(order.get(left.getId()), order.get(right.getId())));
            }
            table.removeAllComponents();
            rowActions.clear();
            sortActions.clear();
            liveDistanceCells.clear();
            liveRows.clear();
            lastDistanceOriginX = Double.NaN;
            lastDistanceOriginY = Double.NaN;
            filteredIds = new ArrayList<UUID>();
            tableLayoutRows.clear();
            tableHeader = header();
            WurmBorderPanel heading = new WurmBorderPanel("waypointer.manager.heading");
            heading.setComponent(tableHeader, NORTH);
            heading.setComponent(gap(1, CONTROL_GAP), SOUTH);
            listTable.setComponent(heading, NORTH);
            for (WaypointManagerRow row : displayRows) {
                filteredIds.add(row.getId());
                table.addComponent(dataRow(row));
                table.addComponent(gap(1, CONTROL_GAP));
            }
            updateCount(snapshot);
            lastResponsiveWidth = -1;
            applyResponsiveLayout();
            restoreScroll(previousOffset, previousIds);
            catalogueRevision = controller.revision();
        } catch (Throwable failure) {
            controller.reportFailure("refresh manager", failure);
        }
    }

    private void updateCount(WaypointManagerSnapshot snapshot) {
        String next = org.waypoints.next.i18n.Messages.format("{0} of {1} waypoints",
                snapshot.getFilteredCount(), snapshot.getTotalCount());
        if (!next.equals(countCaption)) { countCaption = next; countLabel.setLabel(next); }
    }

    private boolean canUpdateLiveRows(WaypointManagerSnapshot snapshot) {
        if (liveRows.size() != snapshot.getRows().size()) return false;
        for (WaypointManagerRow data : snapshot.getRows()) {
            LiveRow previous = liveRows.get(data.getId());
            if (previous == null || !previous.sameControls(data)) return false;
        }
        return true;
    }

    /** Retain the top visible UUID and the intra-row pixel offset, including after deletion. */
    private void restoreScroll(int offset, List<UUID> previousIds) {
        int stride = ROW_HEIGHT + CONTROL_GAP;
        int restored = offset;
        if (offset > 0 && !previousIds.isEmpty()) {
            int index = Math.min(previousIds.size() - 1, Math.max(0, offset / stride));
            int withinRow = offset - index * stride;
            for (int i = index; i < previousIds.size(); i++) {
                int nextIndex = filteredIds.indexOf(previousIds.get(i));
                if (nextIndex >= 0) { restored = nextIndex * stride + withinRow; break; }
            }
        }
        ((ChamomiloUiV1ScrollPanel) listScroll).scrollTo(0, restored);
    }

    private WaypointManagerQuery query(WaypointManagerContext context) {
        WaypointManagerQuery.Builder builder = WaypointManagerQuery.builder()
                .currentContext(context.getServer())
                .originTiles(context.getTileX(), context.getTileY())
                .sort(sortColumn, sortAscending);
        String server = appliedServer;
        if ("all".equals(server)) builder.allServers();
        else if ("unassigned".equals(server)) builder.unassignedServer();
        else if (server.startsWith("specific:")) {
            builder.specificServer(server.substring("specific:".length()));
        } else builder.currentServer(context.getServer());

        String user = appliedUser;
        if ("current".equals(user)) builder.user(context.getUser());
        else if (!"all".equals(user)) builder.user(user);
        if (appliedType > 0) {
            builder.sourceType(WaypointSourceType.values()[appliedType - 1]);
        }
        if (appliedStatus > 0) {
            builder.resolution(WaypointResolution.values()[appliedStatus - 1]);
        }
        return builder.build();
    }

    private WurmArrayPanel<FlexComponent> header() {
        WurmArrayPanel<FlexComponent> row = new WaypointerTableHeader.Row("all-waypoints.headers");
        row.addComponent(sortButton("On", ON_WIDTH,
                WaypointManagerQuery.SortColumn.ENABLED));
        row.addComponent(plainHeader("Nav", NAV_WIDTH));
        row.addComponent(sortButton("Name", NAME_WIDTH,
                WaypointManagerQuery.SortColumn.NAME));
        row.addComponent(sortButton("Type", TYPE_WIDTH,
                WaypointManagerQuery.SortColumn.TYPE));
        row.addComponent(sortButton("Server", SERVER_WIDTH,
                WaypointManagerQuery.SortColumn.SERVER));
        row.addComponent(sortButton("User", USER_WIDTH,
                WaypointManagerQuery.SortColumn.USER));
        row.addComponent(sortButton("Status", STATUS_WIDTH,
                WaypointManagerQuery.SortColumn.STATUS));
        row.addComponent(sortButton("Distance", DISTANCE_WIDTH,
                WaypointManagerQuery.SortColumn.DISTANCE));
        row.addComponent(sortButton("Style", STYLE_WIDTH,
                WaypointManagerQuery.SortColumn.STYLE));
        row.addComponent(plainHeader("Edit", EDIT_WIDTH));
        row.addComponent(plainHeader("Share", SHARE_WIDTH));
        row.addComponent(plainHeader("Copy", COPY_WIDTH));
        row.addComponent(plainHeader("Delete", DELETE_WIDTH));
        registerTableRow(row);
        return row;
    }

    private FlexComponent dataRow(WaypointManagerRow data) {
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.row." + data.getId());
        WButton on = button(data.isEnabled() ? "On" : "Off", ON_WIDTH);
        tableActionGroup.apply((ChamomiloUiV1Button)on,on.width);
        WaypointerButtonGroup.active(on,data.isEnabled());
        on.setHoverString(org.waypoints.next.i18n.Messages.text(data.isSystemManaged()
                ? "Enable or disable this server's managed vanilla landmark. This On/Off choice is remembered per server."
                : data.getSourceType() == WaypointSourceType.LOOT_MAP
                ? "Show or hide the active Loot Map waypoint without deleting hunt progress. New readings keep this choice."
                : data.getSourceType() == WaypointSourceType.DEED
                ? "Show or hide this provider-managed deed waypoint. Feed refreshes keep this choice."
                : "Enable or disable this waypoint's compass marker, label, and world effect."));
        rowActions.put(on, new RowAction(ActionKind.TOGGLE, data.getId(),
                data.isEnabled(), data.getName() + " [" + data.getShortId() + "]"));
        row.addComponent(on);
        boolean navigating = controller.isNavigatorActive(data.getId());
        WButton navigator = button(navigating ? "Stop" : "Nav", NAV_WIDTH);
        tableActionGroup.apply((ChamomiloUiV1Button)navigator,navigator.width);
        WaypointerButtonGroup.active(navigator,navigating);
        navigator.setHoverString(org.waypoints.next.i18n.Messages.text(navigating
                ? "Stop the active on-ground navigation route."
                : "Navigate to this enabled current-server waypoint. Starting it stops the previous route."));
        rowActions.put(navigator, new RowAction(ActionKind.NAVIGATE, data.getId(),
                navigating, data.getName() + " [" + data.getShortId() + "]"));
        row.addComponent(navigator);
        row.addComponent(cell(new WaypointerTableLabel(data.isSystemManaged()
                ? data.getName() : data.getName() + " [" + data.getShortId() + "]",
                org.waypoints.next.i18n.Messages.format("Waypoint name and unique ID: {0}",data.getId()),false), NAME_WIDTH));
        row.addComponent(cell(new WaypointerTableLabel(title(data.getSourceType().name()),
                org.waypoints.next.i18n.Messages.text("The provider or catalogue that created this waypoint.")), TYPE_WIDTH));
        row.addComponent(cell(new WaypointerTableLabel(data.getServerLabel(),
                org.waypoints.next.i18n.Messages.format("Destination server: {0}. {1}",data.getServerLabel(),data.getServerFingerprint()),false), SERVER_WIDTH));
        row.addComponent(cell(new WaypointerTableLabel(data.getUser(),
                org.waypoints.next.i18n.Messages.text("Character that owns this waypoint."),false), USER_WIDTH));
        String statusText = statusText(data);
        WurmLabel status = new WaypointerTableLabel(statusText,statusHelp(data));
        row.addComponent(cell(status, STATUS_WIDTH));
        WurmLabel distance = new WaypointerTableLabel(data.getDistanceMetres() == null
                ? "-" : data.getDistanceMetres() + "m",org.waypoints.next.i18n.Messages.text("Straight-line distance in metres; a dash means coordinates are unavailable here."),true,true);
        row.addComponent(cell(distance, DISTANCE_WIDTH));
        LiveDistanceCell distanceCell = null;
        if (data.getDistanceMetres() != null) {
            distanceCell = new LiveDistanceCell(distance,
                    data.getTileX(), data.getTileY(),
                    data.getDistanceMetres().intValue());
            liveDistanceCells.add(distanceCell);
        }
        liveRows.put(data.getId(), new LiveRow(data, navigating, status, statusText, distanceCell));
        row.addComponent(cell(new WaypointerTableLabel(title(data.getWorldStyle().name()),
                org.waypoints.next.i18n.Messages.text("World marker appearance used when the waypoint is On.")), STYLE_WIDTH));

        if (data.isToggleOnlyManaged()) {
            String explanation = data.isSystemManaged()
                    ? "Managed vanilla landmark. Its server coordinates and exact vanilla renderer are fixed; only On/Off is available."
                    : "Active Loot Map hunt waypoint. On/Off hides or restores its compass, map, label, and world marker without deleting hunt progress.";
            WurmLabel fixed = new WaypointerTableLabel("Fixed");
            registerHover(fixed, explanation);
            row.addComponent(cell(fixed, EDIT_WIDTH));
            row.addComponent(cell(new WaypointerTableLabel("-"), SHARE_WIDTH));
            row.addComponent(cell(new WaypointerTableLabel("-"), COPY_WIDTH));
            row.addComponent(cell(new WaypointerTableLabel("-"), DELETE_WIDTH));
            registerTableRow(row);
            return row;
        }

        if (data.isTrackedTarget()) {
            WurmLabel fixed = new WaypointerTableLabel("Fixed");
            registerHover(fixed, "Coordinates follow this tracked target automatically.");
            row.addComponent(cell(fixed, EDIT_WIDTH));
            row.addComponent(cell(new WaypointerTableLabel("-"), SHARE_WIDTH));
            row.addComponent(cell(new WaypointerTableLabel("-"), COPY_WIDTH));
            WButton delete = button("Delete", DELETE_WIDTH);
            delete.setHoverString(org.waypoints.next.i18n.Messages.text(
                    "Remove this target from ALL WAYPOINTS. Track it again in its catalogue to add it back."));
            rowActions.put(delete, new RowAction(ActionKind.REMOVE_TRACKED, data.getId(), false,
                    data.getName() + " [" + data.getShortId() + "]"));
            row.addComponent(delete);
            registerTableRow(row);
            return row;
        }

        if (data.isProviderManaged()) {
            WurmLabel fixed = new WaypointerTableLabel("Fixed");
            registerHover(fixed,
                    "Provider-managed deed coordinates cannot be edited manually; feed updates move this UUID automatically.");
            row.addComponent(cell(fixed, EDIT_WIDTH));
            row.addComponent(cell(new WaypointerTableLabel("-"), SHARE_WIDTH));
            row.addComponent(cell(new WaypointerTableLabel("-"), COPY_WIDTH));
            WButton delete = button("Delete", DELETE_WIDTH);
            delete.setHoverString(org.waypoints.next.i18n.Messages.text(data.getResolution()
                    == org.waypoints.next.model.WaypointResolution.STALE
                    ? "Delete this stale deed waypoint immediately."
                    : "Stop tracking this deed immediately."));
            rowActions.put(delete, new RowAction(ActionKind.DELETE, data.getId(), false,
                    data.getName() + " [" + data.getShortId() + "]"));
            row.addComponent(delete);
            registerTableRow(row);
            return row;
        }

        WButton edit = button("Edit", EDIT_WIDTH);
        edit.setHoverString(org.waypoints.next.i18n.Messages.text("Edit coordinates and style using a temporary live marker; Cancel restores the stored waypoint."));
        rowActions.put(edit, new RowAction(ActionKind.EDIT, data.getId(), false,
                data.getName() + " [" + data.getShortId() + "]"));
        row.addComponent(edit);
        WButton share = button("Share", SHARE_WIDTH);
        share.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Copy this exact waypoint as a WWP1 service line and print it in Event for sharing."));
        rowActions.put(share, new RowAction(ActionKind.SHARE, data.getId(), false,
                data.getName() + " [" + data.getShortId() + "]"));
        row.addComponent(share);
        WButton copy = button("Copy", COPY_WIDTH);
        copy.setHoverString(org.waypoints.next.i18n.Messages.text("Create a disabled duplicate with a new UUID."));
        rowActions.put(copy, new RowAction(ActionKind.DUPLICATE, data.getId(), false,
                data.getName() + " [" + data.getShortId() + "]"));
        row.addComponent(copy);
        WButton delete = button("Delete", DELETE_WIDTH);
        delete.setHoverString(org.waypoints.next.i18n.Messages.text("Delete this waypoint immediately."));
        rowActions.put(delete, new RowAction(ActionKind.DELETE, data.getId(), false,
                data.getName() + " [" + data.getShortId() + "]"));
        row.addComponent(delete);
        registerTableRow(row);
        return row;
    }

    private FlexComponent sortButton(String label, int width,
                                     WaypointManagerQuery.SortColumn column) {
        WButton button = new WaypointerTableHeader("all-waypoints.headers",label,Math.max(width,WaypointerTableHeader.minimumWidth(label)),column==sortColumn?(sortAscending?1:-1):0,headerBaseline(),column==WaypointManagerQuery.SortColumn.DISTANCE,this,
                org.waypoints.next.i18n.Messages.format("Sort the table by {0}. Click again to reverse the order.",org.waypoints.next.i18n.Messages.text(label)));
        sortActions.put(button, column);
        return button;
    }

    private FlexComponent plainHeader(String caption,int width){
        return new WaypointerTableHeader("all-waypoints.headers",caption,Math.max(width,WaypointerTableHeader.minimumWidth(caption)),0,headerBaseline(),false,null,caption);
    }
    private static int headerBaseline(){return WaypointerTableHeader.baseline(new String[]{"On","Nav","Name","Type","Server","User","Status","Distance","Style","Edit","Share","Copy","Delete"});}

    private void showSourceChooser() {
        rememberListSize();
        clearEditorState();
        viewMode = ViewMode.SOURCE;
        responsiveRows.clear();
        tableLayoutRows.clear();
        hoverTexts.clear();
        activeContent = null;
        lastResponsiveWidth = -1;
        table = null;
        setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer - Add Waypoint"));
        WurmArrayPanel<FlexComponent> content = vertical("waypointer.source.chooser");
        content.addComponent(cell(new WaypointerLabel(
                "Choose a waypoint source."),
                680, 34));
        sourceHere = button("Here", 220);
        sourceCoordinates = button("Coordinates / map link", 220);
        cancelButton = button("Cancel", 120);
        sourceHere.setHoverString(org.waypoints.next.i18n.Messages.text("Create a waypoint at your current character position and layer."));
        sourceCoordinates.setHoverString(org.waypoints.next.i18n.Messages.text(
                WaypointManagerHelpText.COORDINATE_SOURCE));
        cancelButton.setHoverString(org.waypoints.next.i18n.Messages.text("Return to the waypoint list without creating anything."));
        content.addComponent(centered(sourceHere, 680));
        content.addComponent(centered(sourceCoordinates, 680));
        content.addComponent(centered(cancelButton, 680));
        setComponent(content);
        setSize(720, 190);
    }

    private void showHereForm() {
        clearEditorState();
        viewMode = ViewMode.HERE;
        responsiveRows.clear();
        tableLayoutRows.clear();
        hoverTexts.clear();
        lastResponsiveWidth = -1;
        table = null;
        setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer - Add Here"));
        WaypointManagerContext context = controller.context();
        WurmArrayPanel<FlexComponent> content = vertical("waypointer.here.form");
        activeContent = content;
        beginStyleEditor(MarkerStyle.defaultColoredBeam());
        content.addComponent(formRow("Name", createNameInput("")));
        content.addComponent(formRow("World style", createWorldStyleInput(
                MarkerStyle.WorldStyle.COLORED_BEAM)));
        addStyleControls(content);
        addArrivalControl(content, WaypointArrival.DEFAULT_RADIUS_METRES);
        addLifetimeControl(content, null);
        WurmLabel location = new WaypointerLabel("Location: server="
                + context.getServer().getShortName() + " ["
                + context.getServer().getEndpointFingerprint() + "]"
                + ", user=" + context.getUser() + ", X=" + context.getTileX()
                + ", Y=" + context.getTileY() + ", layer=" + context.getLayer());
        registerHover(location, "The live draft follows the current Here location; storage changes only after OK.");
        content.addComponent(fullWidthRow("waypointer.here.location", location, 40));
        WurmArrayPanel<FlexComponent> actions = horizontal("waypointer.here.actions");
        saveButton = button("OK", 100);
        cancelButton = button("Cancel", 100);
        saveButton.setHoverString(org.waypoints.next.i18n.Messages.text("Save this live draft as a new waypoint."));
        cancelButton.setHoverString(org.waypoints.next.i18n.Messages.text("Cancel creation and remove the temporary marker."));
        actions.addComponent(saveButton);
        actions.addComponent(cancelButton);
        registerResponsive(actions, new int[]{100, 100});
        content.addComponent(actions);
        setComponent(content);
        setSize(820, 454);
        syncColorPickerFromCurrentStyle();
        publishHerePreview();
        focusInput(nameInput);
    }

    private void showCoordinateForm(UUID id) {
        rememberListSize();
        clearEditorState();
        viewMode = ViewMode.COORDINATE;
        responsiveRows.clear();
        tableLayoutRows.clear();
        hoverTexts.clear();
        lastResponsiveWidth = -1;
        table = null;
        editingId = id;
        WaypointEditData edit = id == null ? null : controller.editData(id);
        setTitle(org.waypoints.next.i18n.Messages.text(id == null ? "Wurm Waypointer - Add Coordinates"
                : "Wurm Waypointer - Edit Static Waypoint"));
        WurmArrayPanel<FlexComponent> content = vertical("waypointer.coordinate.form");
        activeContent = content;
        MarkerStyle editableStyle = UserMarkerStyles.editable(edit == null
                ? MarkerStyle.defaultColoredBeam() : edit.getMarkerStyle());
        beginStyleEditor(editableStyle);
        content.addComponent(formRow("Name", createNameInput(edit == null
                ? "" : edit.getName())));
        coordinateInput = inputField("waypointer.coordinate.input", 4096);
        registerHover(coordinateInput,
                WaypointManagerHelpText.COORDINATE_INPUT);
        if (edit != null) coordinateInput.setTextMoveToEnd(format(edit));
        content.addComponent(formRow("Coordinates / map link", coordinateInput));
        coordinateStatusLabel = new WaypointerLabel(
                "Parsed coordinates: paste a /gps line, x/y pair, or map link.");
        registerHover(coordinateStatusLabel,
                "This read-only line confirms the parsed server hint, X, Y, layer, and input type before OK.");
        content.addComponent(fullWidthRow("waypointer.coordinate.status",
                coordinateStatusLabel, ROW_HEIGHT));
        content.addComponent(formRow("World style", createWorldStyleInput(
                editableStyle.getWorldStyle())));
        addStyleControls(content);
        addArrivalControl(content, edit == null
                ? WaypointArrival.DEFAULT_RADIUS_METRES
                : edit.getArrivalRadiusMetres());
        addLifetimeControl(content, edit);

        WurmArrayPanel<FlexComponent> actions = horizontal("waypointer.coordinate.actions");
        clipboardButton = button("Use Clipboard", 120);
        saveButton = button("OK", 100);
        cancelButton = button("Cancel", 100);
        clipboardButton.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Paste and parse one /gps line, x/y pair, map fragment, or full wu-map link. A WWP1 service line is imported immediately with its original server, style, arrival radius, and expiry."));
        saveButton.setHoverString(org.waypoints.next.i18n.Messages.text(id == null
                ? "Save the current live draft as a new waypoint."
                : "Commit the live draft to this waypoint."));
        cancelButton.setHoverString(org.waypoints.next.i18n.Messages.text(id == null
                ? "Cancel creation and remove the temporary marker."
                : "Discard the draft and restore the stored waypoint unchanged."));
        actions.addComponent(clipboardButton);
        actions.addComponent(saveButton);
        actions.addComponent(cancelButton);
        registerResponsive(actions, new int[]{120, 100, 100});
        content.addComponent(actions);
        setComponent(content);
        setSize(820, 494);
        syncColorPickerFromCurrentStyle();
        if (edit != null) updateCoordinateDraft(false);
        focusInput(nameInput);
    }

    void openEdit(UUID id) {
        if (id == null) throw new IllegalArgumentException("waypoint id is required");
        showCoordinateForm(id);
    }

    void openCreateCoordinates(String suggestedName, String coordinates) {
        showCoordinateForm(null);
        if (nameInput != null && suggestedName != null) {
            nameInput.setTextMoveToEnd(suggestedName);
        }
        if (coordinateInput != null && coordinates != null) {
            coordinateInput.setTextMoveToEnd(coordinates);
            updateCoordinateDraft(false);
        }
        focusInput(nameInput);
    }

    private WurmInputField createNameInput(String value) {
        nameInput = inputField("waypointer.name.input", 120);
        registerHover(nameInput, "Waypoint name shown in the Manager, compass hover, and world label.");
        nameInput.setTextMoveToEnd(value == null ? "" : value);
        return nameInput;
    }

    private WurmDropDown createWorldStyleInput(MarkerStyle.WorldStyle selected) {
        MarkerStyle.WorldStyle[] values = UserMarkerStyles.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] == MarkerStyle.WorldStyle.HIDDEN
                    ? "Hidden (Manager only)" : title(values[i].name());
        }
        worldStyleInput = WaypointerUi.dropDown("waypointer.world.style",
                UserMarkerStyles.indexOf(selected), labels);
        registerHover(worldStyleInput,
                "Choose a custom presentation. Vanilla White Light, Black Light, and Rift are available only as fixed server landmarks. Hidden keeps the record enabled but Manager-only; Off disables it entirely.");
        observedWorldStyle = worldStyleInput.getValue();
        return worldStyleInput;
    }

    private void beginStyleEditor(MarkerStyle initial) {
        styleEditor = new MarkerStyleEditorState(initial);
    }

    private void addStyleControls(WurmArrayPanel<FlexComponent> content) {
        styleSliderPanel = vertical("waypointer.style.sliders");
        content.addComponent(styleSliderPanel);
        rebuildStyleSliders();
    }

    private void addArrivalControl(WurmArrayPanel<FlexComponent> content,
                                   int initialMetres) {
        arrivalRadiusMetres = WaypointArrival.requireRadius(initialMetres);
        float maximum = Math.max(200.0f, arrivalRadiusMetres);
        arrivalRadiusSlider = new WaypointStyleSlider(
                "waypointer.arrival.radius", 0.0f, maximum, 4.0f,
                arrivalRadiusMetres, WaypointStyleSlider.Display.METRES,
                new WaypointStyleSlider.Listener() {
                    @Override public void valueChanged(float value) {
                        arrivalRadiusMetres = Math.round(value);
                        updateArrivalRadiusLabel();
                    }
                });
        arrivalRadiusLabel = new WaypointerLabel("");
        updateArrivalRadiusLabel();
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.form.arrival.radius", ROW_HEIGHT);
        row.addComponent(cell(arrivalRadiusLabel, 150, ROW_HEIGHT));
        row.addComponent(cell(arrivalRadiusSlider, 620, ROW_HEIGHT));
        registerResponsive(row, new int[]{150, 400}, 1);
        content.addComponent(row);
        registerHover(arrivalRadiusSlider,
                "0m disables arrival alerts. Entering this radius on the correct surface/cave layer sends one Event message and a short bell; leave by at least 8m to re-arm it.");
    }

    private void updateArrivalRadiusLabel() {
        if (arrivalRadiusLabel != null) {
            arrivalRadiusLabel.setLabel(arrivalRadiusMetres == 0
                    ? "Arrival radius (disabled)" : "Arrival radius");
        }
    }

    private void addLifetimeControl(WurmArrayPanel<FlexComponent> content,
                                    WaypointEditData edit) {
        boolean keepCurrent = edit != null && edit.getExpiresAt() != null;
        int[] presets = WaypointLifetime.presetMinutes();
        int baseCount = presets.length + 1;
        lifetimeValues = new int[baseCount + (keepCurrent ? 1 : 0)];
        String[] labels = new String[lifetimeValues.length];
        int index = 0;
        if (keepCurrent) {
            lifetimeValues[index] = WaypointLifetime.KEEP_CURRENT_MINUTES;
            labels[index++] = "Keep current ("
                    + remainingLifetime(edit.getExpiresAt().toEpochMilli()) + ")";
        }
        lifetimeValues[index] = WaypointLifetime.PERMANENT_MINUTES;
        labels[index++] = "Permanent";
        for (int minutes : presets) {
            lifetimeValues[index] = minutes;
            labels[index++] = lifetimeLabel(minutes);
        }
        lifetimeInput = WaypointerUi.dropDown("waypointer.lifetime", 0, labels);
        registerHover(lifetimeInput,
                "Permanent waypoints remain until deleted. A temporary waypoint is automatically removed from the Manager, compass, world, storage, export, and navigation when this lifetime expires.");
        content.addComponent(formRow("Lifetime", lifetimeInput));
    }

    private int selectedLifetimeMinutes() {
        int index = lifetimeInput == null ? -1 : lifetimeInput.getValue();
        if (index < 0 || index >= lifetimeValues.length) {
            throw new IllegalStateException("waypoint lifetime selection is unavailable");
        }
        return lifetimeValues[index];
    }

    private static String lifetimeLabel(int minutes) {
        if (minutes < 60) return minutes + (minutes == 1 ? " minute" : " minutes");
        if (minutes == 60) return "1 hour";
        if (minutes % 1440 == 0) return (minutes / 1440) + " day";
        return (minutes / 60) + " hours";
    }

    private static String remainingLifetime(long expiresAtMillis) {
        long remaining = Math.max(0L, expiresAtMillis - System.currentTimeMillis());
        long minutes = (remaining + 59999L) / 60000L;
        if (minutes <= 0L) return "expired";
        if (minutes < 60L) return minutes + "m left";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + "h left" : hours + "h " + rest + "m left";
    }

    private void rebuildStyleSliders() {
        if (styleSliderPanel == null || styleEditor == null) return;
        responsiveRows.removeAll(styleSliderResponsiveRows);
        styleSliderResponsiveRows.clear();
        for (FlexComponent component : styleSliderHoverComponents) {
            hoverTexts.remove(component);
        }
        styleSliderHoverComponents.clear();
        styleSliderPanel.removeAllComponents();
        alphaSlider = null;
        markerSizeSlider = null;
        beamWidthSlider = null;
        colorPicker = null;

        final MarkerStyle initial = currentMarkerStyle();
        MarkerStyleControlProfile profile = MarkerStyleControlProfile.forStyle(
                initial.getWorldStyle());
        if (profile.isColorEditable()) {
            colorPicker = new WaypointColorPicker(initial.getRed(), initial.getGreen(),
                    initial.getBlue(), new WaypointColorPicker.Listener() {
                        @Override public void colorChanged(float red, float green,
                                                           float blue) {
                            styleEditor.setColor(red, green, blue);
                            publishLivePreview();
                        }
                    });
            registerHover(colorPicker,
                    "Drag in the large field for saturation/brightness and the lower strip for hue.");
            styleSliderHoverComponents.add(colorPicker);
            FlexComponent colorRow = formRow("Color", colorPicker, 118);
            styleSliderPanel.addComponent(colorRow);
            styleSliderResponsiveRows.add(
                    responsiveRows.get(responsiveRows.size() - 1));
        }
        if (profile.getSliderCount() == 0) {
            String text;
            if (initial.getWorldStyle() == MarkerStyle.WorldStyle.COMPASS_ONLY) {
                text = "No world-effect sliders. Compass marker size follows the compass window.";
            } else {
                text = "No world-effect sliders while this waypoint is hidden.";
            }
            addStyleNote(text);
            lastResponsiveWidth = -1;
            return;
        }

        alphaSlider = new WaypointStyleSlider("waypointer.style.alpha",
                0.0f, 1.0f, 0.01f, initial.getAlpha(),
                WaypointStyleSlider.Display.PERCENT,
                new WaypointStyleSlider.Listener() {
                    @Override public void valueChanged(float value) {
                        styleEditor.setAlpha(value);
                        publishLivePreview();
                    }
                });
        addStyleSlider(profile.getAlphaLabel(), alphaSlider,
                "Opacity of this world effect. The name/distance label remains fully bright.");

        float markerMinimum = Math.min(1.0f, initial.getMarkerSize());
        float markerMaximum = Math.max(30.0f, initial.getMarkerSize());
        markerSizeSlider = new WaypointStyleSlider("waypointer.style.marker.size",
                markerMinimum, markerMaximum, 0.5f, initial.getMarkerSize(),
                WaypointStyleSlider.Display.ONE_DECIMAL,
                new WaypointStyleSlider.Listener() {
                    @Override public void valueChanged(float value) {
                        styleEditor.setMarkerSize(value);
                        publishLivePreview();
                    }
                });
        addStyleSlider(profile.getPrimaryLabel(), markerSizeSlider,
                primarySliderHelp(profile.getKind()));

        float widthMinimum = Math.min(0.5f, initial.getBeamWidth());
        float widthMaximum = Math.max(8.0f, initial.getBeamWidth());
        beamWidthSlider = new WaypointStyleSlider("waypointer.style.beam.width",
                widthMinimum, widthMaximum, 0.25f, initial.getBeamWidth(),
                WaypointStyleSlider.Display.TWO_DECIMALS,
                new WaypointStyleSlider.Listener() {
                    @Override public void valueChanged(float value) {
                        styleEditor.setBeamWidth(value);
                        publishLivePreview();
                    }
                });
        addStyleSlider(profile.getSecondaryLabel(), beamWidthSlider,
                secondarySliderHelp(profile.getKind()));
        lastResponsiveWidth = -1;
    }

    private void addStyleSlider(String label, WaypointStyleSlider slider,
                                String hover) {
        registerHover(slider, hover);
        styleSliderHoverComponents.add(slider);
        FlexComponent row = formRow(label, slider, 28);
        styleSliderPanel.addComponent(row);
        styleSliderResponsiveRows.add(
                responsiveRows.get(responsiveRows.size() - 1));
    }

    private void addStyleNote(String text) {
        WurmLabel note = new WaypointerLabel(text);
        registerHover(note, text);
        styleSliderHoverComponents.add(note);
        FlexComponent row = fullWidthRow("waypointer.style.none", note, 28);
        styleSliderPanel.addComponent(row);
        styleSliderResponsiveRows.add(
                responsiveRows.get(responsiveRows.size() - 1));
    }

    private static String primarySliderHelp(MarkerStyleControlProfile.Kind kind) {
        switch (kind) {
            case CIRCLE:
                return "Circle wall radius. At 1 only the thin navigation line remains; at 30 the wall radius is 400m.";
            case SYMBOL:
                return "Size of the world pictogram only. Compass marker size follows the compass window.";
            case BEAM:
            default:
                return "Width and height scale of the beam field. At 1 only the thin navigation line remains; 9 is the original field; 30 is twice as large.";
        }
    }

    private static String secondarySliderHelp(MarkerStyleControlProfile.Kind kind) {
        switch (kind) {
            case CIRCLE:
                return "Thickness of the central beam; the surrounding wall radius is controlled separately.";
            case SYMBOL:
                return "Stroke thickness of the world pictogram.";
            case BEAM:
            default:
                return "Thickness of the visible beam field. The distant navigation line keeps a stable screen width.";
        }
    }

    private WurmInputField inputField(String componentName, int maxInput) {
        // Pinned Wurm constructor arguments are maxLines and maxInput, not pixels.
        WurmInputField field = WaypointerUi.input(
                componentName, this, 1, maxInput);
        field.prompt = org.waypoints.next.i18n.Messages.text("");
        field.simpleInput = true;
        return field;
    }

    private void clearEditorState() {
        try { controller.clearLivePreview(); }
        catch (Throwable ignored) { }
        if (preferredInput != null && hud != null) {
            try { hud.stopTyping(); }
            catch (Throwable ignored) { }
        }
        preferredInput = null;
        editingId = null;
        nameInput = null;
        coordinateInput = null;
        worldStyleInput = null;
        styleEditor = null;
        colorPicker = null;
        styleSliderPanel = null;
        styleSliderResponsiveRows.clear();
        styleSliderHoverComponents.clear();
        alphaSlider = null;
        markerSizeSlider = null;
        beamWidthSlider = null;
        arrivalRadiusSlider = null;
        arrivalRadiusLabel = null;
        arrivalRadiusMetres = WaypointArrival.DEFAULT_RADIUS_METRES;
        lifetimeInput = null;
        lifetimeValues = new int[0];
        observedWorldStyle = -1;
        coordinateStatusLabel = null;
        previewedInput = "";
        previewedCoordinate = null;
        nextHerePreviewAt = 0L;
        saveButton = null;
        cancelButton = null;
        clipboardButton = null;
    }

    private void focusInput(WurmInputField field) {
        preferredInput = field;
        if (field == null || hud == null) return;
        WurmComponent window = this;
        while (window.parent != null) window = window.parent;
        // setActiveWindow registers its argument as a top-level HUD component.
        // This panel belongs to the hub and must never be registered separately.
        if (window == this || !hud.getComponents().contains(window)) return;
        try {
            hud.stopTyping();
            hud.setActiveWindow(window);
            hud.startTyping();
        } catch (Throwable failure) {
            controller.reportFailure("focus manager input", failure);
        }
    }

    @Override boolean hasInputField() {
        return preferredInput != null;
    }

    @Override WurmInputField getInputField() {
        return preferredInput;
    }
    @Override boolean mouseWheeledAt(int x,int y,int delta){if(table==null||listScroll==null||!listScroll.contains(x,y))return false;listScroll.mouseWheeled(x,y,delta);return true;}

    @Override public void pick(PickData pickData, int mouseX, int mouseY) {
        super.pick(pickData, mouseX, mouseY);
        if (pickData == null) return;
        for (Map.Entry<FlexComponent, String> entry : hoverTexts.entrySet()) {
            if (entry.getKey().contains(mouseX, mouseY)) {
                pickData.addText(entry.getValue());
                break;
            }
        }
    }

    private void registerHover(FlexComponent component, String text) {
        if (component != null && text != null && !text.trim().isEmpty()) {
            hoverTexts.put(component, org.waypoints.next.i18n.Messages.text(text.trim()));
        }
    }

    private FlexComponent formRow(String label, FlexComponent input) {
        return formRow(label, input, ROW_HEIGHT);
    }

    private FlexComponent formRow(String label, FlexComponent input, int height) {
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.form." + label, height);
        row.addComponent(cell(new WaypointerLabel(label), 150, height));
        row.addComponent(cell(input, 620, height));
        registerResponsive(row, new int[]{150, 400}, 1);
        return row;
    }

    private void updateCoordinateDraft(boolean reportFailure) {
        try {
            String input = coordinateInput.getText();
            ParsedCoordinate parsed = controller.preview(input);
            WaypointCoordinate coordinate = parsed.getCoordinate();
            previewedInput = input;
            previewedCoordinate = coordinate;
            updateCoordinateStatus(parsed);
            controller.livePreview(editingId, currentDraftName(), coordinate,
                    currentMarkerStyle());
        } catch (Throwable failure) {
            previewedInput = "";
            previewedCoordinate = null;
            if (coordinateStatusLabel != null) {
                coordinateStatusLabel.setLabel("Parsed coordinates: "
                        + shortMessage(failure));
            }
            controller.clearLivePreview();
            if (reportFailure) controller.reportFailure("parse coordinates", failure);
        }
    }

    private void publishHerePreview() {
        try {
            WaypointManagerContext context = controller.context();
            WaypointCoordinate coordinate = new WaypointCoordinate(
                    context.getTileX(), context.getTileY(),
                    Double.valueOf(context.getHeight()), context.getLayer());
            previewedCoordinate = coordinate;
            controller.livePreview(null, currentDraftName(), coordinate,
                    currentMarkerStyle());
        } catch (Throwable failure) {
            controller.reportFailure("show live Here draft", failure);
        }
    }

    private void publishLivePreview() {
        if (styleEditor == null) return;
        if (coordinateInput == null) {
            publishHerePreview();
        } else if (previewedCoordinate != null
                && coordinateInput.getText().equals(previewedInput)) {
            controller.livePreview(editingId, currentDraftName(),
                    previewedCoordinate, currentMarkerStyle());
        } else {
            updateCoordinateDraft(false);
        }
    }

    private String currentDraftName() {
        return nameInput == null ? "" : nameInput.getText().trim();
    }

    private void useClipboard() {
        try {
            String clipboard = controller.clipboardText();
            if (WaypointShareCodec.containsSharedToken(clipboard)) {
                controller.importSharedClipboard();
                showList();
                return;
            }
            ParsedCoordinate parsed = controller.preview(clipboard);
            WaypointCoordinate coordinate = parsed.getCoordinate();
            String canonical = "x=" + coordinate.getTileX() + " y="
                    + coordinate.getTileY()
                    + (coordinate.getLayer() == WaypointLayer.CAVE ? " cave" : "");
            coordinateInput.setTextMoveToEnd(canonical);
            previewedInput = canonical;
            previewedCoordinate = coordinate;
            updateCoordinateStatus(parsed);
            controller.livePreview(editingId, currentDraftName(), coordinate,
                    currentMarkerStyle());
        } catch (Throwable failure) {
            previewedInput = "";
            previewedCoordinate = null;
            if (coordinateStatusLabel != null) {
                coordinateStatusLabel.setLabel("Parsed coordinates: "
                        + shortMessage(failure));
            }
            controller.clearLivePreview();
            controller.reportFailure("clipboard preview", failure);
        }
    }

    @Override public void buttonPressed(WButton button) {
        // Mutations happen only on a completed native click.
    }

    @Override public void buttonClicked(WButton button) {
        try {
            if (button == applyFilters) {
                appliedServer = selected(serverValues, serverFilter, "current");
                appliedUser = selected(userValues, userFilter, "current");
                appliedType = typeFilter.getValue(); appliedStatus = statusFilter.getValue();
                refreshRows();
            }
            else if (button == refreshButton) refreshRows();
            else if (button == addButton) showSourceChooser();
            else if (button == enableFiltered) {
                controller.setEnabled(new ArrayList<UUID>(filteredIds), true);
                refreshRows(false);
            } else if (button == disableFiltered) {
                controller.setEnabled(new ArrayList<UUID>(filteredIds), false);
                refreshRows(false);
            } else if (button == exportButton) controller.exportAll();
            else if (button == importButton) controller.importAll();
            else if (button == pasteSharedButton) {
                controller.importSharedClipboard();
                refreshRows(false);
            }
            else if (button == surroundingsButton) controller.openSurroundings();
            else if (button == sourceHere) showHereForm();
            else if (button == sourceCoordinates) showCoordinateForm(null);
            else if (button == cancelButton) showList();
            else if (button == clipboardButton) useClipboard();
            else if (button == saveButton) saveForm();
            else if (sortActions.containsKey(button)) {
                WaypointManagerQuery.SortColumn next = sortActions.get(button);
                if (next == sortColumn) sortAscending = !sortAscending;
                else { sortColumn = next; sortAscending = true; }
                refreshRows();
            } else if (rowActions.containsKey(button)) {
                RowAction action = rowActions.get(button);
                perform(action);
            }
        } catch (Throwable failure) {
            controller.reportFailure("manager button", failure);
        }
    }

    private void saveForm() {
        String name = nameInput == null ? "" : nameInput.getText().trim();
        pollWorldStyleChange();
        MarkerStyle markerStyle = currentMarkerStyle();
        int lifetimeMinutes = selectedLifetimeMinutes();
        if (coordinateInput == null) {
            controller.addHere(name, markerStyle, arrivalRadiusMetres,
                    lifetimeMinutes);
        } else {
            String input = coordinateInput.getText();
            if (editingId == null) controller.addCoordinates(name, input,
                    markerStyle, arrivalRadiusMetres, lifetimeMinutes);
            else controller.editStatic(editingId, name, input, markerStyle,
                    arrivalRadiusMetres, lifetimeMinutes);
        }
        showList();
    }

    private void perform(RowAction action) {
        switch (action.kind) {
            case DELETE:
            case REMOVE_TRACKED:
                controller.delete(action.id);
                refreshRows(false);
                break;
            case TOGGLE:
                controller.setEnabled(action.id, !action.enabled);
                refreshRows(false);
                break;
            case NAVIGATE:
                controller.toggleNavigator(action.id);
                refreshRows(false);
                break;
            case EDIT: showCoordinateForm(action.id); break;
            case SHARE:
                controller.share(action.id);
                break;
            case DUPLICATE:
                controller.duplicate(action.id);
                refreshRows(false);
                break;
            default: throw new IllegalStateException("unsupported row action");
        }
    }


    @Override public void handleInput(String input) {
        try {
            if (table != null) refreshRows();
            else if (nameInput != null) saveForm();
        } catch (Throwable failure) {
            controller.reportFailure("manager Enter", failure);
        }
    }

    @Override public void handleInputChanged(WurmInputField field, String input) {
        if (field == coordinateInput) {
            updateCoordinateDraft(false);
        } else if (field == nameInput && styleEditor != null) {
            publishLivePreview();
        }
    }

    @Override public void handleEscape(WurmInputField field) {
        showList();
    }

    @Override public void gameTick() {
        super.gameTick();
        pollWorldStyleChange();
        syncColorPickerFromCurrentStyle();
        applyResponsiveLayout();
        long tickNow = System.currentTimeMillis();
        if (viewMode == ViewMode.HERE && styleEditor != null
                && tickNow >= nextHerePreviewAt) {
            nextHerePreviewAt = tickNow + DISTANCE_REFRESH_INTERVAL_MILLIS;
            publishHerePreview();
        }
        if (table != null && tickNow >= nextCatalogueRefresh) {
            nextCatalogueRefresh = tickNow + 1000;
            refreshRows(false);
        }
        if (table == null || liveDistanceCells.isEmpty()) return;
        long now = tickNow;
        if (now < nextDistanceRefreshAt) return;
        nextDistanceRefreshAt = now + DISTANCE_REFRESH_INTERVAL_MILLIS;
        try {
            WaypointManagerContext context = controller.context();
            double originX = context.getTileX();
            double originY = context.getTileY();
            if (Math.abs(originX - lastDistanceOriginX)
                    < DISTANCE_POSITION_EPSILON_TILES
                    && Math.abs(originY - lastDistanceOriginY)
                    < DISTANCE_POSITION_EPSILON_TILES) return;
            lastDistanceOriginX = originX;
            lastDistanceOriginY = originY;
            for (LiveDistanceCell cell : liveDistanceCells) {
                int metres = WaypointDistance.metres(cell.targetTileX,
                        cell.targetTileY, originX, originY);
                if (metres != cell.metres) {
                    cell.metres = metres;
                    cell.label.setLabel(metres + "m");
                }
            }
        } catch (Throwable failure) {
            if (now >= nextDistanceErrorReportAt) {
                nextDistanceErrorReportAt = now + DISTANCE_ERROR_REPORT_INTERVAL_MILLIS;
                controller.reportFailure("refresh live distances", failure);
            }
        }
    }

    @Override void closePressed() {
        WaypointManagerWindowBridge.detach(null, "panel close");
    }

    @Override void leaving() { if (table == null) showList(); prepareDetach(); }

    void prepareDetach() {
        controller.clearLivePreview();
        restoreListSize();
    }

    void normalizeListSizeAfterRestore() {
        int normalizedWidth = Math.max(
                WaypointManagerTableLayout.minimumWindowWidth(), width);
        int normalizedHeight = Math.max(300, height);
        if (normalizedWidth != width || normalizedHeight != height) {
            setSize(normalizedWidth, normalizedHeight);
        }
        listWidth = normalizedWidth;
        listHeight = normalizedHeight;
    }

    private void rememberListSize() {
        if (table != null
                && width >= WaypointManagerTableLayout.minimumWindowWidth()
                && height >= 300) {
            listWidth = width;
            listHeight = height;
        }
    }

    private void restoreListSize() {
        if (listWidth > 0 && listHeight > 0) setSize(listWidth, listHeight);
    }

    @Override void setSize(int requestedWidth, int requestedHeight) {
        super.setSize(requestedWidth,requestedHeight);lastResponsiveWidth=-1;
    }

    private void applyResponsiveLayout() {
        if (width == lastResponsiveWidth) return;
        lastResponsiveWidth = width;
        int contentWidth = viewMode == ViewMode.LIST
                ? WaypointManagerTableLayout.contentWidth(width + WaypointManagerTableLayout.WINDOW_CHROME)
                : Math.max(1, width);
        for (ResponsiveRow row : responsiveRows) row.apply(contentWidth);
        if (viewMode == ViewMode.LIST) {
            int[] columnWidths = minimumTableColumns();
            int minimum = 0;for(int value:columnWidths)minimum+=value;
            int extra = Math.max(0, ((FlexComponent)(Object)listScroll.offs).width - minimum
                    - CONTROL_GAP * (WaypointManagerTableLayout.COLUMN_COUNT - 1));
            columnWidths[2] += extra; // Names use the remaining viewport space.
            int tableWidth=CONTROL_GAP * (WaypointManagerTableLayout.COLUMN_COUNT - 1);for(int columnWidth:columnWidths)tableWidth+=columnWidth;
            for (TableLayoutRow row : tableLayoutRows) {
                row.apply(tableWidth, columnWidths);
            }
            if (table != null) table.componentResized();
            if (listRoot != null) listRoot.componentResized();
        } else if (activeContent != null) {
            activeContent.setSize(contentWidth, activeContent.calcHeight());
            activeContent.componentResized();
        }
    }

    private int[] minimumTableColumns() {
        int[] widths = WaypointManagerTableLayout.columns(WaypointManagerTableLayout.minimumWindowWidth());
        if (!tableLayoutRows.isEmpty()) {
            List<FlexComponent> headers = tableLayoutRows.get(0).columns;
            for (int i = 0; i < headers.size(); i++) {
                FlexComponent header = headers.get(i);
                if (header instanceof WaypointerTableHeader) widths[i] = Math.max(widths[i], WaypointerTableHeader.minimumWidth(((WButton)header).getLabel()));
                else if (header instanceof WButton) widths[i] = Math.max(widths[i], uniformCaptionWidth(((WButton) header).getLabel()));
                else if (header instanceof WaypointerLabel) widths[i] = Math.max(widths[i], ((WaypointerLabel) header).textWidth());
                for (TableLayoutRow row : tableLayoutRows) if (row!=tableLayoutRows.get(0)) {
                    FlexComponent cell = row.columns.get(i);
                    WButton button = cell instanceof WaypointerTableActionCell
                            ? ((WaypointerTableActionCell)cell).button : cell instanceof WButton ? (WButton)cell : null;
                    if (button != null) widths[i] = Math.max(widths[i], uniformCaptionWidth(button.getLabel()));
                }
            }
        }
        return widths;
    }

    @Override int minimumContentWidth() {
        int required = 0;for (int value : minimumTableColumns()) required += value;
        required += 40 + CONTROL_GAP * (WaypointManagerTableLayout.COLUMN_COUNT - 1);
        for (ResponsiveRow row : responsiveRows) {
            int total = CONTROL_GAP * (row.minimums.length - 1);for (int value : row.minimums) total += value;
            required = Math.max(required, total);
        }
        return Math.max(900, required);
    }

    private void registerTableRow(WurmArrayPanel<FlexComponent> row) {
        for (int i=0;i<row.components.size();i++) {
            FlexComponent component=row.components.get(i);
            if(component instanceof ChamomiloUiV1Button) {
                ChamomiloUiV1Button button=(ChamomiloUiV1Button)component;
                if (!WaypointerButtonGroup.id(button.text).equals("all-waypoints.row-actions"))
                    tableActionGroup.apply(button,button.width);
                WaypointerTableActionCell cell=new WaypointerTableActionCell(button);
                cell.parent=row;
                row.components.set(i,cell);
            }
        }
        tableLayoutRows.add(new TableLayoutRow(row,
                new ArrayList<FlexComponent>(row.components)));
        separateControls(row);
    }

    private void registerResponsive(WurmArrayPanel<FlexComponent> row,
                                    int[] minimums, int... flexibleColumns) {
        responsiveRows.add(new ResponsiveRow(row,
                new ArrayList<FlexComponent>(row.components),
                minimums, flexibleColumns));
        separateControls(row);
    }

    private static FlexComponent gap(int width, int height) {
        return new FlexComponent("waypointer.gap", 0, 0, width, height) {
            { sizeFlags = FIXED_WIDTH | FIXED_HEIGHT; }
        };
    }

    private static void separateControls(WurmArrayPanel<FlexComponent> row) {
        List<FlexComponent> cells = new ArrayList<FlexComponent>(row.components);
        row.components.clear();
        for (FlexComponent cell : cells) {
            if (!row.components.isEmpty()) { FlexComponent space = gap(CONTROL_GAP, ROW_HEIGHT); space.parent = row; row.components.add(space); }
            row.components.add(cell);
        }
    }

    private FlexComponent fullWidthRow(String name, FlexComponent value, int height) {
        WurmArrayPanel<FlexComponent> row = horizontal(name, height);
        row.addComponent(cell(value, 770, height));
        registerResponsive(row, new int[]{400}, 0);
        return row;
    }

    private WButton button(String label, int width) {
        ChamomiloUiV1Button result = (ChamomiloUiV1Button) WaypointerUi.button(org.waypoints.next.i18n.Messages.text(label), this, width);
        commandGroup.apply(result,Math.max(width,uniformCaptionWidth(label)));
        return result;
    }

    private static WaypointerButtonGroup commandTypography(){
        List<String> captions=new ArrayList<String>();List<Integer> widths=new ArrayList<Integer>();
        String[] labels={"Refresh","Apply filters","Add...","Enable filtered","Disable filtered","Export","Import","Paste shared","Surroundings",
                "On","Off","Nav","Stop","Delete","Edit","Share","Copy","Here","Coordinates / map link","Cancel","OK","Use Clipboard"};
        int[] preferred={100,110,82,122,126,82,82,112,112,ON_WIDTH,ON_WIDTH,NAV_WIDTH,NAV_WIDTH,DELETE_WIDTH,EDIT_WIDTH,SHARE_WIDTH,COPY_WIDTH,220,220,100,100,120};
        for(int i=0;i<labels.length;i++){captions.add(org.waypoints.next.i18n.Messages.text(labels[i]));widths.add(Math.max(preferred[i],uniformCaptionWidth(labels[i])));}
        int[] geometry=new int[widths.size()];for(int i=0;i<geometry.length;i++)geometry[i]=widths.get(i);
        return new WaypointerButtonGroup("all-waypoints.commands",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,ROW_HEIGHT,captions.toArray(new String[0]),geometry);
    }

    private static int uniformCaptionWidth(String caption) {
        String label = org.waypoints.next.i18n.Messages.text(caption);
        int span=0;
        for(boolean bold:new boolean[]{false,true}){
            java.awt.Rectangle ink=org.chamomilo.wurm.ui.v1.UiTypography.ink(label,BUTTON_FONT_PIXELS,bold);
            span=Math.max(span,Math.max(org.chamomilo.wurm.ui.v1.UiTypography.width(label,BUTTON_FONT_PIXELS,bold),ink.x+ink.width)-Math.min(0,ink.x));
        }
        return span+24;
    }

    private static WaypointerButtonGroup tableActionTypography() {
        String[] labels={"On","Off","Nav","Stop","Delete","Edit","Share","Copy"};
        int[] preferred={ON_WIDTH,ON_WIDTH,NAV_WIDTH,NAV_WIDTH,DELETE_WIDTH,EDIT_WIDTH,SHARE_WIDTH,COPY_WIDTH};
        String[] captions=org.waypoints.next.i18n.Messages.texts(labels);
        for(int i=0;i<preferred.length;i++)preferred[i]=Math.max(preferred[i],uniformCaptionWidth(labels[i]));
        return new WaypointerButtonGroup("all-waypoints.row-actions",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,
                WaypointerTableActionCell.BUTTON_HEIGHT,20,false,captions,preferred);
    }

    private FlexComponent cell(FlexComponent value, int width) {
        return cell(value, width, ROW_HEIGHT);
    }

    private FlexComponent cell(FlexComponent value, int width, int height) {
        value = WaypointerUi.view(value);
        value.setInitialSize(width, height, false);
        return value;
    }

    private WurmArrayPanel<FlexComponent> horizontal(String name) {
        return horizontal(name, ROW_HEIGHT);
    }

    private WurmArrayPanel<FlexComponent> horizontal(String name, int height) {
        WurmArrayPanel<FlexComponent> row = new WurmArrayPanel<FlexComponent>(name, 1);
        row.setInitialSize(TABLE_WIDTH, height, false);
        return row;
    }

    private WurmArrayPanel<FlexComponent> vertical(String name) {
        return new WurmArrayPanel<FlexComponent>(name, 0, true);
    }

    private FlexComponent centered(FlexComponent value, int width) {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.centered");
        row.addComponent(new WurmPanel(Math.max(1, (width - value.width) / 2),
                ROW_HEIGHT, false));
        row.addComponent(value);
        return row;
    }

    private static String selected(String[] values, WurmDropDown dropdown,
                                   String fallback) {
        if (values == null || dropdown == null || dropdown.getValue() < 0
                || dropdown.getValue() >= values.length) return fallback;
        return values[dropdown.getValue()];
    }

    private MarkerStyle.WorldStyle selectedWorldStyle() {
        MarkerStyle.WorldStyle[] values = UserMarkerStyles.values();
        int selected = worldStyleInput == null ? -1 : worldStyleInput.getValue();
        return selected < 0 || selected >= values.length
                ? MarkerStyle.WorldStyle.COLORED_BEAM : values[selected];
    }

    private void pollWorldStyleChange() {
        if (styleEditor == null || worldStyleInput == null) return;
        int selected = worldStyleInput.getValue();
        if (selected == observedWorldStyle) return;
        MarkerStyle.WorldStyle[] values = UserMarkerStyles.values();
        if (selected < 0 || selected >= values.length) return;
        observedWorldStyle = selected;
        styleEditor.selectWorldStyle(values[selected]);
        refreshAllStyleControls();
    }

    private void refreshAllStyleControls() {
        MarkerStyle style = currentMarkerStyle();
        if (colorPicker != null) colorPicker.setColor(
                style.getRed(), style.getGreen(), style.getBlue());
        rebuildStyleSliders();
        publishLivePreview();
    }

    private void syncColorPickerFromCurrentStyle() {
        if (colorPicker == null || styleEditor == null) return;
        MarkerStyle style = styleEditor.getStyle();
        if (!colorPicker.matchesColor(style.getRed(), style.getGreen(),
                style.getBlue())) {
            colorPicker.setColor(style.getRed(), style.getGreen(),
                    style.getBlue());
        }
    }

    private MarkerStyle currentMarkerStyle() {
        return styleEditor == null ? MarkerStyle.defaultColoredBeam()
                : styleEditor.getStyle();
    }

    private static String format(WaypointEditData edit) {
        return "x=" + edit.getTileX() + " y=" + edit.getTileY()
                + (edit.getLayer() == WaypointLayer.CAVE ? " cave" : "");
    }

    private static String title(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ENGLISH)
                .replace('_', ' ');
        if (clean.isEmpty()) return clean;
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static String statusText(WaypointManagerRow data) {
        String age = org.waypoints.next.i18n.Messages.text(data.getDataAgeLabel(java.time.Instant.now()));
        if (data.isTemporary()) return "Temporary";
        if(data.getResolution()==WaypointResolution.LIVE_EXACT)return "Visible now";
        if (age.isEmpty()) return title(data.getResolution().name());
        switch (data.getResolution()) {
            case LAST_SEEN: return "Last seen " + age;
            case STALE: return "Stale " + age;
            case SERVER_BEARING: return title(data.getResolution().name()) + " " + age;
            default: return title(data.getResolution().name());
        }
    }

    private static String statusHelp(WaypointManagerRow data){
        if(data.isTemporary())return org.waypoints.next.i18n.Messages.format("Automatically deleted at {0}",data.getExpiresAt());
        String help;
        switch(data.getResolution()){
            case LIVE_EXACT:help="The target is loaded by the client. Its waypoint follows received movement. Time since the last coordinate update is not time since it disappeared.";break;
            case LAST_SEEN:help="The target is no longer loaded. The waypoint retains its last received position; the time shown is the age of that position.";break;
            case STALE:help="The provider no longer confirms this waypoint. Its last known position is retained.";break;
            case SERVER_BEARING:help="Approximate direction reported by the server; exact coordinates are unavailable.";break;
            case STATIC_EXACT:help="Saved fixed coordinates. This waypoint does not follow a moving object.";break;
            case EXACT_SAVED:help="Exact saved destination of a completed search.";break;
            case SEARCH_STEP:help="Intermediate position in a search; the destination is not confirmed yet.";break;
            default:help="The target has no confirmed position yet. Waiting for client or provider data.";break;
        }
        return org.waypoints.next.i18n.Messages.text(help)+(data.getLastResolvedAt()==null?"":" "+org.waypoints.next.i18n.Messages.format("Last coordinate update: {0}",data.getLastResolvedAt()));
    }

    private void updateCoordinateStatus(ParsedCoordinate parsed) {
        if (coordinateStatusLabel == null || parsed == null) return;
        WaypointCoordinate coordinate = parsed.getCoordinate();
        coordinateStatusLabel.setLabel("Parsed coordinates: server hint="
                + (parsed.getServerHint().isEmpty() ? "none" : parsed.getServerHint())
                + ", X=" + coordinate.getTileX() + ", Y=" + coordinate.getTileY()
                + ", layer=" + coordinate.getLayer()
                + ", source=" + parsed.getSourceKind());
    }

    private static String shortMessage(Throwable failure) {
        if (failure == null) return "invalid input";
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName() : message.trim();
    }

    private enum ViewMode { LIST, SOURCE, HERE, COORDINATE }

    private enum ActionKind { TOGGLE, NAVIGATE, EDIT, SHARE, DUPLICATE, DELETE, REMOVE_TRACKED }

    private static final class TableLayoutRow {
        private final WurmArrayPanel<FlexComponent> panel;
        private final List<FlexComponent> columns;

        private TableLayoutRow(WurmArrayPanel<FlexComponent> panel,
                               List<FlexComponent> columns) {
            this.panel = panel;
            this.columns = columns;
        }

        private void apply(int contentWidth, int[] widths) {
            if (columns.size() != widths.length) return;
            for (int i = 0; i < widths.length; i++) {
                FlexComponent column = columns.get(i);
                if (column instanceof WaypointerTableActionCell) ((WaypointerTableActionCell)column).resizeColumn(widths[i]);
                else if (column instanceof ChamomiloUiV1Button) { ((ChamomiloUiV1Button) column).resize(widths[i], ROW_HEIGHT); ((ChamomiloUiV1Button) column).setFixedWidth(true); }
                else if(column instanceof WaypointerTableHeader)((WaypointerTableHeader)column).resize(widths[i],ROW_HEIGHT);
                else column.setSize(widths[i], ROW_HEIGHT);
            }
            panel.setSize(contentWidth, ROW_HEIGHT);
            panel.componentResized();
        }
    }

    private static final class ResponsiveRow {
        private final WurmArrayPanel<FlexComponent> panel;
        private final List<FlexComponent> components;
        private final int[] minimums;
        private final int[] preferred;
        private final int[] flexible;

        private ResponsiveRow(WurmArrayPanel<FlexComponent> panel,
                              List<FlexComponent> components,
                              int[] minimums, int[] flexible) {
            if (components.size() != minimums.length) {
                throw new IllegalArgumentException(
                        "responsive row minimum count must match components");
            }
            this.panel = panel;
            this.components = components;
            this.minimums = minimums.clone();
            for(int i=0;i<components.size();i++) {
                FlexComponent c=components.get(i);
                if(c instanceof WButton)this.minimums[i]=Math.max(this.minimums[i],uniformCaptionWidth(((WButton)c).getLabel()));
                else if(c instanceof WaypointerLabel&&minimums[i]<100)this.minimums[i]=Math.max(this.minimums[i],((WaypointerLabel)c).textWidth());
            }
            this.preferred = new int[components.size()];
            for (int i = 0; i < components.size(); i++) {
                preferred[i] = Math.max(this.minimums[i], components.get(i).width);
            }
            this.flexible = flexible == null ? new int[0] : flexible.clone();
        }

        private void apply(int availableWidth) {
            int[] widths = allocate(minimums, preferred, availableWidth - CONTROL_GAP * (components.size() - 1), flexible);
            for (int i = 0; i < widths.length; i++) {
                FlexComponent component = components.get(i);
                if (component instanceof ChamomiloUiV1Button) { ((ChamomiloUiV1Button) component).resize(widths[i], ROW_HEIGHT); ((ChamomiloUiV1Button) component).setFixedWidth(true); }
                else component.setSize(widths[i], component.height);
            }
            panel.setSize(availableWidth, panel.height);
            panel.componentResized();
        }

        private static int[] allocate(int[] minimums, int[] preferred,
                                      int availableWidth, int[] flexible) {
            int[] result = minimums.clone();
            int minimumTotal = sum(minimums);
            int preferredTotal = sum(preferred);
            int growth = Math.min(Math.max(0, availableWidth - minimumTotal),
                    Math.max(0, preferredTotal - minimumTotal));
            int capacity = Math.max(0, preferredTotal - minimumTotal);
            int assigned = 0;
            if (capacity > 0) {
                for (int i = 0; i < result.length; i++) {
                    int delta = growth * (preferred[i] - minimums[i]) / capacity;
                    result[i] += delta;
                    assigned += delta;
                }
                for (int i = 0; assigned < growth; i = (i + 1) % result.length) {
                    if (result[i] < preferred[i]) {
                        result[i]++;
                        assigned++;
                    }
                }
            }
            int surplus = availableWidth - sum(result);
            if (surplus > 0 && flexible.length > 0) {
                for (int i = 0; i < surplus; i++) {
                    int column = flexible[i % flexible.length];
                    if (column >= 0 && column < result.length) result[column]++;
                }
            }
            return result;
        }

        private static int sum(int[] values) {
            int result = 0;
            for (int value : values) result += value;
            return result;
        }
    }

    private static final class RowAction {
        private final ActionKind kind;
        private final UUID id;
        private final boolean enabled;
        private final String displayName;

        private RowAction(ActionKind kind, UUID id, boolean enabled,
                          String displayName) {
            this.kind = kind;
            this.id = id;
            this.enabled = enabled;
            this.displayName = displayName;
        }
    }

    private final class LiveRow {
        private WaypointManagerRow data;
        private final boolean navigating;
        private final WurmLabel status;
        private String statusCaption;
        private final LiveDistanceCell distance;

        LiveRow(WaypointManagerRow data, boolean navigating, WurmLabel status,
                String statusCaption, LiveDistanceCell distance) {
            this.data = data; this.navigating = navigating; this.status = status;
            this.statusCaption = statusCaption; this.distance = distance;
        }

        boolean sameControls(WaypointManagerRow next) {
            return data.isEnabled() == next.isEnabled()
                    && navigating == controller.isNavigatorActive(next.getId())
                    && data.getName().equals(next.getName())
                    && data.getSourceType() == next.getSourceType()
                    && data.getServerLabel().equals(next.getServerLabel())
                    && data.getServerFingerprint().equals(next.getServerFingerprint())
                    && data.getUser().equals(next.getUser())
                    && data.getResolution() == next.getResolution()
                    && data.getWorldStyle() == next.getWorldStyle()
                    && Objects.equals(data.getExpiresAt(), next.getExpiresAt())
                    && (data.getDistanceMetres() == null) == (next.getDistanceMetres() == null);
        }

        void update(WaypointManagerRow next) {
            data = next;
            String caption = statusText(next);
            if (!caption.equals(statusCaption)) { statusCaption = caption; status.setLabel(caption); }
            ((WaypointerTableLabel)status).setHint(statusHelp(next));
            if (distance != null) {
                distance.targetTileX = next.getTileX(); distance.targetTileY = next.getTileY();
                if (distance.metres != next.getDistanceMetres()) {
                    distance.metres = next.getDistanceMetres(); distance.label.setLabel(distance.metres + "m");
                }
            }
        }
    }

    private static final class LiveDistanceCell {
        private final WurmLabel label;
        private double targetTileX;
        private double targetTileY;
        private int metres;

        private LiveDistanceCell(WurmLabel label, double targetTileX,
                                 double targetTileY, int metres) {
            this.label = label;
            this.targetTileX = targetTileX;
            this.targetTileY = targetTileY;
            this.metres = metres;
        }
    }
}
