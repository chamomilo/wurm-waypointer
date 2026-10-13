package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts;
import com.wurmonline.client.renderer.gui.text.TextFont;
import org.chamomilo.wurm.ui.v1.*;

/** Native action using the shared Compact group metrics, with optional green selection. */
final class WaypointerCompactAction extends WButton implements ButtonListener {
    final String groupId;
    private final UiButtonMotion motion=new UiButtonMotion();
    private final Runnable action;
    private UiCompactLayout layout;
    private boolean active;
    WaypointerCompactAction(String groupId,String caption,UiCompactLayout layout,Runnable action) {
        super(caption);this.groupId=groupId;this.action=action;setButtonListener(this);apply(caption,layout);
        text=ChamomiloUiV1Fonts.caption(layout.fontPixels,false,UiDensity.HIGH);
        textBold=ChamomiloUiV1Fonts.caption(layout.fontPixels,true,UiDensity.HIGH);
    }
    void apply(String caption,UiCompactLayout layout) {
        this.layout=layout;setLabel(caption,false);setHoverString(caption);
        sizeFlags=0;setSize(layout.width,layout.height);sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;
    }
    void active(boolean selected){active=selected;}
    @Override public void buttonPressed(WButton button){motion.pointerPressed(System.nanoTime());}
    @Override public void buttonClicked(WButton button){if(isEnabled())action.run();}
    @Override protected void renderComponent(Queue queue,float ignoredAlpha) {
        motion.update(isEnabled(),hovered,isCloseHovered,System.nanoTime());
        ChamomiloUiV1Canvas canvas=WaypointerUi.canvas(this,queue);
        UiPainter.button(canvas,motion.brightness(),motion.depth(),motion.hover(),UiScale.BASE,1f,x,y,width,height);
        UiColor color=UiPainter.buttonCaptionColor(isEnabled(),motion.hover());
        if(active&&isEnabled()) {
            canvas.fill(UiColor.rgb(0x61bd70),.18f,x+5,y+5,width-10,height-10);
            canvas.fill(UiColor.rgb(0x84e397),1f,x+5,y+height-4,width-10,2);
            if(!hovered)color=UiColor.rgb(0x84e397);
        }
        boolean bold=isEnabled()&&hovered;TextFont font=bold?textBold:text;
        int shift=Math.round(motion.depth());
        font.moveTo(x+layout.textX(0,bold)+shift,y+layout.baseline(0)+shift);
        font.paint(queue,layout.rows()[0],color.red,color.green,color.blue,1f);
    }
}
