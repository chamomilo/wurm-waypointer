package org.waypoints.next.tracking;

import org.waypoints.next.model.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Account/endpoint-qualified live tracking. No coordinates are inferred from catalogue membership. */
public final class TrackingCatalog {
    private static final String WAYPOINT_ADDED = "tracking.waypoint.added";
    private final Map<UUID,WaypointRecord> records = new LinkedHashMap<UUID,WaypointRecord>();
    private ServerIdentity server;
    private String user = "";
    private long revision;
    private int retentionDays = 90;
    public synchronized void retentionDays(int value) {
        if(value<1||value>3650)throw new IllegalArgumentException("retention days: 1..3650");
        retentionDays=value;
    }
    public synchronized void restore(Collection<WaypointRecord> saved) {
        for(WaypointRecord r:saved) {
            if(!isTracked(r))continue;
            if(!records.containsKey(r.getId()))records.put(r.getId(),
                    membership(offline(r, Instant.now()), isWaypoint(r)));
        }
        revision++;
    }
    public synchronized void bind(ServerIdentity next, String account, Instant now) {
        String name=account==null?"":account;
        if(Objects.equals(user,name) && server!=null && next!=null && server.sameServer(next))return;
        disconnect(now);server=next;user=name;revision++;
    }
    private boolean ready(){return server!=null&&!server.getEndpointFingerprint().isEmpty()&&!user.isEmpty();}
    public synchronized WaypointRecord candidate(ManagedKind kind,String key,String name,String description,Instant now) {
        if(!ready())return null;
        name=bounded(name,org.waypoints.next.validation.WaypointLimits.MAX_NAME);description=bounded(description,org.waypoints.next.validation.WaypointLimits.MAX_DESCRIPTION);
        UUID id=id(kind,key);WaypointRecord previous=records.get(id);
        if(previous!=null) {
            String source="tracked:"+kind.name()+":"+key;
            if(!name.equals(previous.getName())||!description.equals(previous.getDescription())||!source.equals(previous.getSourceKey())) {
                Map<String,List<String>> extensions=new LinkedHashMap<String,List<String>>(previous.getExtensions());extensions.put("tracking.kind",Collections.singletonList(kind.name()));
                previous=WaypointRecord.copyOf(previous).name(name).description(description).sourceKey(source).extensions(extensions).updatedAt(now).build();records.put(id,previous);revision++;
            }
            return previous;
        }
        if(records.size()>=4096){
            UUID prune=null;for(WaypointRecord old:records.values())if(!old.isEnabled()&&(prune==null||old.getUpdatedAt().isBefore(records.get(prune).getUpdatedAt())))prune=old.getId();
            if(prune==null)return null;records.remove(prune);
        }
        Map<String,List<String>> extensions=new LinkedHashMap<String,List<String>>();
        extensions.put("tracking.kind",Collections.singletonList(kind.name()));
        extensions.put(WAYPOINT_ADDED,Collections.singletonList("false"));
        WaypointRecord record=WaypointRecord.builder().id(id).name(name).description(description)
                .sourceType(kind==ManagedKind.PLAYER?WaypointSourceType.PLAYER:kind==ManagedKind.ANIMAL?WaypointSourceType.MANAGED_ANIMAL:WaypointSourceType.MANAGED_ITEM)
                .sourceKey("tracked:"+kind.name()+":"+key).createdByUser(user).serverIdentity(server)
                .resolution(WaypointResolution.PENDING).coordinate(null).lastResolvedAt(null).enabled(false)
                .markerStyle(new MarkerStyle(MarkerStyle.WorldStyle.COLORED_BEAM,kind==ManagedKind.PLAYER?0.3f:1f,0.8f,0.3f,1f,1f,1f,true,true))
                .createdAt(now).updatedAt(now).extensions(extensions).build();
        records.put(id,record);revision++;return record;
    }
    public synchronized WaypointRecord live(ManagedKind kind,String key,String name,String description,WaypointCoordinate coordinate,Instant now) {
        if(coordinate==null)return null;
        WaypointRecord old=candidate(kind,key,name,description,now);if(old==null)return null;
        WaypointRecord next=WaypointRecord.copyOf(old).coordinate(coordinate).resolution(WaypointResolution.LIVE_EXACT)
                .uncertaintyObservations(Collections.<UncertaintyObservation>emptyList()).lastResolvedAt(now).updatedAt(now).build();
        records.put(next.getId(),next);revision++;return next;
    }
    public synchronized void vanished(ManagedKind kind,String key,Instant now){
        if(!ready())return;UUID id=id(kind,key);WaypointRecord r=records.get(id);
        if(r!=null&&r.getResolution()==WaypointResolution.LIVE_EXACT){records.put(id,offline(r,now));revision++;}
    }
    public synchronized void pending(ManagedKind kind,String key,String description,Instant now) {
        if(!ready())return;WaypointRecord r=records.get(id(kind,key));if(r==null)return;
        description=bounded(description,org.waypoints.next.validation.WaypointLimits.MAX_DESCRIPTION);
        if(r.getCoordinate()==null&&r.getResolution()==WaypointResolution.PENDING&&r.getDescription().equals(description))return;
        records.put(r.getId(),WaypointRecord.copyOf(r).coordinate(null).resolution(WaypointResolution.PENDING)
                .description(description).uncertaintyObservations(Collections.<UncertaintyObservation>emptyList()).updatedAt(now).build());revision++;
    }
    public synchronized void bearing(ManagedKind kind,String key,AnimalBearing observation,Instant now) {
        if(!ready())return;WaypointRecord r=records.get(id(kind,key));if(r==null||r.getResolution()==WaypointResolution.LIVE_EXACT)return;
        Map<String,List<String>> ext=new LinkedHashMap<String,List<String>>(r.getExtensions());
        if(r.getCoordinate()!=null&&r.getLastResolvedAt()!=null){
            WaypointCoordinate c=r.getCoordinate();
            ext.put("tracking.last.coordinate",Arrays.asList(Double.toString(c.getTileX()),Double.toString(c.getTileY()),c.getHeight()==null?"":Double.toString(c.getHeight()),c.getLayer().name()));
            ext.put("tracking.last.at",Collections.singletonList(r.getLastResolvedAt().toString()));
        }
        List<UncertaintyObservation> observations=new ArrayList<UncertaintyObservation>();
        List<String> origins=new ArrayList<String>();List<String> oldOrigins=ext.get("tracking.bearing.origins");
        for(int i=0;i<r.getUncertaintyObservations().size();i++){
            UncertaintyObservation old=r.getUncertaintyObservations().get(i);
            if(oldOrigins!=null&&oldOrigins.size()>i&&now.toEpochMilli()-old.getObservedAt().toEpochMilli()<=60000){observations.add(old);origins.add(oldOrigins.get(i));}
        }
        if(observations.size()>=4){observations.remove(0);origins.remove(0);}
        observations.add(new UncertaintyObservation(observation.bearing,observation.halfWidth,observation.minimum,observation.maximum,now));
        origins.add(observation.originX+","+observation.originY);
        ext.put("tracking.bearing.origins",origins);
        records.put(r.getId(),WaypointRecord.copyOf(r).coordinate(null).resolution(WaypointResolution.SERVER_BEARING)
                .uncertaintyObservations(observations)
                .extensions(ext).lastResolvedAt(now).updatedAt(now).build());revision++;
    }
    public synchronized boolean enabled(UUID id,boolean value,Instant now){
        WaypointRecord r=records.get(id);if(r==null)return false;
        records.put(id,membership(r.withEnabled(value,now),value||isWaypoint(r)));revision++;return true;
    }
    public synchronized boolean remove(UUID id){if(records.remove(id)==null)return false;revision++;return true;}
    /** Keep the catalogue/history, but require another explicit Track to add it again. */
    public synchronized boolean removeWaypoint(UUID id,Instant now){
        WaypointRecord r=records.get(id);if(r==null)return false;
        records.put(id,membership(r.withEnabled(false,now),false));revision++;return true;
    }
    public synchronized boolean clearLastSeen(UUID id,Instant now){
        WaypointRecord r=records.get(id);if(r==null||r.getResolution()==WaypointResolution.LIVE_EXACT)return false;
        Map<String,List<String>> extensions=new LinkedHashMap<String,List<String>>(r.getExtensions());extensions.remove("tracking.last.coordinate");extensions.remove("tracking.last.at");
        records.put(id,WaypointRecord.copyOf(r).coordinate(null).resolution(WaypointResolution.PENDING)
                .extensions(extensions).lastResolvedAt(null).uncertaintyObservations(Collections.<UncertaintyObservation>emptyList()).updatedAt(now).build());revision++;return true;
    }
    public synchronized WaypointRecord find(UUID id){return records.get(id);}
    public synchronized WaypointRecord find(ManagedKind kind,String key){return ready()?records.get(id(kind,key)):null;}
    public synchronized List<WaypointRecord> current(){
        List<WaypointRecord> result=new ArrayList<WaypointRecord>();if(!ready())return result;
        for(WaypointRecord r:records.values())if(r.getCreatedByUser().equalsIgnoreCase(user)&&r.getServerIdentity()!=null&&r.getServerIdentity().sameServer(server))result.add(r);
        return result;
    }
    public synchronized List<WaypointRecord> all(){return new ArrayList<WaypointRecord>(records.values());}
    /** Only player-selected records belong in the manager and navigation snapshots. */
    public synchronized List<WaypointRecord> waypoints(){
        List<WaypointRecord> result=new ArrayList<WaypointRecord>();
        for(WaypointRecord r:records.values())if(isWaypoint(r))result.add(r);
        return result;
    }
    public synchronized long revision(){return revision;}
    public synchronized void expire(Instant now){
        for(WaypointRecord r:new ArrayList<WaypointRecord>(records.values())) {
            if(r.getResolution()==WaypointResolution.LAST_SEEN&&r.getLastResolvedAt()!=null
                    &&now.toEpochMilli()-r.getLastResolvedAt().toEpochMilli()>retentionDays*86400000L) {
                records.put(r.getId(),WaypointRecord.copyOf(r).coordinate(null).resolution(WaypointResolution.PENDING).updatedAt(now).build());revision++;
            }
            if(r.getResolution()==WaypointResolution.SERVER_BEARING&&r.getLastResolvedAt()!=null
                    &&now.toEpochMilli()-r.getLastResolvedAt().toEpochMilli()>120000L) {
                records.put(r.getId(),offline(r,now));revision++;
            }
        }
    }
    public synchronized void disconnect(Instant now){
        for(WaypointRecord r:records.values())if(r.getResolution()==WaypointResolution.LIVE_EXACT||r.getResolution()==WaypointResolution.SERVER_BEARING)records.put(r.getId(),offline(r,now));
        server=null;user="";revision++;
    }
    private static WaypointRecord offline(WaypointRecord r,Instant now){
        WaypointCoordinate coordinate=r.getCoordinate();Instant exactTime=r.getLastResolvedAt();
        if(coordinate==null&&r.getResolution()==WaypointResolution.SERVER_BEARING){
            List<String> saved=r.getExtensions().get("tracking.last.coordinate"),time=r.getExtensions().get("tracking.last.at");
            if(saved!=null&&saved.size()==4&&time!=null&&time.size()==1)try{
                coordinate=new WaypointCoordinate(Double.parseDouble(saved.get(0)),Double.parseDouble(saved.get(1)),saved.get(2).isEmpty()?null:Double.valueOf(saved.get(2)),WaypointLayer.valueOf(saved.get(3)));exactTime=Instant.parse(time.get(0));
            }catch(RuntimeException invalid){coordinate=null;}
        }
        return WaypointRecord.copyOf(r).coordinate(coordinate).lastResolvedAt(exactTime).resolution(coordinate==null?WaypointResolution.PENDING:WaypointResolution.LAST_SEEN)
                .uncertaintyObservations(Collections.<UncertaintyObservation>emptyList()).updatedAt(now.isBefore(r.getCreatedAt())?r.getCreatedAt():now).build();
    }
    private UUID id(ManagedKind kind,String key){ManagedKind family=kind==ManagedKind.SHIP&&!key.startsWith("-")?ManagedKind.VEHICLE:kind;return UUID.nameUUIDFromBytes((user.toLowerCase(Locale.ROOT)+"|"+server.getEndpointFingerprint()+"|"+family+"|"+key.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));}
    public static boolean isTracked(WaypointRecord r){return r!=null&&r.getSourceKey().startsWith("tracked:");}
    public static boolean isWaypoint(WaypointRecord r){
        if(!isTracked(r))return false;
        List<String> added=r.getExtensions().get(WAYPOINT_ADDED);
        // Earlier versions only persisted On/Off; retain their explicitly enabled targets.
        return added==null?r.isEnabled():added.equals(Collections.singletonList("true"));
    }
    private static WaypointRecord membership(WaypointRecord r,boolean added){
        Map<String,List<String>> extensions=new LinkedHashMap<String,List<String>>(r.getExtensions());
        extensions.put(WAYPOINT_ADDED,Collections.singletonList(Boolean.toString(added)));
        return WaypointRecord.copyOf(r).extensions(extensions).build();
    }
    private static String bounded(String text,int limit){String value=text==null?"":text.trim();return value.length()<=limit?value:value.substring(0,limit);}
}
