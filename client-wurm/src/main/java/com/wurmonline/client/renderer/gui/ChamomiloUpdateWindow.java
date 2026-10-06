package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.update.ModUpdate;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.ChamomiloResourceUrl;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.ResourceTextureLoader;
import com.wurmonline.client.resources.textures.TextureLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** One Wurm-native catalogue, shown even when all mods are current or checks fail. */
public final class ChamomiloUpdateWindow extends WWindow implements ButtonListener {
    private static final int WIDTH = 900;
    private final Consumer<ModUpdate> download;
    private final Runnable dismiss;
    private final Map<WButton, ModUpdate> downloads =
            new IdentityHashMap<WButton, ModUpdate>();
    private final WButton laterButton;
    private ResourceTexture frame;
    private boolean frameAttempted;

    public ChamomiloUpdateWindow(HeadsUpDisplay hud, List<ModUpdate> updates,
                                 String title, String introduction,
                                 String downloadLabel, String downloadTip,
                                 String laterLabel, String laterTip,
                                 Consumer<ModUpdate> download, Runnable dismiss) {
        super("chamomilo.updates", false);
        if (updates == null || updates.isEmpty())
            throw new IllegalArgumentException("At least one update is required");
        this.download = download;
        this.dismiss = dismiss;
        Logger.getLogger("Chamomilo.UpdateCoordinator").info("Opening Chamomilo versions window: " + updates.size() + " mods");
        setTitle("Chamomilo mods — versions");

        WurmArrayPanel<FlexComponent> content = new WurmArrayPanel<FlexComponent>(
                "chamomilo.updates.content", WurmArrayPanel.DIR_VERTICAL, true);
        content.componentWidthOffset = 4;
        content.addComponent(new WurmLabel("Check runs at every launch. UPDATE / INSTALL opens a ZIP for manual installation."));
        for (ModUpdate update : updates) content.addComponent(row(
                update, downloadLabel, downloadTip));

        WurmBorderPanel root = new WurmBorderPanel("chamomilo.updates.root");
        root.setComponent(new WurmScrollPanel(
                "chamomilo.updates.scroll", content, true, true),
                WurmBorderPanel.CENTER);
        laterButton = new WButton("Close", this);
        laterButton.setHoverString(laterTip);
        root.setComponent(laterButton, WurmBorderPanel.SOUTH);
        WurmBorderPanel padded = new WurmBorderPanel("chamomilo.updates.padding");
        padded.setComponent(spacer(14, 1), WurmBorderPanel.WEST);
        padded.setComponent(spacer(14, 1), WurmBorderPanel.EAST);
        padded.setComponent(spacer(1, 8), WurmBorderPanel.NORTH);
        padded.setComponent(spacer(1, 12), WurmBorderPanel.SOUTH);
        padded.setComponent(root, WurmBorderPanel.CENTER);
        setComponent(padded);

        int height = Math.min(530, 105 + updates.size() * 60);
        setInitialSize(WIDTH, Math.max(140, height), false);
        setPosition(Math.max(10, (hud.getWidth() - width) / 2),
                Math.max(10, (hud.getHeight() - height) / 3));
    }

    private static FlexComponent spacer(int width, int height) {
        WurmLabel label = new WurmLabel(""); label.setSize(width, height); return label;
    }

    @Override void setSize(int width, int height) {
        super.setSize(minimized ? width : Math.max(720, width),
                minimized ? height : Math.max(200, height));
    }

    @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
        // The native components keep drag, resize, close, focus and scroll handling.
        // Paint at a stable opacity, matching Wurm labels and buttons during HUD fading.
        super.renderComponent(queue, 1.0f);
        if (!frameAttempted) {
            frameAttempted = true;
            try {
                frame = ResourceTextureLoader.getInternalTexture(new ChamomiloResourceUrl(
                        "/org/chamomilo/wurm/update/update-frame.png"),
                        TextureLoader.Filter.LINEAR, false, false, false);
            } catch (Exception failure) {
                Logger.getLogger("Chamomilo.UpdateCoordinator").log(Level.WARNING,
                        "Cannot load update window frame; retaining native frame", failure);
            }
        }
        if (frame == null) return;
        fillRect(queue, .055f, .043f, .032f, 1f, x, y, width, Math.min(21, height));
        int border = Math.min(16, height / 2);
        // Source coordinates remain normalized if Wurm scales the NPOT artwork for upload.
        float tw = 1528f, th = 1029f;
        float[] u = {20 / tw, 96 / tw, (tw - 96) / tw, (tw - 19) / tw};
        float[] v = {24 / th, 96 / th, (th - 96) / th, (th - 22) / th};
        int[] xs = {x, x + border, x + width - border, x + width};
        int[] ys = {y, y + border, y + height - border, y + height};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            if (row == 1 && col == 1) continue;
            Renderer.texturedQuadAlphaBlend(queue, frame, 1f, 1f, 1f, 1f,
                    xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                    u[col], v[row], u[col + 1] - u[col], v[row + 1] - v[row]);
        }
        text.moveTo(x + 24, y + 14);
        text.paint(queue, getTitle(), .97f, .92f, .78f, 1f);
        // Native WWindow closes within this exact 16 px corner hit area.
        text.moveTo(x + width - 13, y + 13);
        text.paint(queue, "X", isCloseHovered ? 1f : .85f, .85f, .72f, 1f);
    }

    private FlexComponent row(ModUpdate update, String downloadLabel,
                              String downloadTip) {
        WurmArrayPanel<FlexComponent> row = new WurmArrayPanel<FlexComponent>(
                "chamomilo.updates.row", WurmArrayPanel.DIR_HORIZONTAL);
        row.componentWidthOffset = 8;
        WurmArrayPanel<FlexComponent> details = new WurmArrayPanel<FlexComponent>(
                "chamomilo.updates.details", WurmArrayPanel.DIR_VERTICAL, true);
        details.addComponent(new WurmLabel(update.getDisplayName()));
        details.addComponent(new WurmLabel(update.getStatusText()));
        row.addComponent(details);
        String action = update.getActionLabel();
        if (action.isEmpty()) return row;
        WButton button = new WButton(action, this);
        button.setHoverString("Open download: " + update.getDownloadUrl());
        button.setSize(125, button.height);
        row.addComponent(button);
        downloads.put(button, update);
        return row;
    }

    @Override public void buttonPressed(WButton button) { }

    @Override public void buttonClicked(WButton button) {
        ModUpdate update = downloads.get(button);
        if (update != null) download.accept(update);
        if (button == laterButton) dismiss.run();
    }

    @Override protected void closePressed() { dismiss.run(); }
}
