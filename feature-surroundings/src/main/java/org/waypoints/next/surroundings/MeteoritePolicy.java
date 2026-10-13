package org.waypoints.next.surroundings;

import java.util.Locale;

/** Detects landed meteorite resources, never creatures, flying effects or ordinary metal lumps. */
public final class MeteoritePolicy {
    private MeteoritePolicy(){}
    public static boolean matches(SurroundingEntry entry) {
        if(entry==null||entry.getKind()!=SurroundingKind.ITEM)return false;
        String model=normalize(entry.getModelName()),name=normalize(entry.getName());
        if(model.contains(" meteorite ")||model.contains(" meteor "))return true;
        if(name.contains(" lump ")||name.contains(" scrap ")||name.contains(" ore ")||name.contains(" shard "))return false;
        boolean resource=model.trim().isEmpty()||model.contains(" resource ")||model.contains(" rock ")||model.contains(" boulder ");
        return resource&&(name.contains(" meteorite ")||name.contains(" meteor ")||name.contains(" метеорит ")||name.contains(" метеор "));
    }
    private static String normalize(String value){return " "+(value==null?"":value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+"," ").trim()+" ";}
}
