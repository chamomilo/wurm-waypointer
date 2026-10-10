package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.ui.v1.UiAxis;
import org.waypoints.next.ui.SurroundingsScrollGesture;

/** Vertical-only native clipping, kit scrollbar, and stable live-table offsets. */
final class SurroundingsScrollPanel extends WurmScrollPanel {
    interface ScrollListener { void userScrolled(int offset, long nowMillis); }
    private final int rowHeight;
    private final ScrollListener listener;
    private final ChamomiloUiV1ScrollBar bar;
    private boolean synchronizing, contentPressed;
    private boolean wholeRowOffsets;
    private int dragStartY, dragStartOffset;

    SurroundingsScrollPanel(String name, FlexComponent content, int rowHeight,
                            ScrollListener listener) {
        super(name, content, false, true);
        if (rowHeight <= 0) throw new IllegalArgumentException("row height must be positive");
        this.rowHeight = rowHeight;
        this.listener = listener;
        bar = new ChamomiloUiV1ScrollBar(name + ".vertical", UiAxis.VERTICAL, 180,
                offset -> { if (!synchronizing) scrollAsUser(offset); });
        bar.setStep(rowHeight);
        setComponent(bar, EAST);
    }

    boolean scrollWheel(int wheelDelta) {
        if (wheelDelta == 0) return false;
        scrollAsUser(SurroundingsScrollGesture.wheelTargetOffset(yo, wheelDelta, rowHeight));
        return true;
    }

    void contentChanged() {
        int previous = Math.max(0, yo);
        layout();
        applyOffset(previous);
    }
    void restoreOffset(int requested) { applyOffset(Math.max(0, requested)); }
    void useWholeRowOffsets() { wholeRowOffsets = true;applyOffset(yo); }
    private WurmComponent viewport() { return (WurmComponent) (Object) offs; }

    @Override protected void mouseWheeled(int mx, int my, int delta) { scrollWheel(delta); }
    @Override void leftPressed(int mx, int my, int count) {
        contentPressed = contains(mx, my) && !bar.contains(mx, my);
        dragStartY = my;dragStartOffset = yo;
        super.leftPressed(mx, my, count);
    }
    @Override void mouseDragged(int mx, int my) {
        if (contentPressed) scrollAsUser(SurroundingsScrollGesture.dragTargetOffset(dragStartOffset, dragStartY, my));
        else super.mouseDragged(mx, my);
    }
    @Override void leftReleased(int mx, int my) {
        contentPressed = false;super.leftReleased(mx, my);
    }
    @Override public void gameTick() { super.gameTick();synchronizeBar(); }

    private void synchronizeBar() {
        if (bar == null) return;
        synchronizing = true;
        try { bar.setRange(content.height, viewport().height);bar.setValue(yo); }
        finally { synchronizing = false; }
    }
    private void scrollAsUser(int requested) {
        int previous = yo;applyOffset(requested);
        if (yo != previous && listener != null) listener.userScrolled(yo, System.currentTimeMillis());
    }
    private void applyOffset(int requested) {
        WurmComponent viewport = viewport();
        int hidden = Math.max(0, content.height - viewport.height);
        yo = Math.max(0, Math.min(requested, hidden));xo = 0;
        if (wholeRowOffsets) yo -= yo % rowHeight;
        isAtBottom = yo >= hidden;
        translateSubtree(content, viewport.x - content.x, viewport.y - yo - content.y);
        synchronizeBar();
    }
    // Native widgets keep absolute screen coordinates. Translate descendants
    // directly: setLocation would clamp negative scroll positions to the screen.
    private static void translateSubtree(FlexComponent component, int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        component.x += dx;component.y += dy;
        if (component instanceof WurmArrayPanel) {
            for (FlexComponent child : ((WurmArrayPanel<?>) component).components)
                translateSubtree(child, dx, dy);
        } else if (component instanceof WurmDecorator) {
            FlexComponent child = ((WurmDecorator) component).component;
            if (child != null) translateSubtree(child, dx, dy);
        }
    }
}
