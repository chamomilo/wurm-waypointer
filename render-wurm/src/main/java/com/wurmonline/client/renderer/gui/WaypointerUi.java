package com.wurmonline.client.renderer.gui;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts;
import com.wurmonline.client.renderer.gui.text.WaypointerFonts;
import org.chamomilo.wurm.ui.v1.*;
import org.waypoints.next.i18n.Messages;

/** Native data/focus identities backed by the standard Chamomilo controls. */
public final class WaypointerUi {
    private static final Map<WurmInputField, WeakReference<ChamomiloUiV1TextField>> INPUTS =
            new WeakHashMap<WurmInputField, WeakReference<ChamomiloUiV1TextField>>();
    private WaypointerUi() { }
    private static final Map<WurmComponent, Map<String, UiButtonMotion>> MOTIONS =
            new WeakHashMap<WurmComponent, Map<String, UiButtonMotion>>();
    static ChamomiloUiV1Canvas canvas(WurmComponent owner, Queue queue) {
        return new ChamomiloUiV1Canvas(owner).begin(queue);
    }
    static void paintButton(WurmComponent owner, Queue queue, String id, String caption,
                            boolean hovered, boolean pressed, boolean available, boolean selected,
                            int x, int y, int width, int height) {
        paintButton(owner,queue,id,caption,hovered,pressed,available,selected,x,y,width,height,null);
    }
    static void paintButton(WurmComponent owner, Queue queue, String id, String caption,
                            boolean hovered, boolean pressed, boolean available, boolean selected,
                            int x, int y, int width, int height,WaypointerButtonGroup group) {
        Map<String, UiButtonMotion> motions = MOTIONS.get(owner);
        if (motions == null) { motions = new HashMap<String, UiButtonMotion>(); MOTIONS.put(owner, motions); }
        UiButtonMotion motion = motions.get(id);
        if (motion == null) { motion = new UiButtonMotion(); motions.put(id, motion); }
        motion.update(available, hovered, pressed, System.nanoTime());
        ChamomiloUiV1Canvas canvas = canvas(owner, queue);
        UiScale scale = height <= 22 ? new UiScale(.5f) : UiScale.BASE;
        UiPainter.button(canvas, motion.brightness(), motion.depth(), motion.hover(), scale, 1, x, y, width, height);
        int shift = Math.round(motion.depth()*scale.factor);
        UiColor color = UiPainter.buttonCaptionColor(available, motion.hover());
        UiIcon icon = "-".equals(caption) ? UiIcon.MINUS : "+".equals(caption) ? UiIcon.PLUS
                : "@close".equals(caption) ? UiIcon.CLOSE : null;
        if (icon != null) icon.paint(canvas, color, 1, x + (width - 12) / 2 + shift, y + (height - 12) / 2 + shift, 12);
        else if(group!=null)group.paintCaption(queue,Messages.text(caption),available&&hovered,x,y,width,shift,color);
        else {
            UiButtonLayout layout = UiButtonLayout.fit(new String[]{Messages.text("@search".equals(caption) ? "?" : caption)},width,height,UiDensity.HIGH,scale,true);
            TextFont font = ChamomiloUiV1Fonts.caption(layout.fontPixels,available && hovered);
            String label = layout.rows()[0];
            font.moveTo(x + layout.textX(0,available && hovered) + shift,y + layout.baseline(0) + shift);
            font.paint(queue, label, color.red, color.green, color.blue, 1);
        }
        if (selected) canvas.fill(UiColor.EDGE, 1, x + 5, y + height - 3, Math.max(0, width - 10), 2);
    }
    public static WButton button(String label, ButtonListener listener, int width) {
        return button(label,listener,width,32,UiDensity.HIGH);
    }
    static ChamomiloUiV1Button button(String label, ButtonListener listener, int width,int height,UiDensity density) {
        final WButton[] button = new WButton[1];
        ChamomiloUiV1Button result = new ChamomiloUiV1Button(Messages.text(label),width,height,
                () -> listener.buttonClicked(button[0]));
        result.setDensity(density);button[0]=result;return result;
    }
    static int captionWidth(WButton button) {
        if (button instanceof ChamomiloUiV1Button) {
            ChamomiloUiV1Button nativeButton=(ChamomiloUiV1Button)button;
            return nativeButton.captionWidth()+2*(nativeButton.density()==UiDensity.HIGH?4:18);
        }
        return Math.max(button.text.getWidth(button.getLabel()), button.textBold.getWidth(button.getLabel())) + 24;
    }
    static int compactCaptionWidth(String caption) {
        String label = Messages.text(caption);
        return Math.max(UiTypography.width(label,12,false),UiTypography.width(label,12,true))+16;
    }
    static WurmDropDown dropDown(String id, int selected, String[] labels) {
        WurmDropDown field = new WaypointerFullDropDown(id, 160, Messages.texts(labels), value -> { });
        field.setValue(selected);
        return field;
    }
    static WurmInputField input(String id, InputFieldListener listener) { return input(id, listener, 1, 1024); }
    static WurmInputField input(String id, InputFieldListener listener, int lines, int limit) {
        if (lines != 1) throw new IllegalArgumentException("Only single-line fields are supported");
        ChamomiloUiV1TextField wrapper = new ChamomiloUiV1TextField(id, 300, limit, value -> { }, value -> { });
        try {
            Field inner = ChamomiloUiV1TextField.class.getDeclaredField("input");
            inner.setAccessible(true);
            WurmInputField nativeInput = (WurmInputField) inner.get(wrapper);
            wrapper.text=nativeInput.text=WaypointerFonts.body();
            wrapper.textBold=nativeInput.textBold=WaypointerFonts.body(true);
            nativeInput.setLineHeight(nativeInput.text.getAscent()+1);
            nativeInput.setMaxLines(1); // Wurm caches the single-line baseline in maxHeight.
            nativeInput.setInitialSize(nativeInput.width,nativeInput.text.getHeight()+2,false);
            Field callbacks = WurmInputField.class.getDeclaredField("inputFieldListener");
            callbacks.setAccessible(true);
            callbacks.set(nativeInput, listener);
            INPUTS.put(nativeInput, new WeakReference<ChamomiloUiV1TextField>(wrapper));
            return nativeInput;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot connect standard text field", failure);
        }
    }
    static FlexComponent view(FlexComponent component) {
        if (!(component instanceof WurmInputField)) return component;
        WeakReference<ChamomiloUiV1TextField> reference = INPUTS.get(component);
        ChamomiloUiV1TextField wrapper = reference == null ? null : reference.get();
        if (wrapper == null) throw new IllegalStateException("Unwrapped text field");
        wrapper.resize(Math.max(48, component.width), Math.max(32, component.height));
        wrapper.sizeFlags = component.sizeFlags;
        wrapper.setPlaceholder(((WurmInputField) component).prompt);
        ((WurmInputField) component).prompt = "";
        return wrapper;
    }
    static FlexComponent compactView(WurmInputField input, int width, int height) {
        WurmBorderPanel wrapper = (WurmBorderPanel)view(input);
        input.text = WaypointerFonts.compact(false);
        wrapper.text = input.text;
        input.setLineHeight(16);
        input.setMaxLines(1);
        wrapper.setComponent(null, WurmBorderPanel.NORTH);
        wrapper.setComponent(null, WurmBorderPanel.SOUTH);
        wrapper.setInitialSize(width, height, false);
        return wrapper;
    }
}
