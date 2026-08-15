package org.waypoints.next.ui;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointLayer;

/** Live context captured at the Wurm adapter boundary for manager actions. */
public final class WaypointManagerContext {
    private final String user;
    private final ServerIdentity server;
    private final double tileX;
    private final double tileY;
    private final double height;
    private final WaypointLayer layer;

    public WaypointManagerContext(String user, ServerIdentity server,
                                  double tileX, double tileY, double height,
                                  WaypointLayer layer) {
        this.user = user;
        this.server = server;
        this.tileX = tileX;
        this.tileY = tileY;
        this.height = height;
        this.layer = layer;
    }

    public String getUser() { return user; }
    public ServerIdentity getServer() { return server; }
    public double getTileX() { return tileX; }
    public double getTileY() { return tileY; }
    public double getHeight() { return height; }
    public WaypointLayer getLayer() { return layer; }
}
