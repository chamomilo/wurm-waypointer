package org.waypoints.next.render;

/** Immutable render-thread snapshot for the single Phase 0 compass marker. */
public final class CompassMarkerSnapshot {
    private final float playerX;
    private final float playerY;
    private final float playerFacing;
    private final float targetX;
    private final float targetY;
    private final float red;
    private final float green;
    private final float blue;
    private final String name;
    private final boolean worldBeamVisible;

    public CompassMarkerSnapshot(float playerX, float playerY, float playerFacing,
                                 float targetX, float targetY,
                                 float red, float green, float blue,
                                 String name, boolean worldBeamVisible) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.playerFacing = playerFacing;
        this.targetX = targetX;
        this.targetY = targetY;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.name = name;
        this.worldBeamVisible = worldBeamVisible;
    }

    public float getPlayerX() { return playerX; }
    public float getPlayerY() { return playerY; }
    public float getPlayerFacing() { return playerFacing; }
    public float getTargetX() { return targetX; }
    public float getTargetY() { return targetY; }
    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
    public String getName() { return name; }
    public boolean isWorldBeamVisible() { return worldBeamVisible; }
}
