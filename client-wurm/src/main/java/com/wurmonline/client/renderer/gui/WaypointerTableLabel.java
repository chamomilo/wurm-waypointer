package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.chamomilo.wurm.ui.v1.UiColor;

/** A single-line table cell keeps its full tooltip while fitting the visible column. */
final class WaypointerTableLabel extends WaypointerLabel {
    private String caption;
    private String hint;
    private boolean captionHint;
    private final boolean translate;
    private final boolean rightAligned;
    WaypointerTableLabel(String caption) { this(caption, caption); }
    WaypointerTableLabel(String caption, String tooltip) { this(caption,tooltip,true); }
    WaypointerTableLabel(String caption,String tooltip,boolean translate){this(caption,tooltip,translate,false);}
    WaypointerTableLabel(String caption,String tooltip,boolean translate,boolean rightAligned){super(caption,tooltip,false,translate);this.translate=translate;this.rightAligned=rightAligned;this.caption=translate?org.waypoints.next.i18n.Messages.text(caption):caption;hint=tooltip;captionHint=caption.equals(tooltip);}
    @Override void setLabel(String value) { caption = translate?org.waypoints.next.i18n.Messages.text(value):value;if(captionHint)hint=value;super.setLabel(value,hint); }
    void setHint(String value){hint=value;captionHint=false;super.setLabel(caption,hint);}
    @Override protected void renderComponent(Queue queue, float ignoredAlpha) {
        String visible = caption;
        int available = Math.max(0, width - 8);
        if (text.getWidth(visible) > available) {
            int end = visible.length();
            while (end > 0 && text.getWidth(visible.substring(0, end) + "…") > available)
                end = visible.offsetByCodePoints(end, -1);
            visible = text.getWidth("…") <= available ? visible.substring(0, end) + "…" : "";
        }
        text.moveTo(rightAligned?x+width-4-text.getWidth(visible):x+4, y + (height - text.getHeight()) / 2 + text.getAscent());
        text.paint(queue, visible, UiColor.TEXT.red, UiColor.TEXT.green, UiColor.TEXT.blue, 1f);
    }
}
