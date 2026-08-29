package com.wurmonline.client.renderer.gui;

import org.waypoints.next.ui.SurroundingsScrollGesture;

/** Scroll panel tuned for dense, live-updating Surroundings tables. */
final class SurroundingsScrollPanel extends WurmScrollPanel {
    interface ScrollListener {
        void userScrolled(int offset, long nowMillis);
    }

    private final int rowHeight;
    private final ScrollListener listener;
    private boolean contentPressed;
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
        scrollAsUser(SurroundingsScrollGesture.wheelTargetOffset(
                yo, wheelDelta, rowHeight));
        return true;
    }

    @Override protected void mouseWheeled(int mouseX, int mouseY,
                                           int wheelDelta) {
        scrollWheel(wheelDelta);
    }

    /** Allow grabbing an ordinary label/cell and dragging the table itself. */
    @Override void leftPressed(int mouseX, int mouseY, int clickCount) {
        contentPressed = contains(mouseX, mouseY);
        dragStartY = mouseY;
        dragStartOffset = yo;
        super.leftPressed(mouseX, mouseY, clickCount);
    }

    @Override void mouseDragged(int mouseX, int mouseY) {
        if (!contentPressed) {
            super.mouseDragged(mouseX, mouseY);
            return;
        }
        scrollAsUser(SurroundingsScrollGesture.dragTargetOffset(
                dragStartOffset, dragStartY, mouseY));
    }

    @Override void leftReleased(int mouseX, int mouseY) {
        contentPressed = false;
        super.leftReleased(mouseX, mouseY);
    }

    private void scrollAsUser(int requestedOffset) {
        int previous = yo;
        scrollDownTo(requestedOffset);
        if (yo != previous && listener != null) {
            listener.userScrolled(Math.max(0, yo),
                    System.currentTimeMillis());
        }
    }

}
