package com.bofo.finder.util;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.BofoAnswer;
import com.bofo.finder.model.BofoAnswer.Action;
import com.bofo.finder.model.BofoAnswer.Compare;
import com.bofo.finder.model.BofoAnswer.Result;
import com.bofo.finder.model.BofoAnswer.Row;
import com.bofo.finder.model.College;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based Ask BOFO: no server, no LLM. Same keywords as parseLocal() in supabase/functions/bofo-chat/index.ts
 * (English + Tagalog + Hiligaynon). Used when the Edge Function is unreachable, so "nearest / cheapest / best rated / on the map"
 * never depends on a free model being up. Works on the list the app already has (BhService.list), Geo for distance.
 * answer() returns null when it cannot help (the caller then uses BofoAiService.faq).
 */
public final class BofoIntentLocal {
    private BofoIntentLocal() {}

    private static final Pattern PRIVATE = Pattern.compile("id photos?|selfies?|passwords?|service.?role|api.?keys?|system prompt|other users?'? (chats?|messages?)");
    private static final Pattern COMPARE = Pattern.compile("compare|ikumpara|pagkumpara|kumpara|versus|\\bvs\\b");
    private static final Pattern MAP = Pattern.compile("\\bmap\\b|mapa|where is|\\bsaan\\b|\\basa\\b|locate");
    private static final Pattern PATH = Pattern.compile("\\b(path|route|routes|ruta|daan|dalan|directions?|how (?:do|can|to) i (?:get|go)|how to get|way to|show me the way|take me|guide me|lead me|paano pumunta|unsaon pag)\\b");
    private static final Pattern DIRECTIONS = Pattern.compile("direction|how far|how long|paano pumunta|how do i get|\\broute\\b|\\blayo\\b|unsaon pag");
    private static final Pattern CHEAP = Pattern.compile("cheap|pinakamura|barato|\\bmura\\b|baratilyo|pinakabarato|lowest price");
    private static final Pattern NEAR = Pattern.compile("nearest|closest|closer|\\bnear\\b|nearby|malapit|\\blapit\\b|pinakamalapit|\\bduol\\b|\\bduul\\b|pinakaduol");
    private static final Pattern BEST = Pattern.compile("best.?rated|top.?rated|rating|\\brated\\b|pinakamaganda|pinakamaayo|reviews?|highest");
    private static final Pattern AVAIL = Pattern.compile("available|vacant|bakante|\\bbeds?\\b|\\bkama\\b");
    private static final Pattern DETAILS = Pattern.compile("details|tell me about|more about|info (on|about)");
    private static final Pattern SEARCH = Pattern.compile("find|search|looking for|hanap|gusto ko|boarding|bh\\b|apartment|room|suggest|recommend|irekomenda|mungkahi|\\bhere\\b|\\bdito\\b|\\bdiri\\b");
    private static final Pattern THOSE = Pattern.compile("\\b(those|them|these|sila|iyan|yan|ito|niini|top \\d)\\b");
    private static final Pattern THOSE_WORD = Pattern.compile("\\b(those|them|these|sila|iyan|yan|ito|niini)\\b");
    private static final Pattern FAQ_TOPIC = Pattern.compile("verif|delete|\\bchat\\b");          // the only topics BofoAiService.faq() really answers
    private static final String[] VOCAB = {"cheapest", "nearest", "closest", "nearby", "compare", "available", "affordable", "budget", "pinakamura",
            "pinakamalapit", "pinakabarato", "internet", "directions", "boarding"};
    private static final Pattern THEN = Pattern.compile("(?i),?\\s*\\b(?:and then|then|tapos|pagkatapos|dayon|unya|after that|next)\\b[,:]?\\s*");
    private static final Pattern AT_CAMPUS = Pattern.compile("(?:\\bnear(?:by| to)?|closest to|nearest to|malapit (?:sa|kay)|lapit sa|duol (?:sa|kay|og)|around|beside|\\bsa|\\bto|\\bat)\\s+(?:the\\s+)?([\\p{L}.]+)(?:\\s+([\\p{L}.]+))?");
    private static final Set<String> NOT_CAMPUS = Set.of("school", "campus", "university", "college", "eskwelahan", "eskuylahan", "skwelahan", "please", "pls", "plz",
            "here", "there", "dito", "diri", "this", "that", "area", "place", "map", "them", "those", "me", "us", "wifi", "water", "tubig", "free", "price", "cheap",
            "cheapest", "nearest", "closest", "boarding", "house", "houses", "room", "rooms", "bedspace", "internet", "electricity", "kuryente");
    private static final Pattern FIL = Pattern.compile("\\b(ang|mga|sa|ng|po|ba|nga|pinaka\\w*|malapit|barato|mura|saan|paano|kaayo|unsa|asa|ko|ako|gusto|hanap|may|lang|sang|kag)\\b");

