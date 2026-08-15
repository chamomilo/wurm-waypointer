package org.waypoints.next.surroundings;

import java.util.HashMap;
import java.util.Map;

/** Stable names for the modifier byte sent with a live Wurm creature. */
public enum CreatureModifier {
    NONE(0, "Normal"),
    FIERCE(1, "Fierce"),
    ANGRY(2, "Angry"),
    RAGING(3, "Raging"),
    SLOW(4, "Slow"),
    ALERT(5, "Alert"),
    GREENISH(6, "Greenish"),
    LURKING(7, "Lurking"),
    SLY(8, "Sly"),
    HARDENED(9, "Hardened"),
    SCARED(10, "Scared"),
    DISEASED(11, "Diseased"),
    SMALL(-1, "Small"),
    MINI(-2, "Mini"),
    TINY(-3, "Tiny"),
    CHAMPION(99, "Champion"),
    UNKNOWN(Integer.MIN_VALUE, "Unknown");

    private static final Map<Integer, CreatureModifier> BY_CODE =
            new HashMap<Integer, CreatureModifier>();

    static {
        for (CreatureModifier value : values()) {
            if (value != UNKNOWN) BY_CODE.put(value.wurmCode, value);
        }
    }

    private final int wurmCode;
    private final String label;

    CreatureModifier(int wurmCode, String label) {
        this.wurmCode = wurmCode;
        this.label = label;
    }

    public int getWurmCode() { return wurmCode; }
    public String getLabel() { return label; }

    public static CreatureModifier fromWurmCode(int code) {
        CreatureModifier result = BY_CODE.get(code);
        return result == null ? UNKNOWN : result;
    }

    /**
     * Some server builds decorate the visible creature name but send modifier
     * zero. Prefer an explicit non-zero byte, then recover the visible trait
     * so the catalog row and its filter cannot disagree.
     */
    public static CreatureModifier fromWurmData(int code, String... names) {
        CreatureModifier coded = fromWurmCode(code);
        if (coded != NONE && coded != UNKNOWN) return coded;
        if (names != null) for (String name : names) {
            String searchable = " " + SurroundingsClassifier.normalize(name) + " ";
            for (CreatureModifier value : values()) {
                if (value.wurmCode <= 0 || value == UNKNOWN) continue;
                String label = " " + SurroundingsClassifier.normalize(
                        value.label) + " ";
                if (searchable.contains(label)) return value;
            }
        }
        return coded;
    }
}
