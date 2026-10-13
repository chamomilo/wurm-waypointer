package com.wurmonline.client.renderer.gui;

import org.chamomilo.wurm.ui.v1.UiDensity;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.surroundings.CreatureHighlightRule;

/** Colour editing exists only while a rule's Color action is open. */
final class CreatureHighlightColorWindow extends WaypointerUiWindow implements ButtonListener {
    private final WaypointColorPicker picker;
    private final WButton apply,cancel;
    private final Runnable applyColor,close;

    CreatureHighlightColorWindow(CreatureHighlightRule rule,String hex,WaypointColorPicker.Listener changed,
                                 Runnable applyColor,Runnable close) {
        this("waypointer.creatures-highlight.color",rule.label(),hex,changed,applyColor,close);
    }
    CreatureHighlightColorWindow(String id,String label,String hex,WaypointColorPicker.Listener changed,
                                 Runnable applyColor,Runnable close) {
        super(id,false);
        this.applyColor=applyColor;this.close=close;
        setTitle(Messages.format("Color: {0}",Messages.text(label)));
        int rgb=Integer.parseInt(hex.substring(1),16);
        picker=new WaypointColorPicker(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,changed);
        WurmBorderPanel content=new WurmBorderPanel("highlight.color-content");
        content.setComponent(picker,CENTER);
        WurmBorderPanel footer=new WurmBorderPanel("highlight.color-footer");
        footer.setInitialSize(620,34,false);footer.sizeFlags=FIXED_HEIGHT;
        apply=WaypointerUi.button("Apply color",this,180);cancel=WaypointerUi.button("Cancel",this,180);
        WaypointerButtonGroup.peers("highlight.color-actions",UiDensity.HIGH,28,apply,cancel);
        WurmArrayPanel<FlexComponent> actions=new WurmArrayPanel<FlexComponent>("highlight.color-actions",1);
        actions.componentWidthOffset=6;actions.addComponent(apply);actions.addComponent(cancel);
        footer.setComponent(actions,SOUTH);content.setComponent(footer,SOUTH);setComponent(content);
        setInitialSize(640,220,false);onClose(close);
    }
    @Override public void buttonPressed(WButton button) { }
    @Override public void buttonClicked(WButton button) {
        if(button==apply)applyColor.run();else if(button==cancel)close.run();
    }
}
