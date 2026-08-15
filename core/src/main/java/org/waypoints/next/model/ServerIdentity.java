package org.waypoints.next.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;

/** Endpoint-first identity. Names are metadata and never merge two endpoints. */
public final class ServerIdentity {
    public enum Resolution {
        RESOLVED,
        UNRESOLVED_NO_ENDPOINT,
        UNRESOLVED_DIRECT_CONNECT,
        UNRESOLVED_SERVER_TRANSFER,
        UNRESOLVED_WORLD_NAME,
        UNRESOLVED_NAME_MISMATCH
    }

    private final ServerEndpoint endpoint;
    private final String fullName;
    private final String shortName;
    private final List<String> aliases;
    private final Resolution resolution;

    private ServerIdentity(ServerEndpoint endpoint, String fullName, String shortName,
                           List<String> aliases, Resolution resolution) {
        this.endpoint = endpoint;
        this.fullName = clean(fullName);
        this.shortName = clean(shortName);
        this.aliases = immutableNames(aliases);
        this.resolution = resolution;
    }

    public static ServerIdentity of(ServerEndpoint endpoint, String fullName,
                                    String shortName, Resolution resolution) {
        if (resolution == null) throw new IllegalArgumentException("resolution is required");
        return new ServerIdentity(endpoint, fullName, shortName,
                Collections.<String>emptyList(), resolution);
    }

    /** Rehydrates persisted metadata without weakening endpoint-first matching. */
    public static ServerIdentity restored(ServerEndpoint endpoint, String fullName,
                                          String shortName, List<String> aliases,
                                          Resolution resolution) {
        if (resolution == null) throw new IllegalArgumentException("resolution is required");
        return new ServerIdentity(endpoint, fullName, shortName, aliases, resolution);
    }

    public ServerEndpoint getEndpoint() {
        return endpoint;
    }

    public String getEndpointFingerprint() {
        return endpoint == null ? "" : endpoint.fingerprint();
    }

    public String getFullName() {
        return fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public boolean isResolved() {
        return resolution == Resolution.RESOLVED;
    }

    public boolean isSafeForAutomaticRendering() {
        return isResolved() && endpoint != null;
    }

    public boolean sameServer(ServerIdentity other) {
        return other != null && endpoint != null && other.endpoint != null
                && endpoint.equals(other.endpoint);
    }

    public ServerIdentity withAlias(String alias) {
        String cleanAlias = clean(alias);
        if (cleanAlias.isEmpty() || containsIgnoreCase(aliases, cleanAlias)
                || fullName.equalsIgnoreCase(cleanAlias)) return this;
        List<String> updated = new ArrayList<String>(aliases);
        updated.add(cleanAlias);
        return new ServerIdentity(endpoint, fullName, shortName, updated, resolution);
    }

    private static List<String> immutableNames(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        Set<String> unique = new LinkedHashSet<String>();
        Set<String> folded = new LinkedHashSet<String>();
        for (String value : values) {
            String clean = clean(value);
            String lower = clean.toLowerCase(Locale.ENGLISH);
            if (!clean.isEmpty() && folded.add(lower)) unique.add(clean);
        }
        return Collections.unmodifiableList(new ArrayList<String>(unique));
    }

    private static boolean containsIgnoreCase(List<String> values, String expected) {
        for (String value : values) if (value.equalsIgnoreCase(expected)) return true;
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        String name = fullName.isEmpty() ? shortName : fullName;
        return name + " [" + getEndpointFingerprint() + ", " + resolution + "]";
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerIdentity)) return false;
        ServerIdentity that = (ServerIdentity) other;
        return Objects.equals(endpoint, that.endpoint)
                && fullName.equals(that.fullName)
                && shortName.equals(that.shortName)
                && aliases.equals(that.aliases)
                && resolution == that.resolution;
    }

    @Override public int hashCode() {
        return Objects.hash(endpoint, fullName, shortName, aliases, resolution);
    }
}
