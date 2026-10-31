package com.bofo.finder.util;

import static org.junit.Assert.*;

import org.junit.Test;

/** The form rules must match the CHECK constraints on public.users (username regex, age 10-100). */
public class ProfileRulesTest {
    @Test public void username() {
        assertTrue(Validate.username("jorene_m.1"));
        assertTrue(Validate.username("abc"));
        assertFalse(Validate.username("ab"));
        assertFalse(Validate.username("a b c"));
        assertFalse(Validate.username("this_name_is_way_too_long_x"));
        assertFalse(Validate.username("emoji☺"));
        assertFalse(Validate.username(null));
    }

    @Test public void age() {
        assertEquals(Integer.valueOf(20), Validate.age(" 20 "));
        assertEquals(Integer.valueOf(10), Validate.age("10"));
        assertEquals(Integer.valueOf(100), Validate.age("100"));
        assertNull(Validate.age("9"));
        assertNull(Validate.age("101"));
        assertNull(Validate.age("abc"));
        assertNull(Validate.age(null));
    }

    @Test public void phone() {
        assertTrue(Validate.phone("09171234567"));
        assertTrue(Validate.phone("+63 917 123 4567"));
        assertTrue(Validate.phone("(034) 531-1234"));
        assertFalse(Validate.phone("12345"));
        assertFalse(Validate.phone("call me"));
        assertFalse(Validate.phone(null));
    }

    @Test public void email() {
        assertTrue(Validate.email("a@b.co"));
        assertTrue(Validate.email(" jorene@gmail.com "));
        assertFalse(Validate.email("a@b"));
        assertFalse(Validate.email("a b@c.com"));
        assertFalse(Validate.email(null));
    }
}
