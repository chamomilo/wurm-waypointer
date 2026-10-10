package com.wurmonline.client.renderer.gui;

import org.waypoints.next.i18n.Messages;

/** Standard kit dialog with the existing explicit confirmation callback. */
final class WaypointerConfirmWindow extends WaypointerUiWindow {
    private final ConfirmListener listener;
    WaypointerConfirmWindow(ConfirmListener listener, String prompt, String detail) {
        super("wurm-waypointer.confirm", false);
        this.listener = listener;
        setTitle(Messages.text("Delete"));
        WurmArrayPanel<FlexComponent> lines = new WurmArrayPanel<FlexComponent>("confirm.lines", 0, true);
        addLines(lines, Messages.text(prompt));addLines(lines, Messages.text(detail));
        WurmArrayPanel<FlexComponent> actions = new WurmArrayPanel<FlexComponent>("confirm.actions", 1);
        actions.addComponent(action("Delete", () -> listener.confirmed()));
        actions.addComponent(action("Cancel", () -> listener.cancelled()));
        WurmBorderPanel root = new WurmBorderPanel("confirm.root");
        root.setComponent(lines, CENTER);root.setComponent(actions, SOUTH);setComponent(root);
        setInitialSize(600, 110 + lines.calcHeight(), false);
        onClose(() -> listener.cancelled());
        if (hud != null) { setPosition(Math.max(0,(hud.getWidth()-width)/2),Math.max(0,(hud.getHeight()-height)/2));show(hud); }
    }
    private static ChamomiloUiV1Button action(String caption,Runnable callback){
        ChamomiloUiV1Button button=new ChamomiloUiV1Button(Messages.text(caption),150,callback);
        button.setDensity(org.chamomilo.wurm.ui.v1.UiDensity.LOW);return button;
    }
    private void addLines(WurmArrayPanel<FlexComponent> lines, String value) {
        StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (line.length()>0 && text.getWidth(line+" "+word)>550) { addLine(lines,line.toString());line.setLength(0); }
            if (line.length()>0) line.append(' ');line.append(word);
        }
        addLine(lines,line.toString());
    }
    private void addLine(WurmArrayPanel<FlexComponent> lines, String value) {
        WurmLabel label = new WaypointerLabel(value,value,false,false);label.setInitialSize(550,text.getHeight()+8,false);lines.addComponent(label);
    }
    void close() { dispose(); }
}
