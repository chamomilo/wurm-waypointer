package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.util.Locale;
import java.util.Objects;

/** Explicit endpoint identity with an optional exact world-name discriminator. */
public final class ServerIdentityKey {
    private final String endpointFingerprint;
    private final String worldName;

    public ServerIdentityKey(String endpointFingerprint, String worldName) {
        this.endpointFingerprint = required(endpointFingerprint,
                "endpoint fingerprint");
        this.worldName = clean(worldName);
    }

    public String getEndpointFingerprint() { return endpointFingerprint; }
    public String getWorldName() { return worldName; }

    public boolean matches(ServerIdentity server) {
        if (server == null || !endpointFingerprint.equalsIgnoreCase(
                clean(server.getEndpointFingerprint()))) return false;
        if (worldName.isEmpty()) return true;
        if (worldName.equalsIgnoreCase(clean(server.getFullName()))
                || worldName.equalsIgnoreCase(clean(server.getShortName()))) return true;
        for (String alias : server.getAliases()) {
            if (worldName.equalsIgnoreCase(clean(alias))) return true;
        }
        return false;
    }

    /** Stable cache key; provider selection never relies on a short-name guess. */
    public String externalForm() {
        return endpointFingerprint.toLowerCase(Locale.ENGLISH) + "|"
                + worldName.toLowerCase(Locale.ENGLISH);
    }

    private static String required(String value, String label) {
        String clean = clean(value);
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return clean;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerIdentityKey)) return false;
        ServerIdentityKey that = (ServerIdentityKey) other;
        return endpointFingerprint.equalsIgnoreCase(that.endpointFingerprint)
                && worldName.equalsIgnoreCase(that.worldName);
    }

    @Override public int hashCode() {
        return Objects.hash(endpointFingerprint.toLowerCase(Locale.ENGLISH),
                worldName.toLowerCase(Locale.ENGLISH));
    }
}