    /**
     * @param list the places the app has loaded (beds > 0). @param lastIds ids of the cards shown by the previous answer.
     * v2.1.2: tolerates typos, prices ("under 2k", "hanggang 3000", "dili molapas 3000"), a campus named in the message, uses what the student
     * said earlier in this chat ({@link BofoMemory}), does the first of several requests and offers the next one as a chip, and asks one short
     * question (clarify + chips) instead of the generic line. Returns null only for the app-help topics {@code BofoAiService.faq} answers.
     */
    public static BofoAnswer answer(String msg, List<BoardingHouse> list, List<String> lastIds) {
        if (msg == null || list == null) return null;
        BofoMemory.observe(msg);                                   // budget / free wifi, water, electricity / language the student just said
        String[] two = splitCompound(msg);
        if (two != null) {
            BofoAnswer a = answerOne(two[0], list, lastIds);
            if (a != null && a.clarify() == null) return a.withSuggestions(chips(two[1], a.suggestions()));
        }
        return answerOne(msg, list, lastIds);
    }

    private static BofoAnswer answerOne(String msg, List<BoardingHouse> list, List<String> lastIds) {
        final String fixed = fixTypos(msg);                         // typos repaired, capital letters kept (place names are read from it)
        final String m = fixed.toLowerCase(Locale.ROOT);
        final String lg = BofoMemory.detectLang(m);                 // null = cannot tell from this message
        final boolean fil = FIL.matcher(m).find() || (lg != null && !lg.equals("en"));
        if (PRIVATE.matcher(m).find())
            return BofoAnswer.text(fil ? "Pasensya na, hindi ko maibabahagi ang pribadong impormasyon. Makakatulong ako sa paghahanap ng boarding house!"
                    : "Sorry, I can't share private information such as ID photos, selfies or other people's chats. I can help you find a boarding house instead!");

        final College named = namedCampus(m);                      // "near norsu": the campus in the message wins over the chosen one
        final Double lat = named != null ? Double.valueOf(named.lat()) : BrowseState.refLat();
        final Double lng = named != null ? Double.valueOf(named.lng()) : BrowseState.refLng();
        boolean hasRef = lat != null && lng != null;
        final BofoMemory.Prefs say = BofoMemory.parse(m);
        Double maxPrice = say.budget();
        boolean wifi = say.wifi(), water = say.water(), elec = say.electricity();

        List<String> last = lastIds == null ? new ArrayList<>() : lastIds;
        List<String> refIds = refs(m, last);
        List<BoardingHouse> pool = new ArrayList<>();
        for (BoardingHouse b : list) if (refIds.isEmpty() || refIds.contains(b.id())) pool.add(b);

        String intent;
        if (COMPARE.matcher(m).find()) intent = "compare";
        else if (PATH.matcher(m).find()) intent = "directions";
        else if (MAP.matcher(m).find()) intent = "map";
        else if (DIRECTIONS.matcher(m).find()) intent = "directions";
        else if (CHEAP.matcher(m).find()) intent = "cheapest";
        else if (NEAR.matcher(m).find()) intent = "nearest";
        else if (BEST.matcher(m).find()) intent = "best";
        else if (AVAIL.matcher(m).find()) intent = "avail";
        else if (DETAILS.matcher(m).find()) intent = "details";
        else if (maxPrice != null || wifi || water || elec || SEARCH.matcher(m).find()) intent = "search";
        else if (FAQ_TOPIC.matcher(m).find()) return null;                                     // app help: the caller uses BofoAiService.faq
        else return clarifyAnswer(tr(lg, fil, "I'm not sure what you need yet. Do you want the nearest, the cheapest, or the best rated boarding house?",
                "Hindi ko pa sigurado ang hanap mo. Gusto mo ba ang pinakamalapit, pinakamura o pinakamataas ang rating?",
                "Wala pa ko kasabot sa imong gipangita. Gusto ka ba sa pinakaduol, pinakabarato, o pinakamaayo og rating?"),
                chipsFor(lg, fil));

        if (THOSE_WORD.matcher(m).find() && last.isEmpty() && (intent.equals("cheapest") || intent.equals("best") || intent.equals("avail") || intent.equals("nearest")))
            return clarifyAnswer(tr(lg, fil, "Which places do you mean? Ask for a list first, for example the cheapest with wifi.",
                    "Alin ang tinutukoy mo? Humingi muna ng listahan, halimbawa ang pinakamura na may wifi.",
                    "Hain ang imong gipasabot? Pangayo una og lista, pananglitan ang pinakabarato nga naay wifi."), chipsFor(lg, fil));

        // ---- named places (compare / map / directions / details)
        if (intent.equals("compare") || intent.equals("details") || intent.equals("map") || intent.equals("directions")) {
            List<BoardingHouse> found = new ArrayList<>();
            String missing = null;
            List<String> names = names(fixed, intent);
            for (String n : names) {
                BoardingHouse best = null; int bs = 0;
                for (BoardingHouse b : list) { int s = Fuzzy.score(n, b.name()); if (s > bs) { bs = s; best = b; } }
                if (best != null && bs >= 80) { if (!found.contains(best)) found.add(best); }
                else if (missing == null) missing = suggest(n, list, fil);
            }
            if (missing != null) return BofoAnswer.text(missing);
            if (found.isEmpty()) for (BoardingHouse b : pool) if (!refIds.isEmpty() || last.contains(b.id())) found.add(b);
            if (found.isEmpty() && (intent.equals("map") || intent.equals("directions"))) { found = new ArrayList<>(pool); found.sort(Comparator.<BoardingHouse>comparingDouble(b -> hasRef ? Geo.km(lat, lng, b.lat(), b.lng()) : 0)); }
            if (found.isEmpty()) return clarifyAnswer(tr(lg, fil, "Which place do you mean? Ask for the nearest or cheapest first.",
                    "Anong lugar ang tinutukoy mo? Humingi muna ng pinakamura o pinakamalapit.", "Hain nga lugar ang imong gipasabot? Pangayo una og pinakabarato o pinakaduol."), chipsFor(lg, fil));
            if (intent.equals("compare") && found.size() < 2)
                return clarifyAnswer(fil ? "Kailangan ko ng dalawang pangalan para maikumpara." : "I need two place names to compare, for example: compare Casa Verde and Lola's Place.", new ArrayList<>());
            int n = intent.equals("compare") ? Math.min(4, found.size()) : intent.equals("map") ? Math.min(5, found.size()) : 1;
            List<Result> rs = results(found.subList(0, n), lat, lng);
            List<Action> acts = new ArrayList<>();
            List<String> ids = ids(rs);
            Compare cmp = null; String reply;
            switch (intent) {
                case "compare": cmp = compare(rs); reply = fil ? "Heto ang paghahambing. Pinakamura: " + cheapest(rs).name() + "." : "Here's the side by side. Cheapest: " + cheapest(rs).name() + "."; break;
                case "map": acts.add(new Action("show_on_map", null, ids)); reply = fil ? "Ipinapakita ko ang " + n + " lugar sa mapa." : "Showing " + n + " place" + (n > 1 ? "s" : "") + " on the map."; break;
                case "directions": acts.add(new Action("directions", ids.get(0), new ArrayList<>())); reply = line(rs.get(0)) + (fil ? ". Ipinapakita ko ang daan sa mapa." : ". Showing the path on the map."); break;
                default: acts.add(new Action("open_detail", ids.get(0), new ArrayList<>())); reply = line(rs.get(0)) + "."; break;
            }
            if (!intent.equals("map") && !ids.isEmpty()) acts.add(0, new Action("show_on_map", null, ids));
            return new BofoAnswer(reply, intent, rs, cmp, acts, null, new ArrayList<>(), true);
        }

        // ---- ranked lists. What the student said earlier in this chat fills what this message leaves open (not when it points at "those").
        final List<String> used = new ArrayList<>();
        if (refIds.isEmpty()) {
            if (maxPrice == null && BofoMemory.budget() != null) { maxPrice = BofoMemory.budget(); used.add(peso(maxPrice)); }
            if (!wifi && BofoMemory.wifi()) { wifi = true; used.add(tr(lg, fil, "free wifi", "libreng wifi", "libre nga wifi")); }
            if (!water && BofoMemory.water()) { water = true; used.add(tr(lg, fil, "free water", "libreng tubig", "libre nga tubig")); }
            if (!elec && BofoMemory.electricity()) { elec = true; used.add(tr(lg, fil, "free electricity", "libreng kuryente", "libre nga kuryente")); }
        }
        List<BoardingHouse> out = new ArrayList<>();
        for (BoardingHouse b : pool) {
            if (maxPrice != null && b.price() > maxPrice) continue;
            if (wifi && !b.wifi()) continue;
            if (water && !b.water()) continue;
            if (elec && !b.electricity()) continue;
            out.add(b);
        }
        // A chosen college limits every list answer to the app's radius around it.
        final College campus = named != null ? named : BrowseState.campus();
        final double areaKm = Math.max(0.5, BrowseState.radiusKm);
        if (campus != null && refIds.isEmpty()) out.removeIf(b -> Geo.km(campus.lat(), campus.lng(), b.lat(), b.lng()) > areaKm);
        final Double fLat = lat, fLng = lng;
        Comparator<BoardingHouse> byDist = Comparator.comparingDouble(b -> hasRef ? Geo.km(fLat, fLng, b.lat(), b.lng()) : 0);
        switch (intent) {
            case "cheapest": out.sort(Comparator.<BoardingHouse>comparingDouble(BoardingHouse::price).thenComparing(byDist)); break;
            case "best": out.removeIf(b -> b.reviewCount() < 1);
                out.sort(Comparator.<BoardingHouse>comparingDouble(BoardingHouse::avgRating).reversed().thenComparing(Comparator.<BoardingHouse>comparingInt(BoardingHouse::reviewCount).reversed())); break;
            case "avail": out.sort(Comparator.<BoardingHouse>comparingInt(BoardingHouse::availableBeds).reversed()); break;
            default: if (hasRef) out.sort(byDist); else out.sort(Comparator.<BoardingHouse>comparingDouble(BoardingHouse::avgRating).reversed());
        }
        boolean needRef = intent.equals("nearest") && !hasRef;
        if (needRef) return clarifyAnswer(tr(lg, fil, "Pick a campus or turn on Near me so I can measure the distance.",
                "Pumili muna ng campus o i-on ang Near me para masukat ko ang layo.", "Pili una og campus o ablihi ang Near me aron masukod nako ang gilay-on."),
                List.of(tr(lg, fil, "Cheapest with wifi", "Pinakamura may wifi", "Pinakabarato naay wifi"), tr(lg, fil, "Best rated", "Pinakamataas ang rating", "Pinakamaayo og rating")));
        if (out.isEmpty() && campus != null && refIds.isEmpty()) {
            String km = String.format(Locale.US, areaKm % 1 == 0 ? "%.0f" : "%.1f", areaKm);
            return BofoAnswer.text(fil ? "Walang tugmang boarding house sa loob ng " + km + " km ng " + campus.name() + ". Subukang palakihin ang radius o pumili ng ibang campus."
                    : "I couldn't find a matching boarding house within " + km + " km of " + campus.name() + ". Try a bigger radius on the map or another campus.");
        }
        if (out.isEmpty()) return BofoAnswer.text(fil ? "Wala akong nakitang tugma. Subukan ang mas mataas na budget o mas kaunting filter." : "I couldn't find a place that matches. Try a higher budget or fewer filters.");
        List<Result> rs = results(out.subList(0, Math.min(3, out.size())), lat, lng);
        StringBuilder list3 = new StringBuilder();
        int n = 0;
        for (Result r : rs) { if (list3.length() > 0) list3.append("\n"); list3.append(++n).append(". ").append(line(r)); }
        String head = switch (intent) {
            case "nearest" -> fil ? "Pinakamalapit:" : "Closest places:";
            case "cheapest" -> fil ? "Pinakamura:" : "Cheapest places, lowest first:";
            case "best" -> fil ? "Pinakamataas ang rating:" : "Best rated:";
            case "avail" -> fil ? "May bakanteng kama:" : "These still have free beds:";
            default -> fil ? "Heto ang nahanap ko:" : "Here is what I found:";
        };
        List<Action> acts = new ArrayList<>();
        acts.add(new Action("show_on_map", null, ids(rs)));
        acts.add(new Action("open_detail", rs.get(0).id(), new ArrayList<>()));
        JSONObject f = new JSONObject();
        try {
            if (maxPrice != null) f.put("max_price", maxPrice);
            if (wifi) f.put("free_wifi", true);
            if (water) f.put("free_water", true);
            if (elec) f.put("free_electricity", true);
        } catch (Exception ignored) { }
        if (f.length() > 0) acts.add(new Action("apply_filters", null, new ArrayList<>()));
        List<String> sug = new ArrayList<>();
        if (rs.size() >= 2) sug.add("Compare them");
        sug.add("Show on map");
        String note = used.isEmpty() ? "" : "\n\n" + tr(lg, fil, "Using what you told me: ", "Gamit ang sinabi mo: ", "Gigamit nako imong gisulti: ") + String.join(", ", used) + ".";
        return new BofoAnswer(head + "\n\n" + list3 + note + (hasRef ? "" : (fil ? "\n\nPumili ng campus para makita ang layo." : "\n\nPick a campus to see distances.")), intent, rs, null, acts, f.length() > 0 ? f : null, sug, true);
    }

