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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Native catalog of creatures and ground items currently streamed by Wurm. */
final class SurroundingsWindow extends WWindow
        implements ButtonListener, InputFieldListener {
    private static final int ROW_HEIGHT = 25;
    private static final int TABLE_WIDTH = 950;
    private static final long AUTO_REFRESH_MILLIS = 1000L;

    private static final int MARK_WIDTH = 62;
    private static final int NAME_WIDTH = 190;
    private static final int CATEGORY_WIDTH = 108;
    private static final int TRAIT_WIDTH = 82;
    private static final int UNIQUE_WIDTH = 62;
    private static final int SHORT_NAME_WIDTH = TRAIT_WIDTH + UNIQUE_WIDTH;
    private static final int MATERIAL_WIDTH = 90;
    private static final int RARITY_WIDTH = 64;
    private static final int DEED_WIDTH = 76;
    private static final int DISTANCE_WIDTH = 68;
    private static final int POSITION_WIDTH = 148;

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
    private static final String[] SORT_LABELS = {
            "Distance", "Name", "Category", "Material", "Rarity"
    };
    private static final String[] SHORT_NAME_MODE_LABELS = {
            "Show matching", "Hide matching"
    };
    private static final SurroundingsQuery.SortColumn[] SORT_VALUES =
            SurroundingsQuery.SortColumn.values();

    private final SurroundingsController controller;
    private final Map<WButton, RowAction> rowActions =
            new HashMap<WButton, RowAction>();
    private final List<SurroundingKey> filteredKeys =
            new ArrayList<SurroundingKey>();
    private final Map<SurroundingKind, FilterState> filters =
            new EnumMap<SurroundingKind, FilterState>(SurroundingKind.class);
    private final Map<SurroundingKind, Integer> scrollOffsets =
            new EnumMap<SurroundingKind, Integer>(SurroundingKind.class);

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
    private WurmInputField shortNameInput;
    private WurmDropDown shortNameModeFilter;
    private WButton clearShortName;
    private WurmDropDown sortFilter;
    private WButton applyFilters;
    private WurmLabel countLabel;
    private WurmArrayPanel<FlexComponent> table;
    private WurmScrollPanel scrollPanel;
    private WButton waypointFiltered;
    private WButton clearFiltered;
    private WButton clearAll;
    private WButton refreshButton;
    private WButton managerButton;
    private long displayedRevision = Long.MIN_VALUE;
    private long nextAutoRefreshAt;

    SurroundingsWindow(SurroundingsController controller) {
        super("wurm-waypointer.surroundings", true);
        this.controller = controller;
        for (SurroundingKind kind : SurroundingKind.values()) {
            filters.put(kind, new FilterState());
            scrollOffsets.put(kind, Integer.valueOf(0));
        }
        setTitle("Wurm Waypointer - Surroundings");
        rebuildView("");
    }

    void refreshFromController() { refreshRows(); }

    private void rebuildView(String search) {
        shortNameInput = null;
        shortNameModeFilter = null;
        clearShortName = null;
        WurmBorderPanel root = new WurmBorderPanel("waypointer.surroundings.root");
        WurmArrayPanel<FlexComponent> filterPanel = vertical(
                "waypointer.surroundings.filters");
        filterPanel.addComponent(tabAndSearchRow(search));
        filterPanel.addComponent(fieldFilterRow());
        if (activeKind != SurroundingKind.ANIMAL) {
            filterPanel.addComponent(shortNameFilterRow());
        }
        root.setComponent(filterPanel, WurmBorderPanel.NORTH);

        table = vertical("waypointer.surroundings.table");
        scrollPanel = new WurmScrollPanel("waypointer.surroundings.scroll",
                table, false, true);
        root.setComponent(scrollPanel, WurmBorderPanel.CENTER);
        root.setComponent(actionRow(), WurmBorderPanel.SOUTH);
        setComponent(root);
        filteredKeys.clear();
        refreshRows(new ScrollAnchor(scrollOffsets.get(activeKind).intValue(),
                null, 0));
    }

    private FlexComponent tabAndSearchRow(String search) {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.surroundings.tabs");
        animalsTab = button(tabLabel("Animals", SurroundingKind.ANIMAL), 92);
        containersTab = button(tabLabel("Containers", SurroundingKind.CONTAINER), 104);
        itemsTab = button(tabLabel("Items", SurroundingKind.ITEM), 82);
        row.addComponent(animalsTab);
        row.addComponent(containersTab);
        row.addComponent(itemsTab);
        row.addComponent(cell(new WurmLabel("Search fields"), 92));
        searchInput = new WurmInputField("waypointer.surroundings.search", this);
        searchInput.setInitialSize(310, ROW_HEIGHT, false);
        searchInput.prompt = "Search all fields";
        searchInput.setTextMoveToEnd(search == null ? "" : search);
        row.addComponent(searchInput);
        clearSearch = button("Clear", 62);
        row.addComponent(clearSearch);
        countLabel = new WurmLabel("0 objects");
        row.addComponent(cell(countLabel, 208));
        return row;
    }

    private FlexComponent fieldFilterRow() {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.surroundings.fields");
        categoryFilter = modifierFilter = uniqueFilter = materialFilter = null;
        rarityFilter = deedFilter = layerFilter = markedFilter = null;
        FilterState state = state();
        if (activeKind == SurroundingKind.ANIMAL) {
            modifierFilter = filterButton("Trait", state.modifiers,
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
        sortFilter = new WurmDropDown("waypointer.surroundings.sort",
                state.sort.ordinal(), SORT_LABELS);
        row.addComponent(cell(sortFilter, 105));
        applyFilters = button("Apply", 62);
        row.addComponent(applyFilters);
        return row;
    }

    private FlexComponent shortNameFilterRow() {
        FilterState state = state();
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings.short-name");
        row.addComponent(cell(new WurmLabel("Short name"), 90));
        shortNameInput = new WurmInputField(
                "waypointer.surroundings.short-name.input", this);
        shortNameInput.setInitialSize(360, ROW_HEIGHT, false);
        shortNameInput.prompt = "catseye";
        shortNameInput.setTextMoveToEnd(state.shortName);
        row.addComponent(shortNameInput);
        shortNameModeFilter = new WurmDropDown(
                "waypointer.surroundings.short-name.mode",
                state.shortNameMode == SurroundingsQuery.ShortNameMode.EXCLUDE
                        ? 1 : 0, SHORT_NAME_MODE_LABELS);
        row.addComponent(cell(shortNameModeFilter, 160));
        clearShortName = button("Clear", 62);
        row.addComponent(clearShortName);
        WurmLabel example = new WurmLabel(
                "Example: catseye + Hide matching");
        row.addComponent(cell(example, 278));
        return row;
    }

    private FlexComponent actionRow() {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.surroundings.actions");
        waypointFiltered = button("Mark filtered (15m)", 142);
        clearFiltered = button("Clear filtered", 112);
        clearAll = button("Clear all marks", 122);
        refreshButton = button("Refresh", 82);
        managerButton = button("All waypoints", 112);
        waypointFiltered.setHoverString(
                "Create a standard 15-minute waypoint for every filtered row.");
        clearFiltered.setHoverString(
                "Delete Surroundings waypoints for every filtered row.");
        clearAll.setHoverString("Delete every Surroundings temporary waypoint.");
        row.addComponent(waypointFiltered);
        row.addComponent(clearFiltered);
        row.addComponent(clearAll);
        row.addComponent(refreshButton);
        row.addComponent(managerButton);
        return row;
    }

    private void refreshRows() {
        refreshRows(captureScrollAnchor());
    }

    private void refreshRows(ScrollAnchor anchor) {
        try {
            SurroundingsSnapshot snapshot = controller.snapshot(query());
            displayedRevision = snapshot.getRevision();
            List<FlexComponent> components = new ArrayList<FlexComponent>(
                    snapshot.getRows().size() + 1);
            components.add(header());
            rowActions.clear();
            filteredKeys.clear();
            for (SurroundingsRow row : snapshot.getRows()) {
                filteredKeys.add(row.getEntry().getKey());
                components.add(dataRow(row));
            }
            table.removeAllComponents();
            table.addComponents(components.toArray(
                    new FlexComponent[components.size()]));
            restoreScroll(anchor);
            countLabel.setLabel(snapshot.getFilteredCount() + " of "
                    + snapshot.getTotalCount() + " "
                    + activeKind.name().toLowerCase(Locale.ENGLISH)
                    + "; " + snapshot.getMarkedCount() + " marked");
        } catch (Throwable failure) {
            controller.reportFailure("refresh catalog", failure);
        }
    }

    private SurroundingsQuery query() {
        captureSort();
        captureShortNameFilter();
        FilterState state = state();
        return SurroundingsQuery.builder().kind(activeKind)
                .text(searchInput == null ? "" : searchInput.getText())
                .shortName(state.shortName, state.shortNameMode)
                .categories(state.categories).modifiers(state.modifiers)
                .uniqueStatuses(state.uniques).materials(state.materials)
                .rarities(state.rarities).deedStatuses(state.deeds)
                .layers(state.layers).marks(state.marks)
                .sort(state.sort, true).build();
    }

    private FlexComponent header() {
        WurmArrayPanel<FlexComponent> row = horizontal("waypointer.surroundings.header");
        row.addComponent(cell(new WurmLabel("Mark"), MARK_WIDTH));
        row.addComponent(cell(new WurmLabel("Name"), NAME_WIDTH));
        row.addComponent(cell(new WurmLabel("Category"), CATEGORY_WIDTH));
        if (activeKind == SurroundingKind.ANIMAL) {
            row.addComponent(cell(new WurmLabel("Trait"), TRAIT_WIDTH));
            row.addComponent(cell(new WurmLabel("Unique"), UNIQUE_WIDTH));
        } else {
            row.addComponent(cell(new WurmLabel("Short name"), SHORT_NAME_WIDTH));
        }
        row.addComponent(cell(new WurmLabel("Material"), MATERIAL_WIDTH));
        row.addComponent(cell(new WurmLabel("Rarity"), RARITY_WIDTH));
        row.addComponent(cell(new WurmLabel("Deed"), DEED_WIDTH));
        row.addComponent(cell(new WurmLabel("Distance"), DISTANCE_WIDTH));
        row.addComponent(cell(new WurmLabel("Position / ID"), POSITION_WIDTH));
        return row;
    }

    private FlexComponent dataRow(SurroundingsRow data) {
        SurroundingEntry entry = data.getEntry();
        WurmArrayPanel<FlexComponent> row = horizontal(
                "waypointer.surroundings.row." + entry.getKey());
        WButton mark = button(data.isWaypointEnabled() ? "Clear" : "Mark", MARK_WIDTH);
        mark.setHoverString(data.isWaypointEnabled()
                ? "Delete this Surroundings waypoint."
                : "Create a standard 15-minute waypoint at this position.");
        rowActions.put(mark, new RowAction(entry.getKey(), data.isWaypointEnabled()));
        row.addComponent(mark);
        row.addComponent(cell(new WurmLabel(entry.getName()), NAME_WIDTH));
        row.addComponent(cell(new WurmLabel(entry.getCategory()), CATEGORY_WIDTH));
        if (entry.getKind() == SurroundingKind.ANIMAL) {
            row.addComponent(cell(new WurmLabel(
                    entry.getCreatureModifier().getLabel()), TRAIT_WIDTH));
            row.addComponent(cell(new WurmLabel(
                    entry.isUniqueCreature() ? "Yes" : "No"), UNIQUE_WIDTH));
        } else {
            row.addComponent(cell(new WurmLabel(entry.getShortName()),
                    SHORT_NAME_WIDTH));
        }
        row.addComponent(cell(new WurmLabel(emptyDash(entry.getMaterial())), MATERIAL_WIDTH));
        row.addComponent(cell(new WurmLabel(rarity(entry.getRarity())), RARITY_WIDTH));
        row.addComponent(cell(new WurmLabel(entry.getDeedStatus().getLabel()), DEED_WIDTH));
        row.addComponent(cell(new WurmLabel(data.getDistanceMetres() + "m"), DISTANCE_WIDTH));
        String position = ((int) Math.floor(entry.getWorldX() / 4.0d)) + ","
                + ((int) Math.floor(entry.getWorldY() / 4.0d))
                + (entry.getLayer() < 0 ? " cave" : " surface")
                + " #" + entry.getWurmId();
        row.addComponent(cell(new WurmLabel(position), POSITION_WIDTH));
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
            } else if (button == clearShortName) {
                shortNameInput.setTextMoveToEnd("");
                captureShortNameFilter();
                refreshRows();
            } else if (button == categoryFilter) openFilter(button, "Categories",
                    categoryChoices(activeKind), state().categories);
            else if (button == modifierFilter) openFilter(button, "Traits",
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
            else if (button == applyFilters || button == refreshButton) refreshRows();
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

    private <T> void openFilter(WButton anchor, String title,
                                List<Choice<T>> choices, Set<T> selected) {
        if (hud == null || anchor == null) return;
        hud.clearAllPopups();
        int popupX = Math.max(0, Math.min(anchor.x, SCREEN_WIDTH - 220));
        int estimatedHeight = 85 + choices.size() * ROW_HEIGHT;
        int popupY = Math.max(0, Math.min(anchor.y + anchor.height,
                SCREEN_HEIGHT - estimatedHeight));
        WurmPopup popup = new WurmPopup("waypointer.surroundings.filter",
                title, popupX, popupY);
        popup.addButton(new FilterClearButton<T>(popup, selected));
        popup.addSeparator();
        for (Choice<T> choice : choices) {
            popup.addButton(new FilterChoiceButton<T>(popup, choice, selected));
        }
        popup.addSeparator();
        popup.addButton(new FilterDoneButton(popup));
        popup.recalculateWidth();
        hud.showPopupComponent(popup);
    }

    private void selectKind(SurroundingKind kind) {
        if (kind == activeKind) return;
        captureSort();
        captureShortNameFilter();
        String search = searchInput == null ? "" : searchInput.getText();
        rememberScrollOffset();
        activeKind = kind;
        rebuildView(search);
    }

    private ScrollAnchor captureScrollAnchor() {
        int offset = scrollPanel == null
                ? scrollOffsets.get(activeKind).intValue()
                : Math.max(0, scrollPanel.yo);
        int slot = offset / ROW_HEIGHT;
        SurroundingKey key = slot > 0 && slot - 1 < filteredKeys.size()
                ? filteredKeys.get(slot - 1) : null;
        return new ScrollAnchor(offset, key, offset % ROW_HEIGHT);
    }

    private void restoreScroll(ScrollAnchor anchor) {
        if (scrollPanel == null) return;
        int offset = anchor == null ? 0 : anchor.pixelOffset;
        if (anchor != null && anchor.key != null) {
            int index = filteredKeys.indexOf(anchor.key);
            if (index >= 0) {
                offset = (index + 1) * ROW_HEIGHT + anchor.rowOffset;
            }
        }
        scrollPanel.scrollDownTo(offset);
        scrollOffsets.put(activeKind, Integer.valueOf(scrollPanel.yo));
    }

    private void rememberScrollOffset() {
        if (scrollPanel != null) {
            scrollOffsets.put(activeKind,
                    Integer.valueOf(Math.max(0, scrollPanel.yo)));
        }
    }

    private void captureSort() {
        if (sortFilter == null) return;
        int index = sortFilter.getValue();
        if (index >= 0 && index < SORT_VALUES.length) state().sort = SORT_VALUES[index];
    }

    private void captureShortNameFilter() {
        if (shortNameInput == null || shortNameModeFilter == null) return;
        FilterState state = state();
        state.shortName = shortNameInput.getText() == null
                ? "" : shortNameInput.getText().trim();
        state.shortNameMode = shortNameModeFilter.getValue() == 1
                ? SurroundingsQuery.ShortNameMode.EXCLUDE
                : SurroundingsQuery.ShortNameMode.INCLUDE;
    }

    private void updateFilterLabels() {
        FilterState state = state();
        updateFilterButton(categoryFilter, "Category", state.categories,
                categoryChoices(activeKind));
        updateFilterButton(modifierFilter, "Trait", state.modifiers, modifierChoices());
        updateFilterButton(uniqueFilter, "Unique", state.uniques, uniqueChoices());
        updateFilterButton(materialFilter, "Material", state.materials, materialChoices());
        updateFilterButton(rarityFilter, "Rarity", state.rarities, rarityChoices());
        updateFilterButton(deedFilter, "Deed", state.deeds, deedChoices());
        updateFilterButton(layerFilter, "Layer", state.layers, layerChoices());
        updateFilterButton(markedFilter, "Mark", state.marks, markChoices());
    }

    @Override public void handleInput(String input) { refreshRows(); }

    @Override public void handleInputChanged(WurmInputField field, String input) {
        if (field == searchInput || field == shortNameInput) refreshRows();
    }

    @Override public void handleEscape(WurmInputField field) {
        SurroundingsWindowBridge.closed(this);
    }

    @Override public void gameTick() {
        super.gameTick();
        long now = System.currentTimeMillis();
        if (now < nextAutoRefreshAt) return;
        nextAutoRefreshAt = now + AUTO_REFRESH_MILLIS;
        if (controller.revision() != displayedRevision) refreshRows();
    }

    @Override void closePressed() { SurroundingsWindowBridge.closed(this); }

    private FilterState state() { return filters.get(activeKind); }

    private String tabLabel(String label, SurroundingKind kind) {
        return activeKind == kind ? "[" + label + "]" : label;
    }

    private WButton button(String label, int width) {
        WButton result = new WButton(label, this);
        result.setInitialSize(width, ROW_HEIGHT, false);
        return result;
    }

    private <T> WButton filterButton(String prefix, Set<T> selected,
                                     List<Choice<T>> choices, int width) {
        WButton result = button(summary(prefix, selected, choices), width);
        result.setHoverString(selectionHover(prefix, selected, choices));
        return result;
    }

    private <T> void updateFilterButton(WButton button, String prefix,
                                        Set<T> selected,
                                        List<Choice<T>> choices) {
        if (button == null) return;
        button.setLabel(summary(prefix, selected, choices), false);
        button.setHoverString(selectionHover(prefix, selected, choices));
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
        if (selected == null || selected.isEmpty()) {
            return prefix + ": all values. Click to select one or more values.";
        }
        StringBuilder text = new StringBuilder(prefix).append(": ");
        boolean first = true;
        for (Choice<T> choice : choices) if (selected.contains(choice.value)) {
            if (!first) text.append(", ");
            text.append(choice.label);
            first = false;
        }
        return text.toString();
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

    private final class FilterChoiceButton<T>
            extends WurmPopup.WPopupAbstractButton {
        private final Choice<T> choice;
        private final Set<T> selected;

        private FilterChoiceButton(WurmPopup owner, Choice<T> choice,
                                   Set<T> selected) {
            owner.super(optionLabel(choice, selected), null);
            this.choice = choice;
            this.selected = selected;
        }

        @Override protected void leftPressed(int xMouse, int yMouse, int clickCount) {
            if (!selected.add(choice.value)) selected.remove(choice.value);
            setLabel(optionLabel(choice, selected));
            updateFilterLabels();
            refreshRows();
        }
    }

    private final class FilterClearButton<T>
            extends WurmPopup.WPopupAbstractButton {
        private final Set<T> selected;

        private FilterClearButton(WurmPopup owner, Set<T> selected) {
            owner.super("All (clear selection)", null);
            this.selected = selected;
        }

        @Override protected void leftPressed(int xMouse, int yMouse, int clickCount) {
            selected.clear();
            updateFilterLabels();
            refreshRows();
        }
    }

    private static final class FilterDoneButton extends WurmPopup.WPopupLiveButton {
        private FilterDoneButton(WurmPopup owner) { owner.super("Done"); }
        @Override protected void handleLeftClick() { }
    }

    private static <T> String optionLabel(Choice<T> choice, Set<T> selected) {
        return (selected.contains(choice.value) ? "[x] " : "[ ] ") + choice.label;
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
        private String shortName = "";
        private SurroundingsQuery.ShortNameMode shortNameMode =
                SurroundingsQuery.ShortNameMode.EXCLUDE;
        private SurroundingsQuery.SortColumn sort =
                SurroundingsQuery.SortColumn.DISTANCE;
    }

    private static final class RowAction {
        private final SurroundingKey key;
        private final boolean enabled;
        private RowAction(SurroundingKey key, boolean enabled) {
            this.key = key;
            this.enabled = enabled;
        }
    }

    private static final class ScrollAnchor {
        private final int pixelOffset;
        private final SurroundingKey key;
        private final int rowOffset;

        private ScrollAnchor(int pixelOffset, SurroundingKey key,
                             int rowOffset) {
            this.pixelOffset = Math.max(0, pixelOffset);
            this.key = key;
            this.rowOffset = Math.max(0, rowOffset);
        }
    }
}
