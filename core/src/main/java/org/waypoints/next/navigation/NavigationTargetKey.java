package org.waypoints.next.navigation;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** UUID plus endpoint identity; neither part is allowed to stand alone. */
public final class NavigationTargetKey implements Comparable<NavigationTargetKey> {
    private final String serverFingerprint;
    private final UUID waypointId;

    public NavigationTargetKey(String serverFingerprint, UUID waypointId) {
        String clean = serverFingerprint == null ? "" : serverFingerprint.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(
                "server fingerprint is required");
        if (waypointId == null) throw new IllegalArgumentException("waypoint id is required");
        this.serverFingerprint = clean.toLowerCase(Locale.ENGLISH);
        this.waypointId = waypointId;
    }

    public String getServerFingerprint() { return serverFingerprint; }
    public UUID getWaypointId() { return waypointId; }

    @Override public int compareTo(NavigationTargetKey other) {
        int server = serverFingerprint.compareTo(other.serverFingerprint);
        return server != 0 ? server
                : waypointId.toString().compareTo(other.waypointId.toString());
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NavigationTargetKey)) return false;
        NavigationTargetKey that = (NavigationTargetKey) other;
        return serverFingerprint.equals(that.serverFingerprint)
                && waypointId.equals(that.waypointId);
    }

    @Override public int hashCode() {
        return Objects.hash(serverFingerprint, waypointId);
    }

    @Override public String toString() {
        return serverFingerprint + "|" + waypointId;
    }
}
