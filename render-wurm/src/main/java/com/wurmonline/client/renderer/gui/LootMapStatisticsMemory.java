package com.wurmonline.client.renderer.gui;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Bounded per-item memory for statistics of unfinished Loot Map hunts. */
final class LootMapStatisticsMemory {
    private final int capacity;
    private final LinkedHashMap<Long, Snapshot> snapshots =
            new LinkedHashMap<Long, Snapshot>(16, 0.75f, true);

    LootMapStatisticsMemory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException(
                "Loot Map statistics capacity must be positive");
        this.capacity = capacity;
    }

    synchronized String resolve(Long mapItemId, Float quality, Float damage,
                                int readingCount) {
        if (mapItemId == null) return unknown(readingCount);
        Snapshot remembered = snapshots.get(mapItemId);
        int reads = readingCount >= 0 ? readingCount
                : remembered == null ? -1 : remembered.readingCount;
        if (finite(quality) && finite(damage)) {
            Snapshot live = new Snapshot(quality.floatValue(),
                    damage.floatValue(), reads);
            snapshots.put(mapItemId, live);
            trim();
            return live.summary();
        }
        if (remembered == null) return unknown(readingCount);
        Snapshot updated = remembered.withReadingCount(reads);
        snapshots.put(mapItemId, updated);
        return updated.summary();
    }

    private void trim() {
        while (snapshots.size() > capacity) {
            Iterator<Map.Entry<Long, Snapshot>> values =
                    snapshots.entrySet().iterator();
            if (!values.hasNext()) return;
            values.next();
            values.remove();
        }
    }

    private static String unknown(int readingCount) {
        return "Loot Map: QL ? | damage ? | readings "
                + (readingCount < 0 ? "?" : Integer.toString(readingCount));
    }

    private static boolean finite(Float value) {
        return value != null && !Float.isNaN(value.floatValue())
                && !Float.isInfinite(value.floatValue());
    }

    private static final class Snapshot {
        private final float quality;
        private final float damage;
        private final int readingCount;

        private Snapshot(float quality, float damage, int readingCount) {
            this.quality = quality;
            this.damage = damage;
            this.readingCount = readingCount;
        }

        private Snapshot withReadingCount(int value) {
            return value == readingCount ? this
                    : new Snapshot(quality, damage, value);
        }

        private String summary() {
            return String.format(Locale.ENGLISH,
                    "Loot Map: QL %.2f | damage %.2f | readings %s",
                    quality, damage, readingCount < 0 ? "?"
                            : Integer.toString(readingCount));
        }
    }
}
