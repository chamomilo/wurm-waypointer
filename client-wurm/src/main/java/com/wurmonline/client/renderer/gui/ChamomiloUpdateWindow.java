package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.update.ModUpdate;
import org.chamomilo.wurm.update.UpdatePreferences;
import java.io.IOException;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.resources.ChamomiloResourceUrl;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.ResourceTextureLoader;
import com.wurmonline.client.resources.textures.TextureLoader;
import java.awt.Desktop;
import java.net.URI;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Compact native catalogue. Protocol-1 constructor remains binary compatible. */
public final class ChamomiloUpdateWindow extends WWindow implements ButtonListener {
    static final int FRAME_PIXELS = 5;
    private static final int WIDTH = 880;
    private static final int GAP = 6;
    private static final Logger LOG = Logger.getLogger("Chamomilo.UpdateCoordinator");
    private final Consumer<ModUpdate> download;
    private final Runnable dismiss;
    private final Map<WButton, ModUpdate> downloads = new IdentityHashMap<WButton, ModUpdate>();
    private final WButton laterButton;
    private final StartupCheckBox skipNextStart;
    private final UpdatePreferences preferences;
    private int minimumWidth = 680;
    private ResourceTexture frame;
    private boolean frameAttempted;
    private ResourceTexture buttonSkin;
    private boolean buttonSkinAttempted;

    public ChamomiloUpdateWindow(HeadsUpDisplay hud, List<ModUpdate> updates,
                                 String title, String introduction,
                                 String downloadLabel, String downloadTip,
                                 String laterLabel, String laterTip,
                                 Consumer<ModUpdate> download, Runnable dismiss) {
        this(hud, updates, title, introduction, downloadLabel, downloadTip,
                laterLabel, laterTip, download, dismiss, UpdatePreferences.shared());
    }

    public ChamomiloUpdateWindow(HeadsUpDisplay hud, List<ModUpdate> updates,
                                 String title, String introduction,
                                 String downloadLabel, String downloadTip,
                                 String laterLabel, String laterTip,
                                 Consumer<ModUpdate> download, Runnable dismiss,
                                 UpdatePreferences preferences) {
        super("chamomilo.updates", false);
        if (updates == null || updates.isEmpty())
            throw new IllegalArgumentException("At least one mod is required");
        this.download = download;
        this.dismiss = dismiss;
        this.preferences = preferences;
        setTitle("Mods Registry by Chamomilo");
        LOG.info("Opening compact Chamomilo catalogue: " + updates.size() + " mods");

        WurmArrayPanel<FlexComponent> content = new WurmArrayPanel<FlexComponent>(
                "chamomilo.updates.content", WurmArrayPanel.DIR_VERTICAL, true);
        int cardHeight = Math.max(62, (text.getHeight() + 2) * 3 + 10);
        for (ModUpdate update : updates) {
            if (content.hasComponents()) content.addComponent(spacer(1, GAP));
            content.addComponent(new ModCard(update, cardHeight));
        }

        WurmBorderPanel footer = new WurmBorderPanel("chamomilo.updates.footer");
        laterButton = new RegistryButton("Close", 88);
        laterButton.setHoverString(laterTip);
        footer.setComponent(spacer(1, 8), WurmBorderPanel.NORTH);
        skipNextStart = new StartupCheckBox();
        skipNextStart.checked = preferences.isSkipNextStart();
        skipNextStart.setHoverString("Keep checking versions, but open this window only from Main menu > Mod updates.");
        footer.setComponent(skipNextStart, WurmBorderPanel.CENTER);
        footer.setComponent(laterButton, WurmBorderPanel.EAST);
        footer.setSize(1, 8 + Math.max(laterButton.height, skipNextStart.height));
        footer.sizeFlags = FIXED_HEIGHT;

        WurmBorderPanel root = new WurmBorderPanel("chamomilo.updates.root");
        WurmScrollPanel scroll = new WurmScrollPanel("chamomilo.updates.scroll", content, false, true);
        // Keep native clipping and wheel access on short screens, without a visible scrollbar.
        scroll.setComponent(null, EAST);
        root.setComponent(scroll, CENTER);
        root.setComponent(footer, SOUTH);
        WurmBorderPanel padded = new WurmBorderPanel("chamomilo.updates.padding");
        padded.setComponent(spacer(8, 1), WEST);
        padded.setComponent(spacer(8, 1), EAST);
        padded.setComponent(spacer(1, 8), NORTH);
        padded.setComponent(spacer(1, 6), SOUTH);
        padded.setComponent(root, CENTER);
        setComponent(padded);

        int screenWidth = Math.max(240, hud.getWidth() - 20);
        minimumWidth = Math.min(680, screenWidth);
        int height = initialHeight(updates.size(), cardHeight, footer.height - 8, hud.getHeight());
        setInitialSize(Math.min(WIDTH, screenWidth), height, false);
        setPosition(Math.max(0, (hud.getWidth() - width) / 2),
                Math.max(0, (hud.getHeight() - this.height) / 3));
    }

