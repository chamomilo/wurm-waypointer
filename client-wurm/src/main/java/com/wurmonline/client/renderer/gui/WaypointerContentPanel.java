package com.wurmonline.client.renderer.gui;

/** A reusable hub view without independent native window chrome or lifecycle. */
abstract class WaypointerContentPanel extends WurmBorderPanel {
    private FlexComponent content;
    private String title="";
    WaypointerContentPanel(String name,boolean ignored){super(name);text=com.wurmonline.client.renderer.gui.text.WaypointerFonts.body();textBold=com.wurmonline.client.renderer.gui.text.WaypointerFonts.body(true);}
    void setComponent(FlexComponent next){content=next;setComponent(next,CENTER);}
    FlexComponent getComponent(){return content;}
    void setTitle(String next){title=org.waypoints.next.i18n.Messages.text(next);}
    String getTitle(){return title;}
    void closePressed(){}
    void entered(){}
    void leaving(){}
    int minimumContentWidth(){return 900;}
    // Native components normally delegate focus queries to their parent. The
    // hub delegates down to this panel, so a panel must terminate that lookup.
    @Override boolean hasInputField(){return false;}
    @Override WurmInputField getInputField(){return null;}
    boolean mouseWheeledAt(int x,int y,int delta){return false;}
}
