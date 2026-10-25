package com.bofo.finder.util;

import java.util.*;

/**
 * Tiny offline "did you mean" matcher for amenity names (no API, no library).
 * Understands: typing the start of a word ("laund"), words inside the name ("wifi" in "Free Wi-Fi"), common nicknames
 * ("aircon", "CR", "internet"), and small typos ("laundy", "wify", "kitchn").
 */
public final class Fuzzy {
    private Fuzzy() {}

    /** nickname -> words that a name must contain (letters and digits only, lower case). */
    private static final Map<String, String[]> SYN = new LinkedHashMap<>();
    private static void syn(String keyword, String... aliases) { for (String a : aliases) SYN.put(a, new String[]{keyword}); }
    static {
        syn("airconditioning", "aircon", "ac", "airco", "airconditioner", "aircondition", "cooling", "cool");
        syn("wifi", "internet", "wireless", "wlan", "net");
        syn("bathroom", "cr", "toilet", "comfortroom", "restroom", "banyo", "cr");
        syn("laundry", "washing", "washer", "labada", "wash");
        syn("kitchen", "cook", "cooking", "stove", "kusina");
        syn("parking", "garage", "carpark");
        syn("cctv", "camera", "security", "cam");
        syn("study", "desk");
        syn("mattress", "bed", "foam");
        syn("cabinet", "closet", "wardrobe", "aparador");
        syn("pet", "dog", "cat", "pets");
        syn("female", "girls", "girl", "ladies", "women", "woman");
        syn("male", "boys", "boy", "men", "guys");
        syn("shower", "heater", "hotwater");
        syn("electricity", "power", "kuryente", "electric");
        syn("water", "tubig");
        syn("curfew", "gate");
    }

    /** Lower-case letters and digits only: "Free Wi-Fi" -> "freewifi". */
    public static String norm(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) b.append(c);
        return b.toString();
    }

    private static List<String> words(String s) {
        List<String> l = new ArrayList<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (!w.isEmpty()) l.add(w);
        return l;
    }

    /** 0 = no match; higher = better. 80+ = the text is really in the name; below that = "did you mean" (nickname or typo). */
    public static int score(String query, String name) {
        String q = norm(query);
        if (q.isEmpty()) return 0;
        String n = norm(name);
        if (n.equals(q)) return 100;
        if (n.startsWith(q)) return 95;
        List<String> ws = words(name);
        for (String w : ws) if (w.startsWith(q)) return 90;

        // where each word starts inside the joined name ("freewifi": 0, 4, 6)
        Set<Integer> starts = new HashSet<>();
        int off = 0;
        for (String w : ws) { starts.add(off); off += norm(w).length(); }
        int at = n.indexOf(q);
        while (at >= 0) { if (starts.contains(at)) return 80; at = n.indexOf(q, at + 1); }
        if (q.length() >= 5 && n.contains(q)) return 70;

        for (Map.Entry<String, String[]> e : SYN.entrySet()) {                      // nicknames
            String alias = e.getKey();
            if (q.equals(alias) || (q.length() >= 3 && alias.startsWith(q))) for (String k : e.getValue()) if (n.contains(k)) return 85;
        }

        int allowed = q.length() <= 3 ? 0 : q.length() <= 6 ? 1 : 2;                // typos
        if (allowed == 0) return 0;
        int best = Integer.MAX_VALUE;
        for (int st : starts) {
            for (int len = q.length() - 1; len <= q.length() + 1; len++) {
                int end = Math.min(n.length(), st + len);
                if (end - st < 2) continue;
                best = Math.min(best, lev(q, n.substring(st, end)));
            }
        }
        return best <= allowed ? 60 - 10 * best : 0;
    }

    private static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
