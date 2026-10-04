package org.waypoints.next.deeds;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Auditable endpoint/world table; shared ports require an exact world name. */
public final class SklotopolisDeedMappings {
    private static final String BASE = "https://web.game.sklotopolis.com/unlimited/";

    private SklotopolisDeedMappings() { }

    public static List<DeedProviderMapping> defaults() {
        List<DeedProviderMapping> result = new ArrayList<DeedProviderMapping>();
        result.add(mapping("176.9.149.249:3725", "", 2, 4096));
        result.add(mapping("176.9.149.249:3726", "", 3, 4096));
        addNamed(result, "Caza", 4, 2048);
        addNamed(result, "Sklotopolis Caza", 4, 2048);
        addNamed(result, "Infinity", 5, 2048);
        addNamed(result, "Sklotopolis Infinity", 5, 2048);
        addNamed(result, "Infinity Round 5", 5, 2048);
        addNamed(result, "Old Infinity", 6, 2048);
        addNamed(result, "Sklotopolis Old Infinity", 6, 2048);
        return Collections.unmodifiableList(result);
    }

    private static void addNamed(List<DeedProviderMapping> result,
                                 String world, int backend, int size) {
        result.add(mapping("176.9.149.249:3724", world, backend, size));
    }

    private static DeedProviderMapping mapping(String endpoint, String world,
                                                int backend, int size) {
        return new DeedProviderMapping(SklotopolisDeedProvider.KEY,
                new ServerIdentityKey(endpoint, world),
                URI.create(BASE + backend + "/deeds.json"),
                DeedFeedFormat.SKLOTOPOLIS_JSON, size, size);
    }
}
