package org.waypoints.next.deeds;

import java.net.URI;

/** Exact server-to-feed configuration used by network and local-file providers. */
public final class DeedProviderMapping {
    private final String providerKey;
    private final ServerIdentityKey server;
    private final URI source;
    private final DeedFeedFormat format;
    private final int mapWidth;
    private final int mapHeight;

    public DeedProviderMapping(String providerKey, ServerIdentityKey server,
                               URI source, DeedFeedFormat format,
                               int mapWidth, int mapHeight) {
        this.providerKey = required(providerKey, "provider key");
        if (server == null) throw new IllegalArgumentException("server key is required");
        if (source == null || !source.isAbsolute()) throw new IllegalArgumentException(
                "absolute deed source URI is required");
        if (format == null) throw new IllegalArgumentException("feed format is required");
        if (mapWidth < 1 || mapHeight < 1) throw new IllegalArgumentException(
                "map bounds must be positive");
        this.server = server;
        this.source = source;
        this.format = format;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
    }

    public String getProviderKey() { return providerKey; }
    public ServerIdentityKey getServer() { return server; }
    public URI getSource() { return source; }
    public DeedFeedFormat getFormat() { return format; }
    public int getMapWidth() { return mapWidth; }
    public int getMapHeight() { return mapHeight; }

    private static String required(String value, String label) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return clean;
    }
}
