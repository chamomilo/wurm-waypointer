package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.chamomilo.wurm.ui.v1.*;

/** Separate native hit target using the shared kit's square chrome and check glyph. */
final class WaypointerFilterCheck extends WButton implements ButtonListener {
    private final Runnable action;
    private final UiButtonMotion motion=new UiButtonMotion();
    private boolean checked;
    private int side=28;
    WaypointerFilterCheck(boolean checked,Runnable action){
        this(checked,action,28);
    }
    WaypointerFilterCheck(boolean checked,Runnable action,int side){
        super("");this.checked=checked;this.action=action;
        this.side=side;setButtonListener(this);sizeFlags=0;setSize(side,side);sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;
    }
    @Override void setSize(int width,int height){int size=side==0?28:side;super.setSize(size,size);}
    void setChecked(boolean checked){this.checked=checked;}
    boolean checked(){return checked;}
    @Override public void buttonPressed(WButton button){motion.pointerPressed(System.nanoTime());}
    @Override public void buttonClicked(WButton button){if(isEnabled())action.run();}
    @Override protected void renderComponent(Queue queue,float ignoredAlpha){
        motion.update(isEnabled(),hovered,isCloseHovered,System.nanoTime());
        ChamomiloUiV1Canvas canvas=WaypointerUi.canvas(this,queue);
        UiPainter.button(canvas,motion.brightness(),motion.depth(),motion.hover(),UiScale.BASE,1f,x,y,width,height);
        int shift=Math.round(motion.depth());
        int size=Math.min(20,side-8),left=x+(width-size)/2+shift,top=y+(height-size)/2+shift;
        UiIcon.MAXIMIZE.paint(canvas,UiColor.MUTED,1f,left,top,size);
        if(checked)UiIcon.CHECK.paint(canvas,UiColor.TEXT,1f,left,top,size);
    }
}
