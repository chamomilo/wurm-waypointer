package com.wurmonline.client.renderer.gui;

import org.waypoints.next.ui.SurroundingsScrollGesture;

import java.util.logging.Logger;

/** Scroll panel tuned for dense, live-updating Surroundings tables. */
final class SurroundingsScrollPanel extends WurmScrollPanel {
    private static final Logger LOGGER = Logger.getLogger(
            "WurmWaypointer.Surroundings");

    interface ScrollListener {
        void userScrolled(int offset, long nowMillis);
    }

    private final int rowHeight;
    private final ScrollListener listener;
    private boolean contentPressed;
    private boolean scrollBarInteraction;
    private boolean scrollBarDragging;
    private boolean scrollBarPressDiagnosticWritten;
    private boolean scrollBarDragDiagnosticWritten;
    private boolean wheelDiagnosticWritten;
    private int dragStartY;
    private int dragStartOffset;

    SurroundingsScrollPanel(String name, FlexComponent content, int rowHeight,
                            ScrollListener listener) {
        super(name, content, false, true);
        if (rowHeight <= 0) throw new IllegalArgumentException(
                "row height must be positive");
        this.rowHeight = rowHeight;
        this.listener = listener;
    }

    /** Called by the absolute-coordinate HUD hook and native event bubbling. */
    boolean scrollWheel(int wheelDelta) {
        if (wheelDelta == 0) return false;
        int previous = yo;
        int requested = SurroundingsScrollGesture.wheelTargetOffset(
                previous, wheelDelta, rowHeight);
        scrollAsUser(requested);
        if (!wheelDiagnosticWritten) {
            wheelDiagnosticWritten = true;
            LOGGER.info("Surroundings wheel received: delta=" + wheelDelta
                    + ", offset=" + previous + "->" + yo
                    + ", requested=" + requested
                    + ", content=" + content.width + "x" + content.height
                    + "@" + content.x + "," + content.y
                    + ", viewport=" + viewport().width + "x"
                    + viewport().height + "@" + viewport().x + ","
                    + viewport().y + ", panel=" + width + "x" + height
                    + firstDescendantGeometry());
        }
        return true;
    }

    /**
     * Re-establish the viewport after the live table replaces its children.
     *
     * <p>The pinned client's decorator temporarily grows with a resized child.
     * Until the surrounding border panels are laid out again, the native
     * scrollbar therefore sees no hidden content and clamps every offset to
     * zero.  A panel layout restores the real clipped viewport before either
     * the wheel or the native scrollbar thumb uses it.</p>
     */
    void contentChanged() {
        int previous = Math.max(0, yo);
        layout();
        applyOffset(previous);
    }

    void restoreOffset(int requestedOffset) {
        applyOffset(Math.max(0, requestedOffset));
    }

    private WurmComponent viewport() {
        return (WurmComponent) (Object) offs;
    }

    private WurmComponent verticalBar() {
        return (WurmComponent) (Object) verticalScrollBar;
    }

    private boolean overVerticalScrollBar(int mouseX, int mouseY) {
        return verticalScrollBar != null
                && verticalBar().contains(mouseX, mouseY);
    }

    @Override protected void mouseWheeled(int mouseX, int mouseY,
                                           int wheelDelta) {
        scrollWheel(wheelDelta);
    }

    /**
     * Keep the native bar's visuals, but own its input.  The pinned client bar
     * delegates a thumb drag through Offsetter#layout; a live WurmArrayPanel
     * immediately feeds its resize back into that layout and restores yo=0.
     */
    @Override public FlexComponent getComponentAt(int mouseX, int mouseY) {
        if (overVerticalScrollBar(mouseX, mouseY)) return this;
        return super.getComponentAt(mouseX, mouseY);
    }

    /** Allow grabbing an ordinary label/cell and dragging the table itself. */
    @Override void leftPressed(int mouseX, int mouseY, int clickCount) {
        if (overVerticalScrollBar(mouseX, mouseY)) {
            contentPressed = false;
            scrollBarInteraction = true;
            pressVerticalScrollBar(mouseY);
            return;
        }
        scrollBarInteraction = false;
        scrollBarDragging = false;
        contentPressed = SurroundingsScrollGesture.startsContentDrag(
                contains(mouseX, mouseY),
                false,
                horizontalScrollBar != null
                        && ((WurmComponent) (Object) horizontalScrollBar)
                        .contains(mouseX, mouseY));
        dragStartY = mouseY;
        dragStartOffset = yo;
        super.leftPressed(mouseX, mouseY, clickCount);
    }

    @Override void mouseDragged(int mouseX, int mouseY) {
        if (scrollBarDragging) {
            dragVerticalScrollBar(mouseY);
            return;
        }
        if (!contentPressed) {
            super.mouseDragged(mouseX, mouseY);
            return;
        }
        scrollAsUser(SurroundingsScrollGesture.dragTargetOffset(
                dragStartOffset, dragStartY, mouseY));
    }

    @Override void leftReleased(int mouseX, int mouseY) {
        boolean handledScrollBar = scrollBarInteraction;
        scrollBarInteraction = false;
        scrollBarDragging = false;
        contentPressed = false;
        if (handledScrollBar) return;
        super.leftReleased(mouseX, mouseY);
    }

