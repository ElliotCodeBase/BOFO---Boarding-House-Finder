package com.bofo.finder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.4.0 (AI Announcement Writer, step 3): the 26 announcement types and the option enums, mirrored from the backend catalogue
 * {@code supabase/functions/bofo-announce/catalogue.ts} (ids, field keys, required flags, urgency, default tone, emoji, oneOf must stay IDENTICAL;
 * AnnounceTypeTest parses catalogue.ts and fails when they drift). Pure Java, no Android classes.
 * Resource names are derived by convention (see the *Name methods) so the UI can resolve them with Resources.getIdentifier.
 */
public final class AnnounceType {
    public static final int MAX_FIELD_LEN = 200;        // = MAX_FIELD_LEN in catalogue.ts
    public static final int MAX_CLOSING_NAME = 60;      // = MAX_CLOSING_NAME in catalogue.ts

    /** Badge on the type list: N = no badge, I = important, U = urgent (red-orange). */
    public enum Urgency {
        NORMAL("N"), IMPORTANT("I"), URGENT("U");
        public final String wire;
        Urgency(String w) { wire = w; }
    }
    public enum Lang {
        EN("en"), FIL("fil"), CEB("ceb"), TAGLISH("taglish");
        public final String wire;
        Lang(String w) { wire = w; }
        public static Lang fromWire(String s, Lang def) { for (Lang v : values()) if (v.wire.equals(s)) return v; return def; }
    }
    public enum Tone {
        FRIENDLY("friendly"), FORMAL("formal"), FIRM("firm"), SHORT("short");
        public final String wire;
        Tone(String w) { wire = w; }
        public static Tone fromWire(String s, Tone def) { for (Tone v : values()) if (v.wire.equals(s)) return v; return def; }
    }
    public enum Length {
        SHORT("short"), NORMAL("normal"), DETAILED("detailed");
        public final String wire;
        Length(String w) { wire = w; }
        public static Length fromWire(String s, Length def) { for (Length v : values()) if (v.wire.equals(s)) return v; return def; }
    }
    /** Re-write buttons: Try again / Shorter / More friendly / More formal. */
    public enum Adjust {
        AGAIN("again"), SHORTER("shorter"), FRIENDLIER("friendlier"), FORMAL("formal");
        public final String wire;
        Adjust(String w) { wire = w; }
    }

    /** One owner-entered detail. */
    public static final class Field {
        public final String key;
        public final boolean required;
        Field(String key, boolean required) { this.key = key; this.required = required; }
        /** string resource: the label shown above the box (shared by every type that has this key). */
        public String labelName() { return "announce_field_" + key; }
    }

    public final String id;
    public final Urgency urgency;
    public final Tone defaultTone;
    public final String emoji;                 // used by the backend only when the owner turns emoji on
    public final String icon;                  // drawable resource name
    public final List<Field> fields;           // 1-4, in display order
    public final List<String> oneOf;           // at least one of these keys must be filled (empty = no such rule)

    private AnnounceType(String id, Urgency u, Tone t, String emoji, String icon, String[] oneOf, Field[] fields) {
        this.id = id; this.urgency = u; this.defaultTone = t; this.emoji = emoji; this.icon = icon;
        this.oneOf = Collections.unmodifiableList(java.util.Arrays.asList(oneOf));
        this.fields = Collections.unmodifiableList(java.util.Arrays.asList(fields));
    }
    /** string resources by convention: title, one-line description, and the example hint of one field of THIS type. */
    public String titleName() { return "announce_type_" + id; }
    public String descName() { return "announce_type_" + id + "_desc"; }
    public String hintName(String fieldKey) { return "announce_hint_" + id + "_" + fieldKey; }
    public Field field(String key) { for (Field f : fields) if (f.key.equals(key)) return f; return null; }
    public boolean isUrgent() { return urgency == Urgency.URGENT; }

