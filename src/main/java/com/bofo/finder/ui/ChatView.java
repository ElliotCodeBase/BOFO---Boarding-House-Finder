package com.bofo.finder.ui;

import com.bofo.finder.model.Message;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.Outbox;
import org.json.JSONObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.collections.ListChangeListener;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One conversation: paginated history (newest first, "Load older"), polling for new messages, report/block. */
public final class ChatView extends VBox {
    private final ChatService chat = new ChatService();
    private final String convId, me, otherUserId;
    private final boolean iAmOwner;
    private final Runnable onBack;
    private final HBox quickRow = new HBox(8);
    private final Button sendBtn = new Button("\u27A4");
    private final VBox msgs = new VBox(8);
    private boolean regroupQueued;
    private LocalDate lastDay;                                         // day of the newest message on screen (for the day dividers)
    private javafx.scene.Node emptyNode;                               // "Say hello" placeholder, removed when the first message appears
    private final ScrollPane scroll = Ui.scroll(msgs);
    private final TextField input = new TextField();
    private String oldest, newest = "1970-01-01T00:00:00Z";
    private ScheduledExecutorService poll;
    private boolean stickBottom;                                       // true for a moment after new content: keeps the view glued to the newest message while it is laid out
    private final Set<String> unreadMine = java.util.concurrent.ConcurrentHashMap.newKeySet();   // ids of my messages still showing one tick (not read yet)
    private volatile boolean active = true;
    private int reconTick = 0;
    private int idleTicks = 0;
    private final Set<String> seen = new HashSet<>();                 // message ids already on screen (no duplicates)
    private record Pending(HBox row, StackPane bubble, Label status) {}
    private final java.util.Map<String, Pending> queued = new java.util.LinkedHashMap<>();   // messages in the Outbox (device) that are not on the server yet
    private volatile boolean needsReload = false;                      // history could not be loaded (offline, nothing saved): reload when online
    private static final String WAITING = "Waiting for internet - sends automatically";
    private final Outbox.Listener outboxListener = new Outbox.Listener() {
        @Override public void sent(String id, Message m) { Platform.runLater(() -> onSent(id, m)); }
        @Override public void failed(String id, Throwable why) { Platform.runLater(() -> onFailed(id, why)); }
        @Override public void waiting(String id) { Platform.runLater(() -> { Pending q = queued.get(id); if (q != null) q.status().setText(WAITING); }); }
    };

    /** @param iAmOwner true when I am the owner side of THIS chat (the other person is the student). Comes from the chat, not from my account's current role. */
    public ChatView(String convId, String bhName, String bhPhoto, String otherUserId, boolean iAmOwner, Runnable onBack) {
        this.convId = convId; this.otherUserId = otherUserId; this.onBack = onBack; this.me = AuthService.get().userId();
        this.iAmOwner = iAmOwner;
        Button back = Ui.btn("←", "heart", () -> { stop(); onBack.run(); });
        back.getStyleClass().setAll("button", "chat-icon-btn");
        Button more = Ui.btn("⋯", "heart", () -> {});
        more.setOnAction(e -> chatMenu(more));
        more.getStyleClass().setAll("button", "chat-icon-btn");
        Label who = Ui.label(bhName, "chat-name");
        who.setWrapText(false); who.setMinWidth(0); who.setTextOverrun(OverrunStyle.ELLIPSIS); who.setMaxWidth(Double.MAX_VALUE);
        Label role = Ui.label(iAmOwner ? "Student" : "Boarding house owner", "chat-role");
        role.setWrapText(false); role.setMinWidth(0); role.setTextOverrun(OverrunStyle.ELLIPSIS);
        VBox titleBox = new VBox(1, who, role); titleBox.setAlignment(Pos.CENTER_LEFT); titleBox.setMinWidth(0);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        HBox bar = Ui.appBar("", more); bar.getChildren().setAll(back, Ui.avatar(bhName, 40), titleBox, more);
        bar.setSpacing(10); bar.setAlignment(Pos.CENTER_LEFT); bar.getStyleClass().add("chat-bar");

        msgs.setPadding(new Insets(12, 12, 16, 12));
        msgs.getChildren().addListener((ListChangeListener<javafx.scene.Node>) c -> {      // re-group runs of messages from the same person after any change
            if (regroupQueued) return;
            regroupQueued = true; Platform.runLater(() -> { regroupQueued = false; regroup(); });
        });
        VBox.setVgrow(scroll, Priority.ALWAYS);
        input.setPromptText("Write a message\u2026"); input.getStyleClass().add("chat-input"); HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> send());
        sendBtn.getStyleClass().add("chat-send"); sendBtn.setOnAction(e -> send());
        sendBtn.disableProperty().bind(input.textProperty().isEmpty());
        HBox composer = Ui.hbox(8, input, sendBtn); composer.setPadding(new Insets(8, 12, 12, 12)); composer.setAlignment(Pos.CENTER);
        composer.getStyleClass().add("chat-composer");

