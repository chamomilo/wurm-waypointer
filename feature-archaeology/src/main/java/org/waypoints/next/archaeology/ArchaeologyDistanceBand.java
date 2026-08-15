package org.waypoints.next.archaeology;

import java.util.Locale;

/** Chebyshev distance bands spoken by a completed archaeology report. */
public enum ArchaeologyDistanceBand {
    VERY_CLOSE("very close", 1, 20, 0, 0),
    NEARBY("nearby", 21, 40, 31, 24),
    CLOSE("close", 41, 80, 61, 48),
    FAR("far", 81, 120, 101, 77),
    QUITE_DISTANT("quite distant", 121, 180, 151, 115),
    VERY_FAR("very far", 181, Integer.MAX_VALUE, 217, 217);

    private final String phrase;
    private final int minimum;
    private final int maximum;
    private final int cardinalStep;
    private final int diagonalStep;

    ArchaeologyDistanceBand(String phrase, int minimum, int maximum,
                            int cardinalStep, int diagonalStep) {
        this.phrase = phrase;
        this.minimum = minimum;
        this.maximum = maximum;
        this.cardinalStep = cardinalStep;
        this.diagonalStep = diagonalStep;
    }

    public String getPhrase() { return phrase; }
    public int getMinimum() { return minimum; }
    public int getMaximum() { return maximum; }
    public boolean isVeryClose() { return this == VERY_CLOSE; }
    public int step(ArchaeologyDirection direction) {
        return direction != null && direction.isDiagonal()
                ? diagonalStep : cardinalStep;
    }
    public boolean contains(double chebyshevDistance) {
        return chebyshevDistance >= minimum && chebyshevDistance <= maximum;
    }

    public static ArchaeologyDistanceBand parse(String value) {
        if (value == null) return null;
        String clean = value.trim().toLowerCase(Locale.ENGLISH)
                .replace('-', ' ').replaceAll("\\s+", " ");
        // Longest phrases first so "far" cannot consume "very far".
        for (ArchaeologyDistanceBand band : new ArchaeologyDistanceBand[]{
                QUITE_DISTANT, VERY_CLOSE, VERY_FAR, NEARBY, CLOSE, FAR}) {
            if (band.phrase.equals(clean)) return band;
        }
        return null;
    }
}
