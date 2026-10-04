package org.waypoints.next.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Orders deed-search results by match quality, then by deed name. */
public final class DeedSearchRanker {
    private static final int EXACT = 4_000_000;
    private static final int PREFIX = 3_000_000;
    private static final int WORD_PREFIX = 2_500_000;
    private static final int CONTAINS = 2_000_000;
    private static final int SUBSEQUENCE = 1_000_000;

    private DeedSearchRanker() { }

    public static List<Deed> rank(List<Deed> source, String query) {
        String needle = normalize(query);
        List<Match> matches = new ArrayList<Match>();
        if (source != null) for (Deed deed : source) {
            if (deed == null) continue;
            int score = needle.isEmpty() ? 0 : score(deed, needle);
            if (needle.isEmpty() || score >= 0) {
                matches.add(new Match(deed, score));
            }
        }
        Collections.sort(matches, new Comparator<Match>() {
            @Override public int compare(Match left, Match right) {
                int relevance = Integer.compare(right.score, left.score);
                if (relevance != 0) return relevance;
                int name = safe(left.deed.getName()).compareToIgnoreCase(
                        safe(right.deed.getName()));
                if (name != 0) return name;
                int key = safe(left.deed.getStableKey()).compareToIgnoreCase(
                        safe(right.deed.getStableKey()));
                if (key != 0) return key;
                int x = Integer.compare(left.deed.getX(), right.deed.getX());
                return x != 0 ? x
                        : Integer.compare(left.deed.getY(), right.deed.getY());
            }
        });
        List<Deed> ranked = new ArrayList<Deed>(matches.size());
        for (Match match : matches) ranked.add(match.deed);
        return ranked;
    }

    static int score(Deed deed, String normalizedQuery) {
        String needle = normalize(normalizedQuery);
        if (deed == null || needle.isEmpty()) return -1;
        int best = -1;
        best = Math.max(best, fieldScore(deed.getName(), needle, 800));
        best = Math.max(best, fieldScore(deed.getMayor(), needle, 700));
        best = Math.max(best, fieldScore(deed.getAllianceName(), needle, 600));
        best = Math.max(best, fieldScore(deed.getFounderName(), needle, 500));
        best = Math.max(best, fieldScore(deed.getMotto(), needle, 400));
        best = Math.max(best, fieldScore(deed.getType(), needle, 300));
        best = Math.max(best, fieldScore(deed.getLastActive(), needle, 200));
        best = Math.max(best, fieldScore(Integer.toString(deed.getX()),
                needle, 120));
        best = Math.max(best, fieldScore(Integer.toString(deed.getY()),
                needle, 110));
        best = Math.max(best, fieldScore("x=" + deed.getX(), needle, 100));
        best = Math.max(best, fieldScore("y=" + deed.getY(), needle, 90));

        // Preserve broad legacy matching, including multi-field queries, but
        // rank it below a match contained within one meaningful field.
        best = Math.max(best, fieldScore(combinedText(deed), needle, 0));
        return best;
    }

    private static int fieldScore(String rawValue, String needle,
                                  int fieldPriority) {
        String value = normalize(rawValue);
        if (value.isEmpty()) return -1;
        if (value.equals(needle)) return EXACT + fieldPriority;
        if (value.startsWith(needle)) {
            return PREFIX + fieldPriority - lengthPenalty(value, needle);
        }
        int word = wordPrefixIndex(value, needle);
        if (word >= 0) return WORD_PREFIX + fieldPriority - word;
        int contained = value.indexOf(needle);
        if (contained >= 0) return CONTAINS + fieldPriority - contained;
        int fuzzy = subsequenceScore(value, needle);
        return fuzzy < 0 ? -1 : SUBSEQUENCE + fieldPriority + fuzzy;
    }

    private static int wordPrefixIndex(String value, String needle) {
        int index = value.indexOf(needle);
        while (index >= 0) {
            if (index == 0 || !Character.isLetterOrDigit(
                    value.charAt(index - 1))) return index;
            index = value.indexOf(needle, index + 1);
        }
        return -1;
    }

    private static int subsequenceScore(String value, String needle) {
        int bestSpan = Integer.MAX_VALUE;
        int bestStart = Integer.MAX_VALUE;
        for (int start = value.indexOf(needle.charAt(0)); start >= 0;
             start = value.indexOf(needle.charAt(0), start + 1)) {
            int matched = 1;
            int end = start + 1;
            while (end < value.length() && matched < needle.length()) {
                if (value.charAt(end) == needle.charAt(matched)) matched++;
                end++;
            }
            if (matched != needle.length()) continue;
            int span = end - start;
            if (span < bestSpan || span == bestSpan && start < bestStart) {
                bestSpan = span;
                bestStart = start;
            }
        }
        if (bestSpan == Integer.MAX_VALUE) return -1;
        int gaps = bestSpan - needle.length();
        return Math.max(0, 100_000 - gaps * 1_000 - bestStart * 10);
    }

    private static int lengthPenalty(String value, String needle) {
        return Math.min(10_000, Math.max(0, value.length() - needle.length()));
    }

    private static String combinedText(Deed deed) {
        return safe(deed.getName()) + " " + safe(deed.getMayor()) + " "
                + safe(deed.getType()) + " " + safe(deed.getAllianceName())
                + " " + safe(deed.getFounderName()) + " "
                + safe(deed.getMotto()) + " " + safe(deed.getLastActive())
                + " x=" + deed.getX() + " y=" + deed.getY();
    }

    private static String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ENGLISH)
                .replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Match {
        private final Deed deed;
        private final int score;

        private Match(Deed deed, int score) {
            this.deed = deed;
            this.score = score;
        }
    }
}
