package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.settings.SavePosManager;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.map.MiniMapState;
import org.waypoints.next.map.ServerMapSnapshot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the mini-map and the layer settings shared with the full map. */
public final class MiniMapWindowBridge {
    private static final Logger LOGGER = Logger.getLogger(
            "WurmWaypointer.MiniMap");
    private static final MiniMapState SETTINGS = new MiniMapState();
    private static HeadsUpDisplay owner;
    private static MiniMapWindow window;
    private static boolean enabled = true;
    private static boolean settingsInitialized;

    private MiniMapWindowBridge() { }

    public static synchronized boolean isEnabled() { return enabled; }

    public static synchronized int getZoomFactor() { return SETTINGS.getZoomFactor(); }
    public static synchronized void cycleZoomFactor() { SETTINGS.cycleZoomFactor(); }
    public static synchronized double fullMapWheelSteps(int wheelDelta) {
        return SETTINGS.fullMapWheelSteps(wheelDelta);
    }

    public static synchronized boolean areDeedsVisible() {
        initializeSettings();
        return SETTINGS.areDeedsVisible();
    }

    public static synchronized boolean isNavigationLineVisible() {
        return SETTINGS.isNavigationLineVisible();
    }

    public static synchronized boolean areRoadsVisible() {
        initializeSettings();
        return SETTINGS.areRoadsVisible();
    }

    public static synchronized void toggleDeeds() {
        initializeSettings();
        SETTINGS.toggleDeeds();
        notifySettingsChanged();
    }

    public static synchronized void toggleNavigationLine() {
        SETTINGS.toggleNavigationLine();
        notifySettingsChanged();
    }

    public static synchronized void toggleRoads() {
        initializeSettings();
        SETTINGS.toggleRoads();
        notifySettingsChanged();
    }

    public static synchronized void toggle(HeadsUpDisplay hud) {
        enabled = !enabled;
        reconcile(hud);
    }

    public static synchronized void tick(HeadsUpDisplay hud) {
        reconcile(hud);
    }

    public static synchronized void visibilityChanged(HeadsUpDisplay hud,
                                                      Object component) {
        if (hud != null && component == hud.getWorldMap()) reconcile(hud);
    }

    /** Routes the wheel while its absolute HUD coordinates are still known. */
    public static synchronized boolean mouseWheeled(HeadsUpDisplay hud,
                                                     int mouseX, int mouseY,
                                                     int wheelDelta) {
        return hud != null && hud == owner && window != null
                && hud.getComponents().contains(window)
                && isTopmostAt(hud, window, mouseX, mouseY)
                && window.mouseWheeledAt(mouseX, mouseY, wheelDelta);
    }

    static synchronized void openWorldMap(MiniMapWindow source) {
        if (source == null || source != window || owner == null) return;
        try {
            WorldMap map = owner.getWorldMap();
            if (map != null && !owner.getComponents().contains(map)) {
                owner.toggleWorldMapVisible();
            }
            if (map != null) owner.setActiveWindow(map);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Unable to open the full map", failure);
        }
    }

    static synchronized void closed(MiniMapWindow value) {
        if (value == null || value != window) return;
        enabled = false;
        try {
            removeIfAttached(owner);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Mini-map close failed open", failure);
        }
        window.disposeCaveMap();
        window = null;
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        HeadsUpDisplay target = hud == null ? owner : hud;
        try {
            removeIfAttached(target);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Mini-map detach failed open", failure);
        }
        if (window != null) window.disposeCaveMap();
        window = null;
        SETTINGS.resetPlayerLayerObservation();
        if (target == owner) owner = null;
        LOGGER.info("Mini-map detached: reason=" + oneLine(reason));
    }

    private static void reconcile(HeadsUpDisplay hud) {
        if (hud == null) return;
        try {
            if (owner != hud) {
                detach(owner, "HUD replacement");
                owner = hud;
            }
            initializeSettings();
            if (hud.getWorld() != null && SETTINGS.observePlayerLayer(
                    hud.getWorld().getPlayerLayer() < 0)) notifySettingsChanged();
            ServerMapSnapshot snapshot = WurmWaypointerRuntime
                    .serverMapSnapshot();
            boolean mapReady = snapshot != null
                    && snapshot.getProfile() != null && snapshot.hasSurface();
            if (!enabled || !mapReady) {
                removeIfAttached(hud);
                return;
            }
            if (window == null) {
                window = new MiniMapWindow(SETTINGS);
                window.setInitialSize(MiniMapWindow.WINDOW_WIDTH,
                        MiniMapWindow.WINDOW_HEIGHT, false);
                window.setPosition(Math.max(20,
                                hud.getWidth() - window.width - 25),
                        Math.max(35, hud.getHeight() - window.height - 120));
                add(hud, window);
                registerPosition(hud, window,
                        "wurm-waypointer.mini-map");
                LOGGER.info("Mini-map attached: range="
                        + SETTINGS.getVisibleTiles() + " tiles");
            } else if (!hud.getComponents().contains(window)) {
                add(hud, window);
            }
            window.updateProfile(snapshot.getProfile());
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Mini-map reconciliation failed open",
                    failure);
        }
    }

    private static void removeIfAttached(HeadsUpDisplay hud)
            throws ReflectiveOperationException {
        if (hud != null && window != null
                && hud.getComponents().contains(window)) {
            window.prepareDetach();
            remove(hud, window);
        }
    }

    private static void add(HeadsUpDisplay hud, WurmComponent component)
            throws ReflectiveOperationException {
        Method method = ReflectionUtil.getMethod(HeadsUpDisplay.class,
                "addComponent", new Class<?>[]{WurmComponent.class});
        ReflectionUtil.callPrivateMethod(hud, method, component);
    }

    private static void remove(HeadsUpDisplay hud, WurmComponent component)
            throws ReflectiveOperationException {
        Method method = ReflectionUtil.getMethod(HeadsUpDisplay.class,
                "removeComponent", new Class<?>[]{WurmComponent.class});
        ReflectionUtil.callPrivateMethod(hud, method, component);
    }

    private static void registerPosition(HeadsUpDisplay hud,
                                         WindowSerializer value, String key)
            throws ReflectiveOperationException {
        Field field = ReflectionUtil.getField(HeadsUpDisplay.class,
                "savePosManager");
        SavePosManager positions = ReflectionUtil.getPrivateField(hud, field);
        if (positions != null) positions.registerAndRefresh(value, key);
    }

    private static void initializeSettings() {
        if (settingsInitialized) return;
        SETTINGS.setDeedsVisible(WurmWaypointerRuntime.serverMapShowsDeeds());
        SETTINGS.setRoadsVisible(
                WurmWaypointerRuntime.serverMapShowsHighways());
        settingsInitialized = true;
    }

    private static void notifySettingsChanged() {
        if (window != null) window.sharedSettingsChanged();
    }

    /** Mirrors HeadsUpDisplay#getTopComponentAt without descending into rows. */
    private static boolean isTopmostAt(HeadsUpDisplay hud,
                                       WurmComponent expected,
                                       int mouseX, int mouseY) {
        List<WurmComponent> components = hud.getComponents();
        for (int index = components.size() - 1; index >= 0; index--) {
            WurmComponent candidate = components.get(index);
            if (candidate.contains(mouseX, mouseY) && candidate.isAvailable()) {
                return candidate == expected;
            }
        }
        return false;
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
    }
}
