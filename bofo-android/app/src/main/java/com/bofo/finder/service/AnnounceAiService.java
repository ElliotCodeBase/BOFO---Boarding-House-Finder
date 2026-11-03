package com.bofo.finder.service;

import com.bofo.finder.model.AnnounceDraft;
import com.bofo.finder.model.AnnounceError;
import com.bofo.finder.model.AnnounceType;
import com.bofo.finder.util.AnnounceRules;
import com.bofo.finder.util.Async;

import org.json.JSONObject;

import java.util.Map;
import java.util.function.Consumer;

/**
 * v2.4.1 (AI Announcement Writer, step 4): calls the Edge Function {@code bofo-announce} and turns EVERY outcome into a typed {@link Result}
 * the sheet (steps 5-6) can show. It only WRITES A DRAFT: it never posts an announcement ({@code BhGroupService.postAnnouncement} is untouched).
 *
 * <ul>
 * <li>Invalid details never reach the network: {@code AnnounceRules.validateFields} runs first and the result is {@link AnnounceError#INVALID_DETAILS}.</li>
 * <li>No retry loop: one request per call; the owner presses Try again. A degraded draft is a SUCCESS ({@code draft.degraded}).</li>
 * <li>Privacy: nothing is logged here (no field values, closing name, wifi password or draft text); the request and the draft stay in memory.</li>
 * <li>Late results: {@link Call#cancel()} / {@link #cancelAll()} (call it from onDestroyView) make the callback never run, like the
 *     {@code token} in AnnounceFragment. The callback runs on the main thread.</li>
 * <li>Tests: the network call ({@link Transport}) and the threading ({@link Runner}) are injected; the default constructor uses
 *     SupabaseClient + Async, the tests use fakes and never hit the network.</li>
 * </ul>
 */
public final class AnnounceAiService {
    public static final String FUNCTION = "bofo-announce";

    /** The one network call: POST the body, return the 200 JSON, throw SupabaseClient.ApiException (non-2xx) or an IOException. */
    public interface Transport { JSONObject post(JSONObject body) throws Exception; }

    /** Runs blocking work off the main thread and delivers on the main thread (= {@link Async#run}). */
    public interface Runner { <T> void run(Async.Work<T> work, Consumer<T> ok, Consumer<Throwable> fail); }

    public interface Callback { void onResult(Result result); }

    /** Success (draft != null) or failure (error != null). */
    public static final class Result {
        public final AnnounceDraft draft;
        public final AnnounceError error;
        /** RATE_LIMITED only: seconds to wait (30 when the server sent nothing usable), else 0. */
        public final int retryAfterSeconds;
        /** INVALID only: the field key the server complained about, or null. */
        public final String field;
        /** INVALID_DETAILS only: what the app's own check found (code + field key), else null. */
        public final AnnounceRules.Problem problem;

        private Result(AnnounceDraft draft, AnnounceError error, int retryAfterSeconds, String field, AnnounceRules.Problem problem) {
            this.draft = draft; this.error = error; this.retryAfterSeconds = retryAfterSeconds; this.field = field; this.problem = problem;
        }
        static Result ok(AnnounceDraft d) { return new Result(d, null, 0, null, null); }
        static Result fail(AnnounceError e) { return new Result(null, e, 0, null, null); }
        static Result fail(AnnounceErrors.Failure f) { return new Result(null, f.error, f.retryAfterSeconds, f.field, null); }
        static Result invalidDetails(AnnounceRules.Problem p) { return new Result(null, AnnounceError.INVALID_DETAILS, 0, p.field, p); }

        public boolean ok() { return draft != null; }
    }

    /** Handle of one request. */
    public static final class Call {
        private volatile boolean cancelled;
        public void cancel() { cancelled = true; }
        public boolean isCancelled() { return cancelled; }
    }

    private final Transport transport;
    private final Runner runner;
    private volatile int token;

    public AnnounceAiService() {
        this(body -> SupabaseClient.get().function(FUNCTION, body), Async::run);
    }

    /** Test seam: a fake transport (no network) and, usually, a runner that runs the work at once on the calling thread. */
    public AnnounceAiService(Transport transport, Runner runner) { this.transport = transport; this.runner = runner; }

    /** Ignores the callbacks of every request started so far (call from onDestroyView / when the sheet closes). */
    public void cancelAll() { token++; }

    /**
     * Writes a draft in the background. {@code callback} is called ONCE, on the main thread, with the draft or the error, unless the call was
     * cancelled (then never). Invalid details are reported at once on the calling thread, without any network call.
     */
    public Call generate(AnnounceType type, AnnounceType.Lang lang, AnnounceType.Tone tone, AnnounceType.Length length, boolean emoji,
                         boolean closingOn, String closingName, Map<String, String> fields, AnnounceType.Adjust adjust, Callback callback) {
        final Call call = new Call();
        final int my = ++token;
        final JSONObject body;
        try {
            AnnounceRules.Problem p = type == null ? null : AnnounceRules.validateFields(type, fields);
            if (type == null) { deliver(call, my, callback, Result.fail(AnnounceError.GENERIC)); return call; }
            if (p != null) { deliver(call, my, callback, Result.invalidDetails(p)); return call; }
            body = AnnounceRules.buildRequest(type, lang, tone, length, emoji, closingOn, closingName, fields, adjust);
        } catch (Throwable e) {
            deliver(call, my, callback, Result.fail(AnnounceError.GENERIC));
            return call;
        }
        runner.run(() -> send(body),
                r -> deliver(call, my, callback, r),
                e -> deliver(call, my, callback, Result.fail(AnnounceErrors.from(e))));
        return call;
    }

    /** The blocking part (runs off the main thread): one request, one typed result. Never throws, never retries. */
    Result send(JSONObject body) {
        try {
            return Result.ok(AnnounceDraft.fromJson(transport.post(body)));
        } catch (IllegalArgumentException unreadableBody) {
            return Result.fail(AnnounceError.GENERIC);              // 200 but no title / message
        } catch (Throwable e) {
            return Result.fail(AnnounceErrors.from(e));
        }
    }

    private void deliver(Call call, int my, Callback cb, Result r) {
        if (cb == null || call.isCancelled() || my != token) return;
        cb.onResult(r);
    }
}
