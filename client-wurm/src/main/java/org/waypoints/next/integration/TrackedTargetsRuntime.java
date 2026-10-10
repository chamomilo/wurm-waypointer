package org.waypoints.next.integration;

import com.wurmonline.client.game.*;
import com.wurmonline.client.renderer.cell.*;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.shared.constants.*;
import org.waypoints.next.model.*;
import org.waypoints.next.persistence.*;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tracking.*;
import org.waypoints.next.ui.TrackingController;
import org.waypoints.next.source.MapBounds;
import java.util.function.LongSupplier;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/** Vanilla Manage gateway, locally received targets and one user-started animal search. */
final class TrackedTargetsRuntime implements DynamicWaypointProvider, TrackingController {
    private final Logger logger;
    private final TrackingCatalog catalog=new TrackingCatalog();
    private final Map<String,ManagedEntry> managed=new LinkedHashMap<String,ManagedEntry>();
    private final Map<String,WaypointCoordinate> live=new HashMap<String,WaypointCoordinate>();
    private final AnimalWaypointRace race=new AnimalWaypointRace();
    private final Deque<NavigationTargetKey> navigationRequests=new ArrayDeque<NavigationTargetKey>();
    private final Deque<String> messages=new ArrayDeque<String>();
    private MapBounds bounds=new MapBounds(4096,4096);
    private Double searchHeight;
    private WaypointLayer searchLayer=WaypointLayer.SURFACE;
    private long nextCheck;
    private int failedChecks;
    private boolean ownsNavigator;
    private final Map<ManagedKind,Boolean> directionAvailable=new EnumMap<ManagedKind,Boolean>(ManagedKind.class);
    private final ManagedCatalogueGateway gateway;
    private TrackedTargetStore persistence;
    private long nextExpiry,nextSave,stateRevision;
    private String scope="";
    TrackedTargetsRuntime(Logger logger){this(logger,null,System::currentTimeMillis);}
    TrackedTargetsRuntime(Logger logger,ManagedCatalogueGateway.Transport transport,LongSupplier clock){
        this.logger=logger;
        ManagedCatalogueGateway.Receiver receiver=new ManagedCatalogueGateway.Receiver(){
            @Override public void catalogue(ManagedKind kind,ManagedBmlParser.Result result){acceptCatalogue(kind,result);}
            @Override public void direction(String key,String text,AnimalBearing bearing){
                Instant now=Instant.now();
                if(bearing!=null)catalog.bearing(ManagedKind.ANIMAL,key,bearing,now);
                else {WaypointRecord record=catalog.find(ManagedKind.ANIMAL,key);if(record!=null&&record.getResolution()!=WaypointResolution.LIVE_EXACT)catalog.pending(ManagedKind.ANIMAL,key,record.getDescription(),now);}
                WaypointRecord record=catalog.find(ManagedKind.ANIMAL,key);
                if(record!=null&&race.active(record.getId())){
                    if(record.getResolution()==WaypointResolution.LIVE_EXACT)finishRace(record);
                    else if(bearing!=null){
                        race.reading(bearing,bounds,searchHeight,searchLayer);failedChecks=0;
                        nextCheck=clock.getAsLong()+1500;
                        requestNavigation(record);stateRevision++;
                    }else cancelRace("Animal search stopped: direction is unavailable.");
                }
                stateRevision++;save();
            }
        };
        gateway=transport==null?new ManagedCatalogueGateway(logger,receiver)
                :new ManagedCatalogueGateway(logger,receiver,transport,clock);
    }
    @Override public synchronized void configure(WaypointClientConfiguration configuration) { bounds=configuration.getMapBounds(); }
    synchronized void configure(Properties properties){
        catalog.retentionDays(new ConfigurationProperties(properties,s->logger.warning(s)).integer("trackingRetentionDays",90,1,3650));
        if(persistence==null)persistence=new TrackedTargetStore(Paths.get(properties.getProperty("dynamicTargetCacheFile","wurm-waypointer-data/dynamic-targets.wpt")),catalog,logger);
    }
    synchronized void tick(HeadsUpDisplay hud,ServerIdentity identity,Instant now){
        tick(hud,identity,null,now);
    }
    synchronized void tick(HeadsUpDisplay hud,ServerIdentity identity,SurroundingsRuntime surroundings,Instant now){
        World world=hud==null?null:hud.getWorld();if(world==null||identity==null||persistence==null||!persistence.loaded())return;
        String nextScope=world.getUsername()+"|"+identity.getEndpointFingerprint();
        if(!scope.equals(nextScope)){cancelRace(null);gateway.reset();managed.clear();directionAvailable.clear();if(!scope.isEmpty())live.clear();scope=nextScope;catalog.bind(identity,world.getUsername(),now);if(surroundings!=null)seedVisible(surroundings.entries(),now);}
        gateway.tick(hud,now.toEpochMilli());
        if(now.toEpochMilli()>=nextExpiry){nextExpiry=now.toEpochMilli()+1000;catalog.expire(now);}
        tickRace(world.getPlayerPosX()/4d,world.getPlayerPosY()/4d,(double)world.getPlayerPosH(),
                world.getPlayerLayer()<0?WaypointLayer.CAVE:WaypointLayer.SURFACE,now);
        if(persistence.dirty()&&now.toEpochMilli()>=nextSave){nextSave=now.toEpochMilli()+30000;save();}
    }
    synchronized void nativeAction(PlayerAction action){gateway.nativeAction(action);}
    synchronized boolean intercept(HeadsUpDisplay owner,String title,String bml){return gateway.intercept(owner,title,bml);}
    synchronized void event(String tab,String text,Instant now){gateway.event(tab,text,now.toEpochMilli());}
    private void acceptCatalogue(ManagedKind kind,ManagedBmlParser.Result result){
        Instant now=Instant.now();Set<String> keys=new HashSet<String>();directionAvailable.put(kind,result.canLocate);
        // Old label-only rows used an unstable row number as the source ID.
        // Discard those placeholders when the native catalogue is refreshed.
        for(WaypointRecord record:catalog.current())if(record.getSourceKey().startsWith("tracked:"+kind.name()+":-"))catalog.remove(record.getId());
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
        if(entry!=null){
            ManagedKind kind=entry.getKind()==SurroundingKind.ANIMAL?ManagedKind.ANIMAL:ManagedKind.VEHICLE;
            WaypointCoordinate c=new WaypointCoordinate(entry.getWorldX()/4d,entry.getWorldY()/4d,entry.getHeight(),entry.getLayer()<0?WaypointLayer.CAVE:WaypointLayer.SURFACE);
            live.put(kind.name()+":"+entry.getKey().getWurmId(),c);if(kind==ManagedKind.VEHICLE)live.put("SHIP:"+entry.getKey().getWurmId(),c);
            if(persistence==null||!persistence.loaded())return;
            String key=Long.toString(entry.getKey().getWurmId());WaypointRecord record=catalog.find(kind,key);
            if(record==null&&kind==ManagedKind.VEHICLE)record=catalog.find(ManagedKind.SHIP,key);
            if(record!=null){ManagedKind storedKind=ManagedKind.valueOf(record.getExtensions().get("tracking.kind").get(0));record=catalog.live(storedKind,key,record.getName(),record.getDescription(),c,now);if(race.active(record.getId()))finishRace(record);}
        }
    }
    synchronized void seedVisible(Collection<SurroundingEntry> entries,Instant now){
        for(SurroundingEntry entry:entries)observe(null,null,null,null,entry,now);
    }
    synchronized void removed(Object renderable,SurroundingKey entry,Instant now){
        if(entry!=null)for(ManagedKind k:ManagedKind.values()){String key=Long.toString(entry.getWurmId());live.remove(k.name()+":"+key);catalog.vanished(k,key,now);}
    }
    /** Confirmed destruction/death, distinct from leaving the client stream. */
    synchronized List<UUID> subjectVanished(SurroundingKey subject){
        List<UUID> deleted=new ArrayList<UUID>();
        if(subject==null)return deleted;
        boolean changed=false;
        String key=Long.toString(subject.getWurmId());
        ManagedKind[] kinds=subject.getKind()==SurroundingKind.ANIMAL
                ?new ManagedKind[]{ManagedKind.ANIMAL}:new ManagedKind[]{ManagedKind.VEHICLE,ManagedKind.SHIP};
        for(ManagedKind kind:kinds){
            live.remove(kind.name()+":"+key);managed.remove(kind.name()+":"+key);
            WaypointRecord record=catalog.find(kind,key);
            if(record==null)continue;
            UUID id=record.getId();stopRace(id);
            navigationRequests.removeIf(request->id.equals(request.getWaypointId()));
            if(TrackingCatalog.isWaypoint(record))deleted.add(id);
            changed|=catalog.remove(id);
        }
        if(changed){stateRevision++;save();}
        return deleted;
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
    @Override public synchronized List<WaypointRecord> records(boolean animals,String search){
        List<WaypointRecord> result=new ArrayList<WaypointRecord>();String needle=search==null?"":search.toLowerCase(Locale.ROOT);
        for(WaypointRecord r:catalog.current())if((r.getSourceType()==WaypointSourceType.MANAGED_ANIMAL)==animals&&(r.getName()+" "+r.getDescription()).toLowerCase(Locale.ROOT).contains(needle)){
            if(r.getDescription().startsWith("Manage "))result.add(race.project(r));
        }
        Collections.sort(result,Comparator.comparing(WaypointRecord::getName,String.CASE_INSENSITIVE_ORDER));return result;
    }
    @Override public synchronized String status(){return persistence!=null&&!persistence.status().isEmpty()?persistence.status():race.target()!=null?"Animal waypoint race: "+catalog.find(race.target()).getName():gateway.status();}
    @Override public synchronized boolean canTrack(UUID id){WaypointRecord r=catalog.find(id);return r!=null&&!sourceKey(r).startsWith("-");}
    @Override public synchronized boolean canNavigate(UUID id){WaypointRecord r=catalog.find(id);return r!=null&&canTrack(id)&&(isAnimal(id)?race.active(id)||r.getResolution()==WaypointResolution.LIVE_EXACT||canLocate(id):r.isEnabled()&&r.getCoordinate()!=null);}
    @Override public synchronized String detail(UUID id){WaypointRecord r=catalog.find(id);if(r==null)return "";ManagedEntry entry=managed.get(r.getSourceKey().substring("tracked:".length()));return entry==null?r.getDescription():entry.type+"; "+String.join("; ",entry.details);}
    @Override public synchronized boolean canLocate(UUID id){
        WaypointRecord r=catalog.find(id);if(r==null||r.getSourceType()!=WaypointSourceType.MANAGED_ANIMAL||!Boolean.TRUE.equals(directionAvailable.get(ManagedKind.ANIMAL)))return false;
        ManagedEntry e=managed.get(r.getSourceKey().substring(8));return e!=null&&e.canTrack();
    }
    @Override public synchronized void refresh(boolean animals){gateway.refresh(animals);}
    @Override public synchronized void setTracked(UUID id,boolean enabled){
        if(!canTrack(id))return;
        if(enabled){
            catalog.enabled(id,true,Instant.now());
            WaypointRecord record=catalog.find(id);
            if(isAnimal(id)&&record.getResolution()!=WaypointResolution.LIVE_EXACT
                    &&canLocate(id)&&!race.active(id))navigate(id);
        }else{stopRace(id);catalog.removeWaypoint(id,Instant.now());}
        save();
    }
    @Override public synchronized void locate(UUID id){if(canLocate(id)){WaypointRecord r=catalog.find(id);gateway.locate(sourceKey(r),r.getName());}}
    @Override public synchronized void clearLastSeen(UUID id){stopRace(id);catalog.clearLastSeen(id,Instant.now());save();}
    synchronized boolean enabled(UUID id,boolean value){if(!value)stopRace(id);boolean result=catalog.enabled(id,value,Instant.now());if(result)save();return result;}
    synchronized boolean delete(UUID id){stopRace(id);boolean result=catalog.removeWaypoint(id,Instant.now());if(result)save();return result;}
    synchronized WaypointRecord find(UUID id){return catalog.find(id);}
    synchronized List<WaypointRecord> records(){List<WaypointRecord> result=new ArrayList<WaypointRecord>();for(WaypointRecord r:catalog.waypoints())result.add(race.project(r));return result;}
    synchronized Collection<SurroundingKey> markedKeys(){List<SurroundingKey> result=new ArrayList<SurroundingKey>();for(WaypointRecord r:catalog.current())if(r.isEnabled()){try{long id=Long.parseLong(sourceKey(r));if(r.getSourceType()==WaypointSourceType.MANAGED_ANIMAL)result.add(new SurroundingKey(SurroundingKind.ANIMAL,id));else{result.add(new SurroundingKey(SurroundingKind.ITEM,id));result.add(new SurroundingKey(SurroundingKind.CONTAINER,id));}}catch(NumberFormatException ignored){}}return result;}
    private static String sourceKey(WaypointRecord r){return r.getSourceKey().substring(r.getSourceKey().lastIndexOf(':')+1);}
    @Override public synchronized long revision(){return catalog.revision()*31+stateRevision+gateway.revision();}
    @Override public synchronized WaypointRevisionSnapshot combine(WaypointRevisionSnapshot base){List<WaypointRecord> records=new ArrayList<WaypointRecord>(base.getRecords());records.addAll(records());return new WaypointRevisionSnapshot(base.getRevision()*31+revision(),records);}
    private void save(){if(persistence!=null)persistence.save();}
    synchronized void clearLive(){cancelRace(null);for(WaypointRecord r:catalog.current())if(r.getResolution()==WaypointResolution.LIVE_EXACT)catalog.vanished(ManagedKind.valueOf(r.getExtensions().get("tracking.kind").get(0)),sourceKey(r),Instant.now());live.clear();}
    synchronized void unmarkAll(){cancelRace(null);for(WaypointRecord r:catalog.current())catalog.removeWaypoint(r.getId(),Instant.now());save();}
    synchronized boolean unmark(SurroundingKey key){if(key==null)return false;boolean changed=false;for(ManagedKind k:key.getKind()==SurroundingKind.ANIMAL?new ManagedKind[]{ManagedKind.ANIMAL}:new ManagedKind[]{ManagedKind.VEHICLE,ManagedKind.SHIP}){WaypointRecord r=catalog.find(k,Long.toString(key.getWurmId()));if(r!=null){stopRace(r.getId());changed|=catalog.removeWaypoint(r.getId(),Instant.now());}}if(changed)save();return changed;}
    synchronized boolean isAnimal(UUID id){WaypointRecord r=catalog.find(id);return r!=null&&r.getSourceType()==WaypointSourceType.MANAGED_ANIMAL;}
    synchronized boolean isRaceActive(UUID id){return race.active(id);}
    synchronized boolean navigate(UUID id){
        if(!isAnimal(id)||!canNavigate(id))return false;
        cancelRace(null);catalog.enabled(id,true,Instant.now());WaypointRecord r=catalog.find(id);
        if(r.getResolution()==WaypointResolution.LIVE_EXACT)requestNavigation(r);
        else{race.start(id);nextCheck=0;failedChecks=0;messages.add("Animal waypoint race started: "+r.getName());stateRevision++;}
        save();return true;
    }
    synchronized void stopRace(UUID id){if(race.active(id))cancelRace(null);}
    synchronized void cancelRace(String message){
        if(race.target()==null)return;
        gateway.cancelDirection();race.stop();ownsNavigator=false;navigationRequests.clear();stateRevision++;
        if(message!=null)messages.add(message);
    }
    synchronized void navigationOwner(UUID id){
        if(race.target()==null)return;
        if(race.active(id))ownsNavigator=true;
        else if(ownsNavigator)cancelRace(null);
    }
    synchronized void tickRace(double x,double y,Double height,WaypointLayer layer,Instant now){
        if(race.target()==null)return;
        WaypointRecord r=catalog.find(race.target());
        if(r==null||!r.isEnabled()){cancelRace(null);return;}
        if(r.getResolution()==WaypointResolution.LIVE_EXACT){finishRace(r);return;}
        if(race.waiting()&&!gateway.directionPending()){
            if(++failedChecks>=3){cancelRace("Animal search stopped: no direction response after three checks.");return;}
            race.retry();nextCheck=now.toEpochMilli()+3000;
        }
        if(now.toEpochMilli()<nextCheck||!race.needsReading(x,y))return;
        searchHeight=height;searchLayer=layer;
        if(gateway.locate(sourceKey(r),r.getName())){race.requested();stateRevision++;}
    }
    private void requestNavigation(WaypointRecord r){navigationRequests.clear();navigationRequests.add(new NavigationTargetKey(r.getServerIdentity().getEndpointFingerprint(),r.getId()));}
    private void finishRace(WaypointRecord r){
        if(!race.active(r.getId()))return;
        gateway.cancelDirection();race.stop();ownsNavigator=false;requestNavigation(r);stateRevision++;
        messages.add("Animal found: "+r.getName()+". Navigation now follows its received position.");save();
    }
    @Override public synchronized void connectionEnded(){cancelRace(null);navigationRequests.clear();messages.clear();catalog.disconnect(Instant.now());save();gateway.reset();managed.clear();directionAvailable.clear();live.clear();scope="";stateRevision++;}
    @Override public synchronized NavigationTargetKey pollNavigationRequest(){
        NavigationTargetKey request;
        while((request=navigationRequests.pollFirst())!=null){
            WaypointRecord record=catalog.find(request.getWaypointId());
            if(record!=null&&record.isEnabled()&&race.project(record).getCoordinate()!=null)return request;
        }
        return null;
    }
    @Override public synchronized String pollMessage(){return messages.pollFirst();}
    @Override public void observeAction(long[] targets,String actionName){}
    @Override public String navigationReason(){return "animal waypoint race";}
}
