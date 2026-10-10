package com.wurmonline.client.renderer.gui;

import org.waypoints.next.surroundings.CreatureModifier;
import org.waypoints.next.surroundings.DeedStatus;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKey;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsClassifier;
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.surroundings.SurroundingsRow;
import org.waypoints.next.surroundings.SurroundingsSnapshot;
import org.waypoints.next.surroundings.UniqueStatus;
import org.waypoints.next.ui.SurroundingsController;
import org.waypoints.next.ui.SurroundingsScrollState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Native catalog of creatures and ground items currently streamed by Wurm. */
final class SurroundingsWindow extends WaypointerContentPanel
        implements ButtonListener, InputFieldListener {
    private static final int ROW_HEIGHT = 32;
    private static final int TABLE_WIDTH = 950;
    private static final long AUTO_REFRESH_MILLIS = 1000L;
    private static final long SCROLL_SETTLE_MILLIS = 1200L;

    private static final int MARK_WIDTH = 62;
    private static final int NAME_WIDTH = 190;
    private static final int CATEGORY_WIDTH = 108;
    private static final int TRAIT_WIDTH = 82;
    private static final int HOSTILITY_WIDTH = 100;
    private static final int UNIQUE_WIDTH = 62;
    private static final int SHORT_NAME_WIDTH = TRAIT_WIDTH + UNIQUE_WIDTH;
    private static final int MATERIAL_WIDTH = 90;
    private static final int RARITY_WIDTH = 64;
    private static final int DEED_WIDTH = 76;
    private static final int DISTANCE_WIDTH = 68;

    private static final String[] MATERIAL_LABELS = {
            "Wood", "Iron", "Steel", "Copper", "Silver", "Gold", "Stone",
            "Marble", "Slate", "Leather", "Pottery", "Cotton", "Wemp",
            "Organic", "Magic"
    };
    private static final String[] MATERIAL_VALUES = {
            "wood", "iron", "steel", "copper", "silver", "gold", "stone",
            "marble", "slate", "leather", "pottery", "cotton", "wemp",
            "vegetarian", "magic"
    };

    private final SurroundingsController controller;
    private final Map<WButton, RowAction> rowActions =
            new HashMap<WButton, RowAction>();
    private final List<SurroundingKey> filteredKeys =
            new ArrayList<SurroundingKey>();
    private final Map<SurroundingKind, FilterState> filters =
            new EnumMap<SurroundingKind, FilterState>(SurroundingKind.class);
    private final Map<SurroundingKind, Integer> scrollOffsets =
            new EnumMap<SurroundingKind, Integer>(SurroundingKind.class);
    private final SurroundingsScrollState scrollState =
            new SurroundingsScrollState(SCROLL_SETTLE_MILLIS);

    private SurroundingKind activeKind = SurroundingKind.ANIMAL;
    private WButton animalsTab;
    private WButton containersTab;
    private WButton itemsTab;
    private WurmInputField searchInput;
    private WButton clearSearch;
    private WButton categoryFilter;
    private WButton modifierFilter;
    private WButton uniqueFilter;
    private WButton materialFilter;
    private WButton rarityFilter;
    private WButton deedFilter;
    private WButton layerFilter;
    private WButton markedFilter;
    private WurmInputField excludedNamesInput;
    private WButton clearExcludedNames;
    private final Map<WButton,SurroundingsQuery.SortColumn> sortActions=new LinkedHashMap<WButton,SurroundingsQuery.SortColumn>();
    private WurmLabel countLabel;
    private WurmArrayPanel<FlexComponent> table;
    private WurmArrayPanel<FlexComponent> tableHeader;
    private WurmBorderPanel tablePanel;
    private SurroundingsScrollPanel scrollPanel;
    private WButton waypointFiltered;
    private WButton clearFiltered;
    private WButton clearAll;
    private WButton refreshButton;
    private WButton managerButton;
    private WButton monitoringButton;
    private WurmArrayPanel<FlexComponent> fieldFilters;
    private WurmArrayPanel<FlexComponent> footer;
    private WaypointerButtonGroup filterTypography;
    private WaypointerButtonGroup markTypography;
    private int markWidth;
    private final Map<WButton,Integer> filterWidths=new LinkedHashMap<WButton,Integer>();
    private final Map<WButton,List<String>> filterCaptions=new LinkedHashMap<WButton,List<String>>();
    private long displayedRevision = Long.MIN_VALUE;
    private long nextAutoRefreshAt;

    SurroundingsWindow(SurroundingsController controller) {
        super("wurm-waypointer.surroundings", true);
        this.controller = controller;
        for (SurroundingKind kind : SurroundingKind.values()) {
            filters.put(kind, new FilterState());
            scrollOffsets.put(kind, Integer.valueOf(0));
        }
        setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer - Surroundings"));
        rebuildView("");
    }

    void refreshFromController() { refreshRows(); }
    SurroundingsController controller() { return controller; }

    boolean mouseWheeledAt(int mouseX, int mouseY, int wheelDelta) {
        return scrollPanel != null && scrollPanel.contains(mouseX, mouseY)
                && scrollPanel.scrollWheel(wheelDelta);
    }

    private void rebuildView(String search) {
        excludedNamesInput = null;
        clearExcludedNames = null;
        WurmBorderPanel root = new WurmBorderPanel("waypointer.surroundings.root");
        WurmArrayPanel<FlexComponent> filterPanel = vertical(
                "waypointer.surroundings.filters");
        filterPanel.addComponent(tabAndSearchRow(search));
        filterPanel.addComponent(excludedNamesFilterRow());
        fieldFilters = (WurmArrayPanel<FlexComponent>) fieldFilterRow();
        filterPanel.addComponent(fieldFilters);
        countLabel=new WaypointerLabel("0 objects");filterPanel.addComponent(cell(countLabel,800));
        monitoringButton = WaypointerUi.button("ADD TO MONITOR",this,144,144,org.chamomilo.wurm.ui.v1.UiDensity.LOW);
        ChamomiloUiV1Button monitorButton=(ChamomiloUiV1Button)monitoringButton;
        // A three-row primary action is a singleton, separate from 32 px toolbars.
        WaypointerButtonGroup.primary(monitorButton,"ADD","TO","MONITOR");
        monitoringButton.sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
        monitoringButton.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Open Monitoring with the current filters."));
        WurmBorderPanel top = new WurmBorderPanel("surroundings.top");
        int topHeight = filterPanel.calcHeight();
        top.setInitialSize(TABLE_WIDTH, topHeight, false);
        top.sizeFlags = FIXED_HEIGHT;
        top.setComponent(filterPanel, CENTER);
        root.setComponent(top, WurmBorderPanel.NORTH);

        table = new WurmArrayPanel<FlexComponent>("waypointer.surroundings.table",0);
        scrollPanel = new SurroundingsScrollPanel(
                "waypointer.surroundings.scroll", table, ROW_HEIGHT,
                new SurroundingsScrollPanel.ScrollListener() {
                    @Override public void userScrolled(int offset,
                                                       long nowMillis) {
                        scrollState.observe(offset, nowMillis);
                        scrollOffsets.put(activeKind, Integer.valueOf(offset));
                    }
                });
        tablePanel = new WurmBorderPanel("waypointer.surroundings.table-panel");
        tablePanel.sizeFlags = FIXED_WIDTH;
        tablePanel.setComponent(scrollPanel, WurmBorderPanel.CENTER);
        WurmBorderPanel tableArea = new WurmBorderPanel("surroundings.table-area");
        tableArea.setComponent(tablePanel, WEST);
        WurmBorderPanel monitor = new WurmBorderPanel("surroundings.monitor-action");
        WurmArrayPanel<FlexComponent> monitorRow = horizontal("surroundings.monitor-row");
        monitorRow.addComponent(spacer(24, 1));
        monitorRow.addComponent(monitoringButton);
        monitor.setComponent(monitorRow, NORTH);
        tableArea.setComponent(monitor, CENTER);
        root.setComponent(tableArea, WurmBorderPanel.CENTER);
        footer = (WurmArrayPanel<FlexComponent>) actionRow();
        root.setComponent(footer, WurmBorderPanel.SOUTH);
        setComponent(root);
        filteredKeys.clear();
        refreshRows(scrollOffsets.get(activeKind).intValue());
    }

    private FlexComponent tabAndSearchRow(String search) {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.surroundings.tabs");
        animalsTab = button(tabLabel("Animals", SurroundingKind.ANIMAL), 92);
        containersTab = button(tabLabel("Containers", SurroundingKind.CONTAINER), 104);
        itemsTab = button(tabLabel("Items", SurroundingKind.ITEM), 82);

        searchInput = WaypointerUi.input("waypointer.surroundings.search", this);
        searchInput.setInitialSize(310, ROW_HEIGHT, false);
        searchInput.prompt = "";
        searchInput.setTextMoveToEnd(search == null ? "" : search);
        clearSearch = button("Clear", 62);
        return new WaypointerFilterRow("+ filter",searchInput,clearSearch,"Example: horse, wolf");
    }

    private FlexComponent fieldFilterRow() {
        filterWidths.clear();filterCaptions.clear();
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.surroundings.fields");
        categoryFilter = modifierFilter = uniqueFilter = materialFilter = null;
        rarityFilter = deedFilter = layerFilter = markedFilter = null;
        FilterState state = state();
        if (activeKind == SurroundingKind.ANIMAL) {
            modifierFilter = filterButton("Condition", state.modifiers,
                    modifierChoices(), 150);
            uniqueFilter = filterButton("Unique", state.uniques,
                    uniqueChoices(), 125);
            deedFilter = filterButton("Deed", state.deeds, deedChoices(), 125);
            layerFilter = filterButton("Layer", state.layers, layerChoices(), 120);
            markedFilter = filterButton("Mark", state.marks, markChoices(), 115);
            row.addComponent(modifierFilter);
            row.addComponent(uniqueFilter);
            row.addComponent(deedFilter);
            row.addComponent(layerFilter);
            row.addComponent(markedFilter);
        } else {
            categoryFilter = filterButton("Category", state.categories,
                    categoryChoices(activeKind), 145);
            materialFilter = filterButton("Material", state.materials,
                    materialChoices(), 135);
            rarityFilter = filterButton("Rarity", state.rarities,
                    rarityChoices(), 105);
            deedFilter = filterButton("Deed", state.deeds, deedChoices(), 120);
            layerFilter = filterButton("Layer", state.layers, layerChoices(), 100);
            markedFilter = filterButton("Mark", state.marks, markChoices(), 100);
            row.addComponent(categoryFilter);
            row.addComponent(materialFilter);
            row.addComponent(rarityFilter);
            row.addComponent(deedFilter);
            row.addComponent(layerFilter);
            row.addComponent(markedFilter);
        }
        List<FlexComponent> members=new ArrayList<FlexComponent>(row.components);
        row.removeAllComponents();
        for(int i=0;i<members.size();i++){if(i>0)row.addComponent(spacer(6,ROW_HEIGHT));row.addComponent(members.get(i));}
        coordinateFilters();
        return row;
    }

    private FlexComponent excludedNamesFilterRow() {
        FilterState state = state();
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings.excluded-names");
        excludedNamesInput = WaypointerUi.input(
                "waypointer.surroundings.excluded-names.input", this);
        excludedNamesInput.setInitialSize(310, ROW_HEIGHT, false);
        excludedNamesInput.prompt = org.waypoints.next.i18n.Messages.text("");
        excludedNamesInput.setTextMoveToEnd(joinFragments(state.excludedNames));
        clearExcludedNames = button("Clear", 62);
        return new WaypointerFilterRow("- filter",excludedNamesInput,clearExcludedNames,"Example: catseyes, post");
    }

    private FlexComponent actionRow() {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.surroundings.actions");
        waypointFiltered = button("Track filtered", 142);
        clearFiltered = button("Clear filtered", 112);
        clearAll = button("Clear all marks", 122);
        refreshButton = button("Refresh", 82);
        waypointFiltered.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Mark every filtered object by its ID. The waypoint follows its received position."));
        clearFiltered.setHoverString(org.waypoints.next.i18n.Messages.text(
                "Delete Surroundings waypoints for every filtered row."));
        clearAll.setHoverString(org.waypoints.next.i18n.Messages.text("Delete every Surroundings tracked target."));
        refreshButton.setHoverString(org.waypoints.next.i18n.Messages.text("Refresh the loaded objects using the current filters and sorting."));
        row.addComponent(waypointFiltered);
        row.addComponent(spacer(8, ROW_HEIGHT));
        row.addComponent(clearFiltered);
        row.addComponent(spacer(8, ROW_HEIGHT));
        row.addComponent(clearAll);
        row.addComponent(spacer(8, ROW_HEIGHT));
        row.addComponent(refreshButton);
        WaypointerButtonGroup.peers("surroundings.footer",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,ROW_HEIGHT,
                waypointFiltered,clearFiltered,clearAll,refreshButton);
        return row;
    }

    private void refreshRows() {
        refreshRows(captureScrollOffset());
    }

    private void refreshRows(int scrollOffset) {
        try {
            SurroundingsSnapshot snapshot = controller.snapshot(query());
            displayedRevision = snapshot.getRevision();
            List<FlexComponent> components = new ArrayList<FlexComponent>(
                    snapshot.getRows().size());
            tableHeader = header();
            tablePanel.setComponent(tableHeader, WurmBorderPanel.NORTH);
            rowActions.clear();
            filteredKeys.clear();
            for (SurroundingsRow row : snapshot.getRows()) {
                filteredKeys.add(row.getEntry().getKey());
                components.add(dataRow(row));
            }
            table.removeAllComponents();
            table.addComponents(components.toArray(
                    new FlexComponent[components.size()]));
            fitTableColumns();
            restoreScroll(scrollOffset);
            countLabel.setLabel(org.waypoints.next.i18n.Messages.format("{0} of {1}; {2} tracked",
                    snapshot.getFilteredCount(),snapshot.getTotalCount(),snapshot.getMarkedCount()),org.waypoints.next.i18n.Messages.text("Matching rows / loaded objects in this section; marked objects in this section."));
        } catch (Throwable failure) {
            controller.reportFailure("refresh catalog", failure);
        }
    }

    private SurroundingsQuery query() {
        captureExcludedNames();
        return query(activeKind, searchText());
    }

    private void fitTableColumns(){
        WurmArrayPanel<?> header=tableHeader;
        List<FlexComponent> layoutRows=new ArrayList<FlexComponent>(table.components);
        layoutRows.add(0,tableHeader);
        int[] widths=new int[header.components.size()];
        for(int i=0;i<widths.length;i++){
            FlexComponent label=header.components.get(i);
            widths[i]=Math.max(label.width,label instanceof WaypointerLabel?((WaypointerLabel)label).textWidth():0);
            for(FlexComponent child:layoutRows){
                FlexComponent value=((WurmArrayPanel<?>)child).components.get(i);
                widths[i]=Math.max(widths[i],value.width);
                if(value instanceof WaypointerTableActionCell)widths[i]=Math.max(widths[i],WaypointerUi.captionWidth(((WaypointerTableActionCell)value).button));
                else if(value instanceof WButton)widths[i]=Math.max(widths[i],WaypointerUi.captionWidth((WButton)value));
                else if(value instanceof WaypointerLabel)widths[i]=Math.max(widths[i],((WaypointerLabel)value).textWidth());
            }
        }
        for(FlexComponent child:layoutRows){
            WurmArrayPanel<?> row=(WurmArrayPanel<?>)child;
            for(int i=0;i<widths.length;i++){
                FlexComponent value=row.components.get(i);
                // SDK buttons retain fixed widths; use their explicit resize API.
                if(value instanceof WaypointerTableActionCell)((WaypointerTableActionCell)value).resizeColumn(widths[i]);
                else if(value instanceof ChamomiloUiV1Button)((ChamomiloUiV1Button)value).resize(widths[i],ROW_HEIGHT);
                else if(value instanceof WaypointerTableHeader)((WaypointerTableHeader)value).resize(widths[i],ROW_HEIGHT);
                else value.setSize(widths[i],value.height);
            }
            row.componentResized();
        }
        table.componentResized();
        tablePanel.width = tableHeader.calcWidth() + 18;
        tablePanel.componentResized();
    }

    private SurroundingsQuery query(SurroundingKind kind, String search) {
        FilterState state = filters.get(kind);
        return SurroundingsQuery.builder().kind(kind)
                .text(search)
                .excludedText(joinFragments(state.excludedNames))
                .categories(state.categories).modifiers(state.modifiers)
                .uniqueStatuses(state.uniques).materials(state.materials)
                .rarities(state.rarities).deedStatuses(state.deeds)
                .layers(state.layers).marks(state.marks)
                .sort(state.sort, state.ascending).build();
    }

    private String searchText() {
        return searchInput == null || searchInput.getText() == null
                ? "" : searchInput.getText();
    }

    private WurmArrayPanel<FlexComponent> header() {
        WurmArrayPanel<FlexComponent> row = new WaypointerTableHeader.Row("surroundings.headers");
        sortActions.clear();
        String[] markCaptions=org.waypoints.next.i18n.Messages.texts(new String[]{"Mark","Unmark"});
        markWidth=Math.max(MARK_WIDTH,Math.max(WaypointerButtonGroup.width(markCaptions[0],20,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false),WaypointerButtonGroup.width(markCaptions[1],20,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false)));
        markTypography=new WaypointerButtonGroup("surroundings.row-marks",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,WaypointerTableActionCell.BUTTON_HEIGHT,20,false,markCaptions,new int[]{markWidth,markWidth});
        addHeader(row,"Mark",markWidth,SurroundingsQuery.SortColumn.MARK);
        addHeader(row,"Name",NAME_WIDTH,SurroundingsQuery.SortColumn.NAME);
        if (activeKind == SurroundingKind.ANIMAL) {
            addHeader(row,"Condition",TRAIT_WIDTH,SurroundingsQuery.SortColumn.CONDITION);
            addHeader(row,"Hostility",HOSTILITY_WIDTH,SurroundingsQuery.SortColumn.HOSTILITY);
            addHeader(row,"Unique",UNIQUE_WIDTH,SurroundingsQuery.SortColumn.UNIQUE);
        } else {
            addHeader(row,"Category",CATEGORY_WIDTH,SurroundingsQuery.SortColumn.CATEGORY);
            addHeader(row,"Short name",SHORT_NAME_WIDTH,SurroundingsQuery.SortColumn.SHORT_NAME);
            addHeader(row,"Material",MATERIAL_WIDTH,SurroundingsQuery.SortColumn.MATERIAL);
            addHeader(row,"Rarity",RARITY_WIDTH,SurroundingsQuery.SortColumn.RARITY);
        }
        addHeader(row,"Deed",DEED_WIDTH,SurroundingsQuery.SortColumn.DEED);
        addHeader(row,"Distance",DISTANCE_WIDTH,SurroundingsQuery.SortColumn.DISTANCE);
        return row;
    }

    private void addHeader(WurmArrayPanel<FlexComponent> row,String title,int minimum,SurroundingsQuery.SortColumn column){
        String caption=org.waypoints.next.i18n.Messages.text(title);
        int width=Math.max(minimum,WaypointerTableHeader.minimumWidth(caption));
        String[] titles=activeKind==SurroundingKind.ANIMAL?new String[]{"Mark","Name","Condition","Hostility","Unique","Deed","Distance"}:new String[]{"Mark","Name","Category","Short name","Material","Rarity","Deed","Distance"};
        WButton button=new WaypointerTableHeader("surroundings.headers",caption,width,state().sort==column?(state().ascending?1:-1):0,WaypointerTableHeader.baseline(titles),column==SurroundingsQuery.SortColumn.DISTANCE,this,org.waypoints.next.i18n.Messages.text(columnHelp(column))+" "+org.waypoints.next.i18n.Messages.text("Click: ascending, descending, then original order."));
        row.addComponent(button);sortActions.put(button,column);
    }

    private static String columnHelp(SurroundingsQuery.SortColumn column){
        switch(column){
            case MARK:return "Mark follows the object by ID. Unmark removes its waypoint.";
            case NAME:return "Full object name received from the client.";
            case CONDITION:return "Creature condition/modifier from its name, such as young or fierce.";
            case HOSTILITY:return "Attitude received from the client: friendly, neutral, hostile or unknown.";
            case UNIQUE:return "Whether the creature belongs to a known unique creature type.";
            case CATEGORY:return "Object category inferred from its client model.";
            case SHORT_NAME:return "Undecorated short name received from the client.";
            case MATERIAL:return "Material received from the client; a dash means unavailable.";
            case RARITY:return "Client rarity: ordinary, rare, supreme or fantastic.";
            case DEED:return "Inside or outside known deed bounds; unknown when deed data is unavailable.";
            default:return "Straight-line distance from your current position, in metres.";
        }
    }

    private WurmLabel value(String caption,SurroundingsQuery.SortColumn column){
        boolean translate=column!=SurroundingsQuery.SortColumn.NAME&&column!=SurroundingsQuery.SortColumn.SHORT_NAME&&column!=SurroundingsQuery.SortColumn.CATEGORY&&column!=SurroundingsQuery.SortColumn.MATERIAL;
        return new WaypointerTableLabel(caption,org.waypoints.next.i18n.Messages.text(columnHelp(column)),translate,column==SurroundingsQuery.SortColumn.DISTANCE);
    }

    private FlexComponent dataRow(SurroundingsRow data) {
        SurroundingEntry entry = data.getEntry();
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings.row." + entry.getKey());
        WButton mark = button(data.isWaypointEnabled() ? "Unmark" : "Mark", markWidth);
        markTypography.apply((ChamomiloUiV1Button)mark,markWidth);
        WaypointerButtonGroup.active(mark,data.isWaypointEnabled());
        mark.setHoverString(org.waypoints.next.i18n.Messages.text(data.isWaypointEnabled()
                ? "Delete this Surroundings waypoint."
                : "Mark this object by ID. The waypoint follows its received position."));
        rowActions.put(mark, new RowAction(entry.getKey(), data.isWaypointEnabled()));
        row.addComponent(new WaypointerTableActionCell(mark));
        row.addComponent(cell(value(entry.getName(),SurroundingsQuery.SortColumn.NAME), NAME_WIDTH));
        if (entry.getKind() == SurroundingKind.ANIMAL) {
            row.addComponent(cell(value(entry.getCreatureModifier().getLabel(),SurroundingsQuery.SortColumn.CONDITION), TRAIT_WIDTH));
            row.addComponent(cell(value(entry.getHostility().getLabel(),SurroundingsQuery.SortColumn.HOSTILITY), HOSTILITY_WIDTH));
            row.addComponent(cell(value(entry.isUniqueCreature() ? "Yes" : "No",SurroundingsQuery.SortColumn.UNIQUE), UNIQUE_WIDTH));
        } else {
            row.addComponent(cell(value(entry.getCategory(),SurroundingsQuery.SortColumn.CATEGORY), CATEGORY_WIDTH));
            row.addComponent(cell(value(entry.getShortName(),SurroundingsQuery.SortColumn.SHORT_NAME),
                    SHORT_NAME_WIDTH));
            row.addComponent(cell(value(emptyDash(entry.getMaterial()),SurroundingsQuery.SortColumn.MATERIAL), MATERIAL_WIDTH));
            row.addComponent(cell(value(rarity(entry.getRarity()),SurroundingsQuery.SortColumn.RARITY), RARITY_WIDTH));
        }
        row.addComponent(cell(value(entry.getDeedStatus().getLabel(),SurroundingsQuery.SortColumn.DEED), DEED_WIDTH));
        row.addComponent(cell(value(data.getDistanceMetres() + "m",SurroundingsQuery.SortColumn.DISTANCE), DISTANCE_WIDTH));
        return row;
    }

    @Override public void buttonPressed(WButton button) { }

    @Override public void buttonClicked(WButton button) {
        try {
            if (button == animalsTab) selectKind(SurroundingKind.ANIMAL);
            else if (button == containersTab) selectKind(SurroundingKind.CONTAINER);
            else if (button == itemsTab) selectKind(SurroundingKind.ITEM);
            else if (button == clearSearch) {
                searchInput.setTextMoveToEnd("");
                refreshRows();
            } else if (button == clearExcludedNames) {
                excludedNamesInput.setTextMoveToEnd("");
                captureExcludedNames();
                refreshRows();
            } else if (button == categoryFilter) openFilter(button, "Categories",
                    categoryChoices(activeKind), state().categories);
            else if (button == modifierFilter) openFilter(button, "Conditions",
                    modifierChoices(), state().modifiers);
            else if (button == uniqueFilter) openFilter(button, "Unique creatures",
                    uniqueChoices(), state().uniques);
            else if (button == materialFilter) openFilter(button, "Materials",
                    materialChoices(), state().materials);
            else if (button == rarityFilter) openFilter(button, "Rarities",
                    rarityChoices(), state().rarities);
            else if (button == deedFilter) openFilter(button, "Deed status",
                    deedChoices(), state().deeds);
            else if (button == layerFilter) openFilter(button, "Layers",
                    layerChoices(), state().layers);
            else if (button == markedFilter) openFilter(button, "Mark status",
                    markChoices(), state().marks);
            else if (sortActions.containsKey(button)) {
                SurroundingsQuery.SortColumn column=sortActions.get(button);
                if(state().sort!=column){state().sort=column;state().ascending=true;}
                else if(state().ascending)state().ascending=false;
                else {state().sort=SurroundingsQuery.SortColumn.NONE;state().ascending=true;}
                refreshRows();
            }
            else if (button == refreshButton) refreshRows();
            else if (button == monitoringButton) {
                SurroundingsWindowBridge.showMonitoring(
                        this, java.util.Collections.singletonList(query()));
            }
            else if (button == waypointFiltered) {
                controller.setWaypoints(new ArrayList<SurroundingKey>(filteredKeys), true);
                refreshRows();
            } else if (button == clearFiltered) {
                controller.setWaypoints(new ArrayList<SurroundingKey>(filteredKeys), false);
                refreshRows();
            } else if (button == clearAll) {
                controller.clearAllWaypoints();
                refreshRows();
            } else if (button == managerButton) controller.openWaypointManager();
            else if (rowActions.containsKey(button)) {
                RowAction action = rowActions.get(button);
                controller.setWaypoint(action.key, !action.enabled);
                refreshRows();
            }
        } catch (Throwable failure) {
            controller.reportFailure("catalog button", failure);
        }
    }

    private WaypointerUiWindow filterWindow;

    private <T> void openFilter(WButton anchor, String title, List<Choice<T>> choices, Set<T> selected) {
        if (hud == null || anchor == null) return;
        closeFilter();
        WaypointerUiWindow popup = new WaypointerUiWindow("waypointer.surroundings.filter", false);
        filterWindow = popup;
        popup.setTitle(org.waypoints.next.i18n.Messages.text(title));
        WurmArrayPanel<FlexComponent> columns = new WurmArrayPanel<FlexComponent>("filter.columns", WurmArrayPanel.DIR_HORIZONTAL);
        int maximumRows=Math.max(1,(hud.getHeight()-20-150)/ROW_HEIGHT);
        int columnCount=Math.max(1,(choices.size()+maximumRows-1)/maximumRows);
        int rowsPerColumn=Math.max(1,(choices.size()+columnCount-1)/columnCount);
        int desiredWidth = 300;
        List<String> captions=new ArrayList<String>();
        captions.add(org.waypoints.next.i18n.Messages.text("All (clear selection)"));
        captions.add(org.waypoints.next.i18n.Messages.text("Done"));
        for(Choice<T> choice:choices)captions.add(org.waypoints.next.i18n.Messages.text(choice.label));
        for(String caption:captions)desiredWidth=Math.max(desiredWidth,WaypointerButtonGroup.width(caption,20,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false));
        desiredWidth=Math.min(desiredWidth,Math.max(120,(hud.getWidth()-20-64-(columnCount-1)*8)/columnCount-38));
        int choicesWidth=columnCount*(desiredWidth+38)+(columnCount-1)*8;
        int[] geometry=new int[captions.size()];java.util.Arrays.fill(geometry,desiredWidth);
        WaypointerButtonGroup typography=new WaypointerButtonGroup("surroundings.filter-choices",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,ROW_HEIGHT,20,false,captions.toArray(new String[0]),geometry);
        final Map<WaypointerFilterCheck,Choice<T>> checks=new LinkedHashMap<WaypointerFilterCheck,Choice<T>>();
        Runnable changed = () -> {
            for(Map.Entry<WaypointerFilterCheck,Choice<T>> entry:checks.entrySet())entry.getKey().setChecked(selected.contains(entry.getValue().value));
            updateFilterLabels();refreshRows();
        };
        ChamomiloUiV1Button clear = new ChamomiloUiV1Button(org.waypoints.next.i18n.Messages.text("All (clear selection)"), desiredWidth,
                () -> { selected.clear();changed.run(); });
        typography.apply(clear,choicesWidth);
        clear.setHoverString(org.waypoints.next.i18n.Messages.text("Clear all selections in this filter; allow every value."));
        WurmArrayPanel<FlexComponent> rows=null;
        for (int index=0;index<choices.size();index++) {
            if(index%rowsPerColumn==0){
                if(rows!=null)columns.addComponent(spacer(8,1));
                rows=new WurmArrayPanel<FlexComponent>("filter.choices",WurmArrayPanel.DIR_VERTICAL);
                columns.addComponent(rows);
            }
            Choice<T> choice=choices.get(index);
            Runnable toggle=() -> {if(!selected.add(choice.value))selected.remove(choice.value);changed.run();};
            WaypointerFilterCheck check=new WaypointerFilterCheck(selected.contains(choice.value),toggle);
            ChamomiloUiV1Button button=new ChamomiloUiV1Button(org.waypoints.next.i18n.Messages.text(choice.label),desiredWidth,toggle);
            typography.apply(button,desiredWidth);
            String hint=org.waypoints.next.i18n.Messages.text(choice.label)+". "+org.waypoints.next.i18n.Messages.text("Toggle this value. Multiple selected values are combined with OR.");
            check.setHoverString(hint);button.setHoverString(hint);checks.put(check,choice);
            WurmArrayPanel<FlexComponent> choiceRow=new WurmArrayPanel<FlexComponent>("filter.choice",WurmArrayPanel.DIR_HORIZONTAL);
            choiceRow.addComponent(check);choiceRow.addComponent(spacer(6,ROW_HEIGHT));choiceRow.addComponent(button);
            choiceRow.sizeFlags=FIXED_HEIGHT;rows.addComponent(choiceRow);
        }
        WurmBorderPanel root = new WurmBorderPanel("filter.root");
        root.setComponent(clear,NORTH);
        root.setComponent(columns,CENTER);
        ChamomiloUiV1Button done=new ChamomiloUiV1Button(org.waypoints.next.i18n.Messages.text("Done"),desiredWidth,() -> closeFilter());
        typography.apply(done,choicesWidth);root.setComponent(done,SOUTH);
        done.setHoverString(org.waypoints.next.i18n.Messages.text("Close this list. Selection changes already apply immediately."));
        popup.setComponent(root);
        popup.setInitialSize(choicesWidth+64,150+rowsPerColumn*ROW_HEIGHT,false);
        popup.setPosition(Math.max(0,Math.min(anchor.x,hud.getWidth()-popup.width)),Math.max(0,Math.min(anchor.y+anchor.height,hud.getHeight()-popup.height)));
        popup.onClose(() -> closeFilter());popup.show(hud);
    }
    private void closeFilter() { WaypointerUiWindow previous=filterWindow;filterWindow=null;if(previous!=null)previous.dispose(); }
    @Override void leaving() { closeFilter(); }


    void selectKind(SurroundingKind kind) {
        if (kind == activeKind) return;
        captureExcludedNames();
        String search = searchInput == null ? "" : searchInput.getText();
        rememberScrollOffset();
        activeKind = kind;
        rebuildView(search);
    }

    private int captureScrollOffset() {
        return scrollPanel == null
                ? scrollOffsets.get(activeKind).intValue()
                : Math.max(0, scrollPanel.yo);
    }

    private void restoreScroll(int requestedOffset) {
        if (scrollPanel == null) return;
        scrollPanel.contentChanged();
        scrollPanel.restoreOffset(requestedOffset);
        int restored = Math.max(0, scrollPanel.yo);
        scrollOffsets.put(activeKind, Integer.valueOf(restored));
        scrollState.synchronize(restored);
    }

    private void rememberScrollOffset() {
        if (scrollPanel != null) {
            scrollOffsets.put(activeKind,
                    Integer.valueOf(Math.max(0, scrollPanel.yo)));
        }
    }


    private void captureExcludedNames() {
        if (excludedNamesInput == null) return;
        FilterState state = state();
        state.excludedNames.clear();
        String input = excludedNamesInput.getText();
        if (input == null) return;
        for (String fragment : input.split("[,;]+")) {
            String clean = fragment.trim();
            if (!clean.isEmpty()) state.excludedNames.add(clean);
        }
    }

    private static String joinFragments(Collection<String> values) {
        StringBuilder result = new StringBuilder();
        if (values != null) for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(", ");
            result.append(value.trim());
        }
        return result.toString();
    }

    private void updateFilterLabels() {
        FilterState state = state();
        updateFilterButton(categoryFilter, "Category", state.categories,
                categoryChoices(activeKind));
        updateFilterButton(modifierFilter, "Condition", state.modifiers, modifierChoices());
        updateFilterButton(uniqueFilter, "Unique", state.uniques, uniqueChoices());
        updateFilterButton(materialFilter, "Material", state.materials, materialChoices());
        updateFilterButton(rarityFilter, "Rarity", state.rarities, rarityChoices());
        updateFilterButton(deedFilter, "Deed", state.deeds, deedChoices());
        updateFilterButton(layerFilter, "Layer", state.layers, layerChoices());
        updateFilterButton(markedFilter, "Mark", state.marks, markChoices());
    }

    @Override public void handleInput(String input) { refreshRows(); }

    @Override boolean hasInputField() { return searchInput != null; }

    @Override WurmInputField getInputField() { return searchInput; }

    @Override public void handleInputChanged(WurmInputField field, String input) {
        if (field == searchInput || field == excludedNamesInput) refreshRows();
    }

    @Override public void handleEscape(WurmInputField field) {
        SurroundingsWindowBridge.closed(this);
    }

    @Override public void gameTick() {
        super.gameTick();
        long now = System.currentTimeMillis();
        if (scrollPanel != null) {
            int offset = Math.max(0, scrollPanel.yo);
            if (scrollState.observe(offset, now)) {
                scrollOffsets.put(activeKind, Integer.valueOf(offset));
            }
        }
        if (now < nextAutoRefreshAt || !scrollState.permitsAutoRefresh(now)) return;
        nextAutoRefreshAt = now + AUTO_REFRESH_MILLIS;
        if (controller.revision() != displayedRevision) refreshRows();
    }

    @Override void closePressed() { SurroundingsWindowBridge.closed(this); }

    private FilterState state() { return filters.get(activeKind); }

    private String tabLabel(String label, SurroundingKind kind) {
        return activeKind == kind ? "[" + label + "]" : label;
    }

    private WButton button(String label, int width) {
        return WaypointerUi.button(label,this,width,ROW_HEIGHT,org.chamomilo.wurm.ui.v1.UiDensity.HIGH);
    }

    @Override int minimumContentWidth() {
        int required = Math.max(TABLE_WIDTH, fieldFilters.calcWidth());
        required = Math.max(required, footer.calcWidth());
        if (tableHeader != null) required = Math.max(required,
                tableHeader.calcWidth() + 18 + 24 + monitoringButton.width);
        // Both text filters retain a useful editor and complete example.
        required = Math.max(required, 92 + 220 + clearSearch.width
                + Math.max(250, text.getWidth(org.waypoints.next.i18n.Messages.text("Example: catseyes, post")) + 12));
        return required + 8;
    }

    private static FlexComponent spacer(int width, int height) {
        return new FlexComponent("surroundings.gap", 0, 0, width, height) {{
            sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
        }};
    }

    private <T> WButton filterButton(String prefix, Set<T> selected,
                                     List<Choice<T>> choices, int width) {
        WButton result = button(summary(prefix, selected, choices), width);
        filterWidths.put(result,Math.max(width,WaypointerButtonGroup.width(org.waypoints.next.i18n.Messages.text(prefix+": All"),20,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false)));
        List<String> variants=new ArrayList<String>();variants.add(prefix+": All");
        for(Choice<T> choice:choices)variants.add(prefix+": "+choice.label);
        for(int count=1;count<=choices.size();count++)variants.add(prefix+": "+count);
        filterCaptions.put(result,variants);
        result.setHoverString(org.waypoints.next.i18n.Messages.text(selectionHover(prefix, selected, choices)));
        return result;
    }

    private <T> void updateFilterButton(WButton button, String prefix,
                                        Set<T> selected,
                                        List<Choice<T>> choices) {
        if (button == null) return;
        button.setLabel(org.waypoints.next.i18n.Messages.text(summary(prefix, selected, choices)), false);
        if(filterTypography!=null&&filterWidths.containsKey(button))
            ((ChamomiloUiV1Button)button).setCaptionRows(filterCaption(button.getLabel(),filterWidths.get(button),filterTypography.fontPixels));
        button.setHoverString(org.waypoints.next.i18n.Messages.text(selectionHover(prefix, selected, choices)));
    }

    private void coordinateFilters(){
        List<String> captions=new ArrayList<String>();List<Integer> widths=new ArrayList<Integer>();
        for(Map.Entry<WButton,Integer> member:filterWidths.entrySet())for(String variant:filterCaptions.get(member.getKey())){
            captions.add(filterCaption(org.waypoints.next.i18n.Messages.text(variant),member.getValue(),20));widths.add(member.getValue());
        }
        // Dynamic category text is unbounded: reserve a readable common size and
        // shorten overflowing captions, retaining full selection in the native hint.
        captions.add("ÁgjЙЁ");widths.add(1024);
        int[] geometry=new int[widths.size()];for(int i=0;i<geometry.length;i++)geometry[i]=widths.get(i);
        filterTypography=new WaypointerButtonGroup("surroundings.filters",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,ROW_HEIGHT,20,false,captions.toArray(new String[0]),geometry);
        for(Map.Entry<WButton,Integer> member:filterWidths.entrySet()){
            ChamomiloUiV1Button button=(ChamomiloUiV1Button)member.getKey();
            button.setCaptionRows(filterCaption(button.getLabel(),member.getValue(),filterTypography.fontPixels));
            filterTypography.apply(button,member.getValue());
        }
    }
    private static String filterCaption(String caption,int width,int pixels){
        if(WaypointerButtonGroup.width(caption,pixels,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false)<=width)return caption;
        java.text.BreakIterator boundaries=java.text.BreakIterator.getCharacterInstance(Locale.ROOT);boundaries.setText(caption);
        for(int end=boundaries.last();end!=java.text.BreakIterator.DONE;end=boundaries.previous()){
            String fitted=caption.substring(0,end)+"...";
            if(WaypointerButtonGroup.width(fitted,pixels,org.chamomilo.wurm.ui.v1.UiDensity.HIGH,false)<=width)return fitted;
        }
        return "...";
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

    private static List<Choice<String>> categoryChoices(SurroundingKind kind) {
        List<Choice<String>> result = new ArrayList<Choice<String>>();
        if (kind == SurroundingKind.CONTAINER) {
            add(result, "Chests", SurroundingsClassifier.CHESTS);
            add(result, "Crates", SurroundingsClassifier.CRATES);
            add(result, "Barrels", SurroundingsClassifier.BARRELS);
            add(result, "Bulk storage", SurroundingsClassifier.BULK_STORAGE);
            add(result, "Vehicles", SurroundingsClassifier.VEHICLES);
            add(result, "Ships", SurroundingsClassifier.SHIPS);
            add(result, "Portable", SurroundingsClassifier.PORTABLE_CONTAINERS);
            add(result, "Other", SurroundingsClassifier.OTHER_CONTAINERS);
        } else if (kind == SurroundingKind.ITEM) {
            add(result, "Mushrooms", SurroundingsClassifier.MUSHROOMS);
            add(result, "Corpses", SurroundingsClassifier.CORPSES);
            add(result, "Resources", SurroundingsClassifier.RESOURCES);
            add(result, "Tools", SurroundingsClassifier.TOOLS);
            add(result, "Food", SurroundingsClassifier.FOOD);
            add(result, "Decorations", SurroundingsClassifier.DECORATIONS);
            add(result, "Other", SurroundingsClassifier.OTHER_ITEMS);
        }
        return result;
    }

    private static List<Choice<CreatureModifier>> modifierChoices() {
        List<Choice<CreatureModifier>> result =
                new ArrayList<Choice<CreatureModifier>>();
        for (CreatureModifier value : CreatureModifier.values()) {
            add(result, value.getLabel(), value);
        }
        return result;
    }

    private static List<Choice<UniqueStatus>> uniqueChoices() {
        List<Choice<UniqueStatus>> result = new ArrayList<Choice<UniqueStatus>>();
        for (UniqueStatus value : UniqueStatus.values()) {
            add(result, value.getLabel(), value);
        }
        return result;
    }

    private static List<Choice<String>> materialChoices() {
        List<Choice<String>> result = new ArrayList<Choice<String>>();
        for (int i = 0; i < MATERIAL_VALUES.length; i++) {
            add(result, MATERIAL_LABELS[i], MATERIAL_VALUES[i]);
        }
        return result;
    }

    private static List<Choice<Integer>> rarityChoices() {
        List<Choice<Integer>> result = new ArrayList<Choice<Integer>>();
        add(result, "Ordinary", Integer.valueOf(0));
        add(result, "Rare", Integer.valueOf(1));
        add(result, "Supreme", Integer.valueOf(2));
        add(result, "Fantastic", Integer.valueOf(3));
        return result;
    }

    private static List<Choice<DeedStatus>> deedChoices() {
        List<Choice<DeedStatus>> result = new ArrayList<Choice<DeedStatus>>();
        for (DeedStatus value : DeedStatus.values()) {
            add(result, value.getLabel(), value);
        }
        return result;
    }

    private static List<Choice<SurroundingsQuery.LayerFilter>> layerChoices() {
        List<Choice<SurroundingsQuery.LayerFilter>> result =
                new ArrayList<Choice<SurroundingsQuery.LayerFilter>>();
        add(result, "Surface", SurroundingsQuery.LayerFilter.SURFACE);
        add(result, "Cave", SurroundingsQuery.LayerFilter.CAVE);
        return result;
    }

    private static List<Choice<SurroundingsQuery.MarkFilter>> markChoices() {
        List<Choice<SurroundingsQuery.MarkFilter>> result =
                new ArrayList<Choice<SurroundingsQuery.MarkFilter>>();
        add(result, "Marked", SurroundingsQuery.MarkFilter.MARKED);
        add(result, "Unmarked", SurroundingsQuery.MarkFilter.UNMARKED);
        return result;
    }

    private static <T> void add(List<Choice<T>> values, String label, T value) {
        values.add(new Choice<T>(label, value));
    }

    private static <T> String summary(String prefix, Set<T> selected,
                                      List<Choice<T>> choices) {
        if (selected == null || selected.isEmpty()) return prefix + ": All";
        if (selected.size() == 1) {
            T only = selected.iterator().next();
            for (Choice<T> choice : choices) if (choice.value.equals(only)) {
                return prefix + ": " + choice.label;
            }
        }
        return prefix + ": " + selected.size();
    }

    private static <T> String selectionHover(String prefix, Set<T> selected,
                                             List<Choice<T>> choices) {
        String help=filterHelp(prefix)+" ";
        if (selected == null || selected.isEmpty()) {
            return org.waypoints.next.i18n.Messages.text(help.trim())+" "+org.waypoints.next.i18n.Messages.format("{0}: all values. Click to select one or more values.",org.waypoints.next.i18n.Messages.text(prefix));
        }
        StringBuilder text = new StringBuilder(org.waypoints.next.i18n.Messages.text(prefix)).append(": ");
        boolean first = true;
        for (Choice<T> choice : choices) if (selected.contains(choice.value)) {
            if (!first) text.append(", ");
            text.append(org.waypoints.next.i18n.Messages.text(choice.label));
            first = false;
        }
        return org.waypoints.next.i18n.Messages.text(help.trim())+" "+text.toString();
    }

    private static String filterHelp(String prefix){
        if("Condition".equals(prefix))return columnHelp(SurroundingsQuery.SortColumn.CONDITION);
        if("Unique".equals(prefix))return columnHelp(SurroundingsQuery.SortColumn.UNIQUE);
        if("Deed".equals(prefix))return columnHelp(SurroundingsQuery.SortColumn.DEED);
        if("Category".equals(prefix))return columnHelp(SurroundingsQuery.SortColumn.CATEGORY);
        if("Material".equals(prefix))return columnHelp(SurroundingsQuery.SortColumn.MATERIAL);
        if("Rarity".equals(prefix))return columnHelp(SurroundingsQuery.SortColumn.RARITY);
        if("Layer".equals(prefix))return "Surface means above ground; Cave means underground.";
        return columnHelp(SurroundingsQuery.SortColumn.MARK);
    }

    private static String rarity(int value) {
        switch (value) {
            case 1: return "Rare";
            case 2: return "Supreme";
            case 3: return "Fantastic";
            default: return value <= 0 ? "-" : Integer.toString(value);
        }
    }

    private static String emptyDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private static final class Choice<T> {
        private final String label;
        private final T value;
        private Choice(String label, T value) { this.label = label; this.value = value; }
    }

    private static final class FilterState {
        private final Set<String> categories = new LinkedHashSet<String>();
        private final Set<CreatureModifier> modifiers =
                new LinkedHashSet<CreatureModifier>();
        private final Set<UniqueStatus> uniques = new LinkedHashSet<UniqueStatus>();
        private final Set<String> materials = new LinkedHashSet<String>();
        private final Set<Integer> rarities = new LinkedHashSet<Integer>();
        private final Set<DeedStatus> deeds = new LinkedHashSet<DeedStatus>();
        private final Set<SurroundingsQuery.LayerFilter> layers =
                new LinkedHashSet<SurroundingsQuery.LayerFilter>();
        private final Set<SurroundingsQuery.MarkFilter> marks =
                new LinkedHashSet<SurroundingsQuery.MarkFilter>();
        private final Set<String> excludedNames = new LinkedHashSet<String>();
        private SurroundingsQuery.SortColumn sort =
                SurroundingsQuery.SortColumn.NONE;
        private boolean ascending=true;
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
