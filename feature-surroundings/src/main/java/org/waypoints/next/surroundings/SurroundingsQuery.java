package org.waypoints.next.surroundings;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable inclusion filters and sort definition for the live catalog. */
public final class SurroundingsQuery {
    public enum LayerFilter { ANY, SURFACE, CAVE }
    public enum MarkFilter { ANY, MARKED, UNMARKED }
    public enum ShortNameMode { INCLUDE, EXCLUDE }
    public enum SortColumn { DISTANCE, NAME, CATEGORY, MATERIAL, RARITY }

    private final SurroundingKind kind;
    private final String text;
    private final String shortName;
    private final ShortNameMode shortNameMode;
    private final Set<String> categories;
    private final Set<String> materials;
    private final Set<CreatureModifier> modifiers;
    private final Set<Integer> rarities;
    private final Set<LayerFilter> layers;
    private final Set<MarkFilter> marks;
    private final Set<DeedStatus> deedStatuses;
    private final Set<UniqueStatus> uniqueStatuses;
    private final SortColumn sort;
    private final boolean ascending;

    private SurroundingsQuery(Builder builder) {
        kind = builder.kind == null ? SurroundingKind.ANIMAL : builder.kind;
        text = clean(builder.text);
        shortName = clean(builder.shortName);
        shortNameMode = builder.shortNameMode == null
                ? ShortNameMode.INCLUDE : builder.shortNameMode;
        categories = immutableStrings(builder.categories);
        materials = immutableStrings(builder.materials);
        modifiers = immutable(builder.modifiers);
        rarities = immutable(builder.rarities);
        layers = immutable(builder.layers);
        marks = immutable(builder.marks);
        deedStatuses = immutable(builder.deedStatuses);
        uniqueStatuses = immutable(builder.uniqueStatuses);
        sort = builder.sort == null ? SortColumn.DISTANCE : builder.sort;
        ascending = builder.ascending;
    }

    public static Builder builder() { return new Builder(); }
    public SurroundingKind getKind() { return kind; }
    public String getText() { return text; }
    public String getShortName() { return shortName; }
    public ShortNameMode getShortNameMode() { return shortNameMode; }
    public Set<String> getCategories() { return categories; }
    public Set<String> getMaterials() { return materials; }
    public Set<CreatureModifier> getModifiers() { return modifiers; }
    public Set<Integer> getRarities() { return rarities; }
    public Set<LayerFilter> getLayers() { return layers; }
    public Set<MarkFilter> getMarks() { return marks; }
    public Set<DeedStatus> getDeedStatuses() { return deedStatuses; }
    public Set<UniqueStatus> getUniqueStatuses() { return uniqueStatuses; }
    public SortColumn getSort() { return sort; }
    public boolean isAscending() { return ascending; }

    boolean matches(SurroundingEntry entry, boolean marked) {
        if (entry.getKind() != kind) return false;
        String normalizedText = SurroundingsClassifier.normalize(text);
        if (!normalizedText.isEmpty()) {
            String haystack = SurroundingsClassifier.normalize(entry.getName() + " "
                    + entry.getShortName() + " " + entry.getModelName() + " "
                    + entry.getDescription() + " "
                    + entry.getCategory() + " " + entry.getMaterial() + " "
                    + entry.getCreatureModifier().getLabel() + " "
                    + entry.getDeedStatus().getLabel());
            if (!haystack.contains(normalizedText)) return false;
        }
        String normalizedShortName = SurroundingsClassifier.normalize(shortName);
        if (!normalizedShortName.isEmpty()) {
            boolean contains = SurroundingsClassifier.normalize(
                    entry.getShortName()).contains(normalizedShortName);
            if (shortNameMode == ShortNameMode.EXCLUDE ? contains : !contains) {
                return false;
            }
        }
        if (!categories.isEmpty() && !containsIgnoreCase(
                categories, entry.getCategory())) return false;
        if (!materials.isEmpty() && !containsAnyNormalized(
                entry.getMaterial(), materials)) return false;
        if (!modifiers.isEmpty()
                && !modifiers.contains(entry.getCreatureModifier())) return false;
        if (!rarities.isEmpty()
                && !rarities.contains(Integer.valueOf(entry.getRarity()))) return false;
        if (!layers.isEmpty() && !layers.contains(LayerFilter.ANY)) {
            LayerFilter actual = entry.getLayer() < 0
                    ? LayerFilter.CAVE : LayerFilter.SURFACE;
            if (!layers.contains(actual)) return false;
        }
        if (!marks.isEmpty() && !marks.contains(MarkFilter.ANY)) {
            MarkFilter actual = marked ? MarkFilter.MARKED : MarkFilter.UNMARKED;
            if (!marks.contains(actual)) return false;
        }
        if (!deedStatuses.isEmpty()
                && !deedStatuses.contains(entry.getDeedStatus())) return false;
        if (!uniqueStatuses.isEmpty()) {
            UniqueStatus actual = entry.isUniqueCreature()
                    ? UniqueStatus.UNIQUE : UniqueStatus.NON_UNIQUE;
            if (!uniqueStatuses.contains(actual)) return false;
        }
        return true;
    }

    private static boolean containsIgnoreCase(Set<String> values, String actual) {
        for (String value : values) if (value.equalsIgnoreCase(actual)) return true;
        return false;
    }

    private static boolean containsAnyNormalized(String actual, Set<String> values) {
        String haystack = SurroundingsClassifier.normalize(actual);
        for (String value : values) {
            if (haystack.contains(SurroundingsClassifier.normalize(value))) return true;
        }
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static Set<String> immutableStrings(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        if (values != null) for (String value : values) {
            String clean = clean(value);
            if (!clean.isEmpty()) result.add(clean);
        }
        return Collections.unmodifiableSet(result);
    }

    private static <T> Set<T> immutable(Collection<T> values) {
        LinkedHashSet<T> result = new LinkedHashSet<T>();
        if (values != null) for (T value : values) if (value != null) result.add(value);
        return Collections.unmodifiableSet(result);
    }

    public static final class Builder {
        private SurroundingKind kind;
        private String text;
        private String shortName;
        private ShortNameMode shortNameMode;
        private final Set<String> categories = new LinkedHashSet<String>();
        private final Set<String> materials = new LinkedHashSet<String>();
        private final Set<CreatureModifier> modifiers =
                new LinkedHashSet<CreatureModifier>();
        private final Set<Integer> rarities = new LinkedHashSet<Integer>();
        private final Set<LayerFilter> layers = new LinkedHashSet<LayerFilter>();
        private final Set<MarkFilter> marks = new LinkedHashSet<MarkFilter>();
        private final Set<DeedStatus> deedStatuses = new LinkedHashSet<DeedStatus>();
        private final Set<UniqueStatus> uniqueStatuses =
                new LinkedHashSet<UniqueStatus>();
        private SortColumn sort;
        private boolean ascending = true;

        private Builder() { }
        public Builder kind(SurroundingKind value) { kind = value; return this; }
        public Builder text(String value) { text = value; return this; }
        public Builder shortName(String value, ShortNameMode mode) {
            shortName = value; shortNameMode = mode; return this;
        }
        public Builder category(String value) {
            categories.clear(); if (value != null) categories.add(value); return this;
        }
        public Builder categories(Collection<String> values) {
            categories.clear(); if (values != null) categories.addAll(values); return this;
        }
        public Builder materialContains(String value) {
            materials.clear(); if (value != null) materials.add(value); return this;
        }
        public Builder materials(Collection<String> values) {
            materials.clear(); if (values != null) materials.addAll(values); return this;
        }
        public Builder modifier(CreatureModifier value) {
            modifiers.clear(); if (value != null) modifiers.add(value); return this;
        }
        public Builder modifiers(Collection<CreatureModifier> values) {
            modifiers.clear(); if (values != null) modifiers.addAll(values); return this;
        }
        public Builder rarity(Integer value) {
            rarities.clear(); if (value != null) rarities.add(value); return this;
        }
        public Builder rarities(Collection<Integer> values) {
            rarities.clear(); if (values != null) rarities.addAll(values); return this;
        }
        public Builder layer(LayerFilter value) {
            layers.clear(); if (value != null) layers.add(value); return this;
        }
        public Builder layers(Collection<LayerFilter> values) {
            layers.clear(); if (values != null) layers.addAll(values); return this;
        }
        public Builder mark(MarkFilter value) {
            marks.clear(); if (value != null) marks.add(value); return this;
        }
        public Builder marks(Collection<MarkFilter> values) {
            marks.clear(); if (values != null) marks.addAll(values); return this;
        }
        public Builder deedStatuses(Collection<DeedStatus> values) {
            deedStatuses.clear(); if (values != null) deedStatuses.addAll(values);
            return this;
        }
        public Builder uniqueStatuses(Collection<UniqueStatus> values) {
            uniqueStatuses.clear(); if (values != null) uniqueStatuses.addAll(values);
            return this;
        }
        public Builder sort(SortColumn value, boolean valueAscending) {
            sort = value; ascending = valueAscending; return this;
        }
        public SurroundingsQuery build() { return new SurroundingsQuery(this); }
    }
}
