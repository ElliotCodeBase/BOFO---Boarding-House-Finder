package com.bofo.finder.service;

import com.bofo.finder.model.Conversation;
import com.bofo.finder.model.Message;
import com.bofo.finder.util.Validate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat over PostgREST. {@link #fetchNewer} is used for near-real-time polling (3s).
 * To switch to Supabase Realtime later, open a websocket to /realtime/v1/websocket and subscribe
 * to postgres_changes on public.messages - the UI only needs the onMessage callback.
 */
public final class ChatService {
    public static final int MAX_LEN = 1000;
    public static final int PAGE = 30;
    private final SupabaseClient api = SupabaseClient.get();

    public List<Conversation> inbox() throws Exception {
        JSONArray a = com.bofo.finder.util.Offline.array("inbox", () -> api.select("inbox_v", "select=*&order=last_message_at.desc"));
        List<Conversation> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(Conversation.from(a.getJSONObject(i)));
        return l;
    }

    public int totalUnread() throws Exception {
        int n = 0; for (Conversation c : inbox()) n += c.unread(); return n;
    }

    /** One conversation per (student, owner, BH) - get_or_create_conversation is idempotent. */
    public String openConversation(String bhId) throws Exception {
        JSONObject r = api.rpc("get_or_create_conversation", new JSONObject().put("bh", bhId));
        return r.getString("id");
    }

    /** Newest first. Pass the oldest created_at you already have as {@code before} to page backwards. */
    public List<Message> page(String conversationId, String before) throws Exception {
        String q = "conversation_id=eq." + conversationId + "&select=*&order=created_at.desc&limit=" + PAGE
                + (before != null ? "&created_at=lt." + before : "");
        JSONArray a = before == null ? com.bofo.finder.util.Offline.array("msgs_" + conversationId, () -> api.select("messages", q))
                                     : api.select("messages", q);          // newest page is kept on the device; older pages need internet
        List<Message> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(Message.from(a.getJSONObject(i)));
        return l;
    }

    public List<Message> fetchNewer(String conversationId, String after) throws Exception {
        JSONArray a = api.select("messages", "conversation_id=eq." + conversationId + "&created_at=gt." + after + "&select=*&order=created_at.asc");
        List<Message> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(Message.from(a.getJSONObject(i)));
        return l;
    }

    public Message send(String conversationId, String senderId, String body) throws Exception {
        String b = Validate.clean(body, MAX_LEN);
        if (b.isEmpty()) throw new IllegalArgumentException("Empty message");
        JSONObject row = api.insert("messages", new JSONObject().put("conversation_id", conversationId).put("sender_id", senderId).put("body", b)).getJSONObject(0);
        return Message.from(row);
    }

    public void markRead(String conversationId) throws Exception { api.rpc("mark_read", new JSONObject().put("conv", conversationId)); }

    public void block(String reporterId, String userId, String reason) throws Exception {
        api.insert("blocks_reports", new JSONObject().put("reporter_id", reporterId).put("reported_user_id", userId)
                .put("reason", Validate.clean(reason, 300)).put("is_block", true));
    }
}
