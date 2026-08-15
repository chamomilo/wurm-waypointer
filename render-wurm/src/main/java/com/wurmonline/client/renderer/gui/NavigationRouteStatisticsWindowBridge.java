package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.game.inventory.InventoryMetaWindowView;
import com.wurmonline.client.settings.SavePosManager;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.navigation.NavigationRouteStatistics;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.model.WaypointSourceType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the route-statistics window for the one active navigator. */
public final class NavigationRouteStatisticsWindowBridge {
    private static final Logger LOGGER = Logger.getLogger(
            "WurmWaypointer.RouteStatistics");
    private static HeadsUpDisplay owner;
    private static NavigationRouteStatisticsWindow window;
    private static NavigationTargetKey targetKey;
    private static NavigationRouteStatistics lastStatistics;
    private static String lastWaypointName = "";
    private static String lastLootMapSummary = "";
    private static NavigationTargetKey userDismissedTargetKey;
    private static final LootMapStatisticsMemory LOOT_MAP_STATISTICS =
            new LootMapStatisticsMemory(32);

    private NavigationRouteStatisticsWindowBridge() {
    }

    public static synchronized void reconcile(
            HeadsUpDisplay hud, NavigationTarget target,
            NavigationRouteStatistics statistics) {
        if (hud == null || target == null) {
            detach(hud, "navigator inactive");
            return;
        }
        try {
            boolean sameHud = owner == hud;
            boolean sameDismissedTarget = userDismissedTargetKey != null
                    && userDismissedTargetKey.equals(target.getKey());
            if (shouldRemainDismissed(userDismissedTargetKey != null,
                    sameHud, sameDismissedTarget)) return;
            userDismissedTargetKey = null;
            if (shouldCreateWindow(window != null, owner == hud)) {
                discardWindow("HUD replacement");
                owner = hud;
                window = new NavigationRouteStatisticsWindow();
                window.setInitialSize(
                        Math.min(NavigationRouteStatisticsWindow.PREFERRED_WIDTH,
                                Math.max(260, hud.getWidth() - 20)),
                        Math.min(NavigationRouteStatisticsWindow.PREFERRED_HEIGHT,
                                Math.max(150, hud.getHeight() - 20)), false);
                window.setPosition(Math.max(0, hud.getWidth() - window.width - 20),
                        Math.min(100, Math.max(0, hud.getHeight() - window.height)));
                add(hud, window);
                registerPosition(hud, window);
            } else if (!hud.getComponents().contains(window)) {
                add(hud, window);
            }
            if (!target.getKey().equals(targetKey)) {
                targetKey = target.getKey();
                lastStatistics = null;
                lastWaypointName = "";
                lastLootMapSummary = "";
                LOGGER.info("Route statistics window retargeted: target="
                        + target.getKey());
            }
            String waypointName = target.getName() == null
                    ? "" : target.getName();
            InventoryMetaItem lootMapItem = lootMapItem(hud.getWorld(), target);
            String currentLootMapSummary = lootMapSummary(target, lootMapItem);
            if (statistics != lastStatistics
                    || !waypointName.equals(lastWaypointName)
                    || !currentLootMapSummary.equals(lastLootMapSummary)) {
                window.update(waypointName, statistics, currentLootMapSummary);
                lastStatistics = statistics;
                lastWaypointName = waypointName;
                lastLootMapSummary = currentLootMapSummary;
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Route statistics window failed open", failure);
        }
    }

    public static synchronized void detach(HeadsUpDisplay hud, String reason) {
        if (window == null) return;
        boolean attached = owner != null
                && owner.getComponents().contains(window);
        try {
            if (attached) {
                remove(owner, window);
                LOGGER.info("Route statistics window detached: reason="
                        + oneLine(reason));
            }
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Route statistics window detach failed open", failure);
        }
        targetKey = null;
        lastStatistics = null;
        lastWaypointName = "";
        lastLootMapSummary = "";
        userDismissedTargetKey = null;
    }

    /**
     * A target change must reuse the registered window. SavePosManager retains
     * every registered serializer, so registering one replacement per target
     * lets stale instances overwrite the user's latest position on shutdown.
     */
    static boolean shouldCreateWindow(boolean hasWindow, boolean sameHud) {
        return !hasWindow || !sameHud;
    }

    static boolean shouldRemainDismissed(boolean dismissed, boolean sameHud,
                                         boolean sameTarget) {
        return dismissed && sameHud && sameTarget;
    }

    static synchronized void closed(NavigationRouteStatisticsWindow value) {
        if (value == null || value != window) return;
        userDismissedTargetKey = targetKey;
        try {
            if (owner != null && owner.getComponents().contains(window)) {
                remove(owner, window);
            }
            LOGGER.info("Route statistics window closed by user: target="
                    + String.valueOf(targetKey));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Route statistics window close failed open", failure);
        }
    }

    private static void discardWindow(String reason) {
        NavigationRouteStatisticsWindow discarded = window;
        HeadsUpDisplay discardedOwner = owner;
        if (discarded != null && discardedOwner != null) {
            try {
                if (discardedOwner.getComponents().contains(discarded)) {
                    remove(discardedOwner, discarded);
                }
            } catch (Throwable failure) {
                LOGGER.log(Level.FINE,
                        "Route statistics window replacement failed open", failure);
            }
        }
        if (discarded != null) {
            LOGGER.info("Route statistics window discarded: reason="
                    + oneLine(reason));
        }
        window = null;
        owner = null;
        targetKey = null;
        lastStatistics = null;
        lastWaypointName = "";
        lastLootMapSummary = "";
        userDismissedTargetKey = null;
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

    private static void registerPosition(
            HeadsUpDisplay hud, NavigationRouteStatisticsWindow value)
            throws ReflectiveOperationException {
        Field field = ReflectionUtil.getField(HeadsUpDisplay.class,
                "savePosManager");
        SavePosManager positions = ReflectionUtil.getPrivateField(hud, field);
        if (positions != null) {
            positions.registerAndRefresh(value,
                    "wurm-waypointer.navigation-route-statistics");
        }
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
    }

    static String lootMapSummary(World world, NavigationTarget target) {
        return lootMapSummary(target, lootMapItem(world, target));
    }

    private static String lootMapSummary(NavigationTarget target,
                                         InventoryMetaItem item) {
        if (target == null
                || target.getSourceType() != WaypointSourceType.LOOT_MAP) {
            return "";
        }
        int reads = number(target.getExtension("lootmap.readingCount"));
        return LOOT_MAP_STATISTICS.resolve(lootMapItemId(target),
                item == null ? null : Float.valueOf(item.getQuality()),
                item == null ? null : Float.valueOf(item.getDamage()), reads);
    }

    private static InventoryMetaItem lootMapItem(World world,
                                                  NavigationTarget target) {
        if (target == null
                || target.getSourceType() != WaypointSourceType.LOOT_MAP) {
            return null;
        }
        return inventoryItem(world, lootMapItemId(target));
    }

    private static Long lootMapItemId(NavigationTarget target) {
        return target == null ? null : longValue(target.getExtension(
                "lootmap.mapItemId"));
    }

    private static InventoryMetaItem inventoryItem(World world, Long itemId) {
        if (world == null || itemId == null) return null;
        try {
            if (world.getInventoryManager() == null) return null;
            InventoryMetaWindowView inventory = world.getInventoryManager()
                    .getPlayerInventory();
            InventoryMetaItem item = inventory == null ? null
                    : inventory.getItem(itemId.longValue());
            if (item != null) return item;
            InventoryMetaWindowView equipment = world.getInventoryManager()
                    .getPlayerEquipment();
            return equipment == null ? null
                    : equipment.getItem(itemId.longValue());
        } catch (Throwable unavailableInventory) {
            return null;
        }
    }

    private static Long longValue(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static int number(String value) {
        if (value == null) return -1;
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

}
