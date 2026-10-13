package org.waypoints.next.integration;

import com.wurmonline.client.renderer.PickableUnit;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tiles.TileHighlightSettings;
import java.util.*;

/** Loaded object identities drive outline, mark and sound independently; repeat upserts are quiet. */
final class MeteoriteHighlightRuntime {
    private final Map<SurroundingKey,Subject> loaded=new LinkedHashMap<SurroundingKey,Subject>();
    private final Set<SurroundingKey> marked=new HashSet<SurroundingKey>(),chimed=new HashSet<SurroundingKey>();
    private TileHighlightSettings settings=new TileHighlightSettings(new Properties());
    private long revision;
    synchronized void configure(Properties values){settings=new TileHighlightSettings(values);revision++;}
    synchronized TileHighlightSettings settings(){return settings;}
    synchronized long revision(){return revision;}
    synchronized int count(){return loaded.size();}
    synchronized void observe(SurroundingEntry entry,Object renderable) {
        if(entry==null)return;
        if(!MeteoritePolicy.matches(entry)){if(loaded.remove(entry.getKey())!=null)revision++;return;}
        Subject previous=loaded.put(entry.getKey(),new Subject(entry,renderable));
        if(previous==null||previous.renderable!=renderable)revision++;
    }
    synchronized void remove(SurroundingKey key){if(key!=null&&loaded.remove(key)!=null)revision++;}
    synchronized List<PickableUnit> outlines() {
        if(!settings.meteors())return Collections.emptyList();
        List<PickableUnit> result=new ArrayList<PickableUnit>();int rgb=settings.meteorRgb();
        ScannerColor color=new ScannerColor(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1);
        for(Subject subject:loaded.values())if(subject.renderable instanceof PickableUnit)result.add(new ScannerOutlinePickable((PickableUnit)subject.renderable,color));
        return result;
    }
    synchronized List<SurroundingEntry> pendingMarks() {
        List<SurroundingEntry> result=new ArrayList<SurroundingEntry>();
        if(settings.meteorMark())for(Subject subject:loaded.values())if(!marked.contains(subject.entry.getKey()))result.add(subject.entry);
        return result;
    }
    synchronized void marked(SurroundingKey key){marked.add(key);}
    synchronized boolean pollChime() {
        if(settings.meteorChime())for(SurroundingKey key:loaded.keySet())if(chimed.add(key))return true;
        return false;
    }
    synchronized void clearLoaded(){loaded.clear();revision++;}
    synchronized void endSession(){clearLoaded();marked.clear();chimed.clear();}
    private static final class Subject {
        final SurroundingEntry entry;final Object renderable;
        Subject(SurroundingEntry entry,Object renderable){this.entry=entry;this.renderable=renderable;}
    }
}
