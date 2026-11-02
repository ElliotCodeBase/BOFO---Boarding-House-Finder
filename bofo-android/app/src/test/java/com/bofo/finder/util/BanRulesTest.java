package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.Role;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BanRulesTest {
    @Test public void normalizeKeepsLettersAndDigitsOnly() {
        assertEquals("AB1234", BanRules.normalize(" ab-12 34 "));
        assertEquals("", BanRules.normalize(null));
        assertEquals("1234567890", BanRules.normalize("1234-5678 90"));
    }

    @Test public void numberRules() {
        assertTrue(BanRules.validNumber("N01-23-456789"));
        assertTrue(BanRules.validNumber("1234"));
        assertTrue(BanRules.validNumber(" ab 12 "));
        assertFalse(BanRules.validNumber("123"));              // too short
        assertFalse(BanRules.validNumber("12 3"));             // 3 characters once spaces are gone
        assertFalse(BanRules.validNumber("- - - -"));          // no letters or digits
        assertFalse(BanRules.validNumber("ABC#1234"));         // symbol
        assertFalse(BanRules.validNumber("1".repeat(31)));     // too long
        assertTrue(BanRules.validNumber("1".repeat(30)));
        assertFalse(BanRules.validNumber(null));
        assertFalse(BanRules.validNumber(""));
    }

    @Test public void sameNumberDifferentTyping() {
        assertEquals(BanRules.normalize("ab-12 34"), BanRules.normalize("AB1234"));
        assertEquals("  x1 ".trim(), BanRules.forServer("  x1 "));
    }

    @Test public void reasonIsCleanedAndCapped() {
        assertEquals(300, BanRules.reason("x".repeat(500)).length());
        assertEquals("scam", BanRules.reason("  scam\u0000 "));
        assertEquals("", BanRules.reason(null));
    }

    @Test public void onlyAdminsSee() {
        assertTrue(BanRules.canSee(Role.ADMIN));
        assertFalse(BanRules.canSee(Role.OWNER));
        assertFalse(BanRules.canSee(Role.STUDENT));
        assertFalse(BanRules.canSee(null));
    }

    @Test public void searchFilter() {
        List<BanRules.Row> rows = Arrays.asList(
                new BanRules.Row("1", "Ana Cruz", "ana@example.com", "Fake listing", "2026-11-02T01:00:00Z", "Admin", true),
                new BanRules.Row("2", "", "ben@example.com", "Harassment", "2026-11-01T01:00:00Z", "null", false));
        assertEquals(2, BanRules.filter(rows, "").size());
        assertEquals(2, BanRules.filter(rows, null).size());
        assertEquals(1, BanRules.filter(rows, "ANA").size());
        assertEquals(1, BanRules.filter(rows, "harass ben").size());
        assertEquals(0, BanRules.filter(rows, "ana ben").size());
        assertEquals(0, BanRules.filter(rows, "zzz").size());
        assertEquals("", rows.get(1).bannedBy());              // the text "null" from the server reads as empty
        assertEquals(0, BanRules.filter(null, "a").size());
    }
}
