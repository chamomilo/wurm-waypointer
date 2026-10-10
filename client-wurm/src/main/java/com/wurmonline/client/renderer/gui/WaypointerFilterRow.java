package com.wurmonline.client.renderer.gui;

/** Shared layout for literal comma-separated inclusion and exclusion filters. */
final class WaypointerFilterRow extends WurmBorderPanel {
    private final WurmInputField editor;
    private final String help;
    WaypointerFilterRow(String label, WurmInputField input, WButton clear, String example) {
        super("waypointer.filter.row");
        setInitialSize(800,56,false);
        sizeFlags=FIXED_HEIGHT;
        editor=input;
        help=org.waypoints.next.i18n.Messages.text(label.startsWith("+")
                ? "Show rows matching any comma-separated fragment. Empty means all rows."
                : "Hide rows matching any comma-separated fragment. Empty excludes nothing.");
        WurmLabel caption=new WaypointerLabel(label,help);
        caption.setInitialSize(800,24,false);
        caption.sizeFlags=FIXED_HEIGHT;
        caption.text=com.wurmonline.client.renderer.gui.text.WaypointerFonts.body(true);
        setComponent(caption,NORTH);
        WurmBorderPanel body=new WurmBorderPanel("filter.body");
        input.prompt="";
        input.setInitialSize(310,32,false);
        body.setComponent(WaypointerUi.view(input),CENTER);
        clear.setHoverString(org.waypoints.next.i18n.Messages.text("Clear this text filter."));
        WurmArrayPanel<FlexComponent> trailing=new WurmArrayPanel<FlexComponent>("filter.trailing",1);
        trailing.addComponent(clear);
        trailing.addComponent(new FlexComponent("filter.gap",0,0,6,32){{sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;}});
        WurmLabel hint=new WaypointerLabel(example);
        hint.setInitialSize(Math.max(250,hint.text.getWidth(org.waypoints.next.i18n.Messages.text(example))+12),32,false);
        trailing.addComponent(hint);
        trailing.sizeFlags=FIXED_WIDTH;
        body.setComponent(trailing,EAST);
        setComponent(body,CENTER);
    }
    @Override public void pick(com.wurmonline.client.renderer.PickData data,int mouseX,int mouseY){
        super.pick(data,mouseX,mouseY);if(data!=null&&editor.contains(mouseX,mouseY))data.addText(help);
    }
}
