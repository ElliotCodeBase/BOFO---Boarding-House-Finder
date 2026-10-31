package com.bofo.finder.ui;

import androidx.annotation.DrawableRes;

import com.bofo.finder.R;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The app's own line icons for amenities (vector drawables ic_am_*), picked from the amenity NAME.
 * The emoji stored in the database is no longer shown; owner-made amenities with an unknown name get the sparkle icon.
 */
final class AmenityIcons {
    private AmenityIcons() {}

    private static final Pattern AC = Pattern.compile("\\b(ac|aircon|air[- ]?con\\w*|air[- ]?cond\\w*)\\b");
    private static final Pattern CR = Pattern.compile("\\b(cr|c\\.r\\.|toilet|bathroom|comfort room)\\b");
    private static final Pattern FEMALE = Pattern.compile("\\b(female|females|girl|girls|women|ladies|lady)\\b");
    private static final Pattern MALE = Pattern.compile("\\b(male|males|boy|boys|men)\\b");
    private static final Pattern PET = Pattern.compile("\\b(pet|pets)\\b");

    @DrawableRes
    static int res(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("hot") || n.contains("heater")) return R.drawable.ic_am_flame;
        if (n.contains("fan")) return R.drawable.ic_am_fan;
        if (n.contains("wifi") || n.contains("wi-fi") || n.contains("internet")) return R.drawable.ic_am_wifi;
        if (n.contains("water")) return R.drawable.ic_am_water;
        if (n.contains("electric") || n.contains("power") || n.contains("outlet")) return R.drawable.ic_am_bolt;
        if (AC.matcher(n).find() || n.contains("cooling")) return R.drawable.ic_am_snow;
        if (n.contains("shower") || n.contains("bath") || CR.matcher(n).find()) return R.drawable.ic_am_shower;
        if (n.contains("kitchen") || n.contains("cook") || n.contains("stove")) return R.drawable.ic_am_kitchen;
        if (n.contains("laundry") || n.contains("wash")) return R.drawable.ic_am_laundry;
        if (n.contains("park") || n.contains("garage")) return R.drawable.ic_am_parking;
        if (n.contains("cctv") || n.contains("camera") || n.contains("surveillance")) return R.drawable.ic_am_cctv;
        if (n.contains("study") || n.contains("desk") || n.contains("library")) return R.drawable.ic_am_book;
        if (n.contains("bed") || n.contains("mattress")) return R.drawable.ic_am_bed;
        if (n.contains("lock") || n.contains("key")) return R.drawable.ic_am_lock;
        if (n.contains("cabinet") || n.contains("closet") || n.contains("wardrobe")) return R.drawable.ic_am_closet;
        if (n.contains("visitor") || n.contains("guest")) return R.drawable.ic_am_users;
        if (n.contains("curfew")) return R.drawable.ic_am_moon;
        if (PET.matcher(n).find()) return R.drawable.ic_am_paw;
        if (FEMALE.matcher(n).find()) return R.drawable.ic_am_female;
        if (MALE.matcher(n).find()) return R.drawable.ic_am_male;
        if (n.contains("tv") || n.contains("television") || n.contains("cable")) return R.drawable.ic_am_tv;
        if (n.contains("fridge") || n.contains("refrigerator")) return R.drawable.ic_am_fridge;
        if (n.contains("guard") || n.contains("security") || n.contains("gate")) return R.drawable.ic_am_shield;
        return R.drawable.ic_am_spark;
    }
}
