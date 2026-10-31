package com.bofo.finder.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;

/** The one shared OkHttp client (java.net.http does not exist on Android). OkHttp adds and unzips gzip by itself. */
public final class Http {
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build();
    private static final ConcurrentMap<Integer, OkHttpClient> BY_TIMEOUT = new ConcurrentHashMap<>();
    private Http() {}

    public static OkHttpClient client() { return CLIENT; }

    /** Same client (shared connections) with a shorter whole-call limit, e.g. 15 s for reads that must fail fast. */
    public static OkHttpClient client(int callTimeoutSeconds) {
        return BY_TIMEOUT.computeIfAbsent(callTimeoutSeconds,
                s -> CLIENT.newBuilder().callTimeout(s, TimeUnit.SECONDS).build());
    }
}
