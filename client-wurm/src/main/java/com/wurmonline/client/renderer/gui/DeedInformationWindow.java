package com.wurmonline.client.renderer.gui;

import org.waypoints.next.map.Deed;
import org.waypoints.next.integration.WurmWaypointerRuntime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Read-only native details for a published deed selected on the M-map. */
final class DeedInformationWindow extends WWindow implements ButtonListener {
    private static final int ROW_WIDTH = 430;
    private static final int ROW_HEIGHT = 23;
    private final Deed deed;
    private WButton trackButton;
    private WButton navigationButton;

    DeedInformationWindow(Deed deed) {
        super("wurm-waypointer.deed-information", true);
        if (deed == null) throw new IllegalArgumentException("deed is required");
        this.deed = deed;
        setTitle(deed.getName());
        WurmArrayPanel<FlexComponent> content =
                new WurmArrayPanel<FlexComponent>(
                        "waypointer.deed-information.content", 0, true);
        add(content, typeLabel(deed.getType()));
        add(content, "Mayor: " + value(deed.getMayor()));
        add(content, "Coordinates: X=" + deed.getX() + ", Y=" + deed.getY());
        add(content, "Alliance: " + value(deed.getAllianceName()));
        add(content, "Guards: " + deed.getGuards());
        add(content, "Citizens: " + deed.getCitizens());
        add(content, "Founder: " + value(deed.getFounderName()));
        add(content, "Founded: " + founded(deed.getCreationDate()));
        add(content, "Active: " + active(deed.getLastActive()));
        String motto = deed.getMotto() == null ? "" : deed.getMotto().trim();
        if (!motto.isEmpty()) {
            List<String> lines = wrap("\"" + motto + "\"", 56);
            for (String line : lines) add(content, line);
        }
        trackButton = new WButton("Track deed waypoint", this);
        trackButton.setInitialSize(ROW_WIDTH, ROW_HEIGHT, false);
        trackButton.setHoverString(
                "Create or update this provider-managed DEED waypoint.");
        content.addComponent(trackButton);
        navigationButton = new WButton("Nav to deed", this);
        navigationButton.setInitialSize(ROW_WIDTH, ROW_HEIGHT, false);
        navigationButton.setHoverString(
                "Track this deed waypoint and start the navigator.");
        content.addComponent(navigationButton);
        setComponent(content);
    }

    private static void add(WurmArrayPanel<FlexComponent> content,
                            String text) {
        WurmLabel label = new WurmLabel(text, text, false);
        label.setInitialSize(ROW_WIDTH, ROW_HEIGHT, false);
        content.addComponent(label);
    }

    private static String typeLabel(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return "Settlement";
        String result = Character.toUpperCase(clean.charAt(0))
                + clean.substring(1).toLowerCase(Locale.ENGLISH);
        return result.toLowerCase(Locale.ENGLISH).contains("town")
                ? result : result + " town";
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private static String founded(long epochMillis) {
        if (epochMillis <= 0L) return "-";
        SimpleDateFormat format = new SimpleDateFormat(
                "dd MMM yyyy", Locale.ENGLISH);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMillis));
    }

    private static String active(String value) {
        String clean = value == null ? "" : value.trim();
        String prefix = "Last active:";
        if (clean.regionMatches(true, 0, prefix, 0,
                Math.min(prefix.length(), clean.length()))
                && clean.length() >= prefix.length()) {
            clean = clean.substring(prefix.length()).trim();
        }
        return clean.isEmpty() ? "-" : clean;
    }

    private static List<String> wrap(String text, int maximum) {
        List<String> result = new ArrayList<String>();
        String remaining = text == null ? "" : text.trim();
        while (remaining.length() > maximum) {
            int split = remaining.lastIndexOf(' ', maximum);
            if (split < 1) split = maximum;
            result.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isEmpty()) result.add(remaining);
        return result;
    }

    @Override void closePressed() {
        DeedInformationWindowBridge.closed(this);
    }

    @Override public void buttonPressed(WButton button) { }

    @Override public void buttonClicked(WButton button) {
        if (button == trackButton) {
            WurmWaypointerRuntime.serverMapDeedWaypointRequested(deed);
            trackButton.setLabel("Tracked", false);
        } else if (button == navigationButton) {
            WurmWaypointerRuntime.serverMapDeedNavigationRequested(deed);
            trackButton.setLabel("Tracked", false);
            navigationButton.setLabel("Navigating", false);
        }
    }
}
