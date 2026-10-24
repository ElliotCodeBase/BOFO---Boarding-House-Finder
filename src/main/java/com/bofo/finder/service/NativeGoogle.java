package com.bofo.finder.service;

import com.bofo.finder.config.AppConfig;
import com.bofo.finder.util.Async;
import com.gluonhq.attach.storage.StorageService;
import javafx.application.HostServices;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Android only: Google's own account picker (bottom sheet) inside the app, no browser.
 *
 * How it works (no native bridge needed): this class opens the app's own screen {@code GoogleSignInActivity}
 * (src/android/java/..., written in Android Java) through the link bofo://google-signin. That screen shows the Google
 * account sheet and writes the result into a small file in the app's private folder; this class waits for the file.
 * If the screen does not start within 4 s, {@link Unavailable} is thrown and AuthService falls back to the browser flow.
 */
public final class NativeGoogle {
    /** The native screen is missing or could not start: use the browser sign-in instead. */
    public static final class Unavailable extends RuntimeException { Unavailable(String m) { super(m); } }

    /** Google ID token plus the raw nonce that Supabase needs to check it. */
    public static final class Result {
        public final String idToken, rawNonce;
        Result(String idToken, String rawNonce) { this.idToken = idToken; this.rawNonce = rawNonce; }
    }

    private static final String RESULT = "google-signin.json", STARTED = "google-signin.started";

    private NativeGoogle() {}

    public static boolean available() { return AppConfig.isAndroid() && AppConfig.googleNative(); }

    public static CompletableFuture<Result> signIn(HostServices hs) {
        CompletableFuture<Result> out = new CompletableFuture<>();
        try {
            List<File> dirs = dirs();
            for (File d : dirs) { new File(d, RESULT).delete(); new File(d, STARTED).delete(); }
            byte[] raw = new byte[32]; new SecureRandom().nextBytes(raw);
            String rawNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            String hashed = hex(MessageDigest.getInstance("SHA-256").digest(rawNonce.getBytes(StandardCharsets.UTF_8)));
            hs.showDocument("bofo://google-signin?client=" + URLEncoder.encode(AppConfig.googleWebClientId(), StandardCharsets.UTF_8)
                    + "&nonce=" + hashed);                              // Android opens our own GoogleSignInActivity

            long t0 = System.currentTimeMillis();
            ScheduledExecutorService poll = Async.scheduler();
            poll.scheduleWithFixedDelay(() -> {
                try {
                    if (out.isDone()) { poll.shutdownNow(); return; }       // cancelled by the user
                    File res = find(dirs, RESULT);
                    if (res != null && res.length() > 0) {
                        JSONObject j = new JSONObject(Files.readString(res.toPath()));
                        for (File d : dirs) { new File(d, RESULT).delete(); new File(d, STARTED).delete(); }
                        poll.shutdownNow();
                        if (j.has("id_token")) out.complete(new Result(j.getString("id_token"), rawNonce));
                        else {
                            String err = j.optString("error", "failed");
                            out.completeExceptionally("cancelled".equals(err) ? new CancellationException("cancelled")
                                    : new IllegalStateException("Google sign-in failed: " + err));
                        }
                        return;
                    }
                    long age = System.currentTimeMillis() - t0;
                    if (find(dirs, STARTED) == null && age > 4000) {
                        poll.shutdownNow(); out.completeExceptionally(new Unavailable("The Google account screen did not open"));
                    } else if (age > 180_000) {
                        poll.shutdownNow(); out.completeExceptionally(new IllegalStateException("Google sign-in timed out"));
                    }
                } catch (Throwable t) { poll.shutdownNow(); out.completeExceptionally(t); }
            }, 250, 250, TimeUnit.MILLISECONDS);
        } catch (Throwable t) { out.completeExceptionally(new Unavailable(String.valueOf(t.getMessage()))); }
        return out;
    }

    /** Where the Android screen writes: the app's private files folder (Attach reports it; the second path is the usual one). */
    private static List<File> dirs() {
        List<File> l = new ArrayList<>();
        StorageService.create().flatMap(StorageService::getPrivateStorage).ifPresent(l::add);
        l.add(new File("/data/data/com.bofo.finder/files"));
        return l;
    }
    private static File find(List<File> dirs, String name) {
        for (File d : dirs) { File f = new File(d, name); if (f.isFile()) return f; }
        return null;
    }
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
}
