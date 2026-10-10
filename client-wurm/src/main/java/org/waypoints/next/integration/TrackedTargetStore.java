package org.waypoints.next.integration;

import org.waypoints.next.persistence.*;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.tracking.TrackingCatalog;
import org.waypoints.next.validation.WaypointRecordValidator;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/** Serial disk queue; a failed load disables writes so unread data cannot be replaced. */
final class TrackedTargetStore {
    private final Logger logger;
    private final TrackingCatalog catalog;
    private final ExecutorService disk=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Waypointer-target-store");t.setDaemon(true);return t;});
    private final WaypointStore store;
    private volatile boolean loaded, writable;
    private volatile String status="";
    private volatile long written=-1;
    private List<OpaqueWaypointRecord> opaque=Collections.emptyList();
    TrackedTargetStore(Path path,TrackingCatalog catalog,Logger logger){
        this.logger=logger;this.catalog=catalog;store=new WaypointStore(path,new WaypointFormatCodec(new WaypointRecordValidator()));
        disk.execute(()->{try{WaypointDocument document=store.load();catalog.restore(document.getRecords());opaque=document.getOpaqueRecords();writable=true;}
            catch(Exception failure){status="Tracked target cache could not be read. Existing file preserved.";logger.log(Level.WARNING,status,failure);}finally{loaded=true;}});
    }
    boolean loaded(){return loaded;}
    String status(){return status;}
    boolean dirty(){return loaded&&writable&&written!=catalog.revision();}
    void save(){
        if(!loaded||!writable)return;
        final WaypointDocument document=new WaypointDocument(catalog.all(),opaque);written=catalog.revision();
        disk.execute(()->{try{store.save(document);status="";}catch(Exception failure){written=-1;status="Tracked targets could not be saved.";logger.log(Level.WARNING,status,failure);}});
    }
}
