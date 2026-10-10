package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts;
import org.chamomilo.wurm.ui.v1.*;
import org.waypoints.next.i18n.Messages;
import java.awt.Rectangle;

/** Shared Chamomilo table heading role, separate from action-button typography. */
final class WaypointerTableHeader extends WButton {
    static final int FONT_PIXELS=16;
    final String group;
    private final int order,baseline;
    private final boolean rightAligned,sortable;
    WaypointerTableHeader(String group,String caption,int width,int order,int baseline,boolean rightAligned,ButtonListener listener,String hint){
        super(Messages.text(caption));this.group=group;this.order=order;this.baseline=baseline;
        this.rightAligned=rightAligned;sortable=listener!=null;
        text=textBold=ChamomiloUiV1Fonts.caption(FONT_PIXELS,true,UiDensity.HIGH);
        resize(width,28);
        setButtonListener(listener);
        setHoverString(Messages.text(caption)+". "+Messages.text(hint));
    }
    int order(){return order;}
    boolean sortable(){return sortable;}
    void resize(int width,int height){sizeFlags=0;setSize(width,height);sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;}
    static int minimumWidth(String caption){return UiTypography.width(Messages.text(caption),FONT_PIXELS,true,UiDensity.HIGH)+28;}
    static int baseline(String[] captions){
        int top=0,bottom=0;
        for(String caption:captions){Rectangle ink=UiTypography.ink(Messages.text(caption),FONT_PIXELS,true,UiDensity.HIGH);top=Math.min(top,ink.y);bottom=Math.max(bottom,ink.y+ink.height);}
        return (28-(bottom-top))/2-top;
    }
    @Override protected void leftPressed(int mx,int my,int clicks){if(sortable)super.leftPressed(mx,my,clicks);}
    @Override protected void renderComponent(Queue queue,float ignoredAlpha){
        UiCanvas canvas=WaypointerUi.canvas(this,queue);
        int shift=sortable&&isCloseHovered?1:0,slotX=x+width-16;
        String caption=getLabel();int available=Math.max(0,width-28);
        if(text.getWidth(caption)>available){int end=caption.length();while(end>0&&text.getWidth(caption.substring(0,end)+"…")>available)end=caption.offsetByCodePoints(end,-1);caption=text.getWidth("…")<=available?caption.substring(0,end)+"…":"";}
        UiColor color=!isEnabled()?UiColor.MUTED:sortable&&(order!=0||hovered)?UiPainter.BUTTON_HOVER_COLOR:UiColor.TEXT;
        text.moveTo((rightAligned?slotX-6-text.getWidth(caption):x+6)+shift,y+baseline+shift);text.paint(queue,caption,color.red,color.green,color.blue,1f);
        if(!sortable)return;
        UiColor glyph=!isEnabled()?UiColor.MUTED:order!=0||hovered?UiPainter.BUTTON_HOVER_COLOR:UiColor.MUTED;
        if(order==0){UiIcon.CHEVRON_UP.paint(canvas,glyph,1f,slotX+shift,y+5+shift,12);UiIcon.CHEVRON_DOWN.paint(canvas,glyph,1f,slotX+shift,y+11+shift,12);}
        else (order>0?UiIcon.CHEVRON_UP:UiIcon.CHEVRON_DOWN).paint(canvas,glyph,1f,slotX+shift,y+8+shift,12);
    }
    static final class Row extends WurmArrayPanel<FlexComponent> {
        Row(String group){super(group,DIR_HORIZONTAL);}
        @Override protected void renderComponent(Queue queue,float ignoredAlpha){
            UiCanvas canvas=WaypointerUi.canvas(this,queue);
            UiPainter.background(canvas,UiBackground.LEATHER,1f,x,y,width,height);
            super.renderComponent(queue,1f);
            UiPainter.frame(canvas,3,1f,x,y,width,height);
        }
    }
}
