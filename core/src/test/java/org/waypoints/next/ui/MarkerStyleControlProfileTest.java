package org.waypoints.next.ui;

import org.junit.Test;
import org.waypoints.next.model.MarkerStyle;

import static org.junit.Assert.assertEquals;

public final class MarkerStyleControlProfileTest {
    @Test public void stylesExposeOnlyMeaningfulNamedSliders() {
        MarkerStyleControlProfile beam = profile(MarkerStyle.WorldStyle.COLORED_BEAM);
        assertEquals(3, beam.getSliderCount());
        assertEquals("Beam field size", beam.getPrimaryLabel());

        MarkerStyleControlProfile circle = profile(MarkerStyle.WorldStyle.CIRCLE_BEAM);
        assertEquals("Circle radius", circle.getPrimaryLabel());
        assertEquals("Center beam thickness", circle.getSecondaryLabel());

        MarkerStyleControlProfile symbol = profile(MarkerStyle.WorldStyle.PICKAXE);
        assertEquals("Symbol size", symbol.getPrimaryLabel());
        assertEquals("Stroke thickness", symbol.getSecondaryLabel());
        assertEquals(MarkerStyleControlProfile.Kind.SYMBOL,
                profile(MarkerStyle.WorldStyle.SHOVEL).getKind());
        assertEquals(MarkerStyleControlProfile.Kind.SYMBOL,
                profile(MarkerStyle.WorldStyle.PICKAXE_AND_SHOVEL).getKind());

        for (MarkerStyle.WorldStyle reserved : new MarkerStyle.WorldStyle[]{
                MarkerStyle.WorldStyle.WHITE_LIGHT,
                MarkerStyle.WorldStyle.BLACK_LIGHT,
                MarkerStyle.WorldStyle.RIFT}) {
            boolean rejected = false;
            try { profile(reserved); }
            catch (IllegalArgumentException expected) { rejected = true; }
            assertEquals(true, rejected);
        }

        assertEquals(0, profile(MarkerStyle.WorldStyle.COMPASS_ONLY).getSliderCount());
        assertEquals(0, profile(MarkerStyle.WorldStyle.HIDDEN).getSliderCount());
    }

    private static MarkerStyleControlProfile profile(MarkerStyle.WorldStyle style) {
        return MarkerStyleControlProfile.forStyle(style);
    }
}
