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
    private static final java.util.Set<String> PREFETCHED = java.util.concurrent.ConcurrentHashMap.newKeySet(), INFLIGHT = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public List<Conversation> inbox() throws Exception {
        JSONArray a = com.bofo.finder.util.Offline.array("inbox", () -> api.select("inbox_v", "select=*&order=last_message_at.desc"));
        List<Conversation> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(Conversation.from(a.getJSONObject(i)));
        prefetch(l);
        return l;
    }

    /** Saves the newest messages of the 20 latest chats on the device (once per new message), so every chat opens offline - not only the ones opened before. */
    private void prefetch(List<Conversation> l) {
        int n = 0;
        for (Conversation c : l) {
            if (++n > 20) break;
            String key = c.id() + "|" + c.lastAt();
            if (PREFETCHED.contains(key) || !INFLIGHT.add(key)) continue;
            com.bofo.finder.util.Async.bg(() -> {
                try { page(c.id(), null); PREFETCHED.add(key); } catch (Exception ignored) { /* offline: tried again on the next inbox load */ }
                finally { INFLIGHT.remove(key); }
            });
        }
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
        JSONArray a = api.select("messages", "conversation_id=eq." + conversationId + "&created_at=gt." + com.bofo.finder.util.Url.encode(after) + "&select=*&order=created_at.asc");
        List<Message> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(Message.from(a.getJSONObject(i)));
        return l;
    }

    /** My own messages in this chat that the other person has already read (newest 30): used to turn one tick into two blue ticks live. */
    public List<Message> fetchMyRead(String conversationId, String me) throws Exception {
        JSONArray a = api.select("messages", "conversation_id=eq." + conversationId + "&sender_id=eq." + com.bofo.finder.util.Url.encode(me)
                + "&read_at=not.is.null&select=*&order=created_at.desc&limit=30");
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

    /** Deletes one message. everyone=true removes it for both people (only the sender may); false hides it only for me. */
    public void deleteMessage(String conversationId, String messageId, boolean everyone) throws Exception {
        api.rpc("delete_message", new JSONObject().put("msg", messageId).put("everyone", everyone));
        forgetSavedCopies(conversationId);
    }

    /** Removes the chat card from MY inbox and hides its old messages from me. The other person keeps their copy. */
    public void deleteConversation(String conversationId) throws Exception {
        api.rpc("delete_conversation", new JSONObject().put("conv", conversationId));
        forgetSavedCopies(conversationId);
    }

    /** The copies kept on the device for offline use must not bring a deleted message or chat back. */
    private static void forgetSavedCopies(String conversationId) {
        com.bofo.finder.util.DiskCache.deletePrefix("user_msgs_" + conversationId);
        com.bofo.finder.util.DiskCache.deletePrefix("user_inbox");
        PREFETCHED.removeIf(k -> k.startsWith(conversationId + "|"));
    }

    public void markRead(String conversationId) throws Exception { api.rpc("mark_read", new JSONObject().put("conv", conversationId)); }

    public void block(String reporterId, String userId, String reason) throws Exception {
        api.insert("blocks_reports", new JSONObject().put("reporter_id", reporterId).put("reported_user_id", userId)
                .put("reason", Validate.clean(reason, 300)).put("is_block", true));
    }
}
