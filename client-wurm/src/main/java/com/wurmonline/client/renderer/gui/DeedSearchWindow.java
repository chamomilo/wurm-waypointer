package com.wurmonline.client.renderer.gui;

import org.waypoints.next.map.Deed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Native Wurm-style searchable deed catalog opened from the M-map. */
final class DeedSearchWindow extends WWindow
        implements InputFieldListener {
    private static final int ROW_HEIGHT = 23;
    private static final int TABLE_WIDTH = 558;

    private final List<Deed> deeds = new ArrayList<Deed>();
    private WurmInputField searchInput;
    private WurmLabel countLabel;
    private WurmArrayPanel<FlexComponent> table;
    private int filteredCount;
    private Deed onlyFiltered;

    DeedSearchWindow(List<Deed> source) {
        super("wurm-waypointer.deed-search", true);
        setTitle("Find deed on map");
        updateDeeds(source);
        build();
    }

    void updateDeeds(List<Deed> source) {
        deeds.clear();
        if (source != null) deeds.addAll(source);
        Collections.sort(deeds, new Comparator<Deed>() {
            @Override public int compare(Deed left, Deed right) {
                return safe(left.getName()).compareToIgnoreCase(
                        safe(right.getName()));
            }
        });
        if (table != null) refreshRows();
    }

    void focusSearch() {
        if (hud == null) return;
        try {
            hud.stopTyping();
            hud.setActiveWindow(this);
            hud.startTyping();
        } catch (Throwable ignored) { }
    }

    void prepareDetach() {
        if (hud != null) try { hud.stopTyping(); }
        catch (Throwable ignored) { }
    }

    private void build() {
        WurmBorderPanel root = new WurmBorderPanel(
                "waypointer.deed-search.root");
        WurmArrayPanel<FlexComponent> filters =
                new WurmArrayPanel<FlexComponent>(
                        "waypointer.deed-search.filters", 1);
        filters.setInitialSize(TABLE_WIDTH, ROW_HEIGHT, false);
        searchInput = new WurmInputField(
                "waypointer.deed-search.input", this, 1, 160);
        searchInput.prompt = "";
        searchInput.simpleInput = true;
        searchInput.setInitialSize(310, ROW_HEIGHT, false);
        filters.addComponent(searchInput);
        countLabel = new WurmLabel("0 deeds");
        countLabel.setInitialSize(138, ROW_HEIGHT, false);
        filters.addComponent(countLabel);
        root.setComponent(filters, WurmBorderPanel.NORTH);

        table = new WurmArrayPanel<FlexComponent>(
                "waypointer.deed-search.table", 0, true);
        root.setComponent(new WurmScrollPanel(
                "waypointer.deed-search.scroll", table, false, true),
                WurmBorderPanel.CENTER);
        setComponent(root);
        refreshRows();
    }

    private void refreshRows() {
        if (table == null) return;
        table.removeAllComponents();
        String filter = searchInput == null ? ""
                : safe(searchInput.getText()).trim().toLowerCase(Locale.ENGLISH);
        int shown = 0;
        onlyFiltered = null;
        for (Deed deed : deeds) {
            String line = format(deed);
            if (!filter.isEmpty() && !line.toLowerCase(Locale.ENGLISH)
                    .contains(filter)) continue;
            DeedRow row = new DeedRow(line, deed, this);
            row.setInitialSize(TABLE_WIDTH, ROW_HEIGHT, false);
            table.addComponent(row);
            shown++;
            onlyFiltered = deed;
        }
        filteredCount = shown;
        if (shown != 1) onlyFiltered = null;
        countLabel.setLabel(shown + " of " + deeds.size());
    }

    private void select(Deed deed) {
        if (deed == null) return;
        ServerMapWindowBridge.centerOnDeed(deed.getX(), deed.getY());
        DeedSearchWindowBridge.closed(this);
    }

    @Override public void handleInput(String input) {
        if (filteredCount == 1) select(onlyFiltered);
    }

    @Override public void handleInputChanged(WurmInputField field, String input) {
        if (field == searchInput) refreshRows();
    }

    @Override public void handleEscape(WurmInputField field) {
        DeedSearchWindowBridge.closed(this);
    }

    @Override boolean hasInputField() { return searchInput != null; }

    @Override WurmInputField getInputField() { return searchInput; }

    @Override void closePressed() { DeedSearchWindowBridge.closed(this); }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String format(Deed deed) {
        String mayor = safe(deed.getMayor()).trim();
        if (mayor.isEmpty()) mayor = "unknown";
        return deed.getName() + ", mayor - " + mayor + ", X="
                + deed.getX() + " Y=" + deed.getY();
    }

    /** Plain text row: clickable, but deliberately without a button plate. */
    private static final class DeedRow extends WurmLabel {
        private final Deed deed;
        private final DeedSearchWindow owner;
        private boolean pressed;

        private DeedRow(String text, Deed deed, DeedSearchWindow owner) {
            super(text, "Center the map on " + deed.getName() + ".", false);
            this.deed = deed;
            this.owner = owner;
        }

        @Override void leftPressed(int mouseX, int mouseY, int clickCount) {
            pressed = contains(mouseX, mouseY);
        }

        @Override void leftReleased(int mouseX, int mouseY) {
            boolean selected = pressed && contains(mouseX, mouseY);
            pressed = false;
            if (selected) owner.select(deed);
        }

        @Override void mouseExited() {
            pressed = false;
        }
    }
}
