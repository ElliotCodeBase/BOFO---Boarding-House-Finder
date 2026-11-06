package com.bofo.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class CsvRulesTest {
    @Test public void formulaCellsAreNeutralised() {
        assertEquals("'=1+1", CsvRules.cell("=1+1"));
        assertEquals("'+5", CsvRules.cell("+5"));
        assertEquals("'-5", CsvRules.cell("-5"));
        assertEquals("'@x", CsvRules.cell("@x"));
        assertEquals("'\tx", CsvRules.cell("\tx"));
        assertEquals("scam", CsvRules.cell("scam"));
    }
    @Test public void escaping() {
        assertEquals("\"a,b\"", CsvRules.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvRules.cell("say \"hi\""));
        assertEquals("a b", CsvRules.cell("a\nb"));
        assertEquals("", CsvRules.cell(null));
    }
    @Test public void document() {
        String d = CsvRules.document(new String[]{"id", "status"}, Arrays.asList(new String[]{"1", "pending"}, new String[]{"2", "=x"}));
        assertTrue(d.startsWith(CsvRules.BOM + "id,status\r\n"));
        assertTrue(d.endsWith("2,'=x\r\n"));
        assertEquals(CsvRules.BOM + "a\r\n", CsvRules.document(new String[]{"a"}, Collections.emptyList()));
    }
}
