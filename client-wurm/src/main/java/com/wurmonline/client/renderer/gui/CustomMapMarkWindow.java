package com.wurmonline.client.renderer.gui;

import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.model.WaypointLayer;

/** Small native editor opened by right-clicking a map tile. */
final class CustomMapMarkWindow extends WWindow
        implements InputFieldListener, ButtonListener {
    private static final int ROW_WIDTH = 350;
    private static final int ROW_HEIGHT = 23;
    private final int tileX;
    private final int tileY;
    private final WaypointLayer layer;
    private WurmInputField textInput;
    private WButton saveButton;
    private WButton cancelButton;

    CustomMapMarkWindow(int tileX, int tileY) {
        this(tileX, tileY, WaypointLayer.SURFACE);
    }

    CustomMapMarkWindow(int tileX, int tileY, WaypointLayer layer) {
        super("wurm-waypointer.custom-map-mark", true);
        this.tileX = tileX;
        this.tileY = tileY;
        this.layer = layer;
        setTitle("Custom map mark");
        WurmArrayPanel<FlexComponent> content =
                new WurmArrayPanel<FlexComponent>(
                        "waypointer.custom-map-mark.content", 0, true);
        WurmLabel coordinates = new WurmLabel(
                "Coordinates: X=" + tileX + ", Y=" + tileY + " | " + layer.name());
        coordinates.setInitialSize(ROW_WIDTH, ROW_HEIGHT, false);
        content.addComponent(coordinates);
        textInput = new WurmInputField(
                "waypointer.custom-map-mark.text", this, 1, 120);
        textInput.prompt = "Text shown next to the mark";
        textInput.simpleInput = true;
        textInput.setInitialSize(ROW_WIDTH, ROW_HEIGHT, false);
        content.addComponent(textInput);
        saveButton = new WButton("Save custom mark", this);
        saveButton.setInitialSize(ROW_WIDTH, ROW_HEIGHT, false);
        saveButton.setHoverString("Save a permanent labelled mark on this tile.");
        content.addComponent(saveButton);
        cancelButton = new WButton("Cancel", this);
        cancelButton.setInitialSize(ROW_WIDTH, ROW_HEIGHT, false);
        content.addComponent(cancelButton);
        setComponent(content);
    }

    void focusInput() {
        if (hud == null) return;
        try {
            hud.stopTyping();
            hud.setActiveWindow(this);
            hud.startTyping();
        } catch (Throwable ignored) { }
    }

    void prepareDetach() {
        if (hud != null) try { hud.stopTyping(); }
        catch (Throwable ignored) { }
    }

    private void save() {
        String text = textInput == null ? "" : textInput.getText();
        if (text == null || text.trim().isEmpty()) return;
        WurmWaypointerRuntime.serverMapCustomMarkSaved(
                tileX, tileY, text.trim(), layer);
        CustomMapMarkWindowBridge.closed(this);
    }

    @Override public void handleInput(String input) { save(); }

    @Override public void handleInputChanged(WurmInputField field,
                                             String input) { }

    @Override public void handleEscape(WurmInputField field) {
        CustomMapMarkWindowBridge.closed(this);
    }

    @Override public void buttonPressed(WButton button) { }

    @Override public void buttonClicked(WButton button) {
        if (button == saveButton) save();
        else if (button == cancelButton) CustomMapMarkWindowBridge.closed(this);
    }

    @Override boolean hasInputField() { return textInput != null; }

    @Override WurmInputField getInputField() { return textInput; }

    @Override void closePressed() { CustomMapMarkWindowBridge.closed(this); }
}
