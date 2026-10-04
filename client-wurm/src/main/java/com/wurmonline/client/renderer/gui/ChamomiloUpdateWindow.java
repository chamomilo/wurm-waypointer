package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.update.ModUpdate;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** One Wurm-native window containing every available Chamomilo mod update. */
public final class ChamomiloUpdateWindow extends WWindow implements ButtonListener {
    private static final int WIDTH = 900;
    private final Consumer<ModUpdate> download;
    private final Runnable dismiss;
    private final Map<WButton, ModUpdate> downloads =
            new IdentityHashMap<WButton, ModUpdate>();
    private final WButton laterButton;

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
        setTitle(title);

        WurmArrayPanel<FlexComponent> content = new WurmArrayPanel<FlexComponent>(
                "chamomilo.updates.content", WurmArrayPanel.DIR_VERTICAL, true);
        content.componentWidthOffset = 4;
        content.addComponent(new WurmLabel(introduction));
        for (ModUpdate update : updates) content.addComponent(row(
                update, downloadLabel, downloadTip));

        WurmBorderPanel root = new WurmBorderPanel("chamomilo.updates.root");
        root.setComponent(new WurmScrollPanel(
                "chamomilo.updates.scroll", content, true, true),
                WurmBorderPanel.CENTER);
        laterButton = new WButton(laterLabel, this);
        laterButton.setHoverString(laterTip);
        root.setComponent(laterButton, WurmBorderPanel.SOUTH);
        setComponent(root);

        int height = Math.min(390, 105 + updates.size() * 34);
        setInitialSize(WIDTH, Math.max(140, height), false);
        setPosition(Math.max(10, (hud.getWidth() - width) / 2),
                Math.max(10, (hud.getHeight() - height) / 3));
    }

    private FlexComponent row(ModUpdate update, String downloadLabel,
                              String downloadTip) {
        WurmArrayPanel<FlexComponent> row = new WurmArrayPanel<FlexComponent>(
                "chamomilo.updates.row", WurmArrayPanel.DIR_HORIZONTAL);
        row.componentWidthOffset = 8;
        WurmLabel message = new WurmLabel(update.getNotificationText());
        WButton button = new WButton(downloadLabel, this);
        button.setHoverString(downloadTip.replace("{0}", update.getDownloadUrl()));
        button.setSize(125, button.height);
        row.addComponent(message);
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
