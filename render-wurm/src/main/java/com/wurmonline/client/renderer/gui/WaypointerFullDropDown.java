package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.chamomilo.wurm.ui.v1.*;
import com.wurmonline.client.renderer.gui.text.WaypointerFonts;

/** Chamomilo field with a complete option grid and Wurm's popup lifecycle. */
final class WaypointerFullDropDown extends WurmDropDown {
    private final String[] labels;
    private final IntConsumer changed;
    private final ChamomiloUiV1DropDown field;

    WaypointerFullDropDown(String id, int width, String[] labels, IntConsumer changed) {
        super(id, 0, Objects.requireNonNull(labels, "labels").clone());
        this.labels = labels.clone();
        this.changed = Objects.requireNonNull(changed, "changed");
        field = new ChamomiloUiV1DropDown(id + ".field", width, labels, value -> { });
        text=field.text=WaypointerFonts.body();
        textBold=field.textBold=WaypointerFonts.body(true);
        field.parent = this;
        sizeFlags = 0;
        setSize(Math.max(64, width), 32);
        sizeFlags = FIXED_HEIGHT;
    }

    @Override void componentResized() {
        super.componentResized();
        if (field != null) field.setLocation(x, y, width, height);
    }

    @Override void setValue(int index) {
        int previous = getValue();
        super.setValue(index);
        if (field != null) field.selectIndex(index);
        if (changed != null && previous != index) changed.accept(index);
    }

    @Override void mouseMoved(int mx, int my) { field.mouseMoved(mx, my); }
    @Override void mouseExited() { field.mouseExited(); }
    @Override protected void renderComponent(Queue queue, float ignoredAlpha) { field.render(queue, 1f); }

    @Override protected void leftPressed(int mx, int my, int clicks) {
        if (hud == null || labels.length == 0 || !contains(mx, my)) return;
        int rowHeight = Math.max(32, text.getHeight() + 8);
        int maxRows = Math.max(1, (hud.getHeight() - 20 - 6) / rowHeight);
        int columns = (labels.length + maxRows - 1) / maxRows;
        int rows = (labels.length + columns - 1) / columns;
        int desiredWidth = width;
        for (String label : labels) desiredWidth = Math.max(desiredWidth, text.getWidth(label) + 16);
        int columnWidth = Math.min(desiredWidth, Math.max(64, (hud.getWidth() - 20 - 6) / columns));
        int popupWidth = columns * columnWidth + 6;
        int popupHeight = rows * rowHeight + 6;
        ChamomiloUiV1Panel body = new ChamomiloUiV1Panel(name + ".popup", UiBackground.LEATHER, 3, 0);
        body.put(new Options(columnWidth, rowHeight, rows, columns), ChamomiloUiV1Panel.Region.CENTER);
        int left = Math.max(10, Math.min(x, hud.getWidth() - 10 - popupWidth));
        int top = y + height;
        if (top + popupHeight > hud.getHeight() - 10) top = y - popupHeight;
        top = Math.max(10, Math.min(top, hud.getHeight() - 10 - popupHeight));
        WurmDropdownPopup popup = new WurmDropdownPopup(this, left, top, popupWidth, new String[0], 0);
        popup.component = body;
        body.parent = popup;
        popup.setSize(popupWidth, popupHeight);
        popup.layout();
        hud.showDropdownPopupComponent(popup);
    }

    private final class Options extends FlexComponent {
        private final int columnWidth, rowHeight, rows;
        private int hover = -1, pressed = -1;
        private final ChamomiloUiV1Canvas canvas = new ChamomiloUiV1Canvas(this);

        Options(int columnWidth, int rowHeight, int rows, int columns) {
            super(WaypointerFullDropDown.this.name + ".options", 0, 0, columnWidth * columns, rowHeight * rows);
            this.columnWidth = columnWidth; this.rowHeight = rowHeight; this.rows = rows;
            text=WaypointerFonts.body();textBold=WaypointerFonts.body(true);
        }
        private int index(int mx, int my) {
            if (!contains(mx, my)) return -1;
            int index = (mx - x) / columnWidth * rows + (my - y) / rowHeight;
            return index < labels.length ? index : -1;
        }
        @Override void mouseMoved(int mx, int my) { hover = index(mx, my); }
        @Override void mouseExited() { hover = -1; }
        @Override protected void leftPressed(int mx, int my, int clicks) { pressed = index(mx, my); }
        @Override protected void leftReleased(int mx, int my) {
            if (pressed >= 0 && pressed == index(mx, my)) {
                setValue(pressed);
                hud.clearAllPopups();
            }
            pressed = -1;
        }
        @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
            canvas.begin(queue);
            for (int index = 0; index < labels.length; index++) {
                int left = x + index / rows * columnWidth, top = y + index % rows * rowHeight;
                if (index == hover || index == getValue())
                    canvas.fill(UiColor.TEXT, index == hover ? .14f : .08f, left, top, columnWidth, rowHeight);
                String caption = labels[index];
                while (text.getWidth(caption) > columnWidth - 16 && !caption.isEmpty()) {
                    int end = caption.endsWith("…") ? caption.length() - 1 : caption.length();
                    if (end == 0) break;
                    caption = caption.substring(0, caption.offsetByCodePoints(end, -1)) + "…";
                }
                text.moveTo(left + 8, top + (rowHeight - text.getHeight()) / 2 + text.getAscent());
                text.paint(queue, caption, UiColor.TEXT.red, UiColor.TEXT.green, UiColor.TEXT.blue, 1f);
            }
        }
    }
}
