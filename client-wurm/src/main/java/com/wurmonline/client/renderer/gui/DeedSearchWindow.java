package com.wurmonline.client.renderer.gui;

import org.waypoints.next.map.Deed;
import org.waypoints.next.map.DeedSearchRanker;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.map.DeedDataStatus;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Native Wurm-style searchable deed catalog opened from the M-map. */
final class DeedSearchWindow extends WWindow
        implements InputFieldListener, ButtonListener {
    private static final int ROW_HEIGHT = 23;
    private static final int TABLE_WIDTH = 558;

    private final List<Deed> deeds = new ArrayList<Deed>();
    private WurmInputField searchInput;
    private WurmLabel countLabel;
    private WButton refreshButton;
    private WurmArrayPanel<FlexComponent> table;
    private int filteredCount;
    private Deed onlyFiltered;
    private DeedDataStatus providerStatus = DeedDataStatus.READY;
    private String providerKey = "legacy-map";
    private String providerDetail = "";
    private Instant dataTimestamp;
    private String serverLabel = "current server";
    private ServerMapSnapshot lastSnapshot;

    DeedSearchWindow(List<Deed> source) {
        super("wurm-waypointer.deed-search", true);
        setTitle("Find deed on map");
        updateDeeds(source);
        build();
    }

    DeedSearchWindow(ServerMapSnapshot snapshot) {
        this(snapshot == null ? null : snapshot.getDeeds());
        updateSnapshot(snapshot);
    }

    void updateSnapshot(ServerMapSnapshot snapshot) {
        if (snapshot == null || snapshot == lastSnapshot) return;
        lastSnapshot = snapshot;
        providerStatus = snapshot.getDeedProviderStatus();
        providerKey = safe(snapshot.getDeedProviderKey());
        providerDetail = safe(snapshot.getDeedProviderDetail());
        dataTimestamp = snapshot.getDeedDataTimestamp();
        if (snapshot.getProfile() != null) {
            serverLabel = safe(snapshot.getProfile().getDisplayName());
        } else if (WurmWaypointerRuntime.currentServerIdentity() != null) {
            String shortName = safe(WurmWaypointerRuntime.currentServerIdentity()
                    .getShortName());
            String fullName = safe(WurmWaypointerRuntime.currentServerIdentity()
                    .getFullName());
            serverLabel = !shortName.isEmpty() ? shortName
                    : !fullName.isEmpty() ? fullName : serverLabel;
        }
        setTitle("Find deed on " + serverLabel);
        if (refreshButton != null) refreshButton.setLabel("Refresh", false);
        updateDeeds(snapshot.getDeeds());
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
        searchInput.setInitialSize(265, ROW_HEIGHT, false);
        filters.addComponent(searchInput);
        countLabel = new WurmLabel("0 deeds");
        countLabel.setInitialSize(190, ROW_HEIGHT, false);
        filters.addComponent(countLabel);
        refreshButton = new WButton("Refresh", this);
        refreshButton.setInitialSize(88, ROW_HEIGHT, false);
        refreshButton.setHoverString("Request a provider refresh in the background.");
        filters.addComponent(refreshButton);
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
                : safe(searchInput.getText()).trim();
        int shown = 0;
        onlyFiltered = null;
        for (Deed deed : DeedSearchRanker.rank(deeds, filter)) {
            String line = format(deed);
            DeedRow row = new DeedRow(line, deed, this);
            row.setInitialSize(TABLE_WIDTH, ROW_HEIGHT, false);
            table.addComponent(row);
            shown++;
            onlyFiltered = deed;
        }
        filteredCount = shown;
        if (shown != 1) onlyFiltered = null;
        countLabel.setLabel(providerLabel(shown));
    }

    private void select(Deed deed) {
        if (deed == null) return;
        ServerMapWindowBridge.centerOnDeed(deed.getX(), deed.getY());
        WurmWaypointerRuntime.serverMapDeedWaypointRequested(deed);
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

    @Override public void buttonPressed(WButton button) { }

    @Override public void buttonClicked(WButton button) {
        if (button == refreshButton) {
            WurmWaypointerRuntime.serverMapDeedProviderRefreshRequested();
            refreshButton.setLabel("Queued", false);
        }
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

    private String providerLabel(int shown) {
        if (providerStatus == DeedDataStatus.NO_PROVIDER) return "No provider";
        if (providerStatus == DeedDataStatus.LOADING && deeds.isEmpty()) {
            return "Loading provider...";
        }
        if (providerStatus == DeedDataStatus.ERROR && deeds.isEmpty()) {
            return "Provider error";
        }
        return shown + "/" + deeds.size() + " · "
                + (providerKey.isEmpty() ? "provider" : providerKey)
                + " · " + age(dataTimestamp)
                + (providerStatus == DeedDataStatus.CACHED ? " cached" : "");
    }

    private static String age(Instant timestamp) {
        if (timestamp == null) return "unknown age";
        long millis = Math.max(0L, Instant.now().toEpochMilli()
                - timestamp.toEpochMilli());
        long minutes = millis / 60_000L;
        if (minutes < 1L) return "now";
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        return hours < 48L ? hours + "h" : hours / 24L + "d";
    }

    /** Plain text row: clickable, but deliberately without a button plate. */
    private static final class DeedRow extends WurmLabel {
        private final Deed deed;
        private final DeedSearchWindow owner;
        private boolean pressed;

        private DeedRow(String text, Deed deed, DeedSearchWindow owner) {
            super(text, "Create or update the tracked deed waypoint for "
                    + deed.getName() + " and center the map on it.", false);
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
