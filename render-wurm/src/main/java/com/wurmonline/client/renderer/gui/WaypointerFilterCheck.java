package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.chamomilo.wurm.ui.v1.*;

/** Separate native hit target using the shared kit's square chrome and check glyph. */
final class WaypointerFilterCheck extends WButton implements ButtonListener {
    private final Runnable action;
    private final UiButtonMotion motion=new UiButtonMotion();
    private boolean checked;
    WaypointerFilterCheck(boolean checked,Runnable action){
        super("");this.checked=checked;this.action=action;
        setButtonListener(this);sizeFlags=0;setSize(28,28);sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;
    }
    @Override void setSize(int width,int height){super.setSize(28,28);}
    void setChecked(boolean checked){this.checked=checked;}
    boolean checked(){return checked;}
    @Override public void buttonPressed(WButton button){motion.pointerPressed(System.nanoTime());}
    @Override public void buttonClicked(WButton button){if(isEnabled())action.run();}
    @Override protected void renderComponent(Queue queue,float ignoredAlpha){
        motion.update(isEnabled(),hovered,isCloseHovered,System.nanoTime());
        ChamomiloUiV1Canvas canvas=WaypointerUi.canvas(this,queue);
        UiPainter.button(canvas,motion.brightness(),motion.depth(),motion.hover(),UiScale.BASE,1f,x,y,width,height);
        int shift=Math.round(motion.depth());
        UiIcon.MAXIMIZE.paint(canvas,UiColor.MUTED,1f,x+6+shift,y+6+shift,20);
        if(checked)UiIcon.CHECK.paint(canvas,UiColor.TEXT,1f,x+6+shift,y+6+shift,20);
    }
}
