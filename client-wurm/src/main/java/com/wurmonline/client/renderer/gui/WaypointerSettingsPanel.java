package com.wurmonline.client.renderer.gui;

import org.waypoints.next.i18n.Messages;
import org.waypoints.next.ui.*;
import java.util.*;

/** All mod preferences in one scrollable view; editing does not apply a half-finished draft. */
final class WaypointerSettingsPanel extends WaypointerContentPanel implements ButtonListener,InputFieldListener {
    private final SettingsController controller;
    private final Map<SettingSpec,FlexComponent> editors=new LinkedHashMap<SettingSpec,FlexComponent>();
    private final WurmLabel status=new WaypointerLabel("");
    private final WButton save,reset;
    private String displayedStatus="";
    private WurmScrollPanel scroll;
    private int minimumWidth;
    WaypointerSettingsPanel(SettingsController controller){
        super("waypointer.settings",false);this.controller=controller;Properties values=controller.values();
        int labelWidth=280;for(SettingSpec spec:SettingSpec.ALL)if(!spec.key.equals("language"))labelWidth=Math.max(labelWidth,text.getWidth(Messages.text(spec.label))+12);
        minimumWidth=Math.max(620,labelWidth+250);
        WurmArrayPanel<FlexComponent> content=new WurmArrayPanel<FlexComponent>("settings.rows",0,true);String group="";
        for(SettingSpec spec:SettingSpec.ALL){
            if(spec.key.equals("language"))continue; // The shared updater owns language selection.
            if(!group.equals(spec.group)){group=spec.group;WurmLabel heading=new WaypointerLabel(Messages.text(group));heading.text=com.wurmonline.client.renderer.gui.text.WaypointerFonts.body(true);heading.setInitialSize(800,28,false);content.addComponent(heading);}
            WurmBorderPanel row=new WurmBorderPanel("setting."+spec.key);row.setInitialSize(800,30,false);row.sizeFlags=FIXED_HEIGHT;
            WurmLabel label=new WaypointerLabel(Messages.text(spec.label));label.setInitialSize(labelWidth,30,false);label.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;row.setComponent(label,WEST);
            String value=values.getProperty(spec.key,spec.defaultValue);FlexComponent editor;
            if(spec.type==SettingSpec.Type.BOOLEAN||spec.type==SettingSpec.Type.CHOICE){
                int selected=Arrays.asList(spec.choices).indexOf(value);String[] labels=spec.type==SettingSpec.Type.BOOLEAN?Messages.texts(new String[]{"Off","On"}):Messages.texts(spec.choices);
                editor=WaypointerUi.dropDown("setting."+spec.key,Math.max(0,selected),labels);
            }else{WurmInputField input=WaypointerUi.input("setting."+spec.key,this);input.prompt="";input.setTextMoveToEnd(value);editor=input;}
            editor.setInitialSize(240,28,false);row.setComponent(WaypointerUi.view(editor),CENTER);editors.put(spec,editor);content.addComponent(row);
        }
        scroll=new ChamomiloUiV1ScrollPanel("settings.scroll",content,false,true);setComponent(scroll,CENTER);
        WurmArrayPanel<FlexComponent> footer=new WurmArrayPanel<FlexComponent>("settings.footer",1);
        save=WaypointerUi.button("Save settings",this,130);reset=WaypointerUi.button("Reset draft",this,120);footer.componentWidthOffset=4;footer.addComponent(save);footer.addComponent(reset);status.setInitialSize(240,28,false);footer.addComponent(status);setComponent(footer,SOUTH);
        WaypointerButtonGroup.peers("settings.footer",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,28,save,reset);
    }
    @Override public void buttonPressed(WButton button){}
    @Override public void buttonClicked(WButton button){
        if(button==save){try{Properties values=controller.values();for(Map.Entry<SettingSpec,FlexComponent> item:editors.entrySet()){SettingSpec spec=item.getKey();FlexComponent editor=item.getValue();String value=editor instanceof WurmDropDown?spec.choices[((WurmDropDown)editor).getValue()]:((WurmInputField)editor).getText();values.setProperty(spec.key,spec.validate(value));}controller.save(values);status.setLabel(controller.status());}catch(RuntimeException failure){status.setLabel(Messages.format("Check setting: {0}",Messages.text(failure.getMessage())));}}
        else if(button==reset){Properties values=controller.values();for(Map.Entry<SettingSpec,FlexComponent> item:editors.entrySet()){SettingSpec spec=item.getKey();String value=values.getProperty(spec.key,spec.defaultValue);if(item.getValue() instanceof WurmDropDown)((WurmDropDown)item.getValue()).setValue(Math.max(0,Arrays.asList(spec.choices).indexOf(value)));else((WurmInputField)item.getValue()).setTextMoveToEnd(value);}}
    }
    @Override public void gameTick(){super.gameTick();String text=controller.status();if(!displayedStatus.equals(text)){displayedStatus=text;status.setLabel(text);}}
    @Override public void handleInput(String value){}
    @Override public void handleInputChanged(WurmInputField field,String value){}
    @Override public void handleEscape(WurmInputField field){}
    @Override boolean mouseWheeledAt(int x,int y,int delta){if(!scroll.contains(x,y))return false;scroll.mouseWheeled(x,y,delta);return true;}
    @Override boolean hasInputField(){return getInputField()!=null;}
    @Override int minimumContentWidth(){return minimumWidth;}
    @Override WurmInputField getInputField(){for(FlexComponent editor:editors.values())if(editor instanceof WurmInputField)return (WurmInputField)editor;return null;}
}
