package com.bofo.finder.util;

import com.bofo.finder.service.BofoAiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the student said in THIS chat (budget, free wifi / water / electricity, language), found by simple local parsing of the
 * user's own messages (English, Tagalog, Hiligaynon / Cebuano, Taglish). Sent to bofo-chat as {@code session} and used by the
 * offline planner ({@link BofoIntentLocal}). The last chat (turns + these preferences) is kept on the phone through
 * {@link DiskCache} so reopening the mascot does not lose it.
 *
 * Privacy: the file is named {@code user_bofo_chat}, so sign-out and "Delete my account and data" wipe it with the other
 * {@code user_*} copies ({@code AuthService.clearLocal}), which also calls {@link #clear()}. No keys, no ids except the result ids
 * the chat already shows.
 */
public final class BofoMemory {
    private BofoMemory() {}

    public static final String FILE = "user_bofo_chat";
    public static final int MAX_TURNS = 20, MAX_TEXT = 600;
    private static final double MIN_BUDGET = 300, MAX_BUDGET = 100_000;     // below / above this a number is not a monthly rent

    /** What one message says. budget = null when it names no price; booleans are true only when the message asks for it; lang = null when unsure. */
    public record Prefs(Double budget, boolean wifi, boolean water, boolean electricity, String lang) {}

    // ------------------------------------------------------------------ parsing (pure, no state)

    private static final String NUM = "(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k\\b)?";
    private static final String NOT_UNIT = "(?!\\s*(?:m\\b|meters?|metres?|metro|km\\b|kilomet|min\\b|mins\\b|minutes?|people|persons?|pax|ka tao|sqm|sq))";
    private static final Pattern BUDGET_KEY = Pattern.compile("(?:\\bunder|\\bbelow|less than|lower than|\\bmax(?:imum)?|up to|not more than|no more than|hanggang|hangtod|hangtud"
            + "|dili molapas(?: sa)?|di molapas(?: sa)?|dili molabaw(?: sa)?|di molabaw(?: sa)?|di lalampas(?: sa)?|hindi lalampas(?: sa)?|indi molapas"
            + "|\\bbudget(?: is| ko| nako| na| around| of| about| ng)?|\\bbadyet(?: ko| nako| na)?|<=?)\\s*(?:₱|php|p)?\\s*" + NUM + NOT_UNIT);
    private static final Pattern BUDGET_AFTER = Pattern.compile(NUM + "\\s*(?:budget|badyet|only|lang|ra\\b|max|pesos|piso|php)" );
    private static final Pattern BUDGET_PESO = Pattern.compile("(?:₱|php)\\s*" + NUM + NOT_UNIT);
    private static final Pattern BUDGET_K = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:\\.\\d)?)\\s*k\\b");
    private static final Pattern NO_LIMIT = Pattern.compile("any budget|no budget|kahit magkano|bisan pila|walang limit|no limit|without (?:a )?budget");
    private static final Pattern WIFI = Pattern.compile("wi-?fi|wify|internet|\\bnet\\b");
    private static final Pattern WATER = Pattern.compile("\\b(?:free )?(?:water|tubig)\\b");
    private static final Pattern ELEC = Pattern.compile("electric|kuryente|koryente|\\bpower\\b");

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}']+");
    private static final java.util.Set<String> CEB = java.util.Set.of("unsa", "asa", "duol", "duul", "barato", "pinakabarato", "kaayo", "dili", "diri",
            "naa", "nako", "nimo", "unsaon", "palihug", "molapas", "molabaw", "adto", "ayaw", "kinahanglan", "indi", "diin", "sang", "kag", "daw", "bay", "bai",
            "gamay", "dako", "pila", "ngano", "wala", "murag", "tabangi", "ako");
    private static final java.util.Set<String> TL = java.util.Set.of("pinakamura", "mura", "malapit", "magkano", "hanggang", "paano", "saan", "gusto", "hanap",
            "mo", "po", "ba", "nga", "yung", "yan", "ito", "may", "libre", "lang", "kaya", "pwede", "pwedeng", "naman", "dito", "ang", "mga", "ng", "sa", "ko",
            "pinakamalapit", "kailangan", "hindi", "wala", "akin", "kasi", "tapos", "yong", "pakita", "ipakita");
    private static final java.util.Set<String> EN = java.util.Set.of("the", "with", "near", "nearest", "cheapest", "under", "show", "me", "which", "is", "of", "to",
            "for", "what", "how", "and", "a", "i", "in", "pls", "please", "my", "budget", "need", "want", "place", "boarding", "house", "closest", "best",
            "compare", "map", "them", "those", "that", "one", "are", "do", "can", "get", "there", "free", "have", "any", "find", "good", "around", "about");

    /** Parses ONE message. Never throws; null / empty text gives an empty Prefs. */
    public static Prefs parse(String msg) {
        if (msg == null || msg.isBlank()) return new Prefs(null, false, false, false, null);
        String m = msg.toLowerCase(Locale.ROOT);
        Double budget = parseBudget(m);
        return new Prefs(budget, WIFI.matcher(m).find(), WATER.matcher(m).find(), ELEC.matcher(m).find(), detectLang(m));
    }

    /** "under 2k", "hanggang 3,000", "dili molapas sa 2500", "budget 2.5k", "₱1800", "2000 pesos", "2k lang"; null when no price. */
    public static Double parseBudget(String lower) {
        if (lower == null) return null;
        Pattern[] ps = {BUDGET_KEY, BUDGET_PESO, BUDGET_AFTER};
        for (Pattern p : ps) {
            Matcher mt = p.matcher(lower);
            while (mt.find()) { Double v = value(mt.group(1), mt.group(2) != null); if (v != null) return v; }
        }
        Matcher k = BUDGET_K.matcher(lower);
        while (k.find()) { Double v = value(k.group(1), true); if (v != null) return v; }
        return null;
    }

    private static Double value(String digits, boolean k) {
        if (digits == null) return null;
        try {
            String d = digits.replaceAll(",(?=\\d{3}(?:\\D|$))", "").replace(",", ".");
            double v = Double.parseDouble(d) * (k ? 1000 : 1);
            return v >= MIN_BUDGET && v <= MAX_BUDGET ? Double.valueOf(Math.round(v)) : null;
        } catch (NumberFormatException e) { return null; }
    }

    /** en | tl | ceb | taglish by marker words; null when the message has too few words to tell (so "ok" never flips the language). */
    public static String detectLang(String lower) {
        if (lower == null) return null;
        int ceb = 0, tl = 0, en = 0;
        Matcher t = TOKEN.matcher(lower);
        while (t.find()) {
            String w = t.group();
            if (CEB.contains(w)) ceb++;
            if (TL.contains(w) && !CEB.contains(w)) tl++;
            if (EN.contains(w)) en++;
        }
        int fil = ceb + tl;
        if (fil == 0) return en >= 2 ? "en" : null;
        if (ceb > tl) return "ceb";
        return en >= 2 ? "taglish" : "tl";
    }

    // ------------------------------------------------------------------ state for this chat

    private static Double budget;
    private static boolean wifi, water, electricity;
    private static String lang;
    private static final List<BofoAiService.Turn> turns = new ArrayList<>();
    private static boolean loaded;

    /** Remembers what this student message says (a new value replaces the old one; "any budget" clears the budget). */
    public static synchronized void observe(String userMessage) {
        ensureLoaded();
        if (userMessage == null) return;
        Prefs p = parse(userMessage);
        String lower = userMessage.toLowerCase(Locale.ROOT);
        boolean changed = false;
        if (p.budget() != null && !p.budget().equals(budget)) { budget = p.budget(); changed = true; }
        else if (p.budget() == null && budget != null && NO_LIMIT.matcher(lower).find()) { budget = null; changed = true; }
        if (p.wifi() && !wifi) { wifi = true; changed = true; }
        if (p.water() && !water) { water = true; changed = true; }
        if (p.electricity() && !electricity) { electricity = true; changed = true; }
        if (p.lang() != null && !p.lang().equals(lang)) { lang = p.lang(); changed = true; }
        if (changed) save();
    }

    public static synchronized Double budget() { ensureLoaded(); return budget; }
    public static synchronized boolean wifi() { ensureLoaded(); return wifi; }
    public static synchronized boolean water() { ensureLoaded(); return water; }
    public static synchronized boolean electricity() { ensureLoaded(); return electricity; }
    /** Last language the student wrote in (en | tl | ceb | taglish) or null. */
    public static synchronized String lang() { ensureLoaded(); return lang; }
    /** True when the student writes Tagalog, Hiligaynon / Cebuano or Taglish (the offline answers then use the local words). */
    public static synchronized boolean filipino() { ensureLoaded(); return lang != null && !lang.equals("en"); }

    /** The request field {@code session}: just what the student said in this chat. */
    public static synchronized JSONObject toSession() { return mergeSession(null, false, false, false); }

    /**
     * The request field {@code session} = what the app filters say (price slider, amenity filters) merged with what the student said
     * in this chat. What the student said wins for the price (it is the newer wish); the free-wifi / water / electricity flags are OR-ed.
     * The server reads {@code filters_in_effect} first, so the numbers live there.
     */
    public static synchronized JSONObject mergeSession(Double uiMaxPrice, boolean uiWifi, boolean uiWater, boolean uiElectricity) {
        ensureLoaded();
        JSONObject f = new JSONObject(), s = new JSONObject();
        try {
            Double price = budget != null ? budget : uiMaxPrice;
            if (price != null && price > 0) f.put("max_price", price.doubleValue());
            if (wifi || uiWifi) f.put("free_wifi", true);
            if (water || uiWater) f.put("free_water", true);
            if (electricity || uiElectricity) f.put("free_electricity", true);
            s.put("filters_in_effect", f);
            if (lang != null) s.put("lang", lang);
        } catch (Exception ignored) { }
        return s;
    }

    // ------------------------------------------------------------------ the last chat on the phone

    /** Adds one turn (the student's or BOFO's) to the saved chat. Text is cut to {@link #MAX_TEXT}; at most {@link #MAX_TURNS} are kept. */
    public static synchronized void append(boolean mine, String text, List<String> resultIds) {
        ensureLoaded();
        if (text == null || text.isBlank()) return;
        turns.add(new BofoAiService.Turn(mine, clip(text, MAX_TEXT), resultIds == null ? null : new ArrayList<>(resultIds)));
        while (turns.size() > MAX_TURNS) turns.remove(0);
        save();
    }

    /** A copy of the saved turns, oldest first (empty when there is no saved chat). */
    public static synchronized List<BofoAiService.Turn> loadHistory() { ensureLoaded(); return new ArrayList<>(turns); }

    /** Forgets everything, in memory and on the phone. Called on sign-out, on "Delete my account and data" and by "New chat". */
    public static synchronized void clear() {
        budget = null; wifi = false; water = false; electricity = false; lang = null;
        turns.clear();
        loaded = true;
        DiskCache.deletePrefix(FILE);
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        String raw = DiskCache.read(FILE);
        if (raw == null || raw.isBlank()) return;
        try {
            JSONObject o = new JSONObject(raw);
            if (o.has("budget") && !o.isNull("budget")) budget = o.optDouble("budget");
            wifi = o.optBoolean("wifi"); water = o.optBoolean("water"); electricity = o.optBoolean("electricity");
            lang = o.isNull("lang") ? null : o.optString("lang", null);
            JSONArray a = o.optJSONArray("turns");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject t = a.optJSONObject(i);
                if (t == null || t.optString("t", "").isEmpty()) continue;
                List<String> ids = new ArrayList<>();
                JSONArray ia = t.optJSONArray("ids");
                if (ia != null) for (int j = 0; j < ia.length(); j++) ids.add(ia.optString(j, ""));
                turns.add(new BofoAiService.Turn(t.optBoolean("m"), t.optString("t"), ids.isEmpty() ? null : ids));
            }
        } catch (Exception e) {            // a damaged file is just "no saved chat"
            budget = null; wifi = false; water = false; electricity = false; lang = null; turns.clear();
        }
    }

    private static void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            if (budget != null) o.put("budget", budget.doubleValue());
            o.put("wifi", wifi).put("water", water).put("electricity", electricity);
            if (lang != null) o.put("lang", lang);
            JSONArray a = new JSONArray();
            for (BofoAiService.Turn t : turns) {
                JSONObject j = new JSONObject().put("m", t.mine()).put("t", t.text());
                if (t.resultIds() != null && !t.resultIds().isEmpty()) j.put("ids", new JSONArray(t.resultIds()));
                a.put(j);
            }
            o.put("turns", a);
            DiskCache.write(FILE, o.toString());
        } catch (Exception ignored) { }
    }

    /** Cuts to {@code max} characters without splitting a surrogate pair. */
    public static String clip(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        int end = max;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
