package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;

import java.util.Locale;

/** Fixed-alpha Wurm-style slider used by waypoint visual and arrival editors. */
final class WaypointStyleSlider extends FlexComponent {
    enum Display { PERCENT, METRES, ONE_DECIMAL, TWO_DECIMALS }

    interface Listener {
        void valueChanged(float value);
    }

    private static final int VALUE_WIDTH = 74;
    private final float minimum;
    private final float maximum;
    private final float step;
    private final Display display;
    private final Listener listener;
    private float value;
    private boolean dragging;
    private String valueText = "";

    WaypointStyleSlider(String name, float minimum, float maximum, float step,
                        float initial, Display display, Listener listener) {
        super(name);
        if (Float.isNaN(minimum) || Float.isNaN(maximum) || maximum <= minimum) {
            throw new IllegalArgumentException("slider range is invalid");
        }
        if (Float.isNaN(step) || Float.isInfinite(step) || step <= 0.0f) {
            throw new IllegalArgumentException("slider step must be positive");
        }
        if (display == null || listener == null) {
            throw new IllegalArgumentException("slider display and listener are required");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.display = display;
        this.listener = listener;
        setSize(620, 28);
        setValue(initial);
    }

    void setValue(float next) {
        value = clamp(next);
        updateText();
    }

    @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
        fillRect(queue, 0.10f, 0.08f, 0.05f, 1.0f, x, y, width, height);
        int trackLeft = x + 8;
        int trackWidth = Math.max(20, width - VALUE_WIDTH - 18);
        int trackTop = y + height / 2 - 2;
        fillRect(queue, 0.24f, 0.21f, 0.16f, 1.0f,
                trackLeft, trackTop, trackWidth, 4);
        int selectedWidth = Math.round(fraction() * trackWidth);
        if (selectedWidth > 0) {
            fillRect(queue, 0.82f, 0.67f, 0.30f, 1.0f,
                    trackLeft, trackTop, selectedWidth, 4);
        }
        int knobX = trackLeft + Math.round(fraction() * Math.max(1, trackWidth - 1));
        fillRect(queue, 0.06f, 0.05f, 0.04f, 1.0f,
                knobX - 4, y + 4, 9, Math.max(8, height - 8));
        fillRect(queue, 0.92f, 0.78f, 0.44f, 1.0f,
                knobX - 2, y + 6, 5, Math.max(4, height - 12));
        TextFont font = TextFont.getFixedSizeText();
        int textLeft = x + width - VALUE_WIDTH + 4;
        font.moveTo(textLeft, y + Math.max(font.getAscent(),
                (height + font.getAscent()) / 2 - 1));
        font.paint(queue, valueText, 0.95f, 0.88f, 0.70f, 1.0f);
    }

    @Override protected void leftPressed(int mouseX, int mouseY, int clickCount) {
        if (!contains(mouseX, mouseY)) return;
        dragging = true;
        updateFromMouse(mouseX);
    }

    @Override protected void mouseDragged(int mouseX, int mouseY) {
        if (dragging) updateFromMouse(mouseX);
    }

    @Override protected void leftReleased(int mouseX, int mouseY) {
        if (dragging) updateFromMouse(mouseX);
        dragging = false;
    }

    @Override protected int getMouseCursor(int mouseX, int mouseY) {
        return contains(mouseX, mouseY) ? MOUSE_CURSOR_HAND : MOUSE_CURSOR_NORMAL;
    }

    private void updateFromMouse(int mouseX) {
        int trackLeft = x + 8;
        int trackWidth = Math.max(20, width - VALUE_WIDTH - 18);
        float fraction = Math.max(0.0f, Math.min(1.0f,
                (mouseX - trackLeft) / (float) Math.max(1, trackWidth - 1)));
        float raw = minimum + fraction * (maximum - minimum);
        float snapped = minimum + Math.round((raw - minimum) / step) * step;
        float next = clamp(snapped);
        if (Float.compare(next, value) == 0) return;
        value = next;
        updateText();
        listener.valueChanged(value);
    }

    private float fraction() {
        return (value - minimum) / (maximum - minimum);
    }

    private float clamp(float next) {
        if (Float.isNaN(next) || Float.isInfinite(next)) return minimum;
        return Math.max(minimum, Math.min(maximum, next));
    }

    private void updateText() {
        if (display == Display.PERCENT) {
            valueText = Math.round(value * 100.0f) + "%";
        } else if (display == Display.METRES) {
            valueText = Math.round(value) + "m";
        } else if (display == Display.TWO_DECIMALS) {
            valueText = String.format(Locale.ENGLISH, "%.2f", value);
        } else {
            valueText = String.format(Locale.ENGLISH, "%.1f", value);
        }
    }
}