        // one-tap replies, different for students and owners; they hide after the first message
        String[] quick = iAmOwner
                ? new String[]{"Yes, it is still available", "When would you like to visit?", "Rent is as listed"}
                : new String[]{"Is it still available?", "Can I visit this week?", "What is included in the rent?"};
        for (String q : quick) {
            Button c = new Button(q); c.getStyleClass().add("chat-quick");
            c.setOnAction(e -> { input.setText(q); send(); });
            quickRow.getChildren().add(c);
        }
        quickRow.setPadding(new Insets(0, 12, 0, 12));
        ScrollPane quickScroll = new ScrollPane(quickRow);
        quickScroll.setFitToHeight(true); quickScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); quickScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        quickScroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;");
        quickScroll.managedProperty().bind(quickScroll.visibleProperty());
        quickScroll.visibleProperty().bind(javafx.beans.binding.Bindings.size(msgs.getChildren()).lessThanOrEqualTo(1));
        getChildren().addAll(bar, scroll, quickScroll, composer);

        msgs.heightProperty().addListener((o, a, b) -> { if (stickBottom) scroll.setVvalue(1.0); });
        Outbox.LISTENERS.add(outboxListener);
        for (JSONObject e : Outbox.pending(convId)) addPending(e);     // messages written offline in an earlier visit are still here
        loadOlder(true);
        Outbox.flushAsync();
        poll = Async.scheduler();
        poll.schedule(this::tick, 3, TimeUnit.SECONDS);
    }

    private void stop() { active = false; Outbox.LISTENERS.remove(outboxListener); if (poll != null) poll.shutdownNow(); }

    /** Adaptive polling: 3 s while the chat is active, backing off to 12 s when quiet; resets on any new message. */
    private void tick() {
        if (!active) return;
        boolean got = false;
        try {                                                            // any error here must never stop the polling for good
            if (Outbox.hasPending()) Outbox.flushAsync();                // queued messages go out as soon as the network is back
            if (needsReload) { if (!Connectivity.OFFLINE.get()) { needsReload = false; Platform.runLater(() -> loadOlder(true)); } }
            else { got = pollNew(); refreshReads(); if (++reconTick % 3 == 0) dropDeletedByOther(); }
        } catch (Exception e) { System.err.println("[ChatView] poll error: " + e); }
        idleTicks = got ? 0 : idleTicks + 1;
        long delay = idleTicks < 5 || !unreadMine.isEmpty() ? 3 : idleTicks < 20 ? 6 : 12;   // stay fast while waiting for a "read"
        if (active) poll.schedule(this::tick, delay, TimeUnit.SECONDS);
    }

    private void loadOlder(boolean first) {
        if (first) msgs.getChildren().setAll(Skeleton.bubbles());          // skeleton while the history loads
        Async.run(() -> chat.page(convId, first ? null : oldest), page -> {
            if (first) msgs.getChildren().clear();
            if (page.isEmpty()) { if (first) { emptyNode = Ui.emptyState("Say hello", iAmOwner ? "Reply to the student to answer their questions." : "Ask the owner about price, rooms or a visit."); msgs.getChildren().add(emptyNode); restoreQueued(); } return; }
            List<Message> asc = new ArrayList<>(page); Collections.reverse(asc);
            oldest = asc.get(0).createdAt();
            if (first) newest = asc.get(asc.size() - 1).createdAt();
            if (!first && !msgs.getChildren().isEmpty() && msgs.getChildren().get(0) instanceof Button) msgs.getChildren().remove(0);
            List<javafx.scene.Node> nodes = new ArrayList<>();
            if (page.size() == ChatService.PAGE) nodes.add(Ui.btn("Load older messages", "chip", () -> loadOlder(false)));
            LocalDate prev = null;
            for (Message m : asc) {
                seen.add(m.id());
                LocalDate d = dayOf(m.createdAt());
                if (d != null && !d.equals(prev)) { nodes.add(divider(d)); prev = d; }
                nodes.add(bubble(m));
            }
            if (first) lastDay = prev;
            else if (prev != null) {                                  // the older page ends on the day the shown history starts: keep one divider
                for (int i = 0; i < msgs.getChildren().size(); i++) {
                    javafx.scene.Node n = msgs.getChildren().get(i);
                    if (n.getUserData() instanceof LocalDate ld) { if (ld.equals(prev)) msgs.getChildren().remove(i); break; }
                    if (!(n instanceof Button)) break;
                }
            }
            msgs.getChildren().addAll(first ? msgs.getChildren().size() : 0, nodes);
            if (first) restoreQueued();
            if (first) Platform.runLater(this::toBottom);
            Async.bg(() -> { try { chat.markRead(convId); } catch (Exception ignored) {} });
        }, e -> {
            if (!first) { Nav.toast(Connectivity.isNetworkError(e) ? "No internet connection. Older messages need internet." : "Could not load messages"); return; }
            if (Connectivity.isNetworkError(e)) {           // offline and nothing saved for this chat yet: keep the chat usable, no dead screen
                needsReload = true;
                Label note = Ui.label("No saved messages for this chat yet. Open it once with internet and it will be here offline. You can still write: messages are sent when you are back online.", "secondary");
                note.setWrapText(true); note.setPadding(new Insets(16));
                msgs.getChildren().setAll(note);
            } else msgs.getChildren().setAll(Ui.errorState(e, () -> loadOlder(true)));
            restoreQueued();
        });
    }

    private boolean pollNew() {
        try {
            List<Message> fresh = chat.fetchNewer(convId, newest);
            if (fresh.isEmpty()) return false;
            Platform.runLater(() -> {
                for (Message m : fresh) { newest = m.createdAt(); if (seen.add(m.id())) { clearEmpty(); addDay(dayOf(m.createdAt())); javafx.scene.Node nb = bubble(m); msgs.getChildren().add(nb); appear(nb); } }
                toBottom();
            });
            chat.markRead(convId);
            refreshSavedCopy();
            return true;
        } catch (Exception e) { System.err.println("[ChatView] could not fetch new messages: " + e); return false; /* offline: next tick retries */ }
    }

    /** Scrolls to the newest message. The content is laid out a moment later, so the view stays glued to the bottom for a short time. */
    private void toBottom() {
        stickBottom = true;
        scroll.setVvalue(1.0);
        Platform.runLater(() -> { scroll.layout(); scroll.setVvalue(1.0); });
        javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
        t.setOnFinished(e -> { scroll.setVvalue(1.0); stickBottom = false; });
        t.play();
    }

    /** The other person read some of my messages: swap the one grey tick for two blue ticks without reopening the chat. */
    private void refreshReads() {
        if (unreadMine.isEmpty()) return;
        try {
            List<Message> read = chat.fetchMyRead(convId, me);
            if (read.isEmpty()) return;
            Platform.runLater(() -> {
                for (Message m : read) {
                    if (!unreadMine.contains(m.id())) continue;
                    for (int i = 0; i < msgs.getChildren().size(); i++) {
                        if (m.id().equals(msgs.getChildren().get(i).getProperties().get("id"))) { msgs.getChildren().set(i, bubble(m)); break; }
                    }
                }
            });
        } catch (Exception e) { System.err.println("[ChatView] could not refresh read ticks: " + e); }
    }

    private javafx.scene.Node bubble(Message m) {
        boolean mine = m.senderId().equals(me);
        Label l = new Label(m.body()); l.setWrapText(true); l.getStyleClass().add("bubble-text");
        l.maxWidthProperty().bind(javafx.beans.binding.Bindings.max(180, scroll.widthProperty().multiply(0.74)));
        Label time = new Label(clock(m.createdAt())); time.getStyleClass().add("bubble-meta");
        HBox metaRow = new HBox(4, time); metaRow.setAlignment(Pos.CENTER_RIGHT);
        if (mine) metaRow.getChildren().add(m.readAt() != null ? tickIcon(TICKS, Color.web("#9FE8FF")) : tickIcon(TICK, Color.web("#FFFFFF", 0.8)));   // one tick = sent, two blue ticks = read
        VBox inner = new VBox(2, l, metaRow);
        StackPane b = new StackPane(inner); b.getStyleClass().add(mine ? "bubble-me" : "bubble-them");
        b.setOnContextMenuRequested(e -> { messageMenu(b, m, mine, e.getScreenX(), e.getScreenY()); e.consume(); });
        HBox row = new HBox(b); row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getProperties().put("sender", m.senderId()); row.getProperties().put("bubble", b); row.getProperties().put("id", m.id());
        row.getProperties().put("created", m.createdAt()); row.getProperties().put("shownAt", System.currentTimeMillis());
        if (mine && m.readAt() == null) unreadMine.add(m.id()); else unreadMine.remove(m.id());
        return row;
    }

    // drawn ticks (the text characters looked uneven on some phones): 1 tick = sent, 2 ticks = read
    private static final String TICK = "M4 12.5l5 5L20 6.5";
    private static final String TICKS = "M1.5 12.5l5 5L17 6.5M10 16.5l1.5 1.5L22.5 6.5";
    private static javafx.scene.Node tickIcon(String path, Color c) {
        SVGPath p = new SVGPath(); p.setContent(path); p.setFill(null); p.setStroke(c); p.setStrokeWidth(2.6);
        p.setStrokeLineCap(StrokeLineCap.ROUND); p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        double k = 15 / 24.0; p.getTransforms().add(new javafx.scene.transform.Scale(k, k, 0, 0));
        Pane box = new Pane(p); box.setMinSize(15, 11); box.setPrefSize(15, 11); box.setMaxSize(15, 11); box.setMouseTransparent(true);
        return box;
    }

    /** Runs of messages from the same person: tighter gap, the round corner only on the first, the small tail only on the last. */
    private void regroup() {
        List<javafx.scene.Node> kids = msgs.getChildren();
        for (int i = 0; i < kids.size(); i++) {
            javafx.scene.Node n = kids.get(i);
            Object sender = n.getProperties().get("sender"), bub = n.getProperties().get("bubble");
            if (sender == null || !(bub instanceof StackPane b)) continue;
            boolean prevSame = i > 0 && sender.equals(kids.get(i - 1).getProperties().get("sender"));
            boolean nextSame = i + 1 < kids.size() && sender.equals(kids.get(i + 1).getProperties().get("sender"));
            boolean mine = sender.equals(me);
            String big = "20", mid = "7", tail = "4";
            String near1 = prevSame ? mid : big, near2 = nextSame ? mid : tail;     // the side that faces the screen edge: top, then bottom
            String r = mine ? big + " " + near1 + " " + near2 + " " + big            // TL TR BR BL
                            : near1 + " " + big + " " + big + " " + near2;
            String st = "-fx-background-radius:" + r + ";-fx-border-radius:" + r + ";";
            b.setStyle(st);
            VBox.setMargin(n, new Insets(prevSame ? -5 : 0, 0, 0, 0));
        }
    }

    /** New messages pop in: a short fade and a small scale from 92%. */
    private static void appear(javafx.scene.Node n) {
        n.setOpacity(0); n.setScaleX(0.92); n.setScaleY(0.92);
        FadeTransition f = new FadeTransition(Duration.millis(160), n); f.setToValue(1);
        ScaleTransition sc = new ScaleTransition(Duration.millis(200), n); sc.setToX(1); sc.setToY(1);
        f.play(); sc.play();
    }

    /** Right-click (PC) or press and hold (phone) on a message: copy, delete for me, and for my own messages delete for everyone. */
    private void messageMenu(javafx.scene.Node anchor, Message m, boolean mine, double x, double y) {
        java.util.List<Popup.Action> acts = new java.util.ArrayList<>();
        acts.add(new Popup.Action("Copy", () -> { ClipboardContent cc = new ClipboardContent(); cc.putString(m.body()); Clipboard.getSystemClipboard().setContent(cc); Nav.toast("Copied"); }));
        acts.add(new Popup.Action("Delete for me", true, () -> confirmDeleteMessage(m, false)));
        if (mine) acts.add(new Popup.Action("Delete for everyone", true, () -> confirmDeleteMessage(m, true)));
        Popup.menu("Message", acts.toArray(new Popup.Action[0]));
    }

    private void confirmDeleteMessage(Message m, boolean everyone) {
        Popup.confirm(Popup.Kind.DANGER, everyone ? "Delete for everyone?" : "Delete for you?",
                everyone ? "This message will be removed for you and for the other person."
                : "This message will be removed for you only. The other person still sees it.", "Delete", () ->
                Async.run(() -> { chat.deleteMessage(convId, m.id(), everyone); return true; },
                        ok -> removeMessages(List.of(m.id())), e -> Ui.failToast(e, "Could not delete the message")));
    }

    /** Takes the bubbles off the screen, then cleans up: a day pill with no message under it goes too, and an empty chat says hello again. */
    private void removeMessages(List<String> ids) {
        for (String id : ids) { msgs.getChildren().removeIf(n -> id.equals(n.getProperties().get("id"))); unreadMine.remove(id); }
        List<javafx.scene.Node> kids = msgs.getChildren();
        for (int i = kids.size() - 1; i >= 0; i--) {
            if (!(kids.get(i).getUserData() instanceof LocalDate)) continue;
            boolean hasMessage = i + 1 < kids.size() && !(kids.get(i + 1).getUserData() instanceof LocalDate);
            if (!hasMessage) kids.remove(i);
        }
        lastDay = null;
        for (javafx.scene.Node n : kids) if (n.getUserData() instanceof LocalDate d) lastDay = d;
        boolean any = kids.stream().anyMatch(n -> n.getProperties().containsKey("sender") || n instanceof Button);
        if (!any && emptyNode == null) { emptyNode = Ui.emptyState("Say hello", iAmOwner ? "Reply to the student to answer their questions." : "Ask the owner about price, rooms or a visit."); kids.add(emptyNode); }
    }

    /** The other person deleted a message for everyone: it disappears here too (checked every few polls against the newest page). */
    private void dropDeletedByOther() {
        long started = System.currentTimeMillis();
        try {
            List<Message> page = chat.page(convId, null);
            Set<String> ids = new HashSet<>();
            for (Message m : page) ids.add(m.id());
            String cutoff = page.size() == ChatService.PAGE ? page.get(page.size() - 1).createdAt() : null;   // newest first: the last one is the oldest on this page
            Platform.runLater(() -> {
                if (Connectivity.OFFLINE.get()) return;                      // offline the saved copy is served and can be older than the screen
                List<String> gone = new ArrayList<>();
                for (javafx.scene.Node n : msgs.getChildren()) {
                    Object id = n.getProperties().get("id"), created = n.getProperties().get("created"), shown = n.getProperties().get("shownAt");
                    if (id == null || created == null || ids.contains(id.toString())) continue;
                    if (shown instanceof Long t && t > started - 2000) continue;               // just arrived: the page may be older than it
                    if (cutoff != null && created.toString().compareTo(cutoff) < 0) continue;  // older than this page: cannot tell
                    gone.add(id.toString());
                }
                if (!gone.isEmpty()) removeMessages(gone);
            });
        } catch (Exception ignored) { /* next time */ }
    }

    /** The three dots in the top bar. */
    private void chatMenu(javafx.scene.Node anchor) {
        Popup.menu("Chat options",
                new Popup.Action("Report / block", true, this::reportDialog),
                new Popup.Action("Delete chat", true, this::confirmDeleteChat));
    }

    private void confirmDeleteChat() {
        Popup.confirm(Popup.Kind.DANGER, "Delete this chat?", "The chat and its messages are removed for you. The other person keeps their copy. If they write again, the chat comes back with only the new messages.", "Delete", () ->
                Async.run(() -> { chat.deleteConversation(convId); return true; },
                        ok -> { Nav.toast("Chat deleted"); stop(); onBack.run(); }, e -> Ui.failToast(e, "Could not delete the chat")));
    }

    // ---- time helpers: the server sends UTC, people read their own local time ----
    private static String clock(String iso) {
        try { return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)); }
        catch (Exception e) { return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : ""; }
    }
    private static LocalDate dayOf(String iso) {
        try { return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate(); } catch (Exception e) { return null; }
    }
    /** "Today", "Yesterday" or "Oct 25" centred in a small pill between messages of different days. */
    private javafx.scene.Node divider(LocalDate d) {
        LocalDate today = LocalDate.now();
        String text = d.equals(today) ? "Today" : d.equals(today.minusDays(1)) ? "Yesterday"
                : d.format(DateTimeFormatter.ofPattern(d.getYear() == today.getYear() ? "MMM d" : "MMM d, yyyy", Locale.ENGLISH));
        Label pill = Ui.label(text, "chat-day");
        HBox row = new HBox(pill); row.setAlignment(Pos.CENTER); row.setPadding(new Insets(6, 0, 2, 0));
        row.setUserData(d);
        return row;
    }
    private void addDay(LocalDate d) {
        if (d == null || d.equals(lastDay)) return;
        lastDay = d; msgs.getChildren().add(divider(d));
    }
    private void clearEmpty() { if (emptyNode != null) { msgs.getChildren().remove(emptyNode); emptyNode = null; } }

    private void send() {
        String t = input.getText(); if (t == null || t.isBlank()) return;
        if (t.length() > ChatService.MAX_LEN) { Nav.toast("Message too long"); return; }
        String clean = com.bofo.finder.util.Validate.clean(t, ChatService.MAX_LEN);
        if (clean.isEmpty()) return;
        input.clear(); idleTicks = 0;
        addPending(Outbox.add(convId, clean));                         // saved on the device first, so it is never lost
        Outbox.flushAsync();                                           // online: goes out now; offline: waits and is sent by itself later
    }

    /** A message that is not on the server yet: faded bubble with "Sending..." (online) or "Waiting for internet" (offline). */
    private void addPending(JSONObject e) {
        String id = e.getString("id");
        Label l = new Label(e.getString("body")); l.setWrapText(true);
        l.maxWidthProperty().bind(javafx.beans.binding.Bindings.max(180, scroll.widthProperty().multiply(0.76)));
        StackPane b = new StackPane(l); b.getStyleClass().add("bubble-me"); b.setOpacity(0.6);
        Label status = Ui.label(Connectivity.OFFLINE.get() ? WAITING : "Sending...", "chat-time");
        VBox box = new VBox(2, b, status); box.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(box); row.setAlignment(Pos.CENTER_RIGHT);
        row.getProperties().put("sender", me); row.getProperties().put("bubble", b);
        queued.put(id, new Pending(row, b, status));
        clearEmpty(); addDay(LocalDate.now());
        msgs.getChildren().add(row); appear(row); Platform.runLater(this::toBottom);
    }

    /** After the history is (re)drawn, the queued bubbles go back at the bottom. */
    private void restoreQueued() {
        for (Pending q : queued.values()) if (!msgs.getChildren().contains(q.row())) msgs.getChildren().add(q.row());
    }

    private void onSent(String id, Message m) {
        Pending q = queued.remove(id); if (q == null) return;
        int i = msgs.getChildren().indexOf(q.row());
        if (seen.add(m.id())) { if (i >= 0) msgs.getChildren().set(i, bubble(m)); else msgs.getChildren().add(bubble(m)); }
        else msgs.getChildren().remove(q.row());                       // the poll already showed it
        if (m.createdAt().compareTo(newest) > 0) newest = m.createdAt();
        toBottom();
        Async.bg(this::refreshSavedCopy);
    }

    private void onFailed(String id, Throwable why) {
        Pending q = queued.remove(id); if (q == null) return;
        boolean blocked = why instanceof com.bofo.finder.service.SupabaseClient.ApiException api && (api.status == 401 || api.status == 403);
        q.status().setText((blocked ? "Not sent - you may be blocked" : "Not sent") + " (tap to remove)");
        q.status().getStyleClass().add("error-text"); q.status().setCursor(Cursor.HAND);
        q.status().setOnMouseClicked(x -> msgs.getChildren().remove(q.row()));
    }

    /** Re-saves the newest page on the device so the chat opens offline with the latest messages. */
    private void refreshSavedCopy() { try { chat.page(convId, null); } catch (Exception ignored) { } }

    private void reportDialog() {
        Popup.input("Report / block", "Tell us what happened. This also blocks the user.", "Write what happened...", "Report", reason -> Async.run(() -> { chat.block(me, otherUserId, reason); return true; },
                ok -> Nav.toast("User reported and blocked"), e -> Ui.failToast(e, "Could not submit")));
    }
}
