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
final class DeedSearchWindow extends WaypointerUiWindow
        implements InputFieldListener, ButtonListener {
    private static final int ROW_HEIGHT = 32;
    private static final int TABLE_WIDTH = 558;

    private final List<Deed> deeds = new ArrayList<Deed>();
    private WurmInputField searchInput,minusInput;
    private WurmLabel countLabel;
    private WButton refreshButton;
    private WButton clearPlus,clearMinus;
    private WurmArrayPanel<FlexComponent> table;
    private int filteredCount;
    private Deed onlyFiltered;
    private DeedDataStatus providerStatus = DeedDataStatus.READY;
    private String providerKey = "legacy-map";
    private String providerDetail = "";
    private Instant dataTimestamp;
    private String serverLabel = "current server";
    private ServerMapSnapshot lastSnapshot;
    private int minimumWidth = 800;

    DeedSearchWindow(List<Deed> source) {
        super("wurm-waypointer.deed-search", true);
        setTitle(org.waypoints.next.i18n.Messages.text("Find deed on map"));
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
        setTitle(org.waypoints.next.i18n.Messages.text("Find deed on " + serverLabel));
        if (refreshButton != null) refreshButton.setLabel(org.waypoints.next.i18n.Messages.text("Refresh"), false);
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
                        "waypointer.deed-search.filters", 0,true);
        searchInput = WaypointerUi.input(
                "waypointer.deed-search.input", this, 1, 160);
        searchInput.prompt = org.waypoints.next.i18n.Messages.text("");
        searchInput.simpleInput = true;
        searchInput.setInitialSize(265, ROW_HEIGHT, false);
        minusInput=WaypointerUi.input("waypointer.deed-search.minus",this,1,160);
        clearPlus=WaypointerUi.button(org.waypoints.next.i18n.Messages.text("Clear"),this,70);
        clearMinus=WaypointerUi.button(org.waypoints.next.i18n.Messages.text("Clear"),this,70);
        filters.addComponent(new WaypointerFilterRow("+ filter",searchInput,clearPlus,"Example: horse, wolf"));
        filters.addComponent(new WaypointerFilterRow("- filter",minusInput,clearMinus,"Example: catseyes, post"));
        countLabel = new WaypointerLabel("0 deeds");
        countLabel.setInitialSize(140, ROW_HEIGHT, false);
        filters.addComponent(countLabel);
        refreshButton = WaypointerUi.button(org.waypoints.next.i18n.Messages.text("Refresh"), this, 90);
        refreshButton.setInitialSize(88, ROW_HEIGHT, false);
        refreshButton.setHoverString(org.waypoints.next.i18n.Messages.text("Request a provider refresh in the background."));
        filters.addComponent(refreshButton);
        root.setComponent(filters, WurmBorderPanel.NORTH);
        minimumWidth = Math.max(800,filters.calcWidth()+48);

        table = new WurmArrayPanel<FlexComponent>(
                "waypointer.deed-search.table", 0, true);
        root.setComponent(new ChamomiloUiV1ScrollPanel(
                "waypointer.deed-search.scroll", table, false, true),
                WurmBorderPanel.CENTER);
        setComponent(root);
        refreshRows();
    }

    @Override void setSize(int width,int height) { super.setSize(Math.max(minimumWidth,width),minimized?height:Math.max(340,height)); }

    private void refreshRows() {
        if (table == null) return;
        table.removeAllComponents();
        String filter = searchInput == null ? ""
                : safe(searchInput.getText()).trim();
        int shown = 0;
        onlyFiltered = null;
        for (Deed deed : DeedSearchRanker.rank(deeds, "")) {
            String line = format(deed);
            if(!new org.waypoints.next.service.TextFilter(filter,minusInput.getText()).matches(line))continue;
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
        if (field == searchInput || field == minusInput) refreshRows();
    }

    @Override public void handleEscape(WurmInputField field) {
        DeedSearchWindowBridge.closed(this);
    }

    @Override public void buttonPressed(WButton button) { }

    @Override public void buttonClicked(WButton button) {
        if(button==clearPlus){searchInput.setTextMoveToEnd("");refreshRows();return;}
        if(button==clearMinus){minusInput.setTextMoveToEnd("");refreshRows();return;}
        if (button == refreshButton) {
            WurmWaypointerRuntime.serverMapDeedProviderRefreshRequested();
            refreshButton.setLabel(org.waypoints.next.i18n.Messages.text("Queued"), false);
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
        return org.waypoints.next.i18n.Messages.format("{0}, mayor - {1}, X={2} Y={3}",deed.getName(),mayor,deed.getX(),deed.getY());
    }

    private String providerLabel(int shown) {
        if (providerStatus == DeedDataStatus.NO_PROVIDER) return "No provider";
        if (providerStatus == DeedDataStatus.LOADING && deeds.isEmpty()) {
            return "Loading provider...";
        }
        if (providerStatus == DeedDataStatus.ERROR && deeds.isEmpty()) {
            return "Provider error";
        }
        return shown + "/" + deeds.size() + " В· "
                + (providerKey.isEmpty() ? "provider" : providerKey)
                + " В· " + age(dataTimestamp)
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
