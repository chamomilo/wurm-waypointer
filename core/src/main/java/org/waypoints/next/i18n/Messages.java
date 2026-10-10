package org.waypoints.next.i18n;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** UTF-8 catalogues with English fallback; never translates server protocols or object names. */
public final class Messages {
    public static final String[] CODES={"en","pt-BR","de","ru"};
    public static final String[] NAMES={"English","Português brasileiro","Deutsch","Русский"};
    private static volatile String language="en";
    private static volatile Catalog catalog=new Catalog(Collections.<String,String>emptyMap());
    private static final java.util.regex.Pattern ARGUMENT=java.util.regex.Pattern.compile("\\{([0-9]+)\\}");
    private static volatile long revision;
    private Messages(){}
    public static synchronized void select(String code){
        String chosen="en";for(String supported:CODES)if(supported.equalsIgnoreCase(code))chosen=supported;
        if(chosen.equals(language)&&!catalog.translations.isEmpty())return;
        Map<String,String> next=new LinkedHashMap<String,String>();
        try(InputStream in=Messages.class.getResourceAsStream("/org/waypoints/next/i18n/"+chosen+".tsv")){
            if(in!=null)try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){int tab=line.indexOf('\t');if(tab>0&&!line.startsWith("#"))next.put(unescape(line.substring(0,tab)),unescape(line.substring(tab+1)));}}
        }catch(IOException failure){throw new IllegalStateException("Cannot read language catalogue",failure);}
        catalog=new Catalog(next);language=chosen;revision++;
    }
    public static String language(){return language;}
    public static long revision(){return revision;}
    public static int index(){for(int i=0;i<CODES.length;i++)if(CODES[i].equals(language))return i;return 0;}
    public static String text(String source){
        if(source==null)return "";Catalog selected=catalog;Map<String,String> translations=selected.translations;String exact=translations.get(source);if(exact!=null)return exact;
        // Composite native filter/status labels preserve counts and object names.
        if(source.startsWith("[")&&source.endsWith("]"))return "["+text(source.substring(1,source.length()-1))+"]";
        int colon=source.indexOf(": ");if(colon>0){String head=translations.get(source.substring(0,colon));if(head!=null)return head+": "+text(source.substring(colon+2));}
        for(Template template:selected.templates){
            java.util.regex.Matcher match=template.pattern.matcher(source);
            if(match.matches()){Object[] args=new Object[match.groupCount()];for(int i=0;i<args.length;i++)args[i]=match.group(i+1);return substitute(template.value,args);}
        }
        String folded=selected.folded.get(source.toLowerCase(Locale.ROOT));if(folded!=null)return folded;
        for(String prefix:Arrays.asList("Wurm Waypointer - ","Monitoring (","Current ("))if(source.startsWith(prefix))return translations.getOrDefault(prefix,prefix)+text(source.substring(prefix.length()));
        return source;
    }
    public static String[] texts(String[] sources){String[] result=sources.clone();for(int i=0;i<result.length;i++)result[i]=text(result[i]);return result;}
    public static String format(String source,Object... args){return substitute(text(source),args);}
    private static String substitute(String template,Object[] args){java.util.regex.Matcher match=ARGUMENT.matcher(template);StringBuffer result=new StringBuffer();while(match.find()){int index=Integer.parseInt(match.group(1));String replacement=index<args.length?String.valueOf(args[index]):match.group();match.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));}match.appendTail(result);return result.toString();}
    private static String unescape(String value){return value.replace("\\t","\t").replace("\\n","\n");}
    private static final class Template {
        final java.util.regex.Pattern pattern;
        final String value;
        Template(String key,String value){String[] parts=key.split("\\{[0-9]+\\}",-1);StringBuilder regex=new StringBuilder("^");for(int i=0;i<parts.length;i++){regex.append(java.util.regex.Pattern.quote(parts[i]));if(i<parts.length-1)regex.append("(.*?)");}pattern=java.util.regex.Pattern.compile(regex.append("$").toString());this.value=value;}
    }
    /** Publish one immutable snapshot; dynamic labels do not rebuild regexes for each row. */
    private static final class Catalog {
        final Map<String,String> translations,folded;
        final List<Template> templates;
        Catalog(Map<String,String> entries){translations=Collections.unmodifiableMap(new LinkedHashMap<String,String>(entries));Map<String,String> lower=new HashMap<String,String>();List<Template> patterns=new ArrayList<Template>();for(Map.Entry<String,String> entry:entries.entrySet()){lower.put(entry.getKey().toLowerCase(Locale.ROOT),entry.getValue());if(entry.getKey().contains("{0}"))patterns.add(new Template(entry.getKey(),entry.getValue()));}folded=Collections.unmodifiableMap(lower);templates=Collections.unmodifiableList(patterns);}
    }
}
