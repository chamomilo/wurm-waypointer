package com.wurmonline.client.renderer.gui;

import org.waypoints.next.surroundings.*;
import org.waypoints.next.ui.SettingsController;
import org.waypoints.next.i18n.Messages;
import java.lang.reflect.*;
import java.nio.file.Paths;
import java.util.*;

final class CreatureHighlightPanelProbe {
    @SuppressWarnings("unchecked") static void verify(WaypointerHubWindow hub,CreatureHighlightPanel panel,String output,String language) throws Exception {
        SettingsController settings=(SettingsController)field(panel,"controller");
        Map<WButton,CreatureHighlightRule> toggles=(Map<WButton,CreatureHighlightRule>)field(panel,"toggles");
        Map<WButton,CreatureHighlightRule> colors=(Map<WButton,CreatureHighlightRule>)field(panel,"colors");
        Map<CreatureHighlightRule,WurmLabel> labels=(Map<CreatureHighlightRule,WurmLabel>)field(panel,"labels");
        Map<CreatureHighlightRule,WaypointerFilterCheck> autoMarks=(Map<CreatureHighlightRule,WaypointerFilterCheck>)field(panel,"autoMarks");
        WurmArrayPanel<?> traits=(WurmArrayPanel<?>)field(panel,"traits"),categories=(WurmArrayPanel<?>)field(panel,"categories");
        Field caption=WurmLabel.class.getDeclaredField("label");caption.setAccessible(true);
        check(field(panel,"colorWindow")==null,"No colour picker is created before a Color action");
        WaypointerLayoutProbe.savePreview(hub,Paths.get(output,"highlight-"+language+"-compact-initial.png").toString());
        check(toggles.size()==CreatureHighlightRule.values().length,"Every highlight has a toggle");check(autoMarks.size()==3,"Unique, Champion and Diseased have auto-mark checkboxes");
        for(Map.Entry<WButton,CreatureHighlightRule> entry:toggles.entrySet()) {
            WButton button=entry.getKey();hub.gameTick();
            check(button.y>=panel.y&&button.y+button.height<=panel.y+panel.height,"Every rule fits without scrolling: "+entry.getValue()+" button="+button.y+"+"+button.height+" panel="+panel.y+"+"+panel.height+" hub="+hub.height);
            WurmArrayPanel<?> column=CreatureHighlightPanel.leftColumn(entry.getValue())?traits:categories;
            check(button.x>=column.x&&button.x+button.width<=column.x+column.width,"Column bounds "+entry.getValue()+" button="+button.x+"+"+button.width+" column="+column.x+"+"+column.width);
            check(hub.getComponentAt(button.x+button.width/2,button.y+button.height/2)==button,"Native hit reaches every rule");
            WurmLabel name=labels.get(entry.getValue());check(button.x<=column.x+10 && button.x+button.width<name.x,"ON/OFF is the first column, before the creature name");
            String expected=Messages.text(entry.getValue().label())+" ("+settings.detectedCreatures().getOrDefault(entry.getValue(),0)+")";
            check(expected.equals(caption.get(name)),"Every rule shows its current detected count");
            check(((WaypointerLabel)name).textWidth()<=name.width,"Rule name fits the name column");
            boolean before=new CreatureHighlightSettings(settings.values()).enabled(entry.getValue());click(button);hub.gameTick();
            check(new CreatureHighlightSettings(settings.values()).enabled(entry.getValue())!=before,"ON/OFF saves immediately");
            check(expected.equals(caption.get(name)),"Outline ON/OFF does not change detection counts");
            check(!((org.chamomilo.wurm.ui.v1.UiCompactLayout)field(button,"layout")).shortened,"Switch caption fits");
            check(((WaypointerCompactAction)button).groupId.equals("highlight.switches"),"One shared switch group");
            if(before)click(button); // Leave a green ON row for the preview.
        }
        hub.gameTick();
        for(Map.Entry<CreatureHighlightRule,WaypointerFilterCheck> entry:autoMarks.entrySet()) {
            WaypointerFilterCheck check=entry.getValue();
            hub.gameTick();
            check(check.x>=panel.x && check.x+check.width<=panel.x+panel.width,"Auto-mark checkbox fits the viewport");
            check(hub.getComponentAt(check.x+check.width/2,check.y+check.height/2)==check,"Auto-mark checkbox is visible to native hit testing");
            boolean before=check.checked();click(check);
            check(new CreatureHighlightSettings(settings.values()).autoMark(entry.getKey())!=before,"Native checkbox saves independently");
        }
        CreatureHighlightSettings preserved=new CreatureHighlightSettings(settings.values());
        for(String name:Arrays.asList("allOff","allOn")) {
            WButton button=(WButton)field(panel,name);
            check(hub.getComponentAt(button.x+button.width/2,button.y+button.height/2)==button,"Bulk action is reachable: "+name);
            check(button.y+button.height<=traits.y,"Bulk actions stay above the two columns");
            check(!((ChamomiloUiV1Button)button).captionShortened()&&WaypointerButtonGroup.id(button.text).equals("highlight.bulk"),"Bulk toolbar uses shared, fully fitting captions");
            int fixedY=button.y;check(!panel.mouseWheeledAt(panel.x+10,panel.y+80,1),"Highlight grid does not need scrolling");hub.gameTick();check(button.y==fixedY,"Bulk actions stay fixed");
            click(button);hub.gameTick();CreatureHighlightSettings changed=new CreatureHighlightSettings(settings.values());
            for(CreatureHighlightRule rule:CreatureHighlightRule.values()) {
                check(changed.enabled(rule)==name.equals("allOn"),"Bulk action changes every outline, including offscreen size rules");
                check(changed.hex(rule).equals(preserved.hex(rule))&&changed.autoMark(rule)==preserved.autoMark(rule),"Bulk action preserves colours and independent Auto-mark");
            }
        }
        String before=settings.values().getProperty(CreatureHighlightRule.CHAMPION.key("color"),CreatureHighlightRule.CHAMPION.defaultColor());
        CreatureHighlightColorWindow popup=openDraft(hub,panel);
        WaypointColorPicker picker=(WaypointColorPicker)field(popup,"picker");
        check(WurmComponent.hud.getComponents().contains(popup),"Color opens a native HUD popup");
        check(hub.ownsHudComponent(picker),"Manager owns its picker popup for detach cleanup");
        check(popup.x>=0&&popup.y>=0&&popup.x+popup.width<=WurmComponent.hud.getWidth()&&popup.y+popup.height<=WurmComponent.hud.getHeight(),"Popup fits the HUD");
        check(popup.getComponentAt(picker.x+20,picker.y+18)==picker,"Native hit testing reaches popup picker");
        check(!before.equals(field(panel,"draftColor")),"HSV drag changes the selected draft");
        check(before.equals(settings.values().getProperty(CreatureHighlightRule.CHAMPION.key("color"),CreatureHighlightRule.CHAMPION.defaultColor())),"Drag waits for Apply color");
        WurmLabel animalName=labels.get(CreatureHighlightRule.ANIMALS);
        int oldWidth=animalName.width,oldHeight=animalName.height;
        int oldCount=settings.detectedCreatures().getOrDefault(CreatureHighlightRule.ANIMALS,0);String draft=(String)field(panel,"draftColor");
        HubLayoutProbe.detectedCount(CreatureHighlightRule.ANIMALS,99999);hub.gameTick();
        check((Messages.text(CreatureHighlightRule.ANIMALS.label())+" (99999)").equals(caption.get(animalName)),"Live population updates an existing label, including offscreen rows");
        check(((WaypointerLabel)animalName).textWidth()<=animalName.width,"Localized name and five-digit count fit the column");
        check(labels.get(CreatureHighlightRule.ANIMALS)==animalName&&animalName.width==oldWidth&&animalName.height==oldHeight,"Counter updates preserve native rows, geometry and scroll");
        check(field(panel,"colorWindow")==popup&&draft.equals(field(panel,"draftColor")),"Live count refresh keeps the popup and its draft");
        HubLayoutProbe.detectedCount(CreatureHighlightRule.ANIMALS,0);hub.gameTick();
        check((Messages.text(CreatureHighlightRule.ANIMALS.label())+" (0)").equals(caption.get(animalName)),"Unloaded population can return to zero");
        HubLayoutProbe.detectedCount(CreatureHighlightRule.ANIMALS,oldCount);hub.gameTick();
        WaypointerLayoutProbe.verifyStableOpacity(popup);
        WaypointerLayoutProbe.savePreview(popup,Paths.get(output,"highlight-"+language+"-color-popup.png").toString());
        for(String action:Arrays.asList("apply","cancel")) {
            WButton button=(WButton)field(popup,action);
            check(popup.getComponentAt(button.x+button.width/2,button.y+button.height/2)==button,"Native popup action hit: "+action);
            check(!((ChamomiloUiV1Button)button).captionShortened(),"Popup action caption fits: "+action);
        }
        click((WButton)field(popup,"apply"));check(field(panel,"draftColor").equals(settings.values().getProperty(CreatureHighlightRule.CHAMPION.key("color"))),"Apply saves the selected rule color");
        check(field(panel,"colorWindow")==null&&!WurmComponent.hud.getComponents().contains(popup),"Apply closes and removes the popup");
        String saved=settings.values().getProperty(CreatureHighlightRule.CHAMPION.key("color"));
        popup=openDraft(hub,panel);click((WButton)field(popup,"cancel"));
        check(saved.equals(settings.values().getProperty(CreatureHighlightRule.CHAMPION.key("color"))),"Cancel discards the colour draft");
        check(field(panel,"colorWindow")==null&&!WurmComponent.hud.getComponents().contains(popup),"Cancel removes the popup");
        popup=openDraft(hub,panel);popup.closePressed();
        check(field(panel,"colorWindow")==null&&!WurmComponent.hud.getComponents().contains(popup),"Standard close removes the popup");
        check(saved.equals(settings.values().getProperty(CreatureHighlightRule.CHAMPION.key("color"))),"Standard close keeps the saved colour");
        popup=openDraft(hub,panel);hub.select(org.waypoints.next.ui.WaypointerSection.ALL_WAYPOINTS);hub.gameTick();
        check(field(panel,"colorWindow")==null&&!WurmComponent.hud.getComponents().contains(popup),"Leaving the section closes its popup");
        hub.select(org.waypoints.next.ui.WaypointerSection.CREATURES_HIGHLIGHT);hub.gameTick();
        WaypointerLayoutProbe.verifyStableOpacity(hub);
        hub.gameTick();
        WaypointerLayoutProbe.savePreview(hub,Paths.get(output,"highlight-"+language+"-enabled.png").toString());
        hub.gameTick();
        hub.gameTick();WaypointerLayoutProbe.savePreview(hub,Paths.get(output,"highlight-"+language+"-controls.png").toString());
    }
    @SuppressWarnings("unchecked") static CreatureHighlightColorWindow openDraft(WaypointerHubWindow hub,CreatureHighlightPanel panel)throws Exception {
        Map<WButton,CreatureHighlightRule> colors=(Map<WButton,CreatureHighlightRule>)field(panel,"colors");
        WurmArrayPanel<?> traits=(WurmArrayPanel<?>)field(panel,"traits"),categories=(WurmArrayPanel<?>)field(panel,"categories");
        WButton color=null;for(Map.Entry<WButton,CreatureHighlightRule> entry:colors.entrySet())if(entry.getValue()==CreatureHighlightRule.CHAMPION)color=entry.getKey();
        hub.gameTick();
        click(color);hub.gameTick();CreatureHighlightColorWindow popup=(CreatureHighlightColorWindow)field(panel,"colorWindow");popup.gameTick();
        WaypointColorPicker picker=(WaypointColorPicker)field(popup,"picker");
        String before=picker.hexText();
        SettingsController controller=(SettingsController)field(panel,"controller");
        check(before.equals(new CreatureHighlightSettings(controller.values()).hex(CreatureHighlightRule.CHAMPION)),"Native popup attachment retains the saved rule colour");
        picker.leftPressed(picker.x+20,picker.y+18,0);picker.mouseDragged(picker.x+100,picker.y+45);picker.leftReleased(picker.x+100,picker.y+45);
        if(before.equals(picker.hexText())) {
            picker.leftPressed(picker.x+180,picker.y+65,0);picker.leftReleased(picker.x+180,picker.y+65);
        }
        check(!before.equals(picker.hexText()),"Popup draft differs from the saved colour");
        return popup;
    }
    private static void click(WButton button){int x=button.x+button.width/2,y=button.y+button.height/2;button.leftPressed(x,y,0);button.leftReleased(x,y);}
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
