package com.bofo.finder.service;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.model.Profile;
import com.bofo.finder.model.Role;
import com.bofo.finder.util.DiskCache;
import com.bofo.finder.util.Offline;
import com.bofo.finder.util.Validate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** The signed-in user's own row in public.users, their avatar and their accepted agreements. Blocking: use Async. */
public final class ProfileService {
    private final SupabaseClient api = SupabaseClient.get();

    /** A profile is "set up" once it has a name; the rest (username, phone, sex, age, college, region) is optional. */
    public static boolean isComplete(Profile p) { return p != null && !Validate.blank(p.fullName()); }

    /** The user's row, or null when the account has no row yet (new user: pick a role first). Falls back to the saved copy offline. */
    public Profile fetchMe() throws Exception {
        final String id = AuthService.get().userId();
        JSONArray a = Offline.array("me", () -> api.select("users", "select=*&id=eq." + id));
        return a.length() == 0 ? null : Profile.from(a.getJSONObject(0));
    }

    /** First insert. RLS only allows role student / owner, unverified. */
    public Profile create(Role role, String fullName, String photoUrl) throws Exception {
        if (role != Role.STUDENT && role != Role.OWNER) throw new IllegalArgumentException("Pick student or owner.");
        AuthService au = AuthService.get();
        JSONObject row = new JSONObject().put("id", au.userId()).put("role", role.db()).put("auth_provider", au.provider());
        if (!Validate.blank(fullName)) row.put("full_name", Validate.clean(fullName, 80));
        if (!Validate.blank(photoUrl)) row.put("photo_url", photoUrl);
        JSONObject saved = api.insert("users", row).getJSONObject(0);
        remember(saved);
        return Profile.from(saved);
    }

    /** Changes the role of an existing account (student <-> owner). Admin accounts are not switchable. */
    public void switchRole(Role role) throws Exception {
        api.rpc("switch_role", new JSONObject().put("new_role", role.db()));
    }

    /** Updates only the given columns. role / is_verified / verification_status are ignored by the database trigger on purpose. */
    public Profile update(JSONObject patch) throws Exception {
        JSONArray r = api.update("users", "id=eq." + AuthService.get().userId(), patch);
        if (r.length() == 0) throw new IllegalStateException("The profile was not saved (no row was changed).");
        remember(r.getJSONObject(0));
        return Profile.from(r.getJSONObject(0));
    }

    /** Keeps the offline copy of "my profile" in step with what was just saved. */
    private static void remember(JSONObject row) { DiskCache.write("user_me", new JSONArray().put(row).toString()); }

    /** Uploads a JPEG to avatars/{uid}/avatar.jpg (the bucket is public) and returns the URL, with a version so caches refresh. */
    public String uploadAvatar(byte[] jpeg) throws Exception {
        if (!Validate.imageOk(jpeg, 5L * 1024 * 1024)) throw new IllegalArgumentException("The photo is not a valid JPEG / PNG under 5 MB.");
        String path = AuthService.get().userId() + "/avatar.jpg";
        api.upload("avatars", path, jpeg, "image/jpeg");
        return api.publicUrl("avatars", path) + "?v=" + System.currentTimeMillis();
    }

    /** Records the agreements the user accepted (terms + privacy for everyone, owner agreement for owners) unless that version is already on file. */
    public void recordAgreements(Role role) throws Exception {
        String uid = AuthService.get().userId();
        JSONArray have = api.select("user_agreements", "select=document_type,document_version&user_id=eq." + uid);
        Set<String> done = new HashSet<>();
        for (int i = 0; i < have.length(); i++) {
            JSONObject o = have.getJSONObject(i);
            done.add(o.getString("document_type") + "|" + o.getString("document_version"));
        }
        JSONArray rows = new JSONArray();
        add(rows, done, uid, "terms", version("policy.terms.version"));
        add(rows, done, uid, "privacy", version("policy.privacy.version"));
        if (role == Role.OWNER) add(rows, done, uid, "owner_agreement", version("policy.owner.version"));
        if (rows.length() > 0) api.insert("user_agreements", rows);
    }

    private static String version(String key) { String v = AppConfig.get(key).trim(); return v.isEmpty() ? "1.0" : v; }

    private static void add(JSONArray rows, Set<String> done, String uid, String type, String version) throws Exception {
        if (!done.contains(type + "|" + version))
            rows.put(new JSONObject().put("user_id", uid).put("document_type", type).put("document_version", version));
    }
}
