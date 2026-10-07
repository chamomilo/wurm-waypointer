package com.wurmonline.client.renderer.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chamomilo.wurm.update.ModUpdate;
import org.chamomilo.wurm.update.SharedUpdateCoordinator;
import org.chamomilo.wurm.update.SharedUpdateHooks;
import org.chamomilo.wurm.update.UpdatePreferences;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

/** Shared UI host; resolves native GUI classes only after all mod hooks are installed. */
public final class ChamomiloUpdateBridge {
    private static final Logger LOG = Logger.getLogger("Chamomilo.UpdateCoordinator");
    private static HeadsUpDisplay readyHud;
    private static boolean startupHandled;
    private static ChamomiloUpdateWindow window;
    private ChamomiloUpdateBridge() { }

    public static void hudReady(HeadsUpDisplay hud) {
        if (readyHud != hud) window = null;
        readyHud = hud;
        SharedUpdateCoordinator.startOnce();
        tick(hud);
    }

    public static void tick(final HeadsUpDisplay hud) {
        List<ModUpdate> rows = SharedUpdateHooks.pending();
        if (hud == null || hud != readyHud || window != null || rows == null || rows.isEmpty()) return;
        try {
            window = new ChamomiloUpdateWindow(hud, rows, "Chamomilo versions", "", "", "", "Close", "Close",
                    row -> open(hud, row), () -> hud.hideComponent(window));
            java.lang.reflect.Method addComponent = ReflectionUtil.getMethod(
                    HeadsUpDisplay.class, "addComponent", new Class<?>[]{WurmComponent.class});
            addComponent.setAccessible(true);
            addComponent.invoke(hud, window);
            hud.mainMenu.registerComponent("Mod updates", window);
            hud.mainMenu.setAvailable(window, true);
            if (!startupHandled && !UpdatePreferences.shared().isSkipNextStart()) {
                hud.showComponent(window);
                hud.setActiveWindow(window);
            } else hud.hideComponent(window);
            startupHandled = true;
        } catch (Throwable failure) {
            if (window != null) hud.removeComponent(window);
            window = null;
            LOG.log(Level.WARNING, "Cannot register Chamomilo versions window", failure);
        }
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
