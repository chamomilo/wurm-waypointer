package org.waypoints.next.surroundings;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Built-in Scanner profiles kept deliberately small and predictable. */
public final class ScannerProfiles {
    private static final Map<String, ScannerProfile> PROFILES = profiles();

    private ScannerProfiles() { }

    public static ScannerProfile find(String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ENGLISH);
        return PROFILES.get(key);
    }

    public static List<ScannerProfile> all() {
        return Collections.unmodifiableList(
                Arrays.asList(PROFILES.values().toArray(new ScannerProfile[0])));
    }

    public static String ids() { return "uniques, treasure, animals"; }

    private static Map<String, ScannerProfile> profiles() {
        LinkedHashMap<String, ScannerProfile> result =
                new LinkedHashMap<String, ScannerProfile>();
        add(result, new ScannerProfile("uniques", "Unique creatures",
                Collections.singletonList(ScannerRule.builder()
                        .kind(SurroundingKind.ANIMAL)
                        .uniqueCreature(true).build()),
                new ScannerColor(1.0f, 0.82f, 0.08f, 0.58f)));
        List<String> treasureNames = Arrays.asList("treasure chest");
        List<String> treasureModels = Arrays.asList("treasurechest");
        add(result, new ScannerProfile("treasure", "Treasure chests",
                Arrays.asList(
                        ScannerRule.builder().kind(SurroundingKind.CONTAINER)
                                .nameTerms(treasureNames)
                                .modelTerms(treasureModels).build(),
                        ScannerRule.builder().kind(SurroundingKind.ITEM)
                                .nameTerms(treasureNames)
                                .modelTerms(treasureModels).build()),
                new ScannerColor(1.0f, 0.22f, 0.72f, 0.58f)));
        add(result, new ScannerProfile("animals", "Animals",
                Collections.singletonList(ScannerRule.builder()
                        .kind(SurroundingKind.ANIMAL).build()),
                new ScannerColor(0.18f, 1.0f, 0.38f, 0.48f)));
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, ScannerProfile> result,
                            ScannerProfile profile) {
        result.put(profile.getId(), profile);
    }
}
