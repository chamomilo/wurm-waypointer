package com.wurmonline.client.renderer.gui;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.waypoints.next.ui.WaypointManagerController;
import org.waypoints.next.ui.WaypointerSection;

/** Uses native HUD registration and keyboard focus rather than a detached render tree. */
final class WaypointManagerLifecycleProbe {
    static void verify(WaypointManagerController controller, UUID destination) throws Exception {
        HeadsUpDisplay hud = WurmComponent.hud;
        WurmInputField chat = WaypointerUi.input("fixture.chat", null);
        field(hud, "chatInput").set(hud, chat);
        field(hud, "kbFocusComponent").set(hud, chat);
        WaypointerUiWindow other = new WaypointerUiWindow("fixture.other", false);
        WurmDropDown foreignDropdown = WaypointerUi.dropDown("fixture.other.dropdown", 0, new String[]{"Other one", "Other two"});
        other.setComponent(foreignDropdown);
        other.show(hud);
        check(hud.getComponents().size()==1 && hud.getComponents().contains(other),"Unrelated window starts registered: "+hud.getComponents());
        foreignDropdown.leftPressed(foreignDropdown.x + 10, foreignDropdown.y + 10, 0);
        List<?> popups = (List<?>) field(hud, "dropdownPopups").get(hud);
        Object foreignPopup = popups.get(0);

        WaypointManagerWindowBridge.openCreateCoordinates(hud, controller, "Route destination", "");
        WaypointerHubWindow hub = current(hud);
        WaypointManagerWindow manager = manager(hub);
        WurmInputField input = (WurmInputField) field(manager, "nameInput").get(manager);
        check(!hud.getComponents().contains(manager), "Editor focus must not promote its panel to the HUD");
        check(field(hud, "kbFocusComponent").get(hud) == input && field(hud, "isTyping").getBoolean(hud),
                "Owning hub routes keyboard focus to the editor");
        manager.buttonClicked((WButton) field(manager, "cancelButton").get(manager));
        WButton navigate=null;
        for(Map.Entry<?,?> entry:((Map<?,?>)field(manager,"rowActions").get(manager)).entrySet()) {
            Object action=entry.getValue();
            if(destination.equals(field(action,"id").get(action))
                    &&field(action,"kind").get(action).toString().equals("NAVIGATE")) navigate=(WButton)entry.getKey();
        }
        check(navigate!=null,"Route action exists for the destination"); manager.buttonClicked(navigate);
        WurmDropDown dropdown = (WurmDropDown) field(manager, "typeFilter").get(manager);
        dropdown.leftPressed(dropdown.x + 10, dropdown.y + 10, 0);
        check(popups.size() == 2, "A manager dropdown is open beside an unrelated dropdown at close");
        hub.closePressed();
        check(popups.size() == 1 && popups.contains(foreignPopup), "Close dismisses only the manager dropdown");
        hud.clearAllPopups();
        check(hud.getComponents().size() == 1 && hud.getComponents().contains(other),
                "Edit, route, close removes every manager element and preserves unrelated windows: "+hud.getComponents());
        check(controller.isNavigatorActive(destination), "Closing the manager keeps navigation active");

        check(WaypointManagerWindowBridge.toggle(hud, controller), "Compass opens a fresh hub");
        hub = current(hud); manager = manager(hub);
        // Reproduce the old focus bug, including a cached panel after switching tabs.
        hud.setActiveWindow(manager);
        check(hud.getComponents().contains(manager), "Native setActiveWindow really registers the panel separately");
        hub.select(WaypointerSection.MOBS_AROUND);
        SurroundingsWindow nearby = (SurroundingsWindow) panels(hub).get(WaypointerSection.MOBS_AROUND);
        nearby.buttonClicked((WButton) field(nearby, "modifierFilter").get(nearby));
        WaypointerUiWindow filter = (WaypointerUiWindow) field(nearby, "filterWindow").get(nearby);
        check(filter != null && hud.getComponents().contains(filter), "Owned auxiliary filter is registered");
        check(!WaypointManagerWindowBridge.toggle(hud, controller), "Compass closes the hub");
        check(hud.getComponents().size() == 1 && hud.getComponents().contains(other),
                "Close cleans legacy promoted panels and auxiliary filters");

        WaypointManagerWindowBridge.openCreateCoordinates(hud, controller, "Typing at close", "");
        hub = current(hud);
        hub.closePressed();
        check(!field(hud, "isTyping").getBoolean(hud) && field(hud, "kbFocusComponent").get(hud) == chat,
                "Closing an editor releases typing and restores native chat focus");
        check(hud.getComponents().size() == 1, "No editor can survive its window");

        WaypointManagerController failingCleanup = (WaypointManagerController) java.lang.reflect.Proxy.newProxyInstance(
                WaypointManagerController.class.getClassLoader(), new Class[]{WaypointManagerController.class}, (p,m,a) -> {
                    if (m.getName().equals("clearLivePreview")) throw new IllegalStateException("Fixture preview cleanup failure");
                    return m.invoke(controller,a);
                });
        WaypointManagerWindowBridge.open(hud, failingCleanup);
        current(hud).closePressed();
        check(hud.getComponents().size() == 1, "A failed preview cleanup cannot leave UI behind");

        WaypointManagerWindowBridge.openSection(hud,controller,WaypointerSection.MOBS_AROUND);
        hub=current(hud); nearby=(SurroundingsWindow)panels(hub).get(WaypointerSection.MOBS_AROUND);
        nearby.buttonClicked((WButton)field(nearby,"modifierFilter").get(nearby));
        filter=(WaypointerUiWindow)field(nearby,"filterWindow").get(nearby);
        WaypointerLayoutProbe.preparePreview("", "", 12);
        HeadsUpDisplay replacement=WurmComponent.hud;
        WaypointerUiWindow replacementOther=new WaypointerUiWindow("fixture.replacement.other",false);
        replacementOther.show(replacement);
        WaypointManagerWindowBridge.detach(replacement,"Fixture HUD replacement");
        check(hud.getComponents().size()==1 && !hud.getComponents().contains(filter)
                && replacement.getComponents().size()==1 && replacement.getComponents().contains(replacementOther),
                "HUD replacement removes the old hub and filter from their actual owner");
        replacementOther.dispose(); WurmComponent.hud=hud;
        other.dispose();
        System.out.println("WAYPOINT_MANAGER_LIFECYCLE_OK: native editor focus, route/close, scoped dropdown removal, compass reopen, legacy orphan cleanup, filter disposal, chat focus, cleanup failure, HUD replacement");
    }
    private static WaypointerHubWindow current(HeadsUpDisplay hud) {
        WaypointerHubWindow found = null;
        for (WurmComponent component : hud.getComponents()) if (component instanceof WaypointerHubWindow) {
            check(found == null, "Exactly one hub is registered"); found = (WaypointerHubWindow) component;
        }
        check(found != null, "Hub is registered with native HUD"); return found;
    }
    private static Map<?,?> panels(WaypointerHubWindow hub) throws Exception { return (Map<?,?>) field(hub,"panels").get(hub); }
    private static WaypointManagerWindow manager(WaypointerHubWindow hub) throws Exception {
        return (WaypointManagerWindow) panels(hub).get(WaypointerSection.ALL_WAYPOINTS);
    }
    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