    // ------------------------------------------------------------------ v2.1.2 helpers: typos, campus, several requests, clarify

    /** Picks the wording: Cebuano / Hiligaynon when the message is in it, Tagalog when it looks Filipino, else English. */
    private static String tr(String lg, boolean fil, String en, String tl, String ceb) { return "ceb".equals(lg) ? ceb : fil ? tl : en; }

    private static List<String> chipsFor(String lg, boolean fil) {
        return List.of(tr(lg, fil, "Nearest boarding house", "Pinakamalapit", "Pinakaduol"), tr(lg, fil, "Cheapest with wifi", "Pinakamura may wifi", "Pinakabarato naay wifi"),
                tr(lg, fil, "Best rated", "Pinakamataas ang rating", "Pinakamaayo og rating"));
    }

    /** One short question as a normal answer. The chips go in both {@code suggestions} (shown by the current chat) and {@code quickReplies} (Step 3). */
    private static BofoAnswer clarifyAnswer(String question, List<String> chips) {
        return new BofoAnswer(question, "unknown", new ArrayList<>(), null, new ArrayList<>(), null, new ArrayList<>(chips), true, question, new ArrayList<>(chips), null, null);
    }

    /** "cheepest" -> "cheapest", "nearset" -> "nearest" (any letter case): a word of 6+ letters one slip away from a key word (or two slips when it has 9+ letters). */
    static String fixTypos(String text) {
        Matcher t = Pattern.compile("\\p{L}{6,}").matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (t.find()) {
            String w = t.group(), c = correct(w.toLowerCase(Locale.ROOT));
            sb.append(text, last, t.start()).append(c.equals(w.toLowerCase(Locale.ROOT)) ? w : c);     // only a real fix changes the word
            last = t.end();
        }
        return sb.append(text.substring(last)).toString();
    }

