package org.waypoints.next.surroundings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Named, immutable collection of Scanner matching rules and presentation. */
public final class ScannerProfile {
    private final String id;
    private final String label;
    private final List<ScannerRule> rules;
    private final ScannerColor color;

    public ScannerProfile(String id, String label,
                          Collection<ScannerRule> rules, ScannerColor color) {
        this.id = token(id);
        this.label = clean(label, "label");
        List<ScannerRule> copy = new ArrayList<ScannerRule>();
        if (rules != null) for (ScannerRule rule : rules) {
            if (rule != null) copy.add(rule);
        }
        if (copy.isEmpty()) throw new IllegalArgumentException("rules are required");
        this.rules = Collections.unmodifiableList(copy);
        if (color == null) throw new IllegalArgumentException("color is required");
        this.color = color;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public List<ScannerRule> getRules() { return rules; }
    public ScannerColor getColor() { return color; }

    public boolean matches(SurroundingEntry entry) {
        for (ScannerRule rule : rules) if (rule.matches(entry)) return true;
        return false;
    }

    private static String token(String value) {
        String clean = clean(value, "id").toLowerCase(Locale.ENGLISH);
        if (!clean.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("id must be a simple token");
        }
        return clean;
    }

    private static String clean(String value, String label) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return result;
    }
}
