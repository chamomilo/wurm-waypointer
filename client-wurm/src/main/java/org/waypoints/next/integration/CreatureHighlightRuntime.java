package org.waypoints.next.integration;

import com.wurmonline.client.renderer.CreatureData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.PlayerCellRenderable;
import org.waypoints.next.surroundings.*;
import java.util.*;

/** Loaded creatures only; players have their own cache outside the mob catalogue. */
final class CreatureHighlightRuntime {
    private final Map<Long,Subject> loaded=new LinkedHashMap<Long,Subject>();
    private final Set<Long> dead=new HashSet<Long>();
    private final CreatureAutoMarkSession autoMarks=new CreatureAutoMarkSession();
    private CreatureHighlightSettings settings=new CreatureHighlightSettings(new Properties());
    private long revision;
    private long countRevision=-1;
    private Map<CreatureHighlightRule,Integer> counts=Collections.emptyMap();
    synchronized void configure(Properties values) { settings=new CreatureHighlightSettings(values); revision++; }
    synchronized CreatureHighlightSettings settings() { return settings; }
    synchronized long revision() { return revision; }
    synchronized void upsert(Object value) {
        if(!(value instanceof CreatureCellRenderable))return;
        CreatureCellRenderable creature=(CreatureCellRenderable)value;
        if(creature.isItem() || dead.contains(creature.getId())) { remove(value); return; }
        CreatureData data=creature.getCreatureData();if(data==null)return;
        String model=data.getModelName()==null?"":data.getModelName().toString();
        boolean player=creature instanceof PlayerCellRenderable;
        boolean unique=!player && SurroundingsClassifier.isUniqueCreature(data.getName(),model);
        CreatureModifier modifier=player?CreatureModifier.NONE:CreatureModifier.fromWurmData(data.getModifier(),data.getName(),creature.getHoverName());
        boolean monster=!player && CreatureHighlightPolicy.monster(model,unique);
        boolean aggressiveAnimal=!player && !monster && CreatureHighlightPolicy.aggressiveAnimal(model);
        Subject previous=loaded.get(creature.getId());
        Subject next=new Subject(creature,player,monster,aggressiveAnimal,unique,modifier,creature.getLayer());
        loaded.put(creature.getId(),next);
        if(previous==null || !previous.same(next))revision++;
    }
    synchronized void remove(Object value) {
        if(value instanceof CreatureCellRenderable && loaded.remove(((CreatureCellRenderable)value).getId())!=null)revision++;
    }
    synchronized void died(long id) { dead.add(id);if(loaded.remove(id)!=null)revision++; }
    synchronized void clearLoaded() { loaded.clear();revision++; }
    synchronized void endSession() { clearLoaded();dead.clear();autoMarks.clear(); }
    /** ESP outlines include every received layer, including surface creatures seen from a cave. */
    synchronized List<PickableUnit> outlines() {
        List<PickableUnit> targets=new ArrayList<PickableUnit>();
        for(Subject subject:loaded.values()) {
            CreatureHighlightRule rule=CreatureHighlightPolicy.outline(settings,subject.player,subject.monster,subject.aggressiveAnimal,subject.unique,subject.modifier);
            if(rule!=null)targets.add(new ScannerOutlinePickable(subject.creature,settings.color(rule)));
        }
        return targets;
    }
    /** Detection is independent of enabled outline rules and includes all received layers. */
    synchronized Map<CreatureHighlightRule,Integer> detectedCounts() {
        if(countRevision==revision)return counts;
        Map<CreatureHighlightRule,Integer> next=new EnumMap<CreatureHighlightRule,Integer>(CreatureHighlightRule.class);
        for(CreatureHighlightRule rule:CreatureHighlightRule.values())next.put(rule,0);
        for(Subject subject:loaded.values()) {
            increment(next,subject.player?CreatureHighlightRule.PLAYERS:subject.monster?CreatureHighlightRule.MONSTERS:
                    subject.aggressiveAnimal?CreatureHighlightRule.AGGRESSIVE_ANIMALS:CreatureHighlightRule.ANIMALS);
            if(!subject.player) {
                if(subject.unique)increment(next,CreatureHighlightRule.UNIQUE);
                for(CreatureHighlightRule rule:CreatureHighlightRule.values())
                    if(rule.modifier()!=null&&rule.modifier()==subject.modifier)increment(next,rule);
            }
        }
        counts=Collections.unmodifiableMap(next);countRevision=revision;return counts;
    }
    private static void increment(Map<CreatureHighlightRule,Integer> counts,CreatureHighlightRule rule) { counts.put(rule,counts.get(rule)+1); }
    synchronized CreatureHighlightRule autoMark(SurroundingEntry entry) { return autoMarks.candidate(settings,entry); }
    synchronized void marked(SurroundingKey key) { autoMarks.marked(key); }
    private static final class Subject {
        final CreatureCellRenderable creature;final boolean player,monster,aggressiveAnimal,unique;final CreatureModifier modifier;final int layer;
        Subject(CreatureCellRenderable creature,boolean player,boolean monster,boolean aggressiveAnimal,boolean unique,CreatureModifier modifier,int layer) {
            this.creature=creature;this.player=player;this.monster=monster;this.aggressiveAnimal=aggressiveAnimal;this.unique=unique;this.modifier=modifier;this.layer=layer;
        }
        boolean same(Subject other) { return creature==other.creature && player==other.player && monster==other.monster && aggressiveAnimal==other.aggressiveAnimal && unique==other.unique && modifier==other.modifier && layer==other.layer; }
    }
}
