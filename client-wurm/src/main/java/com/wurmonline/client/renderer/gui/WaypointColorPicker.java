package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import org.waypoints.next.ui.HsvColor;
import org.waypoints.next.ui.WaypointColorPickerState;

/** Wurm-toned HSV picker. Rendering and mouse handling stay in the GUI bridge. */
final class WaypointColorPicker extends FlexComponent {
    interface Listener {
        void colorChanged(float red, float green, float blue);
    }

    private static final int SATURATION_COLUMNS = 32;
    private static final int VALUE_ROWS = 12;
    private static final int HUE_COLUMNS = 36;
    private static final int MARGIN = 6;
    private static final int HUE_HEIGHT = 14;
    private static final int GAP = 6;
    private static final int SWATCH_WIDTH = 116;

    private final Listener listener;
    private final float[] rgb = new float[3];
    private final WaypointColorPickerState state;
    private int activeRegion;

    WaypointColorPicker(float red, float green, float blue, Listener listener) {
        super("waypointer.style.color.picker");
        if (listener == null) throw new IllegalArgumentException("color listener is required");
        this.listener = listener;
        this.state = new WaypointColorPickerState(red, green, blue);
        setSize(620, 118);
    }

    void setColor(float red, float green, float blue) {
        state.setColor(red, green, blue);
    }

    boolean matchesColor(float red, float green, float blue) {
        return state.matches(red, green, blue);
    }

    String hexText() { return state.getHex(); }

    @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
        fillRect(queue, 0.10f, 0.08f, 0.05f, 1.0f, x, y, width, height);
        int svLeft = x + MARGIN;
        int svTop = y + MARGIN;
        int svWidth = saturationWidth();
        int svHeight = saturationHeight();
        for (int row = 0; row < VALUE_ROWS; row++) {
            float value = 1.0f - (row + 0.5f) / VALUE_ROWS;
            int top = svTop + row * svHeight / VALUE_ROWS;
            int bottom = svTop + (row + 1) * svHeight / VALUE_ROWS;
            for (int column = 0; column < SATURATION_COLUMNS; column++) {
                float saturation = (column + 0.5f) / SATURATION_COLUMNS;
                HsvColor.toRgb(state.getHue(), saturation, value, rgb);
                int left = svLeft + column * svWidth / SATURATION_COLUMNS;
                int right = svLeft + (column + 1) * svWidth / SATURATION_COLUMNS;
                fillRect(queue, rgb[0], rgb[1], rgb[2], 1.0f,
                        left, top, Math.max(1, right - left),
                        Math.max(1, bottom - top));
            }
        }
        paintBorder(queue, svLeft, svTop, svWidth, svHeight);
        int selectedX = svLeft + Math.round(state.getSaturation()
                * Math.max(1, svWidth - 1));
        int selectedY = svTop + Math.round((1.0f - state.getValue())
                * Math.max(1, svHeight - 1));
        paintSelector(queue, selectedX, selectedY);

        int hueTop = svTop + svHeight + GAP;
        for (int column = 0; column < HUE_COLUMNS; column++) {
            HsvColor.toRgb((column + 0.5f) / HUE_COLUMNS, 1.0f, 1.0f, rgb);
            int left = svLeft + column * svWidth / HUE_COLUMNS;
            int right = svLeft + (column + 1) * svWidth / HUE_COLUMNS;
            fillRect(queue, rgb[0], rgb[1], rgb[2], 1.0f,
                    left, hueTop, Math.max(1, right - left), HUE_HEIGHT);
        }
        paintBorder(queue, svLeft, hueTop, svWidth, HUE_HEIGHT);
        int hueX = svLeft + Math.round(state.getHue()
                * Math.max(1, svWidth - 1));
        fillRect(queue, 0.05f, 0.04f, 0.03f, 1.0f,
                hueX - 2, hueTop - 2, 5, HUE_HEIGHT + 4);
        fillRect(queue, 0.95f, 0.82f, 0.50f, 1.0f,
                hueX - 1, hueTop - 1, 3, HUE_HEIGHT + 2);

        rgb[0] = state.getRed();
        rgb[1] = state.getGreen();
        rgb[2] = state.getBlue();
        int swatchLeft = svLeft + svWidth + 12;
        int swatchTop = svTop;
        int swatchWidth = Math.max(48, width - (swatchLeft - x) - MARGIN);
        int swatchHeight = Math.max(42, svHeight - 22);
        paintCheckerboard(queue, swatchLeft, swatchTop, swatchWidth, swatchHeight);
        fillRect(queue, rgb[0], rgb[1], rgb[2], 1.0f,
                swatchLeft + 2, swatchTop + 2,
                Math.max(1, swatchWidth - 4), Math.max(1, swatchHeight - 4));
        paintBorder(queue, swatchLeft, swatchTop, swatchWidth, swatchHeight);
        TextFont font = TextFont.getFixedSizeText();
        font.moveTo(swatchLeft, swatchTop + swatchHeight + font.getAscent() + 5);
        font.paint(queue, state.getHex(), 0.95f, 0.88f, 0.70f, 1.0f);
        font.moveTo(swatchLeft, swatchTop + swatchHeight + font.getAscent()
                + font.getHeight() + 6);
        font.paint(queue, "Drag to choose", 0.78f, 0.76f, 0.70f, 1.0f);
    }

    @Override protected void leftPressed(int mouseX, int mouseY, int clickCount) {
        activeRegion = regionAt(mouseX, mouseY);
        if (activeRegion != 0) updateFromMouse(mouseX, mouseY);
    }

    @Override protected void mouseDragged(int mouseX, int mouseY) {
        if (activeRegion != 0) updateFromMouse(mouseX, mouseY);
    }

    @Override protected void leftReleased(int mouseX, int mouseY) {
        if (activeRegion != 0) updateFromMouse(mouseX, mouseY);
        activeRegion = 0;
    }

    @Override protected int getMouseCursor(int mouseX, int mouseY) {
        return regionAt(mouseX, mouseY) == 0 ? MOUSE_CURSOR_NORMAL : MOUSE_CURSOR_HAND;
    }

    private int regionAt(int mouseX, int mouseY) {
        int left = x + MARGIN;
        int top = y + MARGIN;
        int pickerWidth = saturationWidth();
        int pickerHeight = saturationHeight();
        if (inside(mouseX, mouseY, left, top, pickerWidth, pickerHeight)) return 1;
        int hueTop = top + pickerHeight + GAP;
        return inside(mouseX, mouseY, left, hueTop, pickerWidth, HUE_HEIGHT) ? 2 : 0;
    }

    private void updateFromMouse(int mouseX, int mouseY) {
        int left = x + MARGIN;
        int top = y + MARGIN;
        int pickerWidth = saturationWidth();
        int pickerHeight = saturationHeight();
        if (activeRegion == 1) {
            state.setSaturationAndValue(ratio(mouseX - left, pickerWidth),
                    1.0f - ratio(mouseY - top, pickerHeight));
        } else if (activeRegion == 2) {
            state.setHue(ratio(mouseX - left, pickerWidth));
        }
        listener.colorChanged(state.getRed(), state.getGreen(), state.getBlue());
    }

    private int saturationWidth() {
        return Math.max(80, width - SWATCH_WIDTH - MARGIN * 2 - 12);
    }

    private int saturationHeight() {
        return Math.max(42, height - MARGIN * 2 - GAP - HUE_HEIGHT);
    }

    private void paintSelector(Queue queue, int centerX, int centerY) {
        fillRect(queue, 0.02f, 0.02f, 0.02f, 1.0f, centerX - 6, centerY - 1, 13, 3);
        fillRect(queue, 0.02f, 0.02f, 0.02f, 1.0f, centerX - 1, centerY - 6, 3, 13);
        fillRect(queue, 1.0f, 1.0f, 1.0f, 1.0f, centerX - 5, centerY, 11, 1);
        fillRect(queue, 1.0f, 1.0f, 1.0f, 1.0f, centerX, centerY - 5, 1, 11);
    }

    private void paintBorder(Queue queue, int left, int top, int drawWidth,
                             int drawHeight) {
        fillRect(queue, 0.50f, 0.43f, 0.30f, 1.0f, left, top, drawWidth, 1);
        fillRect(queue, 0.50f, 0.43f, 0.30f, 1.0f,
                left, top + drawHeight - 1, drawWidth, 1);
        fillRect(queue, 0.50f, 0.43f, 0.30f, 1.0f, left, top, 1, drawHeight);
        fillRect(queue, 0.50f, 0.43f, 0.30f, 1.0f,
                left + drawWidth - 1, top, 1, drawHeight);
    }

    private void paintCheckerboard(Queue queue, int left, int top,
                                   int drawWidth, int drawHeight) {
        int cell = 8;
        for (int row = 0; row * cell < drawHeight; row++) {
            for (int column = 0; column * cell < drawWidth; column++) {
                float shade = ((row + column) & 1) == 0 ? 0.18f : 0.32f;
                fillRect(queue, shade, shade, shade, 1.0f,
                        left + column * cell, top + row * cell,
                        Math.min(cell, drawWidth - column * cell),
                        Math.min(cell, drawHeight - row * cell));
            }
        }
    }

    private static boolean inside(int mouseX, int mouseY, int left, int top,
                                  int drawWidth, int drawHeight) {
        return mouseX >= left && mouseX < left + drawWidth
                && mouseY >= top && mouseY < top + drawHeight;
    }

    private static float ratio(int offset, int size) {
        if (size <= 1) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, offset / (float) (size - 1)));
    }
}
