package org.waypoints.next.ui;

import org.junit.Test;
import org.waypoints.next.model.MarkerStyle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MarkerStyleEditorStateTest {
    @Test public void unchangedEditorReturnsOriginalStyleExactly() {
        MarkerStyle original = custom();
        MarkerStyleEditorState editor = new MarkerStyleEditorState(original);
        assertSame(original, editor.getStyle());
    }

    @Test public void individualControlsPreserveUnchangedFields() {
        MarkerStyleEditorState editor = new MarkerStyleEditorState(custom());
        editor.setColor(0.9f, 0.8f, 0.7f);
        editor.setAlpha(0.65f);
        editor.setMarkerSize(13.5f);
        editor.setBeamWidth(4.25f);

        MarkerStyle style = editor.getStyle();
        assertEquals(MarkerStyle.WorldStyle.COLORED_BEAM, style.getWorldStyle());
        assertEquals(0.9f, style.getRed(), 0.0001f);
        assertEquals(0.8f, style.getGreen(), 0.0001f);
        assertEquals(0.7f, style.getBlue(), 0.0001f);
        assertEquals(0.65f, style.getAlpha(), 0.0001f);
        assertEquals(13.5f, style.getMarkerSize(), 0.0001f);
        assertEquals(4.25f, style.getBeamWidth(), 0.0001f);
        assertEquals(false, style.isShowLabel());
        assertEquals(false, style.isShowDistance());
    }

    @Test public void typeChangeKeepsEveryUserTunedValue() {
        MarkerStyleEditorState editor = new MarkerStyleEditorState(custom());
        editor.selectWorldStyle(MarkerStyle.WorldStyle.HOLLOW_CIRCLE);

        MarkerStyle style = editor.getStyle();
        assertEquals(MarkerStyle.WorldStyle.HOLLOW_CIRCLE, style.getWorldStyle());
        assertEquals(0.1f, style.getRed(), 0.0001f);
        assertEquals(0.2f, style.getGreen(), 0.0001f);
        assertEquals(0.3f, style.getBlue(), 0.0001f);
        assertEquals(0.4f, style.getAlpha(), 0.0001f);
        assertEquals(12.25f, style.getMarkerSize(), 0.0001f);
        assertEquals(3.75f, style.getBeamWidth(), 0.0001f);
        assertEquals(false, style.isShowLabel());
        assertEquals(false, style.isShowDistance());
    }

    private static MarkerStyle custom() {
        return new MarkerStyle(MarkerStyle.WorldStyle.COLORED_BEAM,
                0.1f, 0.2f, 0.3f, 0.4f, 12.25f, 3.75f, false, false);
    }
}
