package org.waypoints.next.map;

/** Immutable server-owned map endpoints and exact tile bounds. */
public final class ServerMapProfile {
    private final String id;
    private final String displayName;
    private final int backendId;
    private final int mapWidth;
    private final int mapHeight;
    private final String baseUrl;

    ServerMapProfile(String id, String displayName, int backendId,
                     int mapWidth, int mapHeight, String baseUrl) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("profile id is required");
        }
        if (mapWidth < 1 || mapHeight < 1) {
            throw new IllegalArgumentException("map bounds must be positive");
        }
        this.id = id;
        this.displayName = displayName;
        this.backendId = backendId;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.baseUrl = baseUrl;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getBackendId() { return backendId; }
    public int getMapWidth() { return mapWidth; }
    public int getMapHeight() { return mapHeight; }
    public String getSurfaceUrl() { return baseUrl + "/mapdump-flat.png"; }
    public String getDeedsUrl() { return baseUrl + "/deeds.json"; }
    public String getHighwaysUrl() { return baseUrl + "/highways.json"; }

    @Override public String toString() {
        return displayName + " [" + id + ", " + mapWidth + "x" + mapHeight + "]";
    }
}
