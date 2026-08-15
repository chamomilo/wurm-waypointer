package org.waypoints.next.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class UserMarkerStylesTest {
    @Test public void unchangedTypePreservesTheExactCustomizedStyle() {
        MarkerStyle custom = custom(MarkerStyle.WorldStyle.COLORED_BEAM);
        assertSame(custom, UserMarkerStyles.withWorldStyle(
                MarkerStyle.WorldStyle.COLORED_BEAM, custom));
    }

    @Test public void everyTypeChangePreservesAllUserTunedParameters() {
        MarkerStyle custom = custom(MarkerStyle.WorldStyle.COLORED_BEAM);
        for (MarkerStyle.WorldStyle requested : UserMarkerStyles.values()) {
            MarkerStyle changed = UserMarkerStyles.withWorldStyle(requested, custom);
            assertEquals(requested, changed.getWorldStyle());
            assertEquals(custom.getRed(), changed.getRed(), 0.0f);
            assertEquals(custom.getGreen(), changed.getGreen(), 0.0f);
            assertEquals(custom.getBlue(), changed.getBlue(), 0.0f);
            assertEquals(custom.getAlpha(), changed.getAlpha(), 0.0f);
            assertEquals(custom.getMarkerSize(), changed.getMarkerSize(), 0.0f);
            assertEquals(custom.getBeamWidth(), changed.getBeamWidth(), 0.0f);
            assertEquals(custom.isShowLabel(), changed.isShowLabel());
            assertEquals(custom.isShowDistance(), changed.isShowDistance());
        }
    }

    @Test public void vanillaStylesAreRejectedAndLegacyRecordsBecomeColoredBeams() {
        for (MarkerStyle.WorldStyle reserved : new MarkerStyle.WorldStyle[]{
                MarkerStyle.WorldStyle.WHITE_LIGHT,
                MarkerStyle.WorldStyle.BLACK_LIGHT,
                MarkerStyle.WorldStyle.RIFT}) {
            boolean rejected = false;
            try { UserMarkerStyles.withWorldStyle(reserved, null); }
            catch (IllegalArgumentException expected) { rejected = true; }
            assertEquals(true, rejected);
            MarkerStyle legacy = custom(reserved);
            MarkerStyle editable = UserMarkerStyles.editable(legacy);
            assertEquals(MarkerStyle.WorldStyle.COLORED_BEAM,
                    editable.getWorldStyle());
            assertEquals(legacy.getRed(), editable.getRed(), 0.0f);
            assertEquals(legacy.getMarkerSize(), editable.getMarkerSize(), 0.0f);
        }
        assertEquals(15, UserMarkerStyles.values().length);
    }

    @Test public void nullCurrentUsesTheSingleGeneralDefaultOnly() {
        MarkerStyle style = UserMarkerStyles.withWorldStyle(
                MarkerStyle.WorldStyle.HOUSE, null);
        MarkerStyle general = MarkerStyle.defaultColoredBeam();
        assertEquals(MarkerStyle.WorldStyle.HOUSE, style.getWorldStyle());
        assertEquals(general.getRed(), style.getRed(), 0.0f);
        assertEquals(general.getGreen(), style.getGreen(), 0.0f);
        assertEquals(general.getBlue(), style.getBlue(), 0.0f);
        assertEquals(general.getAlpha(), style.getAlpha(), 0.0f);
        assertEquals(general.getMarkerSize(), style.getMarkerSize(), 0.0f);
        assertEquals(general.getBeamWidth(), style.getBeamWidth(), 0.0f);
    }

    @Test public void lootMapScrollIsNotAUserSelectableStyle() {
        assertEquals(false, UserMarkerStyles.isSelectable(
                MarkerStyle.WorldStyle.LOOT_MAP_SCROLL));
        MarkerStyle reserved = custom(MarkerStyle.WorldStyle.LOOT_MAP_SCROLL);
        assertEquals(MarkerStyle.WorldStyle.COLORED_BEAM,
                UserMarkerStyles.editable(reserved).getWorldStyle());
    }

    @Test public void archaeologyReportScrollIsNotAUserSelectableStyle() {
        assertEquals(false, UserMarkerStyles.isSelectable(
                MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL));
        MarkerStyle reserved = custom(
                MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL);
        assertEquals(MarkerStyle.WorldStyle.COLORED_BEAM,
                UserMarkerStyles.editable(reserved).getWorldStyle());
    }

    @Test public void surroundingsExclamationIsNotAUserSelectableStyle() {
        assertEquals(false, UserMarkerStyles.isSelectable(
                MarkerStyle.WorldStyle.EXCLAMATION));
        MarkerStyle reserved = custom(MarkerStyle.WorldStyle.EXCLAMATION);
        assertEquals(MarkerStyle.WorldStyle.COLORED_BEAM,
                UserMarkerStyles.editable(reserved).getWorldStyle());
    }

    private static MarkerStyle custom(MarkerStyle.WorldStyle worldStyle) {
        return new MarkerStyle(worldStyle, 0.1f, 0.2f, 0.3f, 0.4f,
                11.0f, 3.0f, false, false);
    }
}
