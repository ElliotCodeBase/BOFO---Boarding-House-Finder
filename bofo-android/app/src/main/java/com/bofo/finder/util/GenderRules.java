package com.bofo.finder.util;

import com.bofo.finder.model.BoardingHouse;

import java.util.ArrayList;
import java.util.List;

/** v2.0.87: who may stay in a boarding house. Values match the {@code gender_policy} column (012_gender_policy.sql). */
public final class GenderRules {
    private GenderRules() {}

    public static final String ANY = "any", FEMALE = "female", MALE = "male";

    /** One of the three values, or "" when the value is missing or unknown (database without the column). */
    public static String normalize(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals(ANY) || s.equals(FEMALE) || s.equals(MALE) ? s : "";
    }

    /** Text shown on the card and the place page for a normalized value (R.string.gender_*). */
    public static int labelRes(String normalized) {
        if (FEMALE.equals(normalized)) return com.bofo.finder.R.string.gender_female;
        if (MALE.equals(normalized)) return com.bofo.finder.R.string.gender_male;
        return com.bofo.finder.R.string.gender_any;
    }

    /** Filter: "" = every place, otherwise the place must have exactly this policy (female / male / any = both). */
    public static List<BoardingHouse> keep(List<BoardingHouse> in, String filter) {
        String f = normalize(filter);
        if (f.isEmpty()) return in;
        List<BoardingHouse> out = new ArrayList<>();
        for (BoardingHouse b : in) if (f.equals(normalize(b.genderPolicy()))) out.add(b);
        return out;
    }
}
