package org.waypoints.next.render;

import java.util.Locale;

/** Explicit server-specific fixtures used only by the Phase 0 manual gate. */
public final class Phase0ProbeProfile {
    private final String name;
    private final float red;
    private final float green;
    private final float blue;

    private Phase0ProbeProfile(String name, float red, float green, float blue) {
        this.name = name;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public static Phase0ProbeProfile forServer(String serverKey,
                                               BeamProbeConfiguration fallback) {
        String key = serverKey == null ? "" : serverKey.toLowerCase(Locale.ENGLISH);
        if (key.endsWith("|liberty") || key.endsWith("|sklotopolis - liberty")) {
            return new Phase0ProbeProfile("Waypoint 2", 1.0f, 1.0f, 0.0f);
        }
        return new Phase0ProbeProfile(fallback.getWaypointName(),
                fallback.getRed(), fallback.getGreen(), fallback.getBlue());
    }

    public String getName() { return name; }
    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
}
