package com.wurmonline.client.renderer.gui;

import org.waypoints.next.map.Deed;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Read-only native details for a published deed selected on the M-map. */
final class DeedInformationWindow extends WWindow {
    private static final int ROW_WIDTH = 430;
    private static final int ROW_HEIGHT = 23;

    DeedInformationWindow(Deed deed) {
        super("wurm-waypointer.deed-information", true);
        if (deed == null) throw new IllegalArgumentException("deed is required");
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
}