    private static String correct(String w) {
        String best = null; int bs = 0;
        for (String k : VOCAB) {
            if (k.equals(w)) return w;
            int s = Fuzzy.score(w, k);                                   // 50 = one slip, 40 = two; 80+ means "starts with", not a typo
            boolean typo = (s >= 50 && s <= 60) || (w.length() >= 9 && s >= 40 && s <= 60) || swapped(w, k);
            if (typo && Math.max(s, 1) > bs) { bs = Math.max(s, 1); best = k; }
        }
        return best == null ? w : best;
    }

    /** True when the two words differ by exactly one swap of neighbouring letters ("nearset" / "nearest"). */
    private static boolean swapped(String a, String b) {
        if (a.length() != b.length()) return false;
        for (int i = 0; i + 1 < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i) && a.substring(i + 2).equals(b.substring(i + 2));
            }
        }
        return false;
    }

    /** A campus the message names after near / malapit sa / duol sa / sa ("near norsu"); null when none (or no campus list loaded). */
    private static College namedCampus(String m) {
        List<College> all = BrowseState.colleges;
        if (all == null || all.isEmpty()) return null;
        Matcher at = AT_CAMPUS.matcher(m);
        while (at.find()) {
            String a = at.group(1), b = at.group(2);
            if (b != null && !NOT_CAMPUS.contains(b) && !NOT_CAMPUS.contains(a)) {          // two words: only a real part of the name counts
                String two = a + " " + b;
                for (College c : all) if (c.name().toLowerCase(Locale.ROOT).contains(two)) return c;
            }
            String w = a.replace(".", "");
            if (w.length() >= 4 && !NOT_CAMPUS.contains(w)) { List<College> r = CampusMatch.match(all, w); if (!r.isEmpty()) return r.get(0); }
        }
        return null;
    }

    private static boolean hasIntent(String text) {
        String m = fixTypos(text.toLowerCase(Locale.ROOT));
        return COMPARE.matcher(m).find() || PATH.matcher(m).find() || MAP.matcher(m).find() || DIRECTIONS.matcher(m).find() || CHEAP.matcher(m).find()
                || NEAR.matcher(m).find() || BEST.matcher(m).find() || AVAIL.matcher(m).find();
    }

    /** "under 3000 then compare them" -> { "under 3000", "compare them" }; null when there is no second request with a clear intent. */
    private static String[] splitCompound(String msg) {
        Matcher t = THEN.matcher(msg);
        if (!t.find()) return null;
        String first = msg.substring(0, t.start()).trim(), rest = msg.substring(t.end()).trim();
        if (first.length() < 3 || rest.length() < 3 || !hasIntent(rest)) return null;
        return new String[]{first, rest};
    }

    /** The second request as the first chip (the student's own words), then the usual chips; at most 3. */
    private static List<String> chips(String rest, List<String> existing) {
        String c = rest.trim().replaceAll("[.!?\\s]+$", "");
        if (c.length() > 60) { int cut = c.toLowerCase(Locale.ROOT).indexOf(" and ", 10); c = cut > 0 && cut <= 60 ? c.substring(0, cut) : c.substring(0, Math.max(10, c.lastIndexOf(' ', 60))); }
        List<String> out = new ArrayList<>();
        if (c.isEmpty()) return existing == null ? out : existing;
        out.add(Character.toUpperCase(c.charAt(0)) + c.substring(1));
        if (existing != null) for (String e : existing) if (out.size() < 3 && !e.equalsIgnoreCase(out.get(0))) out.add(e);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> refs(String m, List<String> last) {
        List<String> r = new ArrayList<>();
        if (last.isEmpty()) return r;
        if (Pattern.compile("\\b(first|1st|una)\\b").matcher(m).find()) r.add(last.get(0));
        if (last.size() > 1 && Pattern.compile("\\b(second|2nd|ikaduha|pangalawa)\\b").matcher(m).find()) r.add(last.get(1));
        if (last.size() > 2 && Pattern.compile("\\b(third|3rd|ikatlo|pangatlo)\\b").matcher(m).find()) r.add(last.get(2));
        if (THOSE.matcher(m).find()) for (String id : last) if (!r.contains(id)) r.add(id);
        return r;
    }

    private static List<String> names(String msg, String intent) {
        List<String> out = new ArrayList<>();
        String rest = intent.equals("compare") ? msg.replaceFirst("(?i)^.*?(compare|ikumpara|pagkumpara|kumpara)\\s*", "") : "";
        if (rest.isEmpty()) return out;
        for (String p : rest.split("(?i)\\s+(?:and|at|vs\\.?|versus|kag|saka|with)\\s+|,")) {
            String t = p.replaceAll("(?i)\\b(the|top|those|them|these|you showed)\\b", "").trim();
            if (t.length() >= 2) out.add(t);
        }
        return out;
    }

    private static String suggest(String name, List<BoardingHouse> list, boolean fil) {
        List<BoardingHouse> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(Fuzzy.score(name, b.name()), Fuzzy.score(name, a.name())));
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(3, sorted.size()); i++) { if (i > 0) s.append(", "); s.append(sorted.get(i).name()); }
        return fil ? "Hindi ko nakita ang \"" + name + "\". Baka ang ibig mong sabihin: " + s + "?" : "I couldn't find \"" + name + "\". Did you mean: " + s + "?";
    }

    private static List<Result> results(List<BoardingHouse> l, Double lat, Double lng) {
        List<Result> out = new ArrayList<>();
        for (BoardingHouse b : l) {
            Double d = lat == null || lng == null ? null : Geo.km(lat, lng, b.lat(), b.lng());
            out.add(new Result(b.id(), b.name(), b.address(), b.price(), "", d == null ? null : Math.round(d * 100) / 100.0, d == null ? null : Math.max(1, Geo.walkMinutes(d)),
                    Math.round(b.avgRating() * 10) / 10.0, b.reviewCount(), b.availableBeds(), b.water(), b.electricity(), b.wifi(), b.lat(), b.lng(), b.photoUrl(), b.ownerVerified()));
        }
        return out;
    }

    private static List<String> ids(List<Result> rs) { List<String> l = new ArrayList<>(); for (Result r : rs) l.add(r.id()); return l; }

    private static Result cheapest(List<Result> rs) { Result b = rs.get(0); for (Result r : rs) if (r.price() < b.price()) b = r; return b; }

    private static String peso(double v) { return "₱" + String.format(Locale.US, "%,.0f", v); }

    private static String line(Result r) {
        String s = "**" + r.name() + "** \u00B7 **" + peso(r.price()) + "**/mo";
        if (r.distanceKm() != null) s += " \u00B7 " + (r.distanceKm() <= 3 ? Geo.fmt(r.distanceKm()) + " \u00B7 " + Math.max(1, r.walkMin()) + " min walk" : Geo.fmt(r.distanceKm()) + " away");
        return s;
    }

    private static Compare compare(List<Result> rs) {
        List<Row> rows = new ArrayList<>();
        rows.add(row("Price", rs, r -> peso(r.price()), r -> r.price(), true));
        if (rs.get(0).distanceKm() != null) rows.add(row("Distance", rs, r -> Geo.fmt(r.distanceKm()), r -> r.distanceKm(), true));
        rows.add(row("Rating", rs, r -> r.reviewCount() > 0 ? String.format(Locale.US, "%.1f (%d)", r.rating(), r.reviewCount()) : "No reviews", r -> r.reviewCount() > 0 ? r.rating() : null, false));
        rows.add(row("Free water", rs, r -> r.water() ? "Yes" : "No", r -> r.water() ? 1.0 : 0.0, false));
        rows.add(row("Free electricity", rs, r -> r.electricity() ? "Yes" : "No", r -> r.electricity() ? 1.0 : 0.0, false));
        rows.add(row("Free wifi", rs, r -> r.wifi() ? "Yes" : "No", r -> r.wifi() ? 1.0 : 0.0, false));
        rows.add(row("Available beds", rs, r -> String.valueOf(r.availableBeds()), r -> (double) r.availableBeds(), false));
        rows.add(row("Verified owner", rs, r -> r.verified() ? "Yes" : "No", r -> r.verified() ? 1.0 : 0.0, false));
        return new Compare(ids(rs), rows);
    }

    /** v2.0.82: the compare table for houses picked on the Explore map. Built here, on the phone: it works without the chat server. At most 4 houses. */
    public static BofoAnswer compareAnswer(List<BoardingHouse> hs, Double lat, Double lng) {
        List<BoardingHouse> pick = new ArrayList<>(hs.subList(0, Math.min(4, hs.size())));
        List<Result> rs = results(pick, lat, lng);
        Compare c = compare(rs);
        List<Row> rows = new ArrayList<>(c.rows());
        List<String> counts = new ArrayList<>();
        int best = -1, top = -1, nz = 0; boolean same = true;
        for (int i = 0; i < pick.size(); i++) {
            BoardingHouse b = pick.get(i);
            int n = Math.max(new java.util.HashSet<>(b.amenities()).size(), (b.water() ? 1 : 0) + (b.electricity() ? 1 : 0) + (b.wifi() ? 1 : 0));
            counts.add(String.valueOf(n));
            if (n > 0) nz++;
            if (i > 0 && n != Integer.parseInt(counts.get(0))) same = false;
            if (n > top) { top = n; best = i; }
        }
        if (nz > 0) rows.add(new Row("Amenities", counts, same ? -1 : best));
        List<Action> acts = new ArrayList<>();
        acts.add(new Action("show_on_map", null, ids(rs)));
        String reply = "Here's the side by side. Cheapest: " + cheapest(rs).name() + ".";
        return new BofoAnswer(reply, "compare", rs, new Compare(c.ids(), rows), acts, null, new ArrayList<>(), false);
    }

    private interface Txt { String of(Result r); }
    private interface Val { Double of(Result r); }

    /** best = index of the best value (lowest when low = true); -1 when all equal or fewer than two values. */
    private static Row row(String label, List<Result> rs, Txt t, Val v, boolean low) {
        List<String> vals = new ArrayList<>();
        int best = -1, n = 0; Double bv = null; boolean same = true;
        for (int i = 0; i < rs.size(); i++) {
            vals.add(t.of(rs.get(i)));
            Double x = v.of(rs.get(i));
            if (x == null) continue;
            n++;
            if (bv != null && !bv.equals(x)) same = false;
            if (bv == null || (low ? x < bv : x > bv)) { bv = x; best = i; }
        }
        return new Row(label, vals, n < 2 || same ? -1 : best);
    }
}
