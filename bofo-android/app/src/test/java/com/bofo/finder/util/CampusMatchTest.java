package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.College;

import java.util.List;
import org.junit.Test;

public class CampusMatchTest {
    private static final List<College> ALL = List.of(
            new College("1", "Negros Oriental State University - Main Campus (Dumaguete)", 9.3, 123.3),
            new College("2", "Silliman University (Dumaguete)", 9.31, 123.31),
            new College("3", "Carlos Hilado Memorial State University - Alijis Campus (Bacolod)", 10.6, 122.9));

    @Test public void emptyTextReturnsEverything() { assertEquals(3, CampusMatch.match(ALL, "  ").size()); }
    @Test public void startsWithComesFirst() { assertEquals("2", CampusMatch.match(ALL, "sill").get(0).id()); }
    @Test public void wordStartAndContains() { assertEquals(2, CampusMatch.match(ALL, "dumag").size()); }
    @Test public void initialsMatch() { assertEquals("1", CampusMatch.match(ALL, "norsu").get(0).id()); }
    @Test public void noMatchIsEmpty() { assertTrue(CampusMatch.match(ALL, "zzz").isEmpty()); }
    @Test public void splitTitleAndCampusLine() {
        String[] a = CampusMatch.split("Carlos Hilado Memorial State University - Alijis Campus (Bacolod)");
        assertEquals("Carlos Hilado Memorial State University", a[0]);
        assertEquals("Alijis Campus \u00B7 Bacolod", a[1]);
        String[] b = CampusMatch.split("Silliman University (Dumaguete)");
        assertEquals("Silliman University", b[0]);
        assertEquals("Dumaguete", b[1]);
    }
    @Test public void greetingNames() {
        assertEquals("Juan", CampusMatch.firstName("Juan dela Cruz"));
        assertEquals("", CampusMatch.firstName(null));
        assertEquals("JC", CampusMatch.initialsOf("Juan dela Cruz"));
        assertEquals("?", CampusMatch.initialsOf(""));
    }
}
