package com.wurmonline.client.renderer.gui;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.chamomilo.wurm.ui.v1.UiDensity;
import org.chamomilo.wurm.ui.v1.UiTypography;

/** Branded typography, reserved sort space and independent fixed-alpha checks. */
final class TableHeaderProbe {
    static void verifyFixed(WurmArrayPanel<?> row, WurmScrollPanel scroll, int expectedY) {
        FlexComponent viewport=(FlexComponent)(Object)scroll.offs;
        check(row.y==expectedY,"Heading stays fixed while rows scroll");
        check(row.y+row.height<=viewport.y,"Heading stays above the clipped body");
        check(row.parent!=scroll.content&&row.parent!=viewport,"Heading is outside scroll content");
        for(FlexComponent cell:row.components)if(cell instanceof WaypointerTableHeader) {
            check(row.getComponentAt(cell.x+cell.width/2,cell.y+cell.height/2)==cell,"Fixed heading retains native hit testing");
        }
    }
    static void verify(WurmArrayPanel<?> row) {
        check(row instanceof WaypointerTableHeader.Row&&row.height==32,"Standard shared 32px header band: "+row.getClass().getSimpleName()+" height="+row.height);
        int baseline=-1;String group=null;
        for(FlexComponent component:row.components) {
            if(!(component instanceof WButton))continue;
            check(component instanceof WaypointerTableHeader,"Every table heading uses the common component");
            WaypointerTableHeader header=(WaypointerTableHeader)component;
            if(group==null)group=header.group;
            check(group.equals(header.group),"One named heading group per table");
            check(header.text==header.textBold&&((WaypointerLayoutProbe.ProbeFont)header.text).awtFont().equals(UiTypography.font(18,true,UiDensity.HIGH)),"Shared 18px Alegreya Sans SC Bold in every state");
            check(header.height==32&&header.width>=header.text.getWidth(header.getLabel())+34,"Caption and indicator have separate space");
            for(boolean hover:new boolean[]{false,true}) {
                header.hovered=hover;WaypointerLayoutProbe.clearPaintedLabel(header.getLabel());header.render(null,1f);
                Point point=WaypointerLayoutProbe.paintedLabel(header.getLabel());check(point!=null,"Full heading is painted");
                if(baseline<0)baseline=point.y-header.y;
                check(point.y-header.y==baseline,"All headings and hover states share one baseline");
                Rectangle ink=UiTypography.ink(header.getLabel(),18,true,UiDensity.HIGH);
                check(point.y+ink.y>=header.y+3&&point.y+ink.y+ink.height<=header.y+header.height-3,"Heading ink clears frame");
                check(point.x+header.text.getWidth(header.getLabel())<=header.x+header.width-28,"Heading clears fixed sort slot");
            }
            header.hovered=false;
            NativeUiRenderFixture.beginAlphaFrame();header.render(null,1f);List<Float> expected=NativeUiRenderFixture.endAlphaFrame();
            for(float alpha:new float[]{.15f,.85f,.35f}){NativeUiRenderFixture.beginAlphaFrame();header.render(null,alpha);check(expected.equals(NativeUiRenderFixture.endAlphaFrame()),"Header text and glyph opacity stays fixed");}
        }
        check(group!=null,"Header contains columns");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
