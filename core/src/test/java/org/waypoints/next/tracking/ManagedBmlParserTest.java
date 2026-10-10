package org.waypoints.next.tracking;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManagedBmlParserTest {
    private String form(String rows){return "border{center{passthrough{id=\"id\";text=\"173\"}table{rows=\"1\";cols=\"8\";label{text=\"\"}button{text=\"Name\";id=\"sort1\"}label{text=\"Animal Type\"}label{text=\"On Deed?\"}label{text=\"Hitched?\"}label{text=\"Cared For?\"}label{text=\"Branded?\"}label{text=\"Tamed?\"}"+rows+"}button{id=\"find\";text=\"Find\"}button{id=\"close\";text=\"Close\"}}}";}
    private String row(String id){return "radio{group=\"sel\";id=\""+id+"\"}label{text='Mare \"A\"'}label{text=\"horse\"}label{text=\"Yes\"}label{text=\"No\"}button{id=\"care\";text=\"Stop caring\"}label{text=\"Yes\"}label{text=\"No\"}";}
    @Test public void nativeSortArrowsAndBackslashesAreLiteralBmlText(){
        for(String heading:new String[]{"Name /\\","Name \\/","Name"}) {
            String bml=form(row("42")).replace("text=\"Name\"","text=\""+heading+"\"");
            ManagedBmlParser.Result parsed=ManagedBmlParser.parse("Alice's List of Animals",bml,ManagedKind.ANIMAL);
            assertNotNull("Native sorting header "+heading,parsed);assertEquals(42,parsed.entries.get(0).id);
        }
        ManagedBmlParser.Result parsed=ManagedBmlParser.parse("Alice's List of Animals",form(row("42")).replace("Mare \"A\"","Mare \\ Alpha"),ManagedKind.ANIMAL);
        assertEquals("Mare \\ Alpha",parsed.entries.get(0).name);
    }
    @Test public void readsNativeHeadersRowsAndQuestionIdWithoutInvokingSideEffectButtons(){ManagedBmlParser.Result parsed=ManagedBmlParser.parse("Manage Animals",form(row("42")),ManagedKind.ANIMAL);assertNotNull(parsed);assertEquals("173",parsed.questionId);assertEquals(42,parsed.entries.get(0).id);assertEquals("Mare \"A\"",parsed.entries.get(0).name);assertTrue(parsed.canLocate);assertNotNull(ManagedBmlParser.parse("Manage Animals",form(""),ManagedKind.ANIMAL));}
    @Test public void rejectsUnknownTruncatedDuplicateAndOversizedForms(){assertNull(ManagedBmlParser.parse("Manage Ships",form(row("42")),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",form(row("42")+row("42")),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",form(row("42")).replace("cols=\"8\"","cols=\"7\""),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",form(row("42")).substring(0,80),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",new String(new char[ManagedBmlParser.MAX_BYTES+1]),ManagedKind.ANIMAL));}
}
