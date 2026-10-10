package com.wurmonline.client.renderer.gui;

/** Keeps table pitch stable while leaving real, non-clickable space between actions. */
final class WaypointerTableActionCell extends WurmArrayPanel<FlexComponent> {
    static final int ROW_HEIGHT = 32;
    static final int BUTTON_HEIGHT = 28;
    static final int INSET = (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
    final ChamomiloUiV1Button button;

    WaypointerTableActionCell(WButton button) {
        super("waypointer.table-action", DIR_VERTICAL);
        this.button = (ChamomiloUiV1Button) button;
        // Both native and SDK scrolling translate array descendants together.
        addComponent(padding());
        addComponent(this.button);
        addComponent(padding());
        setInitialSize(button.width, ROW_HEIGHT, false);
        sizeFlags = FIXED_HEIGHT;
        resizeColumn(button.width);
    }

    void resizeColumn(int width) {
        button.resize(width, BUTTON_HEIGHT);
        button.setFixedWidth(true);
        setSize(width, ROW_HEIGHT);
        componentResized();
    }

    @Override void setSize(int width,int height) {
        // Updating a native button caption briefly restores its 32 px size.
        // That intermediate size must not increase the array's table pitch.
        super.setSize(width,ROW_HEIGHT);
    }

    private static FlexComponent padding() {
        return new FlexComponent("waypointer.table-action.gap", 0, 0, 1, INSET) {{
            sizeFlags = FIXED_HEIGHT;
        }};
    }
}