    static int initialHeight(int count, int cardHeight, int footerHeight, int screenHeight) {
        // Native title/south bars (21 + 16), padding (14), footer gap (8).
        long fullHeight = 59L + footerHeight + (long) count * cardHeight + (count - 1L) * GAP;
        return (int) Math.min(fullHeight, Math.max(120, screenHeight - 20));
    }

    private static FlexComponent spacer(int width, int height) {
        return new FlexComponent("chamomilo.spacer", 0, 0, width, height) {
            { sizeFlags = FIXED_WIDTH | FIXED_HEIGHT; }
        };
    }

    @Override void setSize(int width, int height) {
        super.setSize(minimized ? width : Math.max(minimumWidth, width),
                minimized ? height : Math.max(120, height));
    }

    @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
        // Preserve native drag, resize and close hit regions while replacing their artwork.
        // Match native labels/buttons with constant opacity during HUD transitions.
        fillRect(queue, .070f, .062f, .049f, 1f, x, y, width, height);
        if (!minimized) getComponent().render(queue, 1f);
        loadFrame();
        // Opaque rail backing keeps the border exactly 5 px despite antialiased PNG edges.
        fillRect(queue, .24f, .19f, .12f, 1f, x, y, width, FRAME_PIXELS);
        fillRect(queue, .24f, .19f, .12f, 1f, x, y + height - FRAME_PIXELS, width, FRAME_PIXELS);
        fillRect(queue, .24f, .19f, .12f, 1f, x, y, FRAME_PIXELS, height);
        fillRect(queue, .24f, .19f, .12f, 1f, x + width - FRAME_PIXELS, y, FRAME_PIXELS, height);
        if (frame != null) {
            int border = FRAME_PIXELS;
            float[] uv = {0f, .08f, .92f, 1f};
            int[] xs = {x, x + border, x + width - border, x + width};
            int[] ys = {y, y + border, y + height - border, y + height};
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 1) continue;
                Renderer.texturedQuadAlphaBlend(queue, frame, 1f, 1f, 1f, 1f,
                        xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                        uv[col], uv[row], uv[col + 1], uv[row + 1]);
            }
        }
        text.moveTo(x + 14, y + 14);
        text.paint(queue, getTitle(), .97f, .92f, .78f, 1f);
        text.moveTo(x + width - 13, y + 13);
        text.paint(queue, "X", isCloseHovered ? 1f : .85f, .85f, .72f, 1f);
    }

    private void loadFrame() {
        if (frameAttempted) return;
        frameAttempted = true;
        try {
            frame = ResourceTextureLoader.getInternalTexture(new ChamomiloResourceUrl(
                    "/org/chamomilo/wurm/update/update-frame.png"),
                    TextureLoader.Filter.LINEAR, false, false, false);
        } catch (Exception failure) {
            LOG.log(Level.WARNING, "Cannot load updater frame; using plain 5 px frame", failure);
        }
    }

    private void loadButtonSkin() {
        if (buttonSkinAttempted) return;
        buttonSkinAttempted = true;
        try {
            buttonSkin = ResourceTextureLoader.getInternalTexture(new ChamomiloResourceUrl(
                    "/org/chamomilo/wurm/update/update-button.png"),
                    TextureLoader.Filter.LINEAR, false, false, false);
        } catch (Exception failure) {
            LOG.log(Level.WARNING, "Cannot load registry button skin; using dark button face", failure);
        }
    }

    private final class RegistryButton extends WButton {
        RegistryButton(String label, int desiredWidth) {
            super(label, ChamomiloUpdateWindow.this);
            int desiredHeight = height + 1;
            sizeFlags = 0;
            setSize(Math.max(desiredWidth, text.getWidth(label) + 24), desiredHeight);
            sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
        }

        @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
            loadButtonSkin();
            boolean enabled = isEnabled();
            float tint = !enabled ? .43f : isDown ? .65f : hovered ? 1.15f : .92f;
            fillRect(queue, .10f * tint, .085f * tint, .061f * tint, 1f, x, y, width, height);
            if (buttonSkin != null) {
                // Preserve the generated alpha and sample the face without its transparent canvas padding.
                float[] us = {14f / 2172f, 154f / 2172f, 2019f / 2172f, 2159f / 2172f};
                float[] vs = {96f / 724f, 196f / 724f, 524f / 724f, 624f / 724f};
                int[] xs = {x, x + 5, x + width - 5, x + width};
                int[] ys = {y, y + 4, y + height - 4, y + height};
                for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                    // Reuse the upper iron rail upside-down so both edges have the same detail and visibility.
                    float v0 = row == 2 ? vs[1] : vs[row];
                    float v1 = row == 2 ? vs[0] : vs[row + 1];
                    Renderer.texturedQuadAlphaBlend(queue, buttonSkin, tint, tint, tint, 1f,
                            xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                            us[col], v0, us[col + 1], v1);
                }
            } else {
                fillRect(queue, .34f * tint, .29f * tint, .20f * tint, 1f, x, y, width, 1);
                fillRect(queue, .34f * tint, .29f * tint, .20f * tint, 1f, x, y + height - 1, width, 1);
            }
            int offset = enabled && isDown ? 1 : 0;
            text.moveTo(x + (width - text.getWidth(label)) / 2 + offset,
                    y + (height - text.getHeight() - 1) / 2 + text.getAscent() - 1 + offset);
            text.paint(queue, label, enabled ? .96f : .49f, enabled ? .91f : .48f,
                    enabled ? .77f : .43f, 1f);
        }
    }

    /** One hit area and one paint baseline; native checkbox painting uses a stretched height as its label offset. */
    private final class StartupCheckBox extends WButton {
        boolean checked;
        StartupCheckBox() {
            super("Don't show on next start", ChamomiloUpdateWindow.this);
            int desiredHeight = height + 1;
            sizeFlags = 0;
            setSize(text.getWidth(label) + 28, desiredHeight);
            sizeFlags = FIXED_HEIGHT;
        }

        @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
            int box = 14;
            int top = y + (height - box) / 2;
            float edge = hovered ? .68f : .44f;
            fillRect(queue, edge, edge * .89f, edge * .65f, 1f, x, top, box, box);
            fillRect(queue, .08f, .073f, .056f, 1f, x + 1, top + 1, box - 2, box - 2);
            if (checked) {
                for (int i = 0; i < 4; i++) fillRect(queue, .95f, .90f, .74f, 1f, x + 3 + i, top + 6 + i, 2, 2);
                for (int i = 0; i < 6; i++) fillRect(queue, .95f, .90f, .74f, 1f, x + 6 + i, top + 9 - i, 2, 2);
            }
            text.moveTo(x + 22, y + (height - text.getHeight() - 1) / 2 + text.getAscent() - 1);
            text.paint(queue, label, .87f, .85f, .75f, 1f);
        }
    }

    private final class ModCard extends WurmBorderPanel {
        ModCard(ModUpdate update, int cardHeight) {
            super("chamomilo.card." + update.getId());
            WButton button = new RegistryButton(update.getActionLabel(), 108);
            int actionWidth = Math.max(108, button.width);
            button.setSize(actionWidth, button.height);
            button.setEnabled(update.canDownload());
            button.setHoverString(update.canDownload() ? "Open download: " + update.getDownloadUrl()
                    : update.isLatest() ? "The installed version is current or newer."
                    : "The installed version could not be compared. Visit the project page.");
            if (update.canDownload()) downloads.put(button, update);

            WurmBorderPanel action = new WurmBorderPanel("chamomilo.action." + update.getId());
            int top = Math.max(0, (cardHeight - button.height) / 2);
            action.setComponent(spacer(1, top), NORTH);
            action.setComponent(spacer(1, cardHeight - top - button.height), SOUTH);
            action.setComponent(button, CENTER);
            action.setSize(actionWidth, cardHeight);
            setComponent(spacer(10, 1), WEST);
            WurmBorderPanel actionPadding = new WurmBorderPanel("chamomilo.action.padding");
            actionPadding.setComponent(action, CENTER);
            actionPadding.setComponent(spacer(10, 1), EAST);
            actionPadding.setComponent(spacer(10, 1), WEST);
            actionPadding.setSize(actionWidth + 20, cardHeight);
            setComponent(actionPadding, EAST);
            setComponent(new ModDetails(update, cardHeight), CENTER);
            setSize(200, cardHeight);
            sizeFlags = FIXED_HEIGHT;
        }

        @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
            fillRect(queue, .26f, .23f, .17f, 1f, x, y, width, height);
            fillRect(queue, .105f, .097f, .079f, 1f, x + 1, y + 1, width - 2, height - 2);
            super.renderComponent(queue, 1f);
        }
    }

    private static final class ModDetails extends FlexComponent {
        private final ModUpdate update;
        private final int lineHeight;
        private boolean linkHovered;
        private boolean linkPressed;

        ModDetails(ModUpdate update, int height) {
            super("chamomilo.details." + update.getId(), 0, 0, 1, height);
            this.update = update;
            lineHeight = text.getHeight() + 2;
            sizeFlags = FIXED_HEIGHT;
        }

        @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
            int baseline = y + 5 + text.getAscent();
            String installed = update.getInstalledText();
            int installedWidth = Math.min(text.getWidth(installed), width / 2);
            textBold.moveTo(x, baseline);
            textBold.paint(queue, fit(textBold, update.getDisplayName(), width - installedWidth - 20),
                    .96f, .91f, .76f, 1f);
            text.moveTo(x + width - installedWidth, baseline);
            text.paint(queue, fit(text, installed, installedWidth), .74f, .73f, .65f, 1f);
            text.moveTo(x, baseline + lineHeight);
            text.paint(queue, fit(text, update.getDescription(), width), .79f, .78f, .70f, 1f);

            String link = linkText();
            int linkWidth = Math.min(text.getWidth(link), width / 2);
            text.moveTo(x, baseline + lineHeight * 2);
            text.paint(queue, fit(text, link, linkWidth), linkHovered ? .82f : .59f, .76f, .90f, 1f);
            fillRect(queue, .59f, .76f, .90f, 1f, x, baseline + lineHeight * 2 + 2, linkWidth, 1);
            text.moveTo(x + linkWidth + 14, baseline + lineHeight * 2);
            text.paint(queue, fit(text, update.getReleaseText(), width - linkWidth - 14),
                    update.isUpdateAvailable() ? .95f : .73f, .76f, .47f, 1f);
        }

        private String linkText() { return update.getProjectUrl().replace("https://", ""); }
        private boolean overLink(int mx, int my) {
            int top = y + 5 + lineHeight * 2;
            return !update.getProjectUrl().isEmpty() && mx >= x
                    && mx < x + Math.min(text.getWidth(linkText()), width / 2)
                    && my >= top && my < top + text.getHeight() + 3;
        }
        @Override protected int getMouseCursor(int mx, int my) {
            return overLink(mx, my) ? MOUSE_CURSOR_HAND : MOUSE_CURSOR_NORMAL;
        }
        @Override protected void mouseMoved(int mx, int my) { linkHovered = overLink(mx, my); }
        @Override protected void mouseExited() { linkHovered = false; linkPressed = false; }
        @Override protected void leftPressed(int mx, int my, int modifiers) { linkPressed = overLink(mx, my); }
        @Override protected void leftReleased(int mx, int my) {
            boolean open = linkPressed && overLink(mx, my);
            linkPressed = false;
            if (!open) return;
            try {
                Desktop.getDesktop().browse(URI.create(update.getProjectUrl()));
            } catch (Exception failure) {
                LOG.log(Level.WARNING, "Could not open project: " + update.getProjectUrl(), failure);
                if (hud != null) hud.textMessage(":Event", 180, 195, 210,
                        "[Chamomilo] Project page: " + update.getProjectUrl());
            }
        }
    }

    private static String fit(com.wurmonline.client.renderer.gui.text.TextFont font,
                              String value, int available) {
        if (available <= 0) return "";
        if (font.getWidth(value) <= available) return value;
        int end = value.length();
        while (end > 0 && font.getWidth(value.substring(0, end) + "...") > available) end--;
        return end == 0 ? "" : value.substring(0, end) + "...";
    }

    @Override public void buttonPressed(WButton button) { }
    @Override public void gameTick() {
        super.gameTick();
        saveStartupPreference();
    }

    private void saveStartupPreference() {
        if (skipNextStart.checked == preferences.isSkipNextStart()) return;
        try {
            preferences.setSkipNextStart(skipNextStart.checked);
        } catch (IOException failure) {
            skipNextStart.checked = preferences.isSkipNextStart();
            LOG.log(Level.WARNING, "Could not save mod update startup preference", failure);
            if (hud != null) hud.textMessage(":Event", 255, 220, 160,
                    "[Chamomilo] Could not save the mod updates preference. See the mod log.");
        }
    }

    @Override public void buttonClicked(WButton button) {
        if (button == skipNextStart) {
            skipNextStart.checked = !skipNextStart.checked;
            saveStartupPreference();
            return;
        }
        ModUpdate update = downloads.get(button);
        if (update != null && button.isEnabled()) download.accept(update);
        if (button == laterButton) closePressed();
    }
    @Override protected void closePressed() { saveStartupPreference(); dismiss.run(); }
}
