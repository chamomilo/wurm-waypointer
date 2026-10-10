package org.waypoints.next.integration;

import com.wurmonline.client.game.*;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.shared.constants.PlayerAction;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.tracking.*;
import org.waypoints.next.i18n.Messages;
import java.util.*;
import java.util.logging.*;
import java.util.function.LongSupplier;

/** Explicit, serialized vanilla requests. Unknown or unsolicited forms always pass through. */
final class ManagedCatalogueGateway {
    interface Receiver {
        void catalogue(ManagedKind kind, ManagedBmlParser.Result result);
        void direction(String key, String text, AnimalBearing bearing);
    }
    interface Transport {
        Object owner();
        void request(ManagedKind kind)throws Exception;
        void reply(Map<String,String> response,String title)throws Exception;
        double[] pose();
    }
    private final Logger logger;
    private final Receiver receiver;
    private final Deque<ManagedKind> queue = new ArrayDeque<ManagedKind>();
    private final Transport transport;
    private final LongSupplier clock;
    private Object requestOwner;
    private ManagedKind pending;
    private String locateKey, locateName, status="Press Refresh to request your Manage lists.";
    private long deadline, nextRequest, revision;
    private boolean sending, waitingDirection;
    private double x,y,facing;
    ManagedCatalogueGateway(Logger logger, Receiver receiver){this(logger,receiver,new WurmTransport(),System::currentTimeMillis);}
    ManagedCatalogueGateway(Logger logger,Receiver receiver,Transport transport,LongSupplier clock){this.logger=logger;this.receiver=receiver;this.transport=transport;this.clock=clock;}
    void tick(HeadsUpDisplay owner,long now){
        if(transport instanceof WurmTransport)((WurmTransport)transport).hud=owner;
        if((pending!=null||waitingDirection)&&now>deadline){reset();status="Manage request timed out. Native forms remain available.";revision++;}
        if(pending==null&&!waitingDirection&&!queue.isEmpty()&&now>=nextRequest)send(queue.removeFirst(),now);
    }
    void refresh(){if(busy())return;queue.add(ManagedKind.ANIMAL);queue.add(ManagedKind.VEHICLE);queue.add(ManagedKind.SHIP);revision++;}
    void locate(String key,String name){if(busy())return;locateKey=key;locateName=name;queue.add(ManagedKind.ANIMAL);revision++;}
    private boolean busy(){return pending!=null||waitingDirection||!queue.isEmpty()||clock.getAsLong()<nextRequest;}
    private void send(ManagedKind kind,long now){
        try{
            requestOwner=transport.owner();
            pending=kind;deadline=now+8000;nextRequest=now+1500;sending=true;
            transport.request(kind);
            status="Requesting Manage list…";revision++;
        }catch(Exception failure){reset();status="Manage request failed.";revision++;logger.log(Level.WARNING,status,failure);}
        finally{sending=false;}
    }
    void nativeAction(PlayerAction action){if(!sending&&action!=null&&(action.getId()==663||action.getId()==665||action.getId()==668)){reset();status="Native Manage opened.";revision++;}}
    boolean intercept(Object owner,String title,String bml){
        if(owner!=requestOwner||pending==null||clock.getAsLong()>deadline)return false;
        ManagedBmlParser.Result parsed=ManagedBmlParser.parse(title,bml,pending);if(parsed==null)return false;
        ManagedKind kind=pending;boolean found=false;
        if(locateKey!=null&&parsed.canLocate)for(ManagedEntry e:parsed.entries)if(e.canTrack()&&Long.toString(e.id).equals(locateKey)){found=true;locateName=e.name;break;}
        Map<String,String> response=new LinkedHashMap<String,String>();response.put("id",parsed.questionId);
        try{
            if(found){response.put("sel",locateKey);response.put("find","true");double[] pose=transport.pose();x=pose[0];y=pose[1];facing=pose[2];}
            else response.put("close","true");
            transport.reply(response,title);
        }
        catch(Throwable failure){reset();logger.log(Level.WARNING,"Manage reply failed; passing native form through",failure);return false;}
        pending=null;receiver.catalogue(kind,parsed);revision++;
        status="Manage list received.";
        if(found){waitingDirection=true;deadline=clock.getAsLong()+8000;status="Waiting for animal direction…";}
        else if(locateKey!=null){locateKey=null;status="Animal direction is unavailable for this list.";}
        return true;
    }
    void event(String tab,String text,long now){
        if(!waitingDirection||!("Event".equals(tab)||":Event".equals(tab))||now>deadline)return;
        AnimalBearing bearing=AnimalBearing.parse(text,locateName,x,y,facing);
        if(bearing==null&&!"This creature is loaded in a cage, or on another server.".equals(text)&&(text==null||!text.startsWith("Cannot find animal,")))return;
        receiver.direction(locateKey,text,bearing);waitingDirection=false;locateKey=null;status=bearing==null?"Animal position is unavailable.":"Animal direction received.";revision++;
    }
    void reset(){queue.clear();pending=null;waitingDirection=false;locateKey=null;locateName=null;requestOwner=null;revision++;}
    String status(){return Messages.text(status);}
    long revision(){return revision;}

    /** Pinned native protocol is isolated from request ownership, timing and parsing. */
    private static final class WurmTransport implements Transport {
        private HeadsUpDisplay hud;
        @Override public Object owner(){return hud;}
        @Override public void request(ManagedKind kind)throws Exception{
            PaperDollSlot body=ReflectionUtil.getPrivateField(hud.getPaperDollInventory(),ReflectionUtil.getField(PaperDollInventory.class,"bodyItem"));
            if(body==null||body.getItem()==null)throw new IllegalStateException("Body target unavailable");
            hud.sendAction(new PlayerAction("Manage",(short)kind.action(),0),body.getItem().getId());
        }
        @Override public void reply(Map<String,String> response,String title){hud.getWorld().getServerConnection().sendBmlResponse(response,title);}
        @Override public double[] pose(){World world=hud.getWorld();return new double[]{world.getPlayerPosX()/4d,world.getPlayerPosY()/4d,world.getPlayerRotX()};}
    }
}
