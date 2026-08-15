package org.waypoints.next.surroundings;

import java.time.Instant;

/** Immutable projection of one object currently present in the client stream. */
public final class SurroundingEntry {
    private final SurroundingKey key;
    private final String name;
    private final String shortName;
    private final String modelName;
    private final String description;
    private final String category;
    private final String material;
    private final CreatureModifier creatureModifier;
    private final DeedStatus deedStatus;
    private final boolean uniqueCreature;
    private final int rarity;
    private final int layer;
    private final double worldX;
    private final double worldY;
    private final double height;
    private final Instant firstSeenAt;
    private final Instant updatedAt;

    private SurroundingEntry(Builder builder) {
        if (builder.kind == null) throw new IllegalArgumentException("kind is required");
        key = new SurroundingKey(builder.kind, builder.wurmId);
        name = cleanRequired(builder.name, "name");
        String requestedShortName = clean(builder.shortName);
        shortName = requestedShortName.isEmpty() ? name : requestedShortName;
        modelName = clean(builder.modelName);
        description = clean(builder.description);
        category = cleanRequired(builder.category, "category");
        material = clean(builder.material);
        CreatureModifier requestedModifier = builder.creatureModifier == null
                ? CreatureModifier.NONE : builder.creatureModifier;
        creatureModifier = CreatureModifier.fromWurmData(
                requestedModifier.getWurmCode(), name, description);
        deedStatus = builder.deedStatus == null
                ? DeedStatus.UNKNOWN : builder.deedStatus;
        uniqueCreature = builder.uniqueCreature;
        rarity = builder.rarity;
        layer = builder.layer;
        worldX = finiteNonNegative(builder.worldX, "world X");
        worldY = finiteNonNegative(builder.worldY, "world Y");
        height = finite(builder.height, "height");
        firstSeenAt = builder.firstSeenAt == null ? Instant.now() : builder.firstSeenAt;
        updatedAt = builder.updatedAt == null ? firstSeenAt : builder.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    public SurroundingKey getKey() { return key; }
    public SurroundingKind getKind() { return key.getKind(); }
    public long getWurmId() { return key.getWurmId(); }
    public String getName() { return name; }
    public String getShortName() { return shortName; }
    public String getModelName() { return modelName; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getMaterial() { return material; }
    public CreatureModifier getCreatureModifier() { return creatureModifier; }
    public DeedStatus getDeedStatus() { return deedStatus; }
    public boolean isUniqueCreature() { return uniqueCreature; }
    public int getRarity() { return rarity; }
    public int getLayer() { return layer; }
    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public double getHeight() { return height; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    SurroundingEntry withFirstSeenAt(Instant value) {
        return builder().kind(getKind()).wurmId(getWurmId()).name(name)
                .shortName(shortName).modelName(modelName).description(description)
                .category(category)
                .material(material).creatureModifier(creatureModifier).rarity(rarity)
                .deedStatus(deedStatus).uniqueCreature(uniqueCreature)
                .layer(layer).position(worldX, worldY, height)
                .firstSeenAt(value).updatedAt(updatedAt).build();
    }

    public SurroundingEntry withDeedStatus(DeedStatus value) {
        return builder().kind(getKind()).wurmId(getWurmId()).name(name)
                .shortName(shortName).modelName(modelName).description(description)
                .category(category)
                .material(material).creatureModifier(creatureModifier).rarity(rarity)
                .deedStatus(value).uniqueCreature(uniqueCreature)
                .layer(layer).position(worldX, worldY, height)
                .firstSeenAt(firstSeenAt).updatedAt(updatedAt).build();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String cleanRequired(String value, String label) {
        String result = clean(value);
        if (result.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return result;
    }

    private static double finite(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return value;
    }

    private static double finiteNonNegative(double value, String label) {
        double result = finite(value, label);
        if (result < 0.0d) throw new IllegalArgumentException(label + " must not be negative");
        return result;
    }

    public static final class Builder {
        private SurroundingKind kind;
        private long wurmId;
        private String name;
        private String shortName;
        private String modelName;
        private String description;
        private String category;
        private String material;
        private CreatureModifier creatureModifier;
        private DeedStatus deedStatus;
        private boolean uniqueCreature;
        private int rarity;
        private int layer;
        private double worldX;
        private double worldY;
        private double height;
        private Instant firstSeenAt;
        private Instant updatedAt;

        private Builder() { }

        public Builder kind(SurroundingKind value) { kind = value; return this; }
        public Builder wurmId(long value) { wurmId = value; return this; }
        public Builder name(String value) { name = value; return this; }
        public Builder shortName(String value) { shortName = value; return this; }
        public Builder modelName(String value) { modelName = value; return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder category(String value) { category = value; return this; }
        public Builder material(String value) { material = value; return this; }
        public Builder creatureModifier(CreatureModifier value) {
            creatureModifier = value; return this;
        }
        public Builder deedStatus(DeedStatus value) { deedStatus = value; return this; }
        public Builder uniqueCreature(boolean value) {
            uniqueCreature = value; return this;
        }
        public Builder rarity(int value) { rarity = value; return this; }
        public Builder layer(int value) { layer = value; return this; }
        public Builder position(double x, double y, double h) {
            worldX = x; worldY = y; height = h; return this;
        }
        public Builder firstSeenAt(Instant value) { firstSeenAt = value; return this; }
        public Builder updatedAt(Instant value) { updatedAt = value; return this; }
        public SurroundingEntry build() { return new SurroundingEntry(this); }
    }
}
