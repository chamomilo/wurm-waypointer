package org.waypoints.next.service;
import java.util.*;

/** Comma-separated literal substring alternatives; exclusions take precedence. */
public final class TextFilter {
    private final List<String> include,exclude;
    public TextFilter(String plus,String minus){include=fragments(plus);exclude=fragments(minus);}
    public boolean matches(String text){String haystack=text==null?"":text.toLowerCase(Locale.ROOT);for(String token:exclude)if(haystack.contains(token))return false;if(include.isEmpty())return true;for(String token:include)if(haystack.contains(token))return true;return false;}
    private static List<String> fragments(String source){Set<String> result=new LinkedHashSet<String>();if(source!=null)for(String value:source.split(",")){String token=value.trim().toLowerCase(Locale.ROOT);if(!token.isEmpty())result.add(token);}return new ArrayList<String>(result);}
}
