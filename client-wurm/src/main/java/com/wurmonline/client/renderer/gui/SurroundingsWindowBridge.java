package com.wurmonline.client.renderer.gui;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.ui.SurroundingsController;
import com.wurmonline.client.settings.SavePosManager;
import java.util.List;
import java.util.logging.*;

/** Compatibility entry point. The browser is a hub panel; monitoring is a compact companion. */
public final class SurroundingsWindowBridge {
    private static HeadsUpDisplay owner;
    private static SurroundingsMonitoringWindow monitoring;
    private static final Logger LOG=Logger.getLogger("WurmWaypointer.Surroundings");
    private SurroundingsWindowBridge(){}
    public static synchronized void open(HeadsUpDisplay hud,SurroundingsController controller){WurmWaypointerRuntime.openSurroundings();}
    public static synchronized boolean mouseWheeled(HeadsUpDisplay hud,int x,int y,int delta){
        if(WaypointManagerWindowBridge.mouseWheeled(hud,x,y,delta))return true;
        if(hud!=owner||monitoring==null||!hud.getComponents().contains(monitoring))return false;
        List<WurmComponent> components=hud.getComponents();
        for(int i=components.size()-1;i>=0;i--)if(components.get(i).contains(x,y)&&components.get(i).isAvailable())return components.get(i)==monitoring&&monitoring.mouseWheeledAt(x,y,delta);
        return false;
    }
    static synchronized void showMonitoring(SurroundingsWindow source,List<SurroundingsQuery> queries){
        if(source==null||queries==null||queries.isEmpty())return;
        try{HeadsUpDisplay hud=WurmWaypointerRuntime.currentHud();if(hud==null)return;detach(owner,"replace monitoring");owner=hud;
            monitoring=new SurroundingsMonitoringWindow(source.controller(),queries);
            monitoring.setInitialSize(SurroundingsMonitoringWindow.WINDOW_WIDTH,Math.min(540,Math.max(300,hud.getHeight()-180)),true);
            monitoring.setPosition(Math.max(20,hud.getWidth()-monitoring.width-35),Math.max(35,(hud.getHeight()-monitoring.height)/2));
            ReflectionUtil.callPrivateMethod(hud,ReflectionUtil.getMethod(HeadsUpDisplay.class,"addComponent",new Class<?>[]{WurmComponent.class}),monitoring);
            SavePosManager positions=ReflectionUtil.getPrivateField(hud,ReflectionUtil.getField(HeadsUpDisplay.class,"savePosManager"));if(positions!=null)positions.registerAndRefresh(monitoring,"wurm-waypointer.surroundings-monitoring");hud.setActiveWindow(monitoring);
        }catch(Throwable failure){LOG.log(Level.WARNING,"Cannot open monitoring",failure);}
    }
    static synchronized void showSurroundings(SurroundingsMonitoringWindow source){if(source!=monitoring)return;detach(owner,"return to hub");WurmWaypointerRuntime.openSurroundings();}
    public static synchronized void detach(HeadsUpDisplay hud,String reason){
        if(monitoring!=null&&owner!=null)try{ReflectionUtil.callPrivateMethod(owner,ReflectionUtil.getMethod(HeadsUpDisplay.class,"removeComponent",new Class<?>[]{WurmComponent.class}),monitoring);}catch(Throwable failure){LOG.log(Level.FINE,"Monitoring detach failed",failure);}
        monitoring=null;owner=null;
    }
    static synchronized void closed(SurroundingsWindow source){WaypointManagerWindowBridge.detach(null,"browser close");}
    static synchronized void closed(SurroundingsMonitoringWindow source){if(source==monitoring)detach(owner,"monitor close");}
}