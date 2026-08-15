package org.waypoints.next.lootmap;

import java.util.Locale;

/** Strict English TreasureHunting parser. Non-matching Event text is ignored. */
public final class LootMapMessageParser {
    public LootMapMessage parse(String tab, String text) {
        if (tab == null || !":Event".equalsIgnoreCase(tab.trim()) || text == null) {
            return null;
        }
        String value = normalize(text);
        if (isChestFound(value)) {
            return LootMapMessage.chestDugUp();
        }
        if (!value.contains("marked spot")) return null;
        LootMapDistanceBand band = band(value);
        if (band == null) return null;
        if (band == LootMapDistanceBand.EXACT) {
            return LootMapMessage.reading(band, LootMapRelativeDirection.AHEAD);
        }
        LootMapRelativeDirection direction = direction(value);
        return direction == null ? null : LootMapMessage.reading(band, direction);
    }

    private static LootMapDistanceBand band(String text) {
        // Longer overlapping phrases and optional mapTiles diagnostics first.
        if (text.contains("practically standing")) return LootMapDistanceBand.EXACT;
        if (text.contains("2000+ tiles") || text.contains("very far away")) {
            return LootMapDistanceBand.TWO_THOUSAND_PLUS;
        }
        if (text.contains("1000-2000 tiles") || text.contains("1000-1999 tiles")
                || containsPhrase(text, "far away")) {
            return LootMapDistanceBand.ONE_THOUSAND_TO_NINETEEN_NINETY_NINE;
        }
        if (text.contains("500-999 tiles") || text.contains("pretty far away")) {
            return LootMapDistanceBand.FIVE_HUNDRED_TO_NINE_NINETY_NINE;
        }
        if (text.contains("200-499 tiles") || text.contains("rather a long distance")) {
            return LootMapDistanceBand.TWO_HUNDRED_TO_FOUR_NINETY_NINE;
        }
        if (text.contains("50-199 tiles") || text.contains("quite some distance")) {
            return LootMapDistanceBand.FIFTY_TO_ONE_NINETY_NINE;
        }
        if (text.contains("20-49 tiles") || containsPhrase(text, "some distance")) {
            return LootMapDistanceBand.TWENTY_TO_FORTY_NINE;
        }
        if (text.contains("10-19 tiles") || text.contains("fairly close")) {
            return LootMapDistanceBand.TEN_TO_NINETEEN;
        }
        if (text.contains("6-9 tiles") || text.contains("pretty close")) {
            return LootMapDistanceBand.SIX_TO_NINE;
        }
        if (text.contains("4-5 tiles") || text.contains("very close")) {
            return LootMapDistanceBand.FOUR_TO_FIVE;
        }
        if (text.contains("1-3 tiles") || text.contains("stone's throw")
                || text.contains("stones throw")) {
            return LootMapDistanceBand.ONE_TO_THREE;
        }
        return null;
    }

    private static LootMapRelativeDirection direction(String text) {
        if (text.contains("behind you to the right")) return LootMapRelativeDirection.BEHIND_RIGHT;
        if (text.contains("behind you to the left")) return LootMapRelativeDirection.BEHIND_LEFT;
        if (text.contains("ahead of you to the right") || text.contains("in front of you to the right")) {
            return LootMapRelativeDirection.AHEAD_RIGHT;
        }
        if (text.contains("ahead of you to the left") || text.contains("in front of you to the left")) {
            return LootMapRelativeDirection.AHEAD_LEFT;
        }
        if (text.contains("right of you") || text.contains("to your right")) {
            return LootMapRelativeDirection.RIGHT;
        }
        if (text.contains("left of you") || text.contains("to your left")) {
            return LootMapRelativeDirection.LEFT;
        }
        if (text.contains("behind you")) return LootMapRelativeDirection.BEHIND;
        if (text.contains("in front of you") || text.contains("ahead of you")) {
            return LootMapRelativeDirection.AHEAD;
        }
        return null;
    }

    private static boolean isChestFound(String text) {
        return text.contains("you find a loot chest")
                || text.contains("you find a treasure chest")
                || text.contains("you dig up a loot chest")
                || text.contains("you dig up a treasure chest");
    }

    private static boolean containsPhrase(String text, String phrase) {
        int at = text.indexOf(phrase);
        if (at < 0) return false;
        // Prevent "some distance" matching "quite some distance" and
        // "far away" matching "pretty/very far away".
        String prefix = text.substring(Math.max(0, at - 8), at).trim();
        return !(prefix.endsWith("quite") || prefix.endsWith("pretty")
                || prefix.endsWith("very"));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ENGLISH).replace('\u2019', '\'')
                .replaceAll("\\s+", " ").trim();
    }
}
