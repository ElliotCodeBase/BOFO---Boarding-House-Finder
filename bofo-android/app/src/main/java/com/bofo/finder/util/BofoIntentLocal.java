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

    private static final Pattern PRICE = Pattern.compile("(?:under|below|less than|max(?:imum)?|hanggang|up to|not more than|<=?)\\s*(?:₱|php|p)?\\s*(\\d[\\d,]*)|(?:₱|php)\\s*(\\d[\\d,]*)|(\\d[\\d,]{2,})\\s*(?:pesos|php|piso)");
    private static final Pattern PRIVATE = Pattern.compile("id photos?|selfies?|passwords?|service.?role|api.?keys?|system prompt|other users?'? (chats?|messages?)");
    private static final Pattern COMPARE = Pattern.compile("compare|ikumpara|pagkumpara|kumpara|versus|\\bvs\\b");
    private static final Pattern MAP = Pattern.compile("\\bmap\\b|mapa|where is|\\bsaan\\b|\\basa\\b|locate");
    private static final Pattern PATH = Pattern.compile("\\b(path|route|routes|ruta|daan|dalan|directions?|how (?:do|can|to) i (?:get|go)|how to get|way to|show me the way|take me|guide me|lead me|paano pumunta|unsaon pag)\\b");
    private static final Pattern DIRECTIONS = Pattern.compile("direction|how far|how long|paano pumunta|how do i get|\\broute\\b|\\blayo\\b|unsaon pag");
    private static final Pattern CHEAP = Pattern.compile("cheap|pinakamura|barato|\\bmura\\b|baratilyo|pinakabarato|lowest price");
    private static final Pattern NEAR = Pattern.compile("nearest|closest|\\bnear\\b|nearby|malapit|\\blapit\\b|pinakamalapit");
    private static final Pattern BEST = Pattern.compile("best.?rated|top.?rated|rating|\\brated\\b|pinakamaganda|pinakamaayo|reviews?|highest");
    private static final Pattern AVAIL = Pattern.compile("available|vacant|bakante|\\bbeds?\\b|\\bkama\\b");
    private static final Pattern DETAILS = Pattern.compile("details|tell me about|more about|info (on|about)");
    private static final Pattern SEARCH = Pattern.compile("find|search|looking for|hanap|gusto ko|boarding|bh\\b|apartment|room|suggest|recommend|irekomenda|mungkahi|\\bhere\\b|\\bdito\\b|\\bdiri\\b");
    private static final Pattern THOSE = Pattern.compile("\\b(those|them|these|sila|iyan|yan|ito|niini|top \\d)\\b");
    private static final Pattern FIL = Pattern.compile("\\b(ang|mga|sa|ng|po|ba|nga|pinaka\\w*|malapit|barato|mura|saan|paano|kaayo|unsa|asa|ko|ako|gusto|hanap|may|lang|sang|kag)\\b");

    /** @param list the places the app has loaded (beds > 0). @param lastIds ids of the cards shown by the previous answer. */
    public static BofoAnswer answer(String msg, List<BoardingHouse> list, List<String> lastIds) {
        if (msg == null || list == null) return null;
        final String m = msg.toLowerCase(Locale.ROOT);
        final boolean fil = FIL.matcher(m).find();
        if (PRIVATE.matcher(m).find())
            return BofoAnswer.text(fil ? "Pasensya na, hindi ko maibabahagi ang pribadong impormasyon. Makakatulong ako sa paghahanap ng boarding house!"
                    : "Sorry, I can't share private information such as ID photos, selfies or other people's chats. I can help you find a boarding house instead!");

        Double lat = BrowseState.refLat(), lng = BrowseState.refLng();
        boolean hasRef = lat != null && lng != null;
        Double maxPrice = null;
        Matcher pm = PRICE.matcher(m);
        if (pm.find()) {
            String g = pm.group(1) != null ? pm.group(1) : pm.group(2) != null ? pm.group(2) : pm.group(3);
            try { maxPrice = Double.valueOf(g.replace(",", "")); } catch (NumberFormatException ignored) { }
        }
        boolean wifi = m.contains("wifi") || m.contains("wi-fi") || m.contains("internet");
        boolean water = Pattern.compile("\\b(water|tubig)\\b").matcher(m).find();
        boolean elec = m.contains("electric") || m.contains("kuryente");

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
        else return null;                                                                      // app help / chit-chat: FAQ

        // ---- named places (compare / map / directions / details)
        if (intent.equals("compare") || intent.equals("details") || intent.equals("map") || intent.equals("directions")) {
            List<BoardingHouse> found = new ArrayList<>();
            String missing = null;
            List<String> names = names(msg, intent);
            for (String n : names) {
                BoardingHouse best = null; int bs = 0;
                for (BoardingHouse b : list) { int s = Fuzzy.score(n, b.name()); if (s > bs) { bs = s; best = b; } }
                if (best != null && bs >= 80) { if (!found.contains(best)) found.add(best); }
                else if (missing == null) missing = suggest(n, list, fil);
            }
            if (missing != null) return BofoAnswer.text(missing);
            if (found.isEmpty()) for (BoardingHouse b : pool) if (!refIds.isEmpty() || last.contains(b.id())) found.add(b);
            if (found.isEmpty() && (intent.equals("map") || intent.equals("directions"))) { found = new ArrayList<>(pool); found.sort(Comparator.<BoardingHouse>comparingDouble(b -> hasRef ? Geo.km(lat, lng, b.lat(), b.lng()) : 0)); }
            if (found.isEmpty()) return BofoAnswer.text(fil ? "Anong lugar ang tinutukoy mo?" : "Which place do you mean? Ask for the nearest or cheapest first.");
            if (intent.equals("compare") && found.size() < 2)
                return BofoAnswer.text(fil ? "Kailangan ko ng dalawang pangalan para maikumpara." : "I need two place names to compare, for example: compare Casa Verde and Lola's Place.");
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

        // ---- ranked lists
        List<BoardingHouse> out = new ArrayList<>();
        for (BoardingHouse b : pool) {
            if (maxPrice != null && b.price() > maxPrice) continue;
            if (wifi && !b.wifi()) continue;
            if (water && !b.water()) continue;
            if (elec && !b.electricity()) continue;
            out.add(b);
        }
        // A chosen college limits every list answer to the app's radius around it.
        final College campus = BrowseState.campus();
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
        if (needRef) return BofoAnswer.text(fil ? "Pumili muna ng campus o i-on ang Near me para masukat ko ang layo." : "Pick a campus or turn on Near me so I can measure the distance.");
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
        return new BofoAnswer(head + "\n\n" + list3 + (hasRef ? "" : (fil ? "\n\nPumili ng campus para makita ang layo." : "\n\nPick a campus to see distances.")), intent, rs, null, acts, f.length() > 0 ? f : null, sug, true);
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
