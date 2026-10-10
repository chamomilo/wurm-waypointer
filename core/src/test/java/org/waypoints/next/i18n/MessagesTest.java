package org.waypoints.next.i18n;
import org.junit.Test;
import org.waypoints.next.ui.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
public class MessagesTest {
    @Test public void placeholderLikeObjectNamesAreNotReinterpretedAsArguments(){Messages.select("ru");try{assertEquals("{1}, мэр — Alice, X=10 Y=20",Messages.format("{0}, mayor - {1}, X={2} Y={3}","{1}","Alice",10,20));}finally{Messages.select("en");}}
    @Test public void compositeTitlesHaveFiniteFallback(){Messages.select("ru");try{assertEquals("Wurm Waypointer - Точки",Messages.text("Wurm Waypointer - Waypoints"));assertEquals("Наблюдение (3)",Messages.text("Monitoring (3)"));}finally{Messages.select("en");}}
    @Test public void allFourCataloguesCoverSelectorsAndSettingsAndPreserveUnknownNames()throws Exception{
        try{for(String code:Messages.CODES){Messages.select(code);assertEquals(code,Messages.language());Set<String> keys=new HashSet<String>();try(BufferedReader in=new BufferedReader(new InputStreamReader(Messages.class.getResourceAsStream(code+".tsv"),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null)if(!line.startsWith("#")){String key=line.split("\t",2)[0];assertTrue("Duplicate key: "+key,keys.add(key));}}
            for(WaypointerSection section:WaypointerSection.values())assertTrue(code+":"+section.label(),keys.contains(section.label()));for(SettingSpec spec:SettingSpec.ALL){assertTrue(code+":"+spec.label,keys.contains(spec.label));assertTrue(keys.contains(spec.group));}assertEquals("My horse Alpha",Messages.text("My horse Alpha"));assertFalse(Messages.format("{0} readings",3).contains("{0}"));}}
        finally{Messages.select("en");}
    }
}
