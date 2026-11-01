package com.bofo.finder.util;

import androidx.lifecycle.MutableLiveData;

import com.bofo.finder.model.College;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.ProfileService;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What Home and Explore share: the colleges, the user's chosen campus, the radius and the Explore filters.
 * Main thread only. {@link #CHANGED} ticks whenever campus, radius or filters change, so screens reload.
 */
public final class BrowseState {
    private BrowseState() {}
    public static final MutableLiveData<Integer> CHANGED = new MutableLiveData<>(0);

    public static final double RADIUS_MIN = 0.5, RADIUS_MAX = 10, RADIUS_DEFAULT = 3;
    public static final int PRICE_MAX = 10_000;          // slider end = "no upper limit"

    public static volatile List<College> colleges = new ArrayList<>();
    public static volatile Profile me;
    public static String collegeId;
    public static double radiusKm = RADIUS_DEFAULT;
    /** "Near me" (Stage 5): distances are measured from the phone's position instead of a campus. Memory only, never saved. */
    public static boolean nearMe;
    public static Double userLat, userLng;
    /** The phone's last known position, read when Ask BOFO opens (permission + Settings > Use my location). Sent to the chat only; never changes the campus or Near me. Memory only. */
    public static Double deviceLat, deviceLng;
    public static Double minPrice, maxPrice;
    public static final Set<String> amenityIds = new LinkedHashSet<>();
    /** Step 9: Explore sort (ExploreRules.SORT_*) and amenities picked from the built-in fallback list (matched by name on the cards). */
    public static int sort;
    public static final Set<String> amenityNames = new LinkedHashSet<>();
    /** v2.0.82: quick filters. verifiedOnly = owner verified, bedsOnly = at least one free bed. Applied in Explore (client side). */
    public static boolean verifiedOnly, bedsOnly;
    /** v2.0.82: id -> name of the catalog amenities ticked in the filter sheet, so a quick chip ("Wi-Fi", "Female-only") can tell it is on. */
    public static final java.util.Map<String, String> amenityLabels = new java.util.HashMap<>();
    public static final double QUICK_PRICE = 2000;
    public static final String AMEN_WIFI = "Free Wi-Fi", AMEN_FEMALE = "Female only";
    private static boolean loaded;

    public static void bump() { Integer v = CHANGED.getValue(); CHANGED.setValue(v == null ? 1 : v + 1); }

    public static College campus() {
        if (collegeId == null) return null;
        for (College c : colleges) if (c.id().equals(collegeId)) return c;
        return null;
    }

    /** The point distances and the radius are measured from: the campus, else the phone (Near me), else null (whole list). */
    public static Double refLat() { College c = campus(); return c != null ? Double.valueOf(c.lat()) : nearMe ? userLat : null; }
    public static Double refLng() { College c = campus(); return c != null ? Double.valueOf(c.lng()) : nearMe ? userLng : null; }
    public static boolean hasRef() { return refLat() != null && refLng() != null; }

    /** Switches to Near me: the campus is forgotten for this session (the one saved on the profile stays). */
    public static void setNearMe(double lat, double lng) {
        nearMe = true; userLat = lat; userLng = lng; collegeId = null;
        bump();
    }

    public static void clearNearMe() { nearMe = false; bump(); }

    /** Explore filter for BhService (max price + amenities + search text). Min price is applied afterwards (ListRules.inPriceRange). */
    public static BhService.Filter filter(String text) {
        BhService.Filter f = new BhService.Filter();
        f.maxPrice = maxPrice;
        f.amenityIds.addAll(amenityIds);
        f.text = text;
        return f;
    }

    /** Price range counts as one filter, each amenity as one. */
    public static int activeFilters() { return (minPrice != null || maxPrice != null ? 1 : 0) + (sort != 0 ? 1 : 0) + amenityIds.size() + amenityNames.size() + (verifiedOnly ? 1 : 0) + (bedsOnly ? 1 : 0); }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }

    /** Is the amenity called this (spaces / punctuation / case ignored) part of the filters, as a name or as a ticked catalog id? */
    public static boolean hasAmenityNamed(String name) {
        String k = norm(name);
        for (String n : amenityNames) if (norm(n).equals(k)) return true;
        for (String id : amenityIds) if (norm(amenityLabels.get(id)).equals(k)) return true;
        return false;
    }

    /** Quick chip: adds the amenity by name (matched on the cards like the built-in list), or removes it whichever way it was ticked. */
    public static void setAmenityNamed(String name, boolean on) {
        String k = norm(name);
        if (on) { if (!hasAmenityNamed(name)) amenityNames.add(name); return; }
        amenityNames.removeIf(n -> norm(n).equals(k));
        java.util.Iterator<String> it = amenityIds.iterator();
        while (it.hasNext()) { String id = it.next(); if (norm(amenityLabels.get(id)).equals(k)) { it.remove(); amenityLabels.remove(id); } }
    }

    public static void clearFilters() { minPrice = null; maxPrice = null; sort = 0; amenityIds.clear(); amenityNames.clear(); amenityLabels.clear(); verifiedOnly = false; bedsOnly = false; }

    /** Loads colleges + my profile once (offline copies are used when there is no network). */
    public static void ensureLoaded(Runnable done, Consumer<Throwable> fail) {
        if (loaded) { done.run(); return; }
        Async.run(() -> {
            List<College> cs = new BhService().colleges();
            Profile p = new ProfileService().fetchMe();
            return new Object[]{cs, p};
        }, (Object[] r) -> {
            @SuppressWarnings("unchecked") List<College> cs = (List<College>) r[0];
            colleges = cs; me = (Profile) r[1];
            if (!loaded && me != null) collegeId = me.defaultCollegeId();
            loaded = true;
            done.run();
        }, e -> { if (fail != null) fail.accept(e); });
    }

    /** null = no campus. Saved on the profile (default_college_id) so it is the same next time. */
    public static void setCampus(College c) {
        collegeId = c == null ? null : c.id();
        nearMe = false;
        bump();
        final Object value = c == null ? JSONObject.NULL : c.id();
        Async.run(() -> { new ProfileService().update(new JSONObject().put("default_college_id", value)); return true; }, ok -> { }, e -> { /* the choice still works for this session */ });
    }

    /** New sign-in: forget the previous user. */
    public static void reset() {
        loaded = false; me = null; collegeId = null; colleges = new ArrayList<>();
        nearMe = false; userLat = null; userLng = null; deviceLat = null; deviceLng = null;
        radiusKm = RADIUS_DEFAULT; clearFilters();
    }
}
