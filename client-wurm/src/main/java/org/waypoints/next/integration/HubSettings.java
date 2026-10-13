package org.waypoints.next.integration;

import org.waypoints.next.ui.*;
import org.waypoints.next.i18n.Messages;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.waypoints.next.surroundings.CreatureHighlightRule;
import java.util.logging.*;

/** One validated settings snapshot and a comment-preserving background writer. */
final class HubSettings implements SettingsController {
    private Properties current=new Properties();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Waypointer-settings");t.setDaemon(true);return t;});
    private final Consumer<Properties> apply;
    private volatile String status="";
    private final Path file;
    private final Supplier<Map<CreatureHighlightRule,Integer>> detected;
    private final java.util.function.IntSupplier meteors;
    HubSettings(Path file,Consumer<Properties> apply){this(file,apply,Collections::emptyMap);}
    HubSettings(Path file,Consumer<Properties> apply,Supplier<Map<CreatureHighlightRule,Integer>> detected){this(file,apply,detected,()->0);}
    HubSettings(Path file,Consumer<Properties> apply,Supplier<Map<CreatureHighlightRule,Integer>> detected,java.util.function.IntSupplier meteors){this.file=file;this.apply=apply;this.detected=detected;this.meteors=meteors;}
    synchronized void configure(Properties source){current=new Properties();current.putAll(source);org.waypoints.next.tiles.TileHighlightSettings.prepareStartup(current);boolean migrated=org.waypoints.next.surroundings.CreatureHighlightSettings.migrateScared(current);Messages.select(current.getProperty("language","en"));if(migrated)persist(current);}
    @Override public synchronized Properties values(){Properties result=new Properties();result.putAll(current);return result;}
    synchronized void setUserLanguage(String code) {
        if (!Arrays.asList(Messages.CODES).contains(code)
                || code.equals(current.getProperty("language", "en"))) return;
        Properties next = values();
        next.setProperty("language", code);
        current=next;
        Messages.select(code); // The updater changes every mod in the same HUD operation.
        persist(next);
    }
    @Override public synchronized void save(Properties draft){
        final Properties next=values();for(SettingSpec spec:SettingSpec.ALL)if(!spec.key.equals("language"))next.setProperty(spec.key,spec.validate(draft.getProperty(spec.key,next.getProperty(spec.key,spec.defaultValue))));
        org.waypoints.next.surroundings.CreatureHighlightSettings.merge(next,draft);
        org.waypoints.next.tiles.TileHighlightSettings.merge(next,draft);
        WaypointClientConfiguration.from(next);current=next;persist(next);
    }
    private void persist(final Properties next){
        status="Saving settings…";
        writer.execute(()->{synchronized(HubSettings.this){try{
            // A queued hub/language save must not overwrite a newer registry edit.
            Properties latest=values();write(file,latest);apply.accept(latest);status="Settings saved. File paths take effect after restart.";
        }catch(Exception failure){status="Settings could not be saved.";Logger.getLogger("WurmWaypointer.Settings").log(Level.WARNING,status,failure);}}});
    }
    @Override public String status(){return Messages.text(status);}
    /** Shared Settings commits one validated value without waiting for a background writer. */
    synchronized void saveImmediate(String key,String value)throws IOException {
        Properties next=values();next.setProperty(key,value);
        for(SettingSpec spec:SettingSpec.ALL)if(spec.key.equals(key))next.setProperty(key,spec.validate(value));
        org.waypoints.next.surroundings.CreatureHighlightSettings.merge(next,next);
        org.waypoints.next.tiles.TileHighlightSettings.merge(next,next);
        WaypointClientConfiguration.from(next);
        write(file,next);current=next;apply.accept(values());status="Settings saved. File paths take effect after restart.";
    }
    @Override public Map<CreatureHighlightRule,Integer> detectedCreatures(){return detected.get();}
    @Override public int detectedMeteors(){return meteors.getAsInt();}
    static boolean generalChanged(Properties before,Properties after) {
        if(before==null)return true;
        for(SettingSpec spec:SettingSpec.ALL)if(!spec.key.equals("language")
                && !before.getProperty(spec.key,spec.defaultValue).equals(after.getProperty(spec.key,spec.defaultValue)))return true;
        return false;
    }
    static void write(Path file,Properties values)throws IOException{
        Path target=file.toAbsolutePath().normalize();Files.createDirectories(target.getParent());
        List<String> source=Files.isRegularFile(target)?Files.readAllLines(target,StandardCharsets.UTF_8):Collections.<String>emptyList();
        List<String> result=new ArrayList<String>();Set<String> done=new HashSet<String>();
        for(int i=0;i<source.size();i++){String line=source.get(i),trimmed=line.trim();
            java.util.regex.Matcher property=java.util.regex.Pattern.compile("^([^:=\\s]+)[\\s:=].*$").matcher(trimmed);
            String key=property.matches()?property.group(1):"";
            if(!trimmed.startsWith("#")&&!trimmed.startsWith("!")&&values.containsKey(key)){
                if(done.add(key))result.add(key+"="+escape(values.getProperty(key)));
                while(continued(line)&&i+1<source.size())line=source.get(++i);
            }else result.add(line);
        }
        for(SettingSpec spec:SettingSpec.ALL)if(!done.contains(spec.key)&&values.containsKey(spec.key))result.add(spec.key+"="+escape(values.getProperty(spec.key)));
        for(String key:org.waypoints.next.surroundings.CreatureHighlightSettings.keys())if(!done.contains(key)&&values.containsKey(key))result.add(key+"="+escape(values.getProperty(key)));
        for(String key:org.waypoints.next.tiles.TileHighlightSettings.KEYS)if(!done.contains(key)&&values.containsKey(key))result.add(key+"="+escape(values.getProperty(key)));
        byte[] bytes=(String.join(System.lineSeparator(),result)+System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        Path temporary=Files.createTempFile(target.getParent(),"waypointer-settings-",".tmp");
        try{try(FileChannel out=FileChannel.open(temporary,StandardOpenOption.WRITE)){ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())out.write(data);out.force(true);}
            if(Files.isRegularFile(target))Files.copy(target,target.resolveSibling(target.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private static boolean continued(String line){int slashes=0;for(int i=line.length()-1;i>=0&&line.charAt(i)=='\\';i--)slashes++;return slashes%2==1;}
    private static String escape(String value){StringBuilder out=new StringBuilder();for(char c:value.toCharArray()){
        if(c=='\\')out.append("\\\\");else if(c=='\t')out.append("\\t");else if(c>127)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else out.append(c);
    }return out.toString();}
}
