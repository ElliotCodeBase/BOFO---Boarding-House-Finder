package com.bofo.admin;

import android.content.Context;
import android.content.SharedPreferences;

import com.bofo.shared.SavedReplyRules;

import java.util.List;

/** The reviewer's own saved rejection replies. Phone only (never sent to the server); not secret, so plain private preferences. */
final class RepliesStore {
    private static final String FILE = "admin_replies", KEY = "own";
    private RepliesStore() {}

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    static List<String> load(Context c) { return SavedReplyRules.decode(prefs(c).getString(KEY, "")); }
    static void save(Context c, List<String> own) { prefs(c).edit().putString(KEY, SavedReplyRules.encode(own)).apply(); }
}