    // ------------------------------------------------------------------ the catalogue (same order as catalogue.ts)
    private static final String[] NONE = new String[0];
    private static final List<AnnounceType> ALL_LIST = new ArrayList<>();
    private static final Map<String, AnnounceType> BY_ID = new HashMap<>();
    private static Field r(String key) { return new Field(key, true); }
    private static Field o(String key) { return new Field(key, false); }
    private static void add(String id, Urgency u, Tone t, String emoji, String icon, String[] oneOf, Field... fields) {
        AnnounceType a = new AnnounceType(id, u, t, emoji, icon, oneOf, fields);
        ALL_LIST.add(a); BY_ID.put(id, a);
    }
    static {
        add("rent_due", Urgency.IMPORTANT, Tone.FIRM, "\uD83D\uDCB0", "ic_ann_rent_due", NONE,
            r("due_date"), r("amount"), r("how_to_pay"), o("late_fee"));
        add("water_cut", Urgency.IMPORTANT, Tone.SHORT, "\uD83D\uDCA7", "ic_am_water", NONE,
            r("date"), r("time_range"), o("reason"));
        add("power_cut", Urgency.IMPORTANT, Tone.SHORT, "\uD83D\uDCA1", "ic_am_bolt", NONE,
            r("date"), r("time_range"), o("source"));
        add("house_rules", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDCCB", "ic_am_rule", NONE,
            r("rule"), o("effective_date"));
        add("visitors_curfew", Urgency.NORMAL, Tone.FORMAL, "\uD83D\uDEAA", "ic_ann_visitors_curfew", NONE,
            r("rule_time"), r("what_changes"));
        add("cleaning_schedule", Urgency.NORMAL, Tone.FRIENDLY, "\uD83E\uDDF9", "ic_am_spark", NONE,
            r("date"), r("area"), o("responsible"));
        add("welcome_tenant", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDC4B", "ic_ann_welcome_tenant", NONE,
            o("first_name"), o("room"), o("move_in_info"), o("rules_summary"));
        add("vacancy", Urgency.NORMAL, Tone.FRIENDLY, "\uD83C\uDFE0", "ic_am_bed", NONE,
            r("room_type"), r("price"), r("slots"), r("how_to_reserve"));
        add("payment_thanks", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDE4F", "ic_ann_payment_thanks", NONE,
            r("month"), o("note"));
        add("general", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDCE3", "ic_ann_general", NONE,
            r("idea"));
        add("emergency", Urgency.URGENT, Tone.SHORT, "\uD83D\uDEA8", "ic_ann_emergency", NONE,
            r("kind"), o("what_to_do"), o("contact"));
        add("typhoon", Urgency.URGENT, Tone.SHORT, "\uD83C\uDF00", "ic_ann_typhoon", NONE,
            r("signal"), o("house_action"), o("notes"));
        add("move_out", Urgency.IMPORTANT, Tone.FORMAL, "\uD83D\uDCE6", "ic_ann_move_out", NONE,
            r("date"), r("deposit_rule"), o("room_check_date"));
        add("deposit_advance", Urgency.IMPORTANT, Tone.FORMAL, "\uD83E\uDDFE", "ic_ann_deposit_advance", NONE,
            r("amount"), r("due_date"), o("receipt_note"));
        add("wifi_update", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDCF6", "ic_am_wifi", NONE,
            o("password"), o("outage"), o("schedule"), o("fair_use"));
        add("pest_control", Urgency.IMPORTANT, Tone.FRIENDLY, "\uD83E\uDEB2", "ic_ann_pest_control", NONE,
            r("date"), r("rooms"), o("tenant_action"));
        add("room_inspection", Urgency.IMPORTANT, Tone.FORMAL, "\uD83D\uDD0D", "ic_ann_room_inspection", NONE,
            r("date"), r("time_window"), o("reason"));
        add("gate_lock_change", Urgency.IMPORTANT, Tone.FORMAL, "\uD83D\uDD11", "ic_am_lock", new String[]{"new_gate_time", "key_code_instructions"},
            o("new_gate_time"), o("key_code_instructions"), o("effective_date"));
        add("meeting", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDDD3\uFE0F", "ic_am_users", NONE,
            r("date"), r("place"), r("agenda"));
        add("holiday", Urgency.NORMAL, Tone.FRIENDLY, "\uD83C\uDF89", "ic_ann_holiday", NONE,
            r("occasion"), o("owner_away_note"));
        add("price_update", Urgency.IMPORTANT, Tone.FORMAL, "\uD83D\uDCC8", "ic_ann_price_update", NONE,
            r("effective_date"), r("new_amount"), o("reason"));
        add("lost_found", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDD0E", "ic_ann_lost_found", NONE,
            r("item"), r("found_where"), r("how_to_claim"));
        add("rule_violation", Urgency.IMPORTANT, Tone.FIRM, "\u26A0\uFE0F", "ic_ann_rule_violation", NONE,
            r("rule"), o("detail"));
        add("thanks", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDC9B", "ic_ann_thanks", NONE,
            r("reason"));
        add("congrats", Urgency.NORMAL, Tone.FRIENDLY, "\uD83C\uDF93", "ic_ann_congrats", NONE,
            r("occasion"), o("who_group"));
        add("survey", Urgency.NORMAL, Tone.FRIENDLY, "\uD83D\uDCDD", "ic_ann_survey", NONE,
            r("question"), r("how_to_reply"));
    }
    public static final List<AnnounceType> ALL = Collections.unmodifiableList(ALL_LIST);
    /** null when unknown. */
    public static AnnounceType byId(String id) { return id == null ? null : BY_ID.get(id); }
}
