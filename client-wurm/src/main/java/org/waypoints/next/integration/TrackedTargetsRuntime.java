package org.waypoints.next.integration;

import com.wurmonline.client.game.*;
import com.wurmonline.client.renderer.cell.*;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.shared.constants.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.model.*;
import org.waypoints.next.persistence.*;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tracking.*;
import org.waypoints.next.ui.TrackingController;
import org.waypoints.next.validation.WaypointRecordValidator;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/** Vanilla Manage gateway and locally received moving targets. All network queries are explicit. */
final class TrackedTargetsRuntime implements DynamicWaypointProvider, TrackingController {
    private final Logger logger;
    private final TrackingCatalog catalog=new TrackingCatalog();
    private final Map<String,ManagedEntry> managed=new LinkedHashMap<String,ManagedEntry>();
    private final Map<String,String> friendDetails=new LinkedHashMap<String,String>();
    private final Map<String,Boolean> friendOnline=new HashMap<String,Boolean>();
    private final Map<String,WaypointCoordinate> live=new HashMap<String,WaypointCoordinate>();
    private final Map<Long,String> playerNames=new HashMap<Long,String>();
    private final ClientFriendsAdapter friends=new ClientFriendsAdapter();
    private final Map<ManagedKind,Boolean> directionAvailable=new EnumMap<ManagedKind,Boolean>(ManagedKind.class);
    private final ManagedCatalogueGateway gateway;
    private TrackedTargetStore persistence;
    private long nextFriends,nextSave,stateRevision;
    private String scope="";
    private boolean friendsReceived;
    TrackedTargetsRuntime(Logger logger){
        this.logger=logger;
        gateway=new ManagedCatalogueGateway(logger,new ManagedCatalogueGateway.Receiver(){
            @Override public void catalogue(ManagedKind kind,ManagedBmlParser.Result result){acceptCatalogue(kind,result);}
            @Override public void direction(String key,String text,AnimalBearing bearing){
                Instant now=Instant.now();
                if(bearing!=null)catalog.bearing(ManagedKind.ANIMAL,key,bearing,now);
                else {WaypointRecord record=catalog.find(ManagedKind.ANIMAL,key);if(record!=null)catalog.pending(ManagedKind.ANIMAL,key,record.getDescription(),now);}
                stateRevision++;save();
            }
        });
    }
    @Override public void configure(WaypointClientConfiguration configuration) { }
    synchronized void configure(Properties properties){
        catalog.retentionDays(new ConfigurationProperties(properties,s->logger.warning(s)).integer("trackingRetentionDays",90,1,3650));
        if(persistence==null)persistence=new TrackedTargetStore(Paths.get(properties.getProperty("dynamicTargetCacheFile","wurm-waypointer-data/dynamic-targets.wpt")),catalog,logger);
    }
    synchronized void tick(HeadsUpDisplay hud,ServerIdentity identity,Instant now){
        World world=hud==null?null:hud.getWorld();if(world==null||identity==null||persistence==null||!persistence.loaded())return;
        String nextScope=world.getUsername()+"|"+identity.getEndpointFingerprint();
        if(!scope.equals(nextScope)){gateway.reset();managed.clear();friendDetails.clear();friendOnline.clear();friendsReceived=false;if(!scope.isEmpty()){live.clear();playerNames.clear();}scope=nextScope;catalog.bind(identity,world.getUsername(),now);}
        gateway.tick(hud,now.toEpochMilli());
        if(now.toEpochMilli()>=nextFriends){nextFriends=now.toEpochMilli()+1000;pollFriends(now);catalog.expire(now);}
        if(persistence.dirty()&&now.toEpochMilli()>=nextSave){nextSave=now.toEpochMilli()+30000;save();}
    }
    synchronized void nativeAction(PlayerAction action){gateway.nativeAction(action);}
    synchronized boolean intercept(HeadsUpDisplay owner,String title,String bml){return gateway.intercept(owner,title,bml);}
    synchronized void event(String tab,String text,Instant now){gateway.event(tab,text,now.toEpochMilli());}
    private void acceptCatalogue(ManagedKind kind,ManagedBmlParser.Result result){
        Instant now=Instant.now();Set<String> keys=new HashSet<String>();directionAvailable.put(kind,result.canLocate);
        for(ManagedEntry e:result.entries){managed.put(e.key(),e);keys.add(e.key());
            catalog.candidate(kind,Long.toString(e.id),e.name,"Manage "+e.type,now);
            WaypointCoordinate c=live.get(e.key());if(c!=null)catalog.live(kind,Long.toString(e.id),e.name,"Manage "+e.type,c,now);
        }
        for(Iterator<Map.Entry<String,ManagedEntry>> it=managed.entrySet().iterator();it.hasNext();){
            Map.Entry<String,ManagedEntry> e=it.next();
            if(e.getValue().kind==kind&&!keys.contains(e.getKey())){catalog.vanished(kind,Long.toString(e.getValue().id),now);it.remove();}
        }
        stateRevision++;save();
    }
    synchronized void observe(Object renderable,Double x,Double y,Double height,SurroundingEntry entry,Instant now){
        if(persistence==null||!persistence.loaded())return;
        if(renderable instanceof PlayerCellRenderable){
            PlayerCellRenderable p=(PlayerCellRenderable)renderable;String name=p.getCreatureData().getName();
            WaypointCoordinate c=new WaypointCoordinate(x==null?p.getXPos()/4d:x/4d,y==null?p.getYPos()/4d:y/4d,height==null?(double)p.getHPos():height,p.getLayer()<0?WaypointLayer.CAVE:WaypointLayer.SURFACE);
            playerNames.put(p.getId(),name);live.put("PLAYER:"+name.toLowerCase(Locale.ROOT),c);
            if(catalog.find(ManagedKind.PLAYER,name)!=null)catalog.live(ManagedKind.PLAYER,name,name,friendDetails.containsKey(name.toLowerCase(Locale.ROOT))?friendDetails.get(name.toLowerCase(Locale.ROOT)):"Nearby player",c,now);
        }else if(entry!=null){
            ManagedKind kind=entry.getKind()==SurroundingKind.ANIMAL?ManagedKind.ANIMAL:ManagedKind.VEHICLE;
            WaypointCoordinate c=new WaypointCoordinate(entry.getWorldX()/4d,entry.getWorldY()/4d,entry.getHeight(),entry.getLayer()<0?WaypointLayer.CAVE:WaypointLayer.SURFACE);
            live.put(kind.name()+":"+entry.getKey().getWurmId(),c);live.put("SHIP:"+entry.getKey().getWurmId(),c);
            String key=Long.toString(entry.getKey().getWurmId());WaypointRecord record=catalog.find(kind,key);
            if(record==null&&kind==ManagedKind.VEHICLE)record=catalog.find(ManagedKind.SHIP,key);
            if(record!=null){ManagedKind storedKind=ManagedKind.valueOf(record.getExtensions().get("tracking.kind").get(0));catalog.live(storedKind,key,record.getName(),record.getDescription(),c,now);}
        }
    }
    synchronized void removed(Object renderable,SurroundingKey entry,Instant now){
        if(renderable instanceof PlayerCellRenderable){long id=((PlayerCellRenderable)renderable).getId();String name=playerNames.remove(id);if(name!=null){live.remove("PLAYER:"+name.toLowerCase(Locale.ROOT));catalog.vanished(ManagedKind.PLAYER,name,now);}}
        else if(entry!=null)for(ManagedKind k:new ManagedKind[]{ManagedKind.ANIMAL,ManagedKind.VEHICLE,ManagedKind.SHIP}){String key=Long.toString(entry.getWurmId());live.remove(k.name()+":"+key);catalog.vanished(k,key,now);}
    }
    synchronized UUID trackNearby(SurroundingEntry entry,boolean enabled){
        if(entry==null)return null;ManagedKind kind=entry.getKind()==SurroundingKind.ANIMAL?ManagedKind.ANIMAL:ManagedKind.VEHICLE;
        String key=Long.toString(entry.getKey().getWurmId());Instant now=Instant.now();
        WaypointRecord previous=catalog.find(kind,key);
        if(previous==null&&kind==ManagedKind.VEHICLE)previous=catalog.find(ManagedKind.SHIP,key);
        if(!enabled){unmark(entry.getKey());return previous==null?null:previous.getId();}
        if(previous!=null&&previous.getSourceKey().startsWith("tracked:SHIP:"))kind=ManagedKind.SHIP;
        String name=previous!=null&&previous.getDescription().startsWith("Manage ")?previous.getName():entry.getName();
        String detail=previous!=null&&previous.getDescription().startsWith("Manage ")?previous.getDescription():"Nearby "+entry.getCategory();
        WaypointRecord r=catalog.live(kind,key,name,detail,new WaypointCoordinate(entry.getWorldX()/4d,entry.getWorldY()/4d,entry.getHeight(),entry.getLayer()<0?WaypointLayer.CAVE:WaypointLayer.SURFACE),now);
        if(r==null)return null;catalog.enabled(r.getId(),enabled,now);save();return r.getId();
    }
    private void pollFriends(Instant now){
        try {
            Set<String> names=new HashSet<String>();
            for(ClientFriendsAdapter.Entry f:friends.snapshot()){
                String key=f.name.toLowerCase(Locale.ROOT);names.add(key);friendDetails.put(key,f.detail);
                if(!Objects.equals(friendOnline.put(key,f.online),f.online))stateRevision++;
                catalog.candidate(ManagedKind.PLAYER,f.name,f.name,f.detail,now);
                WaypointCoordinate c=live.get("PLAYER:"+key);
                if(!f.sameServer){live.remove("PLAYER:"+key);catalog.pending(ManagedKind.PLAYER,f.name,f.detail,now);}
                else if(c!=null)catalog.live(ManagedKind.PLAYER,f.name,f.name,f.detail,c,now);
            }
            for(String old:new ArrayList<String>(friendDetails.keySet()))if(!names.contains(old)){catalog.vanished(ManagedKind.PLAYER,old,now);friendDetails.remove(old);friendOnline.remove(old);}
            friendsReceived=true;
        }catch(Throwable failure){logger.log(Level.FINE,"Friends catalogue unavailable",failure);}
    }
    @Override public synchronized List<WaypointRecord> records(boolean friends,String search){
        List<WaypointRecord> result=new ArrayList<WaypointRecord>();String needle=search==null?"":search.toLowerCase(Locale.ROOT);
        for(WaypointRecord r:catalog.current())if((r.getSourceType()==WaypointSourceType.PLAYER)==friends&&(r.getName()+" "+r.getDescription()).toLowerCase(Locale.ROOT).contains(needle)){
            if(friends?(!friendsReceived||friendDetails.containsKey(sourceKey(r).toLowerCase(Locale.ROOT))):r.getDescription().startsWith("Manage "))result.add(r);
        }
        Collections.sort(result,Comparator.comparing(WaypointRecord::getName,String.CASE_INSENSITIVE_ORDER));return result;
    }
    @Override public synchronized String status(){return persistence!=null&&!persistence.status().isEmpty()?persistence.status():gateway.status();}
    @Override public synchronized boolean canTrack(UUID id){WaypointRecord r=catalog.find(id);return r!=null&&(r.getSourceType()==WaypointSourceType.PLAYER||!sourceKey(r).startsWith("-"));}
    @Override public synchronized Boolean online(UUID id){WaypointRecord r=catalog.find(id);return r!=null&&r.getSourceType()==WaypointSourceType.PLAYER?friendOnline.get(sourceKey(r).toLowerCase(Locale.ROOT)):null;}
    @Override public synchronized String detail(UUID id){WaypointRecord r=catalog.find(id);if(r==null)return "";ManagedEntry entry=managed.get(r.getSourceKey().substring("tracked:".length()));return entry==null?r.getDescription():entry.type+"; "+String.join("; ",entry.details);}
    @Override public synchronized boolean canLocate(UUID id){
        WaypointRecord r=catalog.find(id);if(r==null||r.getSourceType()!=WaypointSourceType.MANAGED_ANIMAL||!Boolean.TRUE.equals(directionAvailable.get(ManagedKind.ANIMAL)))return false;
        ManagedEntry e=managed.get(r.getSourceKey().substring(8));return e!=null&&e.canTrack();
    }
    @Override public synchronized void refresh(boolean friendList){if(friendList){nextFriends=0;stateRevision++;}else gateway.refresh();}
    @Override public synchronized void setTracked(UUID id,boolean enabled){if(canTrack(id)){if(enabled)catalog.enabled(id,true,Instant.now());else catalog.removeWaypoint(id,Instant.now());save();}}
    @Override public synchronized void locate(UUID id){if(canLocate(id)){WaypointRecord r=catalog.find(id);gateway.locate(sourceKey(r),r.getName());}}
    @Override public synchronized void clearLastSeen(UUID id){catalog.clearLastSeen(id,Instant.now());save();}
    synchronized boolean enabled(UUID id,boolean value){boolean result=catalog.enabled(id,value,Instant.now());if(result)save();return result;}
    synchronized boolean delete(UUID id){boolean result=catalog.removeWaypoint(id,Instant.now());if(result)save();return result;}
    synchronized WaypointRecord find(UUID id){return catalog.find(id);}
    synchronized List<WaypointRecord> records(){return catalog.waypoints();}
    synchronized Collection<SurroundingKey> markedKeys(){List<SurroundingKey> result=new ArrayList<SurroundingKey>();for(WaypointRecord r:catalog.current())if(r.isEnabled()&&r.getSourceType()!=WaypointSourceType.PLAYER){try{long id=Long.parseLong(sourceKey(r));if(r.getSourceType()==WaypointSourceType.MANAGED_ANIMAL)result.add(new SurroundingKey(SurroundingKind.ANIMAL,id));else{result.add(new SurroundingKey(SurroundingKind.ITEM,id));result.add(new SurroundingKey(SurroundingKind.CONTAINER,id));}}catch(NumberFormatException ignored){}}return result;}
    private static String sourceKey(WaypointRecord r){return r.getSourceKey().substring(r.getSourceKey().lastIndexOf(':')+1);}
    @Override public synchronized long revision(){return catalog.revision()*31+stateRevision+gateway.revision();}
    @Override public synchronized WaypointRevisionSnapshot combine(WaypointRevisionSnapshot base){List<WaypointRecord> records=new ArrayList<WaypointRecord>(base.getRecords());records.addAll(catalog.waypoints());return new WaypointRevisionSnapshot(base.getRevision()*31+revision(),records);}
    private void save(){if(persistence!=null)persistence.save();}
    synchronized void clearLive(){for(WaypointRecord r:catalog.current())if(r.getResolution()==WaypointResolution.LIVE_EXACT)catalog.vanished(ManagedKind.valueOf(r.getExtensions().get("tracking.kind").get(0)),sourceKey(r),Instant.now());live.clear();playerNames.clear();}
    synchronized void unmarkAll(){for(WaypointRecord r:catalog.current())if(r.getSourceType()!=WaypointSourceType.PLAYER)catalog.removeWaypoint(r.getId(),Instant.now());save();}
    synchronized boolean unmark(SurroundingKey key){if(key==null)return false;boolean changed=false;for(ManagedKind k:key.getKind()==SurroundingKind.ANIMAL?new ManagedKind[]{ManagedKind.ANIMAL}:new ManagedKind[]{ManagedKind.VEHICLE,ManagedKind.SHIP}){WaypointRecord r=catalog.find(k,Long.toString(key.getWurmId()));if(r!=null)changed|=catalog.removeWaypoint(r.getId(),Instant.now());}if(changed)save();return changed;}
    @Override public synchronized void connectionEnded(){catalog.disconnect(Instant.now());save();gateway.reset();managed.clear();friendDetails.clear();friendOnline.clear();directionAvailable.clear();live.clear();playerNames.clear();scope="";stateRevision++;}
    @Override public NavigationTargetKey pollNavigationRequest(){return null;}
    @Override public synchronized String pollMessage(){return null;}
    @Override public void observeAction(long[] targets,String actionName){}
    @Override public String navigationReason(){return "tracked target";}
}
