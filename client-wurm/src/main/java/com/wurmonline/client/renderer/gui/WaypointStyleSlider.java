package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.ui.v1.*;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.WaypointerFonts;

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

    @Override protected void renderComponent(Queue queue, float alpha) {
        ChamomiloUiV1Canvas canvas = WaypointerUi.canvas(this, queue);
        int trackLeft=x+8, trackWidth=Math.max(20,width-VALUE_WIDTH-18), trackTop=y+height/2-4;
        UiPainter.background(canvas,UiBackground.LEATHER,alpha,trackLeft,trackTop,trackWidth,8);
        UiHudPainter.value(canvas,UiAxis.HORIZONTAL,fraction(),UiColor.EDGE,alpha,new UiRect(trackLeft,trackTop,trackWidth,8));
        UiPainter.frame(canvas,2,alpha,trackLeft,trackTop,trackWidth,8);
        int knob=trackLeft+Math.round(fraction()*Math.max(1,trackWidth-1));
        UiPainter.button(canvas,1,dragging?1:0,UiScale.BASE,alpha,knob-5,y+4,11,Math.max(8,height-8));
        TextFont font=WaypointerFonts.body();
        font.moveTo(x+width-VALUE_WIDTH+4,y+(height-font.getHeight())/2+font.getAscent());
        font.paint(queue,valueText,UiColor.TEXT.red,UiColor.TEXT.green,UiColor.TEXT.blue,alpha);
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
