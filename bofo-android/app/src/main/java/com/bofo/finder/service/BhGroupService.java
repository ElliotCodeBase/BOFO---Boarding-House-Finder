package com.bofo.finder.service;

import com.bofo.finder.model.BhGroup;
import com.bofo.finder.model.BhMember;
import com.bofo.finder.model.GroupMessage;
import com.bofo.finder.util.Url;
import com.bofo.finder.util.Validate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Boarding group chats (migration 024). Same style as {@link ChatService}: PostgREST + RPC, polling via {@link #fetchNewer}.
 * Rules (who may post / pin / edit / delete) are enforced by the database; this class only calls it and cleans the text.
 * No UI uses it yet (step 1 of 5).
 */
public final class BhGroupService {
    public static final int MAX_LEN = 1000, MAX_TITLE = 120, MAX_PINNED = 3, MAX_BHS_PER_ANNOUNCEMENT = 20, PAGE = 30;
    private static final String FEED = "bh_group_feed_v";
    private final SupabaseClient api = SupabaseClient.get();

    /* ---------- groups ---------- */

    /** Student: the house they joined (normally one row). Owner: one row per house they listed. Newest activity first. */
    public List<BhGroup> myGroups() throws Exception {
        JSONArray a = com.bofo.finder.util.Offline.array("bh_groups", () -> rows(api.rpc("my_bh_groups", new JSONObject())));
        List<BhGroup> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(BhGroup.from(a.getJSONObject(i)));
        return l;
    }

    public int totalUnread() throws Exception {
        int n = 0; for (BhGroup g : myGroups()) if (!g.muted()) n += g.unread(); return n;
    }

    /** Student joins with the owner's invite code. Returns the group id; the server refuses a wrong code, a removed member, or a second house. */
    public String join(String code) throws Exception {
        String c = normalizeCode(code);
        if (c.length() != 8) throw new IllegalArgumentException("Enter the 8-character code from your owner");
        return api.rpc("join_bh_group", new JSONObject().put("code", c)).getString("bh_id");
    }

    public void leave(String bhId) throws Exception { api.rpc("leave_bh_group", new JSONObject().put("bh", bhId)); }

    public void markRead(String bhId) throws Exception { api.rpc("mark_group_read", new JSONObject().put("bh", bhId)); }

    /* ---------- messages ---------- */

    /** Newest first. Pass the oldest created_at you already have as {@code before} to page backwards. */
    public List<GroupMessage> page(String bhId, String before) throws Exception {
        String q = "bh_id=eq." + bhId + "&select=*&order=created_at.desc&limit=" + PAGE + (before != null ? "&created_at=lt." + Url.encode(before) : "");
        JSONArray a = before == null ? com.bofo.finder.util.Offline.array("bhmsgs_" + bhId, () -> api.select(FEED, q)) : api.select(FEED, q);
        return messages(a);
    }

    /** Messages newer than {@code after} (oldest first): the 3 s refresh, like ChatView. Edits and acks show up on the next full page load. */
    public List<GroupMessage> fetchNewer(String bhId, String after) throws Exception {
        return messages(api.select(FEED, "bh_id=eq." + bhId + "&created_at=gt." + Url.encode(after) + "&select=*&order=created_at.asc"));
    }

    /** Pinned announcements of a group, newest pin first (at most {@value #MAX_PINNED}). */
    public List<GroupMessage> pinned(String bhId) throws Exception {
        return messages(api.select(FEED, "bh_id=eq." + bhId + "&pinned=eq.true&select=*&order=pinned_at.desc&limit=" + MAX_PINNED));
    }

    /** Plain chat message (students and the owner). Announcements go through {@link #postAnnouncement}. */
    public GroupMessage send(String bhId, String senderId, String body) throws Exception {
        String b = Validate.clean(body, MAX_LEN);
        if (b.isEmpty()) throw new IllegalArgumentException("Empty message");
        JSONObject row = api.insert("bh_group_messages", new JSONObject().put("bh_id", bhId).put("sender_id", senderId).put("body", b)).getJSONObject(0);
        JSONArray full = api.select(FEED, "id=eq." + row.getString("id") + "&select=*");     // the view adds sender name and role badge
        return GroupMessage.from(full.getJSONObject(0));
    }

    /** Sender deletes their own message; the owner may delete any message in their house. Returns false when the database hid/refused the row. */
    public boolean deleteMessage(String messageId) throws Exception {
        return api.deleteReturning("bh_group_messages", "id=eq." + messageId).length() > 0;
    }

    /* ---------- announcements (owner) ---------- */

    /** One announcement to one or several of the owner's houses; each copy is pinned (a house keeps at most 3 pinned, oldest unpinned). */
    public List<String> postAnnouncement(List<String> bhIds, String title, String body) throws Exception {
        String t = Validate.clean(title, MAX_TITLE), b = Validate.clean(body, MAX_LEN);
        if (bhIds == null || bhIds.isEmpty()) throw new IllegalArgumentException("Choose a boarding house");
        if (bhIds.size() > MAX_BHS_PER_ANNOUNCEMENT) throw new IllegalArgumentException("Choose up to " + MAX_BHS_PER_ANNOUNCEMENT + " houses");
        if (t.isEmpty()) throw new IllegalArgumentException("Add a title");
        if (b.isEmpty()) throw new IllegalArgumentException("Write the announcement");
        JSONObject r = api.rpc("post_announcement", new JSONObject().put("bhs", new JSONArray(bhIds)).put("ttl", t).put("msg", b));
        JSONArray ids = r.optJSONArray("ids");
        List<String> out = new ArrayList<>();
        if (ids != null) for (int i = 0; i < ids.length(); i++) out.add(ids.getString(i));
        return out;
    }

    public void pin(String messageId) throws Exception { api.rpc("pin_announcement", new JSONObject().put("msg", messageId).put("pin", true)); }

    public void unpin(String messageId) throws Exception { api.rpc("pin_announcement", new JSONObject().put("msg", messageId).put("pin", false)); }

    public void editAnnouncement(String messageId, String title, String body) throws Exception {
        String t = Validate.clean(title, MAX_TITLE), b = Validate.clean(body, MAX_LEN);
        if (t.isEmpty() || b.isEmpty()) throw new IllegalArgumentException("Title and message cannot be empty");
        api.rpc("edit_announcement", new JSONObject().put("msg", messageId).put("ttl", t).put("body_text", b));
    }

    /** Deleting an announcement is deleting the message (its acks go with it). */
    public boolean deleteAnnouncement(String messageId) throws Exception { return deleteMessage(messageId); }

    /** "Got it". Safe to call twice. */
    public void ack(String messageId) throws Exception { api.rpc("ack_announcement", new JSONObject().put("msg", messageId)); }

    /** Owner: {messageId -> {acked, members}} for the announcements of one house, e.g. "seen by 7 of 12". */
    public java.util.Map<String, int[]> announcementStats(String bhId) throws Exception {
        JSONArray a = rows(api.rpc("announcement_stats", new JSONObject().put("bh", bhId)));
        java.util.Map<String, int[]> m = new java.util.HashMap<>();
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); m.put(o.getString("message_id"), new int[]{o.optInt("acked", 0), o.optInt("members", 0)}); }
        return m;
    }

    /* ---------- owner: members, moderation, invite code ---------- */

    public List<BhMember> members(String bhId) throws Exception {
        JSONArray a = rows(api.rpc("bh_members_list", new JSONObject().put("bh", bhId)));
        List<BhMember> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(BhMember.from(a.getJSONObject(i)));
        return l;
    }

    public void setMuted(String bhId, String userId, boolean muted) throws Exception {
        api.rpc("set_member_muted", new JSONObject().put("bh", bhId).put("uid", userId).put("mute", muted));
    }

    public void removeMember(String bhId, String userId) throws Exception {
        api.rpc("remove_member", new JSONObject().put("bh", bhId).put("uid", userId));
    }

    /** The current invite code of a house I own (null when none is readable). */
    public String inviteCode(String bhId) throws Exception {
        JSONArray a = api.select("bh_invite_codes", "bh_id=eq." + bhId + "&select=code");
        return a.length() == 0 ? null : a.getJSONObject(0).getString("code");
    }

    /** New code; the old one stops working at once. Members already inside stay. */
    public String regenerateInviteCode(String bhId) throws Exception {
        return api.rpc("regenerate_invite_code", new JSONObject().put("bh", bhId)).getString("code");
    }

    /* ---------- helpers ---------- */

    /** Upper-case, letters/digits only (people type spaces and dashes: "abcd-2345" works). */
    public static String normalizeCode(String s) { return s == null ? "" : s.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT); }

    private static List<GroupMessage> messages(JSONArray a) throws Exception {
        List<GroupMessage> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(GroupMessage.from(a.getJSONObject(i)));
        return l;
    }

    /** rpc() wraps an array answer as {"data":[...]}; table functions return arrays. */
    private static JSONArray rows(JSONObject r) { JSONArray a = r.optJSONArray("data"); return a == null ? new JSONArray() : a; }
}
