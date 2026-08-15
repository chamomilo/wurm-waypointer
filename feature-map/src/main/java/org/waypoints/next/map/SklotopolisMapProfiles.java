package org.waypoints.next.map;

import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Explicit profiles for the published Sklotopolis map backend. */
public final class SklotopolisMapProfiles {
    private static final String BASE =
            "https://web.game.sklotopolis.com/unlimited";

    public static final ServerMapProfile LIBERTY = profile(
            "sklotopolis-liberty", "Sklotopolis Liberty", 2, 4096);
    public static final ServerMapProfile NOVUS = profile(
            "sklotopolis-novus", "Sklotopolis Novus", 3, 4096);
    public static final ServerMapProfile CAZA = profile(
            "sklotopolis-caza", "Sklotopolis Caza", 4, 2048);
    public static final ServerMapProfile OLD_INFINITY = profile(
            "sklotopolis-old-infinity", "Sklotopolis Old Infinity", 5, 2048);
    public static final ServerMapProfile INFINITY = profile(
            "sklotopolis-infinity", "Sklotopolis Infinity", 6, 2048);

    private static final List<ServerMapProfile> ALL =
            Collections.unmodifiableList(Arrays.asList(
                    LIBERTY, NOVUS, CAZA, OLD_INFINITY, INFINITY));

    private SklotopolisMapProfiles() { }

    public static List<ServerMapProfile> all() { return ALL; }

    /**
     * Resolve only after Wurm has supplied the actual world name. Port 3724 is
     * shared with cluster/login traffic, so it is never sufficient for Caza.
     */
    public static ServerMapProfile resolve(ServerIdentity server) {
        if (server == null || server.getEndpoint() == null) return null;
        ServerEndpoint endpoint = server.getEndpoint();
        String host = clean(endpoint.getHost());
        String names = clean(server.getFullName() + " " + server.getShortName());
        boolean knownHost = "176.9.149.249".equals(host)
                || host.endsWith(".sklotopolis.com")
                || "sklotopolis.com".equals(host);
        boolean namedCluster = names.contains("sklotopolis");
        if (!knownHost && !namedCluster) return null;

        if (names.contains("old infinity")) return OLD_INFINITY;
        if (names.contains("liberty")) return LIBERTY;
        if (names.contains("novus")) return NOVUS;
        if (names.contains("caza")) return CAZA;
        if (names.contains("infinity")) return INFINITY;

        // Stable original-world ports are useful for direct-connect records,
        // but intentionally do not guess Caza from the shared 3724 endpoint.
        if (endpoint.getGamePort() == 3725) return LIBERTY;
        if (endpoint.getGamePort() == 3726) return NOVUS;
        return null;
    }

    private static ServerMapProfile profile(String id, String name,
                                            int backend, int size) {
        return new ServerMapProfile(id, name, backend, size, size,
                BASE + "/" + backend);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }
}
