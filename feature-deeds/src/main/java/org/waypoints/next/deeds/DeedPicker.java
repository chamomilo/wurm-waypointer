package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fuzzy deed picker across names, metadata, providers, and an optional server. */
public final class DeedPicker {
    private final List<DeedPickerEntry> entries = new ArrayList<DeedPickerEntry>();

    public DeedPicker add(ServerIdentity server, DeedProviderSnapshot snapshot) {
        if (server == null || snapshot == null) return this;
        for (DeedRecord deed : snapshot.getDeeds()) entries.add(
                new DeedPickerEntry(server, snapshot.getProviderKey(), deed,
                        snapshot.getDataTimestamp(), 0));
        return this;
    }

    public List<DeedPickerEntry> search(String query,
                                        String serverFingerprint, int limit) {
        if (limit < 1 || limit > 10_000) throw new IllegalArgumentException(
                "picker limit must be in 1..10000");
        String needle = fold(query);
        String serverFilter = fold(serverFingerprint);
        List<DeedPickerEntry> result = new ArrayList<DeedPickerEntry>();
        for (DeedPickerEntry entry : entries) {
            if (!serverFilter.isEmpty() && !serverFilter.equals(fold(
                    entry.getServer().getEndpointFingerprint()))) continue;
            int score = score(entry, needle);
            if (score < 0) continue;
            result.add(new DeedPickerEntry(entry.getServer(), entry.getProviderKey(),
                    entry.getDeed(), entry.getDataTimestamp(), score));
        }
        Collections.sort(result, new Comparator<DeedPickerEntry>() {
            @Override public int compare(DeedPickerEntry left, DeedPickerEntry right) {
                int byScore = right.getScore() - left.getScore();
                if (byScore != 0) return byScore;
                return left.getDeed().getName().compareToIgnoreCase(
                        right.getDeed().getName());
            }
        });
        if (result.size() > limit) result = new ArrayList<DeedPickerEntry>(
                result.subList(0, limit));
        return Collections.unmodifiableList(result);
    }

    private static int score(DeedPickerEntry entry, String needle) {
        if (needle.isEmpty()) return 1;
        int nameScore = fieldScore(fold(entry.getDeed().getName()), needle);
        int best = nameScore < 0 ? -1 : nameScore + 100;
        for (Map.Entry<String, String> field
                : entry.getDeed().getMetadata().entrySet()) {
            best = Math.max(best, fieldScore(fold(field.getValue()), needle));
        }
        best = Math.max(best, fieldScore(fold(entry.getProviderKey()), needle));
        return best <= 0 ? -1 : best;
    }

    private static int fieldScore(String haystack, String needle) {
        if (haystack.equals(needle)) return 1000;
        if (haystack.startsWith(needle)) return 800 - haystack.length();
        int contains = haystack.indexOf(needle);
        if (contains >= 0) return 600 - contains;
        int at = 0;
        int gaps = 0;
        for (int i = 0; i < haystack.length() && at < needle.length(); i++) {
            if (haystack.charAt(i) == needle.charAt(at)) at++;
            else if (at > 0) gaps++;
        }
        return at == needle.length() ? Math.max(1, 300 - gaps) : -1;
    }

    private static String fold(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH)
                .replaceAll("\\s+", " ");
    }
}
