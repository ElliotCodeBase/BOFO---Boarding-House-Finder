package com.bofo.finder.util;

import static org.junit.Assert.assertEquals;

import com.bofo.finder.model.BoardingHouse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GenderRulesTest {
    private static BoardingHouse bh(String id, String gender) {
        return new BoardingHouse(id, "o", id, "", 0, 0, 1000, 1, false, false, false, 0, 0, "", false, 0, false, new ArrayList<String>(), gender);
    }

    @Test public void normalizeKeepsOnlyKnownValues() {
        assertEquals("female", GenderRules.normalize(" Female "));
        assertEquals("any", GenderRules.normalize("ANY"));
        assertEquals("", GenderRules.normalize("null"));
        assertEquals("", GenderRules.normalize(null));
    }

    @Test public void keepFiltersByExactPolicy() {
        List<BoardingHouse> all = List.of(bh("a", "female"), bh("b", "male"), bh("c", "any"), bh("d", ""));
        assertEquals(4, GenderRules.keep(all, "").size());
        assertEquals("a", GenderRules.keep(all, "female").get(0).id());
        assertEquals("b", GenderRules.keep(all, "male").get(0).id());
        assertEquals("c", GenderRules.keep(all, "any").get(0).id());
        assertEquals(1, GenderRules.keep(all, "any").size());
    }
}
