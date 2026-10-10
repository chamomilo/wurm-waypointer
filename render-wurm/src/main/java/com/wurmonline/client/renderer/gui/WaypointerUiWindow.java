package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.chamomilo.wurm.ui.v1.UiBackground;
import org.waypoints.next.i18n.Messages;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.WaypointerFonts;

/** Shared standard chrome while existing bridges retain window lifecycle. */
public class WaypointerUiWindow extends ChamomiloUiV1Window {
    private boolean ready;
    private boolean normalizing;
    private HeadsUpDisplay attachedHud;
    private FlexComponent titleHeader;
    public WaypointerUiWindow(String id, boolean resize) {
        super(id, "", Messages.language(), UiBackground.SOLID, 5);
        ready = true;
        resizable = resize;
        text=WaypointerFonts.body();textBold=WaypointerFonts.body(true);
        setTitleFont(WaypointerFonts.title());
    }
    @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
        // Match Keybinder: HUD focus/fade alpha must not pulse an open custom window.
        super.renderComponent(queue, 1f);
    }
    @Override void setComponent(FlexComponent content) {
        if (ready) setContent(content); else super.setComponent(content);
    }
    @Override void setComponent(FlexComponent content, int region) {
        super.setComponent(content, region);
        if (region == NORTH) titleHeader = content;
    }
    void setTitleFont(TextFont font) {
        if (titleHeader != null) titleHeader.text = font;
    }
    @Override void componentResized() {
        super.componentResized();
        // Native setLocation/position restoration bypass virtual setSize.
        if (ready && !normalizing) {
            normalizing = true;
            try { setSize(width, height); } finally { normalizing = false; }
        }
    }
    @Override public void toggleMaximized() {
        if (hud != null) show(hud);
        super.toggleMaximized();
    }
    @Override public void show(HeadsUpDisplay target) {
        attachedHud = target;
        super.show(target);
    }
    void dispose() {
        HeadsUpDisplay target = attachedHud == null ? hud : attachedHud;
        if (target != null) { target.hideComponent(this); target.removeComponent(this); }
        attachedHud = null;
    }
}