    private void pressVerticalScrollBar(int mouseY) {
        WurmComponent bar = verticalBar();
        int hidden = hiddenContent();
        if (!scrollBarPressDiagnosticWritten) {
            scrollBarPressDiagnosticWritten = true;
            LOGGER.info("Surroundings scrollbar received press: offset=" + yo
                    + ", hidden=" + hidden + ", content=" + content.width
                    + "x" + content.height + ", viewport="
                    + viewport().width + "x" + viewport().height
                    + ", bar=" + bar.width + "x" + bar.height);
        }
        if (hidden <= 0) return;
        if (mouseY < bar.y + 12) {
            scrollAsUser(yo - rowHeight);
            return;
        }
        if (mouseY >= bar.y + bar.height - 12) {
            scrollAsUser(yo + rowHeight);
            return;
        }

        int sliderStart = bar.y + 12 + sliderOffset(hidden);
        int sliderEnd = sliderStart + sliderSize(hidden);
        if (mouseY < sliderStart) {
            scrollAsUser(yo - viewport().height);
        } else if (mouseY >= sliderEnd) {
            scrollAsUser(yo + viewport().height);
        } else {
            scrollBarDragging = true;
            dragStartY = mouseY;
            dragStartOffset = yo;
        }
    }

    private void dragVerticalScrollBar(int mouseY) {
        int hidden = hiddenContent();
        int free = freeScrollBarSize(hidden);
        if (hidden <= 0 || free <= 0) return;
        long requested = (long) dragStartOffset
                + (long) (mouseY - dragStartY) * hidden / free;
        int target = requested <= Integer.MIN_VALUE ? Integer.MIN_VALUE
                : requested >= Integer.MAX_VALUE ? Integer.MAX_VALUE
                : (int) requested;
        scrollAsUser(target);
        if (!scrollBarDragDiagnosticWritten) {
            scrollBarDragDiagnosticWritten = true;
            LOGGER.info("Surroundings scrollbar drag applied: mouseDelta="
                    + (mouseY - dragStartY) + ", offset=" + dragStartOffset
                    + "->" + yo + ", hidden=" + hidden + ", free=" + free
                    + ", content@" + content.x + "," + content.y
                    + firstDescendantGeometry());
        }
    }

    private int hiddenContent() {
        return Math.max(0, content.height - viewport().height);
    }

    private int sliderSize(int hidden) {
        int available = Math.max(0, verticalBar().height - 24);
        if (hidden <= 0 || content.height <= 0) return available;
        int proportional = (int) ((long) available * viewport().height
                / content.height);
        return Math.min(available, Math.max(16, proportional));
    }

    private int freeScrollBarSize(int hidden) {
        return Math.max(0, verticalBar().height - 24 - sliderSize(hidden));
    }

    private int sliderOffset(int hidden) {
        return hidden <= 0 ? 0
                : (int) ((long) yo * freeScrollBarSize(hidden) / hidden);
    }

    private void scrollAsUser(int requestedOffset) {
        int previous = yo;
        applyOffset(requestedOffset);
        if (yo != previous && listener != null) {
            listener.userScrolled(Math.max(0, yo),
                    System.currentTimeMillis());
        }
    }

    private void applyOffset(int requestedOffset) {
        WurmComponent viewport = viewport();
        int hidden = hiddenContent();
        int target = Math.max(0, Math.min(requestedOffset, hidden));
        yo = target;
        isAtBottom = target >= hidden;
        translateSubtree(content, viewport.x - xo - content.x,
                viewport.y - target - content.y);
    }

    /**
     * Wurm's widgets store screen coordinates, including every child row and
     * cell. Moving only the root changes the scrollbar state but leaves the
     * visible descendants behind. Using setLocation while detached is also
     * unsafe: the client clamps a root widget to the screen and turns a
     * negative scroll position back into zero. Translate the already-laid-out
     * tree in place instead, without triggering that clamp or layout feedback.
     */
    private static void translateSubtree(FlexComponent component,
                                         int deltaX, int deltaY) {
        if (deltaX == 0 && deltaY == 0) return;
        component.x += deltaX;
        component.y += deltaY;
        if (component instanceof WurmArrayPanel) {
            WurmArrayPanel<?> array = (WurmArrayPanel<?>) component;
            for (FlexComponent child : array.components) {
                translateSubtree(child, deltaX, deltaY);
            }
        } else if (component instanceof WurmDecorator) {
            FlexComponent child = ((WurmDecorator) component).component;
            if (child != null) translateSubtree(child, deltaX, deltaY);
        }
    }

    private String firstDescendantGeometry() {
        if (!(content instanceof WurmArrayPanel)) return "";
        WurmArrayPanel<?> table = (WurmArrayPanel<?>) content;
        if (table.components.isEmpty()) return ", first=<none>";
        FlexComponent first = table.components.get(0);
        String result = ", first@" + first.x + "," + first.y;
        if (first instanceof WurmArrayPanel) {
            WurmArrayPanel<?> row = (WurmArrayPanel<?>) first;
            if (!row.components.isEmpty()) {
                FlexComponent cell = row.components.get(0);
                result += ", firstCell@" + cell.x + "," + cell.y;
            }
        }
        return result;
    }

}
