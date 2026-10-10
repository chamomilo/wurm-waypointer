package com.wurmonline.client.renderer.gui;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.*;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.ui.WaypointerSection;

/** Native full-list selection and viewport checks, including short screens. */
final class FilterListProbe {
    static void verify(WaypointerHubWindow hub, String output, String language) throws Exception {
        hub.select(WaypointerSection.ALL_WAYPOINTS);
        WaypointManagerWindow manager=(WaypointManagerWindow)panels(hub).get(WaypointerSection.ALL_WAYPOINTS);
        for(String name:new String[]{"serverFilter","userFilter","typeFilter","statusFilter"})
            verifyDropdown((WurmDropDown)field(manager,name),output,null);
        String[] many=new String[80];for(int i=0;i<many.length;i++)many[i]="Choice "+i;
        WurmDropDown large=WaypointerUi.dropDown("fixture.full.filter",0,many);
        large.setPosition(1100,WurmComponent.hud.getHeight()-40);
        verifyDropdown(large,output,"full-dropdown-"+language+".png");

        int originalHeight=WurmComponent.hud.getHeight();
        Field height=HeadsUpDisplay.class.getDeclaredField("height");height.setAccessible(true);
        try {
            // Force both long checkbox catalogues to use more than one column.
            height.setInt(WurmComponent.hud,480);
            for(WaypointerSection section:new WaypointerSection[]{WaypointerSection.MOBS_AROUND,WaypointerSection.CONTAINERS_AROUND,WaypointerSection.OBJECTS_AROUND}) {
                hub.select(section);
                SurroundingsWindow panel=(SurroundingsWindow)panels(hub).get(section);
                String[] filters=section==WaypointerSection.MOBS_AROUND
                        ?new String[]{"modifierFilter","uniqueFilter","deedFilter","layerFilter","markedFilter"}
                        :new String[]{"categoryFilter","materialFilter","rarityFilter","deedFilter","layerFilter","markedFilter"};
                int[] counts=section==WaypointerSection.MOBS_AROUND
                        ?new int[]{CreatureModifier.values().length,UniqueStatus.values().length,DeedStatus.values().length,2,2}
                        :new int[]{section==WaypointerSection.CONTAINERS_AROUND?8:7,15,4,DeedStatus.values().length,2,2};
                for(int i=0;i<filters.length;i++) {
                    panel.buttonClicked((WButton)field(panel,filters[i]));
                    WaypointerUiWindow popup=(WaypointerUiWindow)field(panel,"filterWindow");
                    check(popup!=null,"Filter opens: "+filters[i]);
                    List<FlexComponent> content=descendants(popup.getComponent());
                    List<WaypointerFilterCheck> checks=new ArrayList<WaypointerFilterCheck>();
                    for(FlexComponent component:content) {
                        check(!(component instanceof WurmScrollPanel),"Filter contains no scroller: "+filters[i]);
                        if(component instanceof WaypointerFilterCheck)checks.add((WaypointerFilterCheck)component);
                        if(component instanceof WButton)check(component.x>=popup.x&&component.y>=popup.y
                                &&component.x+component.width<=popup.x+popup.width&&component.y+component.height<=popup.y+popup.height,"Every choice/action is visible: "+filters[i]);
                    }
                    check(checks.size()==counts[i],"Complete option count: "+filters[i]);
                    check(popup.y>=0&&popup.y+popup.height<=480,"Complete filter fits a short screen");
                    if(counts[i]>10)check(checks.get(0).x!=checks.get(checks.size()-1).x,"Long list uses multiple columns");
                    WaypointerFilterCheck last=checks.get(checks.size()-1);boolean before=last.checked();
                    last.leftPressed(last.x+16,last.y+16,0);last.leftReleased(last.x+16,last.y+16);
                    check(last.checked()!=before,"Last option is immediately clickable without scrolling");
                    last.leftPressed(last.x+16,last.y+16,0);last.leftReleased(last.x+16,last.y+16);
                    if(i==0||filters[i].equals("materialFilter"))
                        WaypointerLayoutProbe.savePreview(popup,Paths.get(output,"full-filter-"+section+"-"+filters[i]+"-"+language+".png").toString());
                    WButton done=null;for(FlexComponent component:content)if(component instanceof WButton&&((WButton)component).getLabel().equals(Messages.text("Done")))done=(WButton)component;
                    check(done!=null,"Done action exists");done.leftPressed(done.x+8,done.y+8,0);done.leftReleased(done.x+8,done.y+8);
                    check(field(panel,"filterWindow")==null,"Done closes the full list");
                }
            }
        } finally {height.setInt(WurmComponent.hud,originalHeight);}
        hub.select(WaypointerSection.SETTINGS);
        for(FlexComponent component:descendants(panels(hub).get(WaypointerSection.SETTINGS).getComponent()))
            if(component instanceof WurmDropDown)verifyDropdown((WurmDropDown)component,output,null);
        System.out.println("FULL_FILTER_LISTS_OK: "+language+", every checkbox filter, all manager/settings dropdowns, 80 options and 480px viewport");
    }

    private static void verifyDropdown(WurmDropDown field,String output,String preview) throws Exception {
        int previous=field.getValue();
        field.leftPressed(field.x+8,field.y+8,0);
        List<?> popups=(List<?>)field(WurmComponent.hud,"dropdownPopups");
        check(popups.size()==1,"Exactly one native dropdown popup");
        WurmDropdownPopup popup=(WurmDropdownPopup)popups.get(0);
        FlexComponent options=null;
        for(FlexComponent component:descendants(popup.component)) {
            check(!(component instanceof WurmScrollPanel),"Full dropdown has no scroller");
            if(component.getClass().getName().endsWith("$Options"))options=component;
        }
        check(options!=null,"Full option grid exists");
        check(popup.x>=0&&popup.y>=0&&popup.x+popup.width<=WurmComponent.hud.getWidth()
                &&popup.y+popup.height<=WurmComponent.hud.getHeight(),"Dropdown stays inside the viewport");
        int count=((String[])field(field,"options")).length;
        int rows=(Integer)field(options,"rows"),columnWidth=(Integer)field(options,"columnWidth"),rowHeight=(Integer)field(options,"rowHeight");
        int last=count-1,mx=options.x+last/rows*columnWidth+8,my=options.y+last%rows*rowHeight+8;
        check(mx<options.x+options.width&&my<options.y+options.height,"Last option lies in the visible grid");
        if(preview!=null)WaypointerLayoutProbe.savePreview(popup,Paths.get(output,preview).toString());
        options.leftPressed(mx,my,0);options.leftReleased(mx,my);
        check(field.getValue()==last&&popups.isEmpty(),"Last dropdown option selects and dismisses through native input");
        field.setValue(previous);
    }
    private static List<FlexComponent> descendants(FlexComponent root) throws Exception {
        List<FlexComponent> result=new ArrayList<FlexComponent>();result.add(root);
        List<FlexComponent> children=new ArrayList<FlexComponent>();
        if(root instanceof WurmArrayPanel)children.addAll(((WurmArrayPanel<?>)root).components);
        else if(root instanceof WurmBorderPanel)children.addAll(Arrays.asList((FlexComponent[])field(root,"components")));
        else if(root instanceof WurmDecorator)children.add(((WurmDecorator)root).component);
        for(FlexComponent child:children)if(child!=null)result.addAll(descendants(child));
        return result;
    }
    private static Map<WaypointerSection,WaypointerContentPanel> panels(Object hub)throws Exception{return (Map<WaypointerSection,WaypointerContentPanel>)field(hub,"panels");}
    private static Object field(Object object,String name)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(NoSuchFieldException absent){ }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
