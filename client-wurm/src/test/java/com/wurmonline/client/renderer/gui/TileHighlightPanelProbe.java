package com.wurmonline.client.renderer.gui;

import org.waypoints.next.tiles.TileHighlightSettings;
import org.waypoints.next.ui.*;
import org.waypoints.next.i18n.Messages;
import org.chamomilo.wurm.ui.v1.UiCompactLayout;
import java.lang.reflect.Field;
import java.nio.file.Paths;

final class TileHighlightPanelProbe {
    static void verify(WaypointerHubWindow hub,TileHighlightPanel panel,String output,String language)throws Exception {
        SettingsController controller=(SettingsController)field(panel,"controller");
        check(field(panel,"colorWindow")==null,"Tile picker exists only when changing color");
        for(String name:new String[]{"nonRideable","flowers","harvestable","overaged","meteors","color","harvestableColor","overagedColor","meteorColor"}) {
            WButton button=(WButton)field(panel,name);
            check(button.height==24&&button.y>=panel.y&&button.y+24<=panel.y+panel.height,"Compact tile actions fit");
            check(hub.getComponentAt(button.x+button.width/2,button.y+12)==button,"Native hit reaches "+name);
            check(!((UiCompactLayout)field(button,"layout")).shortened,"Localized tile action fits: "+language);
            if(!name.equals("color")&&!name.endsWith("Color")) {
                String key=name.equals("flowers")?TileHighlightSettings.FLOWERS:name.equals("meteors")?TileHighlightSettings.METEORS:name.equals("harvestable")?TileHighlightSettings.HARVESTABLE:name.equals("overaged")?TileHighlightSettings.OVERAGED:TileHighlightSettings.NON_RIDEABLE;
                boolean old=Boolean.parseBoolean(controller.values().getProperty(key,"false"));click(button);hub.gameTick();
                check(Boolean.parseBoolean(controller.values().getProperty(key))!=old,"Independent tile switch saves");
                if(!Boolean.parseBoolean(controller.values().getProperty(key))){click(button);hub.gameTick();}
            }
        }
        for(String name:new String[]{"autoMark","chime"}) {
            WButton button=(WButton)field(panel,name);String key=name.equals("autoMark")?TileHighlightSettings.METEOR_MARK:TileHighlightSettings.METEOR_CHIME;
            check(hub.getComponentAt(button.x+button.width/2,button.y+12)==button,"Meteor checkbox hit: "+name);
            boolean old=Boolean.parseBoolean(controller.values().getProperty(key,"false"));click(button);hub.gameTick();check(Boolean.parseBoolean(controller.values().getProperty(key))!=old,"Independent meteor preference saves: "+name);
            if(!Boolean.parseBoolean(controller.values().getProperty(key))){click(button);hub.gameTick();}
        }
        WaypointerLayoutProbe.verifyStableOpacity(hub);
        WaypointerLayoutProbe.savePreview(hub,Paths.get(output,"tiles-"+language+"-enabled.png").toString());
        WButton color=(WButton)field(panel,"color");click(color);hub.gameTick();
        CreatureHighlightColorWindow popup=(CreatureHighlightColorWindow)field(panel,"colorWindow");
        WaypointColorPicker picker=(WaypointColorPicker)field(popup,"picker");
        check(hub.ownsHudComponent(picker),"Manager owns the tile color popup");
        check(picker.hexText().equals(new TileHighlightSettings(controller.values()).hex()),"Popup keeps saved tile color");
        picker.leftPressed(picker.x+20,picker.y+18,0);picker.mouseDragged(picker.x+100,picker.y+45);picker.leftReleased(picker.x+100,picker.y+45);
        String draft=(String)field(panel,"draftColor");
        WaypointerLayoutProbe.verifyStableOpacity(popup);
        WaypointerLayoutProbe.savePreview(popup,Paths.get(output,"tiles-"+language+"-color-popup.png").toString());
        click((WButton)field(popup,"apply"));hub.gameTick();
        check(draft.equals(controller.values().getProperty(TileHighlightSettings.COLOR)),"Apply saves tile color");
        click(color);hub.gameTick();popup=(CreatureHighlightColorWindow)field(panel,"colorWindow");
        click((WButton)field(popup,"cancel"));check(field(panel,"colorWindow")==null,"Cancel removes tile popup");
        click((WButton)field(panel,"meteorColor"));hub.gameTick();popup=(CreatureHighlightColorWindow)field(panel,"colorWindow");
        picker=(WaypointColorPicker)field(popup,"picker");check(picker.hexText().equals(new TileHighlightSettings(controller.values()).meteorHex()),"Meteor has its own saved color");
        picker.leftPressed(picker.x+60,picker.y+30,0);picker.leftReleased(picker.x+60,picker.y+30);String meteorDraft=(String)field(panel,"draftColor");
        click((WButton)field(popup,"apply"));hub.gameTick();check(meteorDraft.equals(controller.values().getProperty(TileHighlightSettings.METEOR_COLOR)),"Meteor picker applies only meteor color");
        check(draft.equals(controller.values().getProperty(TileHighlightSettings.COLOR)),"Meteor edit retains non-rideable tile color");
        for(String name:new String[]{"harvestableColor","overagedColor"}) {
            java.util.Properties before=controller.values();String key=name.equals("harvestableColor")?TileHighlightSettings.HARVESTABLE_COLOR:TileHighlightSettings.OVERAGED_COLOR;
            click((WButton)field(panel,name));hub.gameTick();popup=(CreatureHighlightColorWindow)field(panel,"colorWindow");picker=(WaypointColorPicker)field(popup,"picker");
            TileHighlightSettings saved=new TileHighlightSettings(before);
            check(picker.hexText().equals(name.equals("harvestableColor")?saved.harvestableHex():saved.overagedHex()),"New rule has its own saved colour");
            picker.leftPressed(picker.x+80,picker.y+40,0);picker.leftReleased(picker.x+80,picker.y+40);String selected=(String)field(panel,"draftColor");
            click((WButton)field(popup,"apply"));hub.gameTick();check(selected.equals(controller.values().getProperty(key)),"New rule picker saves its own colour");
            for(String other:TileHighlightSettings.KEYS)if(!other.equals(key))check(java.util.Objects.equals(before.getProperty(other),controller.values().getProperty(other)),"Colour edit preserves "+other);
            click((WButton)field(panel,name));hub.gameTick();popup=(CreatureHighlightColorWindow)field(panel,"colorWindow");picker=(WaypointColorPicker)field(popup,"picker");
            picker.leftPressed(picker.x+10,picker.y+10,0);picker.leftReleased(picker.x+10,picker.y+10);click((WButton)field(popup,"cancel"));
            check(selected.equals(controller.values().getProperty(key)),"Cancel retains saved new rule colour");
        }
        click(color);hub.gameTick();popup=(CreatureHighlightColorWindow)field(panel,"colorWindow");
        hub.select(WaypointerSection.ALL_WAYPOINTS);check(!panel.colorWindowOpen(),"Section change closes tile popup");
        hub.select(WaypointerSection.TILES_HIGHLIGHT);hub.gameTick();
    }
    static void verifyLanguage(WaypointManagerController controller)throws Exception {
        Messages.select("en");WaypointerHubWindow hub=new WaypointerHubWindow(controller);hub.setSize(1,480);hub.select(WaypointerSection.TILES_HIGHLIGHT);hub.gameTick();
        TileHighlightPanel original=panel(hub);click((WButton)field(original,"overagedColor"));hub.gameTick();
        CreatureHighlightColorWindow old=(CreatureHighlightColorWindow)field(original,"colorWindow");
        WaypointColorPicker picker=(WaypointColorPicker)field(old,"picker");
        picker.leftPressed(picker.x+40,picker.y+25,0);picker.leftReleased(picker.x+40,picker.y+25);
        String draft=(String)field(original,"draftColor");Messages.select("ru");hub.gameTick();
        TileHighlightPanel translated=panel(hub);check(!WurmComponent.hud.getComponents().contains(old),"Language replaces the old popup");
        check(translated.colorWindowOpen()&&draft.equals(field(translated,"draftColor")),"Language retains tile popup draft");
        check(TileHighlightSettings.OVERAGED_COLOR.equals(field(translated,"colorKey")),"Language retains the edited tile rule");
        hub.prepareDetach();check(!translated.colorWindowOpen(),"Manager cleanup removes tile popup");Messages.select("en");
    }
    @SuppressWarnings("unchecked") private static TileHighlightPanel panel(WaypointerHubWindow hub)throws Exception {
        return (TileHighlightPanel)((java.util.Map<WaypointerSection,WaypointerContentPanel>)field(hub,"panels")).get(WaypointerSection.TILES_HIGHLIGHT);
    }
    private static void click(WButton b){int x=b.x+b.width/2,y=b.y+b.height/2;b.leftPressed(x,y,0);b.leftReleased(x,y);}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
