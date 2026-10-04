package com.wurmonline.client.renderer.gui;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chamomilo.wurm.update.ModUpdate;
import org.chamomilo.wurm.update.SharedUpdateCoordinator;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

/** Delivers the shared updater notification on the HUD thread. */
public final class WaypointerUpdateBridge {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.Updates");
    private static volatile List<ModUpdate> pending;
    private static boolean shown;
    private static ChamomiloUpdateWindow window;

    private WaypointerUpdateBridge() { }

    public static void claimHost() {
        SharedUpdateCoordinator.registerHost("wurm-waypointer", new SharedUpdateCoordinator.Host() {
            @Override public void updatesReady(List<ModUpdate> updates) { pending = updates; }
            @Override public void checkFailed(String repository, Throwable failure) {
                LOGGER.log(Level.FINE, "Update check failed for " + repository, failure);
            }
        });
    }

    public static void tick(final HeadsUpDisplay hud) {
        if (hud == null) return;
        SharedUpdateCoordinator.startOnce();
        List<ModUpdate> updates = pending;
        if (shown || updates == null || updates.isEmpty()) return;
        try {
            window = new ChamomiloUpdateWindow(hud, updates, "Chamomilo mod updates",
                    "Updates are available. Download a ZIP and install it manually.",
                    "Download", "Open {0}", "Later", "Dismiss this notification",
                    update -> {
                        try { java.awt.Desktop.getDesktop().browse(
                                java.net.URI.create(update.getDownloadUrl())); }
                        catch (Exception failure) { LOGGER.log(Level.WARNING, "Cannot open download", failure); }
                    }, () -> hud.hideComponent(window));
            ReflectionUtil.getMethod(HeadsUpDisplay.class, "addComponent",
                    new Class<?>[]{WurmComponent.class}).invoke(hud, window);
            hud.setActiveWindow(window);
            shown = true;
        } catch (Exception failure) {
            shown = true;
            LOGGER.log(Level.WARNING, "Cannot show updates", failure);
        }
    }
}
