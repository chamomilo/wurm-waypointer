package com.wurmonline.client.renderer.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chamomilo.wurm.update.ModUpdate;
import org.chamomilo.wurm.update.SharedUpdateCoordinator;
import org.chamomilo.wurm.update.SharedUpdateHooks;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

/** Shared UI host; resolves native GUI classes only after all mod hooks are installed. */
public final class ChamomiloUpdateBridge {
    private static final Logger LOG = Logger.getLogger("Chamomilo.UpdateCoordinator");
    private static HeadsUpDisplay readyHud;
    private static boolean shown;
    private static ChamomiloUpdateWindow window;
    private ChamomiloUpdateBridge() { }

    public static void hudReady(HeadsUpDisplay hud) {
        readyHud = hud;
        SharedUpdateCoordinator.startOnce();
        tick(hud);
    }

    public static void tick(final HeadsUpDisplay hud) {
        List<ModUpdate> rows = SharedUpdateHooks.pending();
        if (hud == null || hud != readyHud || shown || rows == null || rows.isEmpty()) return;
        shown = true;
        try {
            window = new ChamomiloUpdateWindow(hud, rows, "Chamomilo versions", "", "", "", "Close", "Close",
                    row -> open(hud, row), () -> hud.hideComponent(window));
            ReflectionUtil.getMethod(HeadsUpDisplay.class, "addComponent", new Class<?>[]{WurmComponent.class})
                    .invoke(hud, window);
            hud.setActiveWindow(window);
        } catch (Throwable failure) { LOG.log(Level.WARNING, "Cannot show Chamomilo versions window", failure); }
    }

    private static void open(HeadsUpDisplay hud, ModUpdate row) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IllegalStateException("Browser integration unavailable");
            Desktop.getDesktop().browse(URI.create(row.getDownloadUrl()));
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "Cannot open download: " + row.getDownloadUrl(), failure);
            hud.textMessage(":Event", 255, 220, 160, "Download " + row.getDisplayName() + ": " + row.getDownloadUrl());
        }
    }
}
