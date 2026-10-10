package org.waypoints.next.tracking;

import java.util.*;

/** Bounded non-executing BML reader. Unknown schemas must remain native forms. */
public final class ManagedBmlParser {
    public static final int MAX_BYTES = 1_048_576, MAX_ROWS = 4096;
    public static final class Result {
        public final String questionId;
        public final List<ManagedEntry> entries;
        public final boolean canLocate;
        Result(String id, List<ManagedEntry> entries, boolean locate) {
            questionId = id; this.entries = Collections.unmodifiableList(entries); canLocate = locate;
        }
    }
    private static final class Node {
        final String type;
        final Map<String,String> attrs = new LinkedHashMap<String,String>();
        final List<Node> children = new ArrayList<Node>();
        Node(String type) { this.type = type; }
        String get(String key) { String s = attrs.get(key); return s == null ? "" : s; }
    }
    private final String source;
    private int pos, nodes;
    private ManagedBmlParser(String source) { this.source = source; }
    public static Result parse(String title, String text, ManagedKind kind) {
        if (text == null || text.length() > MAX_BYTES || kind == null) return null;
        try {
            ManagedBmlParser parser = new ManagedBmlParser(text);
            List<Node> roots = parser.sequence(0, false);
            List<Node> all = new ArrayList<Node>(); flatten(roots, all);
            String id = ""; Node table = null; boolean close = false, find = false;
            for (Node node : all) {
                if (node.type.equals("passthrough") && node.get("id").equals("id")) id = node.get("text");
                if (node.type.equals("button") && node.get("id").equals("close")) close = true;
                if (node.type.equals("button") && node.get("id").equals("find")) find = true;
                if (node.type.equals("table") && table == null) table = node;
            }
            if (!id.matches("[0-9]{1,20}") || !close || table == null) return null;
            int cols = Integer.parseInt(table.get("cols"));
            int expected = kind == ManagedKind.ANIMAL ? 8 : 6;
            if (cols != expected || table.children.size() < cols || table.children.size() % cols != 0) return null;
            String headings = "";
            for (int i=0;i<cols;i++) headings += " " + value(table.children.get(i));
            if (!headings.contains("Name") || (kind == ManagedKind.ANIMAL
                    ? !headings.contains("Animal Type") || !headings.contains("Cared For")
                    : !headings.contains("Owner") || !headings.contains("Locked"))) return null;
            String lower = title == null ? "" : title.toLowerCase(Locale.ROOT);
            if (kind == ManagedKind.ANIMAL && !lower.contains("animal")) return null;
            if (kind == ManagedKind.SHIP && !lower.contains("ship")) return null;
            if (kind == ManagedKind.VEHICLE && !lower.contains("cart") && !lower.contains("wagon")) return null;
            List<ManagedEntry> entries = new ArrayList<ManagedEntry>(); Set<Long> ids = new HashSet<Long>();
            for (int start=cols;start<table.children.size();start+=cols) {
                if (entries.size() >= MAX_ROWS) return null;
                Node selector = table.children.get(start);
                long objectId = -(entries.size()+1L);
                if (selector.type.equals("radio")) {
                    if (!selector.get("group").equals("sel")) return null;
                    objectId = Long.parseLong(selector.get("id"));
                    if (objectId <= 0L) return null;
                } else if (!selector.type.equals("label")) return null;
                if (kind == ManagedKind.ANIMAL) {
                    // Animals without permission controls have no radio selector.
                    // Their native care/brand/tame buttons still carry the same
                    // creature ID. Read those IDs without executing the actions.
                    String[] prefixes = {"uncarefor", "unbrand", "untame"};
                    for (int i=0;i<prefixes.length;i++) {
                        long actionId = actionObjectId(table.children.get(start+5+i), prefixes[i]);
                        if (actionId == 0L) continue;
                        if (objectId > 0L && objectId != actionId) return null;
                        objectId = actionId;
                    }
                }
                if (objectId > 0L && !ids.add(objectId)) return null;
                String name = value(table.children.get(start+1)), type = value(table.children.get(start+2));
                if (name.isEmpty() || name.length()>256 || type.length()>256) return null;
                List<String> details = new ArrayList<String>();
                for(int i=3;i<cols;i++) details.add(value(table.children.get(start+i)));
                entries.add(new ManagedEntry(kind, objectId, name, type, details));
            }
            return new Result(id, entries, find && kind == ManagedKind.ANIMAL);
        } catch (RuntimeException invalid) { return null; }
    }
    private static long actionObjectId(Node node, String prefix) {
        long id = 0L;
        if (node.type.equals("button") && node.get("id").startsWith(prefix)) {
            String suffix = node.get("id").substring(prefix.length());
            if (!suffix.matches("[0-9]{1,19}")) throw new IllegalArgumentException("animal action ID");
            id = Long.parseLong(suffix);
            if (id <= 0L) throw new IllegalArgumentException("animal action ID");
        }
        for (Node child : node.children) {
            long childId = actionObjectId(child, prefix);
            if (childId == 0L) continue;
            if (id > 0L && id != childId) throw new IllegalArgumentException("conflicting animal IDs");
            id = childId;
        }
        return id;
    }
    private static String value(Node n) {
        String v=n.get("text");
        if (!v.isEmpty()) return v;
        for(Node c:n.children) { v=value(c); if(!v.isEmpty())return v; }
        return "";
    }
    private static void flatten(List<Node> roots,List<Node> all) {
        for(Node n:roots) { all.add(n); flatten(n.children,all); }
    }
    private List<Node> sequence(int depth, boolean closing) {
        if(depth>32)throw new IllegalArgumentException("BML nesting");
        List<Node> result=new ArrayList<Node>();
        while(true) {
            space();
            if(pos==source.length()) { if(closing)throw new IllegalArgumentException("truncated BML"); return result; }
            if(source.charAt(pos)=='}') { if(!closing)throw new IllegalArgumentException("unexpected close"); pos++;return result; }
            String type=word(); space();
            if(type.equals("null")) { result.add(new Node(type));continue; }
            if(pos>=source.length() || source.charAt(pos++)!='{')throw new IllegalArgumentException("BML block");
            Node n=new Node(type); if(++nodes>50000)throw new IllegalArgumentException("BML nodes");
            while(true) {
                space(); if(pos>=source.length())throw new IllegalArgumentException("truncated BML");
                if(source.charAt(pos)=='}') {pos++;break;}
                int saved=pos;String key=word();space();
                if(pos<source.length() && source.charAt(pos)=='=') {
                    pos++;space();String val=string();if(val.length()>8192)throw new IllegalArgumentException("BML field");
                    n.attrs.put(key,val);
                } else {
                    pos=saved;
                    // Read one child while respecting its own closing brace.
                    String childType=word();space();
                    if(childType.equals("null")) {n.children.add(new Node("null"));continue;}
                    if(pos>=source.length()||source.charAt(pos++)!='{')throw new IllegalArgumentException("BML child");
                    pos=saved; n.children.add(readNode(depth+1));
                }
            }
            result.add(n);
        }
    }
    private Node readNode(int depth) {
        if(depth>32)throw new IllegalArgumentException("BML nesting");
        String type=word();space(); if(source.charAt(pos++)!='{')throw new IllegalArgumentException();
        Node n=new Node(type);if(++nodes>50000)throw new IllegalArgumentException("BML nodes");
        while(true) {
            space();if(pos>=source.length())throw new IllegalArgumentException("truncated BML");
            if(source.charAt(pos)=='}'){pos++;return n;}
            int saved=pos;String key=word();space();
            if(pos<source.length()&&source.charAt(pos)=='='){pos++;space();String s=string();if(s.length()>8192)throw new IllegalArgumentException();n.attrs.put(key,s);}
            else if(key.equals("null")){n.children.add(new Node(key));}
            else {pos=saved;n.children.add(readNode(depth+1));}
        }
    }
    private String word(){int start=pos;while(pos<source.length()&&(Character.isLetterOrDigit(source.charAt(pos))||source.charAt(pos)=='_'||source.charAt(pos)=='-'))pos++;if(start==pos)throw new IllegalArgumentException("BML token");return source.substring(start,pos);}
    private String string(){
        if(pos>=source.length())throw new IllegalArgumentException();char quote=source.charAt(pos);
        if(quote!='\''&&quote!='"'){int start=pos;while(pos<source.length()&&";{}\r\n".indexOf(source.charAt(pos))<0)pos++;return source.substring(start,pos).trim();}
        pos++;StringBuilder b=new StringBuilder();
        // Native BML quotes delimit values; backslashes are ordinary text, including
        // the ascending Name /\ header. Treating them as Java escapes eats its closing quote.
        while(pos<source.length()){char c=source.charAt(pos++);if(c==quote)return b.toString();b.append(c);}
        throw new IllegalArgumentException("BML quote");
    }
    private void space(){while(pos<source.length()&&(Character.isWhitespace(source.charAt(pos))||source.charAt(pos)==';'))pos++;}
}
