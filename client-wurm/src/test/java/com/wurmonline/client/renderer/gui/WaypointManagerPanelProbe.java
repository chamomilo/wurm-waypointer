package com.wurmonline.client.renderer.gui;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.model.*;
import org.waypoints.next.service.*;
import org.waypoints.next.tracking.*;
import org.waypoints.next.ui.*;
import org.waypoints.next.surroundings.*;
import org.chamomilo.wurm.ui.v1.UiDensity;

/** Regression checks through real native tables, immediate deletion and popup lifecycle. */
public final class WaypointManagerPanelProbe {
    private static final Set<String> paintedCommands=new HashSet<String>();
    public static void main(String[] args) throws Exception {
        WaypointerLayoutProbe.preparePreview(args[0], args[2], 12);
        verify(args[1]); NativeUiRenderFixture.verified();
    }
    static void verify(String output) throws Exception {
        Messages.select("en");
        ServerIdentity server = ServerIdentity.of(new ServerEndpoint("127.0.0.1", 3724, 27016),
                "Fixture", "Fixture", ServerIdentity.Resolution.RESOLVED);
        TrackingCatalog catalog = new TrackingCatalog(); Instant now = Instant.now();
        catalog.bind(server, "Alice", now);
        List<UUID> ids = new ArrayList<UUID>();
        final UUID[] navigating={null};
        for (int i = 0; i < 70; i++) {
            WaypointRecord r = catalog.live(ManagedKind.ANIMAL, Integer.toString(i), "Brown cow",
                    "Manage cow", new WaypointCoordinate(100 + i, 110, 2d, WaypointLayer.SURFACE), now);
            catalog.enabled(r.getId(), true, now); catalog.enabled(r.getId(), false, now);
            ids.add(r.getId());
        }
        WaypointManagerController controller = (WaypointManagerController) Proxy.newProxyInstance(
                WaypointManagerController.class.getClassLoader(), new Class[]{WaypointManagerController.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "context": return new WaypointManagerContext("Alice", server, 100, 100, 2, WaypointLayer.SURFACE);
                        case "snapshot": return new WaypointManagerViewService().snapshot(catalog.waypoints(), (WaypointManagerQuery)a[0]);
                        case "delete": check(catalog.removeWaypoint((UUID)a[0], Instant.now()), "Selected UUID deleted"); return null;
                        case "setEnabled":catalog.enabled((UUID)a[0],(Boolean)a[1],Instant.now());return null;
                        case "isNavigatorActive":return a[0].equals(navigating[0]);
                        case "toggleNavigator":navigating[0]=a[0].equals(navigating[0])?null:(UUID)a[0];return navigating[0]!=null;
                        case "revision": return catalog.revision();
                        case "surroundings": return Proxy.newProxyInstance(SurroundingsController.class.getClassLoader(), new Class[]{SurroundingsController.class},
                                (p2,m2,a2) -> m2.getName().equals("snapshot") ? new SurroundingsCatalog().snapshot((SurroundingsQuery)a2[0], 400, 400) : defaultValue(m2));
                        case "tracking": return Proxy.newProxyInstance(TrackingController.class.getClassLoader(), new Class[]{TrackingController.class},
                                (p2,m2,a2) -> m2.getName().equals("records") ? Collections.emptyList() : m2.getName().equals("status") ? "" : defaultValue(m2));
                        case "reportFailure": throw new AssertionError(String.valueOf(a[0]), (Throwable)a[1]);
                        case "settings": return Proxy.newProxyInstance(SettingsController.class.getClassLoader(), new Class[]{SettingsController.class},
                                (p2,m2,a2) -> m2.getName().equals("values") ? new Properties() : "");
                        default: return defaultValue(m);
                    }
                });
        WaypointerHubWindow hub = new WaypointerHubWindow(controller);
        hub.setSize(1480, 700); hub.setPosition(10, 10); hub.gameTick();
        Map<?,?> panels = (Map<?,?>)field(hub, "panels");
        WaypointManagerWindow manager = (WaypointManagerWindow)panels.get(WaypointerSection.ALL_WAYPOINTS);
        WurmArrayPanel<?> table = (WurmArrayPanel<?>)field(manager, "table");
        ChamomiloUiV1ScrollPanel scroll = (ChamomiloUiV1ScrollPanel)field(manager, "listScroll");
        Map<WButton, WaypointManagerQuery.SortColumn> sorts = (Map<WButton, WaypointManagerQuery.SortColumn>)field(manager, "sortActions");
        for (Map.Entry<WButton, WaypointManagerQuery.SortColumn> sort : sorts.entrySet()) {
            if (sort.getValue() == WaypointManagerQuery.SortColumn.DISTANCE) { manager.buttonClicked(sort.getKey()); break; }
        }
        WurmArrayPanel<?> header=(WurmArrayPanel<?>)field(manager,"tableHeader");
        int headingY=header.y;
        scroll.scrollTo(0, 38 * 15 + 7); int offset = scroll.yo;
        TableHeaderProbe.verifyFixed(header,scroll,headingY);
        List<?> rows = new ArrayList<Object>(table.components);
        for (int iteration = 0; iteration < 4; iteration++) {
            for (int i = 0; i < ids.size(); i++) catalog.live(ManagedKind.ANIMAL, Integer.toString(i), "Brown cow", "Manage cow",
                    new WaypointCoordinate(300 - i * 2 + iteration, 110, 2d, WaypointLayer.SURFACE), now.plusSeconds(iteration + 1));
            tick(manager);
            check(rows.equals(table.components), "Live positions keep the same native row/control instances");
            check(scroll.yo == offset, "Live revisions do not move the scroll offset");
            TableHeaderProbe.verifyFixed(header,scroll,headingY);
        }
        Map<WButton,?> actions = (Map<WButton,?>)field(manager, "rowActions");
        for (WButton button : actions.keySet()) {
            verifySharedFont(button);
        }
        TableHeaderProbe.verify(header);
        for(String name:new String[]{"refreshButton","applyFilters","addButton","enableFiltered","disableFiltered","exportButton","importButton","pasteSharedButton"})
            verifySharedFont((WButton)field(manager,name));
        System.out.println("BUTTON_GROUP_OK: all-waypoints.commands="+WaypointerButtonGroup.fontPixels(((WButton)field(manager,"refreshButton")).text)+" px, shared normal/hover baseline");
        List<UUID> before = new ArrayList<UUID>((List<UUID>)field(manager, "filteredIds"));
        UUID top = before.get(15), removed = before.get(5);
        String removedSource = catalog.find(removed).getSourceKey();
        String removedKey = removedSource.substring(removedSource.lastIndexOf(':') + 1);
        WButton delete = null;
        for (Map.Entry<WButton,?> action : actions.entrySet()) if (removed.equals(field(action.getValue(), "id"))
                && field(action.getValue(), "kind").toString().equals("REMOVE_TRACKED")) delete = action.getKey();
        check(delete != null, "Delete action is bound to a UUID, including identical names");
        manager.buttonClicked(delete);
        List<UUID> after = (List<UUID>)field(manager, "filteredIds");
        check(after.size() == 69 && !after.contains(removed), "Deleted row disappears immediately");
        check(after.get(14).equals(top) && scroll.yo == offset - 38, "Deletion before viewport retains visible UUID and intra-row offset");
        TableHeaderProbe.verifyFixed((WurmArrayPanel<?>)field(manager,"tableHeader"),scroll,headingY);
        catalog.live(ManagedKind.ANIMAL, removedKey, "Brown cow", "Manage cow",
                new WaypointCoordinate(150, 100, 2d, WaypointLayer.SURFACE), now.plusSeconds(10));
        tick(manager);
        check(!((List<?>)field(manager, "filteredIds")).contains(removed), "Movement never re-adds a deleted row");
        WurmDropDown type = (WurmDropDown)field(manager, "typeFilter");
        type.setValue(1); tick(manager);
        check(((List<?>)field(manager, "filteredIds")).size() == 69, "Draft dropdown choice waits for Apply filters");
        manager.buttonClicked((WButton)field(manager, "applyFilters"));
        check(((List<?>)field(manager, "filteredIds")).isEmpty(), "Apply filters commits the selection");
        manager.buttonClicked((WButton)field(manager, "refreshButton"));
        check(type.getValue() == 1 && ((List<?>)field(manager, "filteredIds")).isEmpty(), "Refresh retains applied filters");
        type.setValue(0); manager.buttonClicked((WButton)field(manager, "applyFilters"));
        WButton add=(WButton)field(manager,"addButton"),refresh=(WButton)field(manager,"refreshButton");
        check(add.y==refresh.y&&add.x+add.width<refresh.x,"Add appears before Refresh in the same row");
        check(WaypointerButtonGroup.id(add.text).equals("all-waypoints.top-actions")&&WaypointerButtonGroup.id(refresh.text).equals("all-waypoints.top-actions")&&WaypointerButtonGroup.fontPixels(add.text)==WaypointerButtonGroup.fontPixels(refresh.text),"Top actions form one font group");
        UUID selected=((List<UUID>)field(manager,"filteredIds")).get(0);
        manager.buttonClicked(rowButton(manager,selected,"TOGGLE"));
        WButton on=rowButton(manager,selected,"TOGGLE");
        check(on.getLabel().equals("On")&&(Boolean)field(on.text,"active")&&(Boolean)field(on.textBold,"active"),"On has green state in both weights");
        manager.buttonClicked(rowButton(manager,selected,"NAVIGATE"));
        WButton nav=rowButton(manager,selected,"NAVIGATE");
        check(navigating[0].equals(selected)&&(Boolean)field(nav.text,"active")&&(Boolean)field(nav.textBold,"active"),"Active NAV has green state in both weights");
        verifyAlpha(on);verifyAlpha(nav);
        WaypointerLayoutProbe.savePreview(hub, Paths.get(output, "all-waypoints-dense.png").toString());
        verifyAlpha(type);
        type.leftPressed(type.x + 10, type.y + 10, 0);
        List<?> popups = (List<?>)field(WurmComponent.hud, "dropdownPopups");
        check(popups.size() == 1, "Popup registered with native HUD dismissal lifecycle");
        WurmDropdownPopup popup = (WurmDropdownPopup)popups.get(0);
        verifyAlpha(popup);
        tick(manager); check(popups.size() == 1, "Live refresh does not close or replace the dropdown");
        WaypointerLayoutProbe.savePreview(popup, Paths.get(output, "all-waypoints-dropdown.png").toString());
        WurmComponent.hud.clearAllPopups(); check(popups.isEmpty(), "Native popup dismissal works");
        controller.toggleNavigator(selected);
        WaypointManagerLifecycleProbe.verify(controller, selected);
        System.out.println("WAYPOINT_MANAGER_STABILITY_OK: 70 repeated names, live revisions, UUID deletion, scroll anchor, applied filters, field/popup alpha");
    }
    private static WButton rowButton(WaypointManagerWindow manager,UUID id,String kind)throws Exception{
        for(Map.Entry<WButton,?> entry:((Map<WButton,?>)field(manager,"rowActions")).entrySet())if(id.equals(field(entry.getValue(),"id"))&&field(entry.getValue(),"kind").toString().equals(kind))return entry.getKey();
        throw new AssertionError("Missing row action "+kind+" "+id);
    }
    private static void tick(WaypointManagerWindow manager) throws Exception {
        Field next = WaypointManagerWindow.class.getDeclaredField("nextCatalogueRefresh"); next.setAccessible(true); next.setLong(manager, 0); manager.gameTick();
    }
    private static void verifySharedFont(WButton button){
        boolean tableAction=button.parent instanceof WaypointerTableActionCell;
        check(button.height==(tableAction?28:32),"Manager action has the intended height: "+button.getLabel()+" height="+button.height);
        if(tableAction)HubLayoutProbe.verifyTableAction(button);
        check(((ChamomiloUiV1Button)button).density()==UiDensity.HIGH,"High density actions");
        check(WaypointerButtonGroup.fontPixels(button.text)==WaypointerButtonGroup.fontPixels(button.textBold)&&WaypointerButtonGroup.fontPixels(button.text)>=18,
                "All manager rows use readable shared regular/bold captions: "+button.getLabel());
        check(WaypointerButtonGroup.id(button.text).startsWith("all-waypoints."),"Every action belongs to a recorded manager group");
        check(button.text.getAscent()==button.textBold.getAscent(),"Both weights share the group baseline");
        check(button.width>=Math.max(button.text.getWidth(button.getLabel()),button.textBold.getWidth(button.getLabel()))+24,"Both weights fit full caption: "+button.getLabel());
        if(paintedCommands.add(button.getLabel())){
            ChamomiloUiV1Button nativeButton=(ChamomiloUiV1Button)button;nativeButton.setAnimationsEnabled(false);
            int baseline=-1;
            for(boolean hover:new boolean[]{false,true}){
                button.hovered=hover;button.render(null,1f);
                java.awt.Point point=WaypointerLayoutProbe.paintedLabel(button.getLabel());
                int expected=(button.height-button.text.getHeight())/2+button.text.getAscent();
                check(point!=null&&point.y-button.y==expected,"Actual caption uses group baseline: "+button.getLabel());
                if(baseline>=0)check(point.y-button.y==baseline,"Hover keeps baseline: "+button.getLabel());
                baseline=point.y-button.y;
                java.awt.Rectangle ink=org.chamomilo.wurm.ui.v1.UiTypography.ink(button.getLabel(),WaypointerButtonGroup.fontPixels(button.text),hover,UiDensity.HIGH);
                check(point.y+ink.y>=button.y+3&&point.y+ink.y+ink.height<=button.y+button.height-3,"Group ink/press clearance: "+button.getLabel());
            }
            button.hovered=false;
        }
    }
    private static void verifyAlpha(WurmComponent component) {
        NativeUiRenderFixture.beginAlphaFrame(); component.render(null, 1f);
        List<Float> expected = NativeUiRenderFixture.endAlphaFrame(); check(!expected.isEmpty(), "Independent alpha audit paints actual primitives");
        for (float alpha : new float[]{.15f, .85f, .35f}) {
            NativeUiRenderFixture.beginAlphaFrame(); component.render(null, alpha);
            check(expected.equals(NativeUiRenderFixture.endAlphaFrame()), "Dropdown field/popup ignores incoming HUD alpha");
        }
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException absent) { }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Object defaultValue(Method method) {
        if (method.getReturnType() == boolean.class) return false;
        if (method.getReturnType() == long.class) return 0L;
        if (method.getReturnType() == int.class) return 0;
        return null;
    }
}
