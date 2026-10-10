package org.waypoints.next.surroundings;

/** The server-supplied attitude used by the pinned client's outline colours. */
public enum CreatureHostility {
    UNKNOWN("Unknown"), NEUTRAL("Neutral"), ALLY("Ally"), HOSTILE("Hostile"),
    FRIEND("Friendly"), DEV("Developer"), GM("GM");

    private final String label;
    CreatureHostility(String label) { this.label = label; }
    public String getLabel() { return label; }
    public static CreatureHostility fromAttitude(int attitude) {
        switch (attitude) {
            case 0: return NEUTRAL;
            case 1: case 5: return ALLY;
            case 2: case 4: return HOSTILE;
            case 3: return GM;
            case 7: return FRIEND;
            case 6: return DEV;
            default: return UNKNOWN;
        }
    }
}
