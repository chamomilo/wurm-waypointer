package org.waypoints.next.navigation;

import org.waypoints.next.model.ServerIdentity;

/** Confirmed account/server context used to project persisted records. */
public final class NavigationContext {
    private final ServerIdentity currentServer;
    private final String currentUser;
    private final int markerCap;

    public NavigationContext(ServerIdentity currentServer, String currentUser,
                             int markerCap) {
        if (currentServer == null || !currentServer.isSafeForAutomaticRendering()) {
            throw new IllegalArgumentException("a resolved endpoint server is required");
        }
        String user = currentUser == null ? "" : currentUser.trim();
        if (user.isEmpty()) throw new IllegalArgumentException("current user is required");
        if (markerCap < 1 || markerCap > 1024) {
            throw new IllegalArgumentException("marker cap must be in 1..1024");
        }
        this.currentServer = currentServer;
        this.currentUser = user;
        this.markerCap = markerCap;
    }

    public ServerIdentity getCurrentServer() { return currentServer; }
    public String getCurrentUser() { return currentUser; }
    public int getMarkerCap() { return markerCap; }
}
