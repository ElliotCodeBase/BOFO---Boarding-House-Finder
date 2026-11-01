package com.bofo.finder.util;

import com.bofo.finder.model.College;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Campus autocomplete for the Home search bar (port of CampusPicker.match / initials / split). Pure Java, no Android. */
public final class CampusMatch {
    private CampusMatch() {}

    private static final Set<String> SMALL = Set.of("of", "the", "and", "de", "del", "for", "in", "at");

    /** Name starts with the text, then a word starts with it, then the name contains it, then the initials match. Empty text = everything. */
    public static List<College> match(List<College> all, String text) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return new ArrayList<>(all);
        List<College> a = new ArrayList<>(), b = new ArrayList<>(), c = new ArrayList<>(), d = new ArrayList<>();
        String compact = q.replace(".", "").replace(" ", "");
        for (College k : all) {
            String n = k.name().toLowerCase(Locale.ROOT);
            if (n.startsWith(q)) a.add(k);
            else if (n.contains(" " + q) || n.contains("(" + q)) b.add(k);
            else if (n.contains(q)) c.add(k);
            else if (initials(n).startsWith(compact) || abbreviation(n, compact)) d.add(k);          // "norsu" -> Negros Oriental State University
        }
        a.addAll(b); a.addAll(c); a.addAll(d);
        return a;
    }

    /** v2.0.101: a typed abbreviation (4+ letters) whose letters appear in order in the name, starting with its first letter: "norsu" = Negros ORiental State University. */
    static boolean abbreviation(String name, String compact) {
        if (compact.length() < 4) return false;
        String letters = name.replaceAll("[^\\p{L}]", "");
        if (letters.isEmpty() || letters.charAt(0) != compact.charAt(0)) return false;
        int at = 0;
        for (int i = 0; i < compact.length(); i++) {
            at = letters.indexOf(compact.charAt(i), at);
            if (at < 0) return false;
            at++;
        }
        return true;
    }

    /** First letters of the words in a name, skipping small words (of, the, and, de, ...). */
    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.replaceAll("[^\\p{L}\\p{N} ]", " ").split("\\s+"))
            if (!w.isEmpty() && !SMALL.contains(w)) sb.append(w.charAt(0));
        return sb.toString();
    }

    /** "Carlos Hilado Memorial State University - Alijis Campus (Bacolod)" -> { "Carlos Hilado Memorial State University", "Alijis Campus · Bacolod" }. */
    public static String[] split(String name) {
        String n = name == null ? "" : name.trim();
        String main = n, city = "", sub = "";
        int par = n.lastIndexOf('(');
        if (par > 0 && n.endsWith(")")) { city = n.substring(par + 1, n.length() - 1).trim(); main = n.substring(0, par).trim(); }
        int dash = main.indexOf(" - ");
        if (dash > 0) { sub = main.substring(dash + 3).trim(); main = main.substring(0, dash).trim(); }
        if (!city.isEmpty()) sub = sub.isEmpty() ? city : sub + " \u00B7 " + city;
        return new String[]{main, sub};
    }

    /** First word of a full name for the greeting; "" when unknown. */
    public static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        return fullName.trim().split("\\s+")[0];
    }

    /** Two initials for the avatar placeholder; "?" when unknown. */
    public static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] p = fullName.trim().split("\\s+");
        String s = p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "");
        return s.toUpperCase(Locale.ROOT);
    }
}
