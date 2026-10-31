package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Map;

public class AuthLinksTest {
    @Test public void recognisesTheCallbackLink() {
        assertTrue(AuthLinks.isCallback("bofo://auth-callback?code=abc"));
        assertTrue(AuthLinks.isCallback("BOFO://auth-callback#error=x"));
        assertFalse(AuthLinks.isCallback("https://example.com/?code=abc"));
        assertFalse(AuthLinks.isCallback("bofo://other"));
        assertFalse(AuthLinks.isCallback(null));
    }

    @Test public void readsTheCodeFromTheQuery() {
        Map<String, String> p = AuthLinks.params("bofo://auth-callback?code=1234-abcd");
        assertEquals("1234-abcd", p.get("code"));
    }

    @Test public void readsErrorsFromQueryAndFragmentAndDecodes() {
        Map<String, String> p = AuthLinks.params("bofo://auth-callback?error=access_denied#error_description=Email+link%20is+invalid");
        assertEquals("access_denied", p.get("error"));
        assertEquals("Email link is invalid", p.get("error_description"));
    }

    @Test public void noParamsGivesEmptyMap() {
        assertTrue(AuthLinks.params("bofo://auth-callback").isEmpty());
        assertTrue(AuthLinks.params(null).isEmpty());
    }

    @Test public void firstValueWins() {
        assertEquals("1", AuthLinks.params("bofo://auth-callback?code=1&code=2").get("code"));
    }
}
