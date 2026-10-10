package com.wurmonline.client.renderer.gui;

/** Shared layout for literal comma-separated inclusion and exclusion filters. */
final class WaypointerFilterRow extends WurmBorderPanel {
    private final WurmInputField editor;
    private final String help;
    WaypointerFilterRow(String label, WurmInputField input, WButton clear, String example) {
        super("waypointer.filter.row");
        setInitialSize(800,28,false);
        sizeFlags=FIXED_HEIGHT;
        editor=input;
        WaypointerButtonGroup.peers("text-filter.clear",org.chamomilo.wurm.ui.v1.UiDensity.HIGH,28,clear);
        help=org.waypoints.next.i18n.Messages.text(label.startsWith("+")
                ? "Show rows matching any comma-separated fragment. Empty means all rows."
                : "Hide rows matching any comma-separated fragment. Empty excludes nothing.")
                +" "+org.waypoints.next.i18n.Messages.text(example);
        WurmLabel caption=new WaypointerLabel(label,help);
        caption.text=com.wurmonline.client.renderer.gui.text.WaypointerFonts.body(true);
        caption.setInitialSize(Math.max(58,caption.text.getWidth(org.waypoints.next.i18n.Messages.text(label))+10),28,false);
        caption.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;
        setComponent(caption,WEST);
        WurmBorderPanel body=new WurmBorderPanel("filter.body");
        input.prompt="";
        input.setInitialSize(310,28,false);
        body.setComponent(WaypointerUi.view(input),CENTER);
        WurmArrayPanel<FlexComponent> trailing=new WurmArrayPanel<FlexComponent>("filter.trailing",1);
        trailing.addComponent(clear);
        clear.setHoverString(org.waypoints.next.i18n.Messages.text("Clear this text filter.")+" "+help);
        trailing.sizeFlags=FIXED_WIDTH;
        body.setComponent(trailing,EAST);
        setComponent(body,CENTER);
    }
    @Override public void pick(com.wurmonline.client.renderer.PickData data,int mouseX,int mouseY){
        super.pick(data,mouseX,mouseY);if(data!=null&&editor.contains(mouseX,mouseY))data.addText(help);
    }
}
