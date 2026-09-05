package org.waypoints.next.surroundings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** One OR branch in a Scanner profile. Conditions inside a branch are ANDed. */
public final class ScannerRule {
    private final SurroundingKind kind;
    private final boolean uniqueCreature;
    private final List<String> nameTerms;
    private final List<String> modelTerms;

    private ScannerRule(Builder builder) {
        if (builder.kind == null) throw new IllegalArgumentException("kind is required");
        kind = builder.kind;
        uniqueCreature = builder.uniqueCreature;
        nameTerms = immutableTerms(builder.nameTerms);
        modelTerms = immutableTerms(builder.modelTerms);
    }

    public static Builder builder() { return new Builder(); }
    public SurroundingKind getKind() { return kind; }
    public boolean isUniqueCreatureRequired() { return uniqueCreature; }
    public List<String> getNameTerms() { return nameTerms; }
    public List<String> getModelTerms() { return modelTerms; }

    public boolean matches(SurroundingEntry entry) {
        if (entry == null || entry.getKind() != kind) return false;
        if (uniqueCreature && !entry.isUniqueCreature()) return false;
        if (nameTerms.isEmpty() && modelTerms.isEmpty()) return true;
        String name = SurroundingsClassifier.normalize(entry.getName() + " "
                + entry.getShortName());
        for (String term : nameTerms) if (name.contains(term)) return true;
        String model = SurroundingsClassifier.normalize(entry.getModelName());
        for (String term : modelTerms) if (model.contains(term)) return true;
        return false;
    }

    private static List<String> immutableTerms(Collection<String> source) {
        List<String> result = new ArrayList<String>();
        if (source != null) for (String value : source) {
            String normalized = SurroundingsClassifier.normalize(value);
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static final class Builder {
        private SurroundingKind kind;
        private boolean uniqueCreature;
        private final List<String> nameTerms = new ArrayList<String>();
        private final List<String> modelTerms = new ArrayList<String>();

        private Builder() { }
        public Builder kind(SurroundingKind value) { kind = value; return this; }
        public Builder uniqueCreature(boolean value) {
            uniqueCreature = value; return this;
        }
        public Builder nameTerms(Collection<String> values) {
            nameTerms.clear(); if (values != null) nameTerms.addAll(values); return this;
        }
        public Builder modelTerms(Collection<String> values) {
            modelTerms.clear(); if (values != null) modelTerms.addAll(values); return this;
        }
        public ScannerRule build() { return new ScannerRule(this); }
    }
}
