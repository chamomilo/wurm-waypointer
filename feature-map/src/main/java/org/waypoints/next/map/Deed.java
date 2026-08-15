package org.waypoints.next.map;

/** One published deed, including deed and perimeter extents. */
public final class Deed {
    private final String name;
    private final String type;
    private final String mayor;
    private final String allianceName;
    private final String founderName;
    private final String motto;
    private final String lastActive;
    private final int guards;
    private final int citizens;
    private final long creationDate;
    private final int x;
    private final int y;
    private final int north;
    private final int south;
    private final int east;
    private final int west;
    private final int perimeter;
    private final boolean spawnPoint;

    Deed(String name, String type, String mayor, String allianceName,
         String founderName, String motto, String lastActive,
         int guards, int citizens, long creationDate, int x, int y,
         int north, int south,
         int east, int west, int perimeter, boolean spawnPoint) {
        this.name = name;
        this.type = type;
        this.mayor = mayor;
        this.allianceName = allianceName;
        this.founderName = founderName;
        this.motto = motto;
        this.lastActive = lastActive;
        this.guards = guards;
        this.citizens = citizens;
        this.creationDate = creationDate;
        this.x = x;
        this.y = y;
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
        this.perimeter = perimeter;
        this.spawnPoint = spawnPoint;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getMayor() { return mayor; }
    public String getAllianceName() { return allianceName; }
    public String getFounderName() { return founderName; }
    public String getMotto() { return motto; }
    public String getLastActive() { return lastActive; }
    public int getGuards() { return guards; }
    public int getCitizens() { return citizens; }
    public long getCreationDate() { return creationDate; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getNorth() { return north; }
    public int getSouth() { return south; }
    public int getEast() { return east; }
    public int getWest() { return west; }
    public int getPerimeter() { return perimeter; }
    public boolean isSpawnPoint() { return spawnPoint; }
    public int getMinimumX() { return x - west; }
    public int getMaximumX() { return x + east; }
    public int getMinimumY() { return y - north; }
    public int getMaximumY() { return y + south; }
    public int getPerimeterMinimumX() { return getMinimumX() - perimeter; }
    public int getPerimeterMaximumX() { return getMaximumX() + perimeter; }
    public int getPerimeterMinimumY() { return getMinimumY() - perimeter; }
    public int getPerimeterMaximumY() { return getMaximumY() + perimeter; }
}
