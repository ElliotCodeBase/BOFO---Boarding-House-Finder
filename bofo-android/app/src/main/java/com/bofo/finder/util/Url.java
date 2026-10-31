package com.bofo.finder.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** URL-encoding that works on every Android version (URLEncoder.encode(String, Charset) needs API 33). */
public final class Url {
    private Url() {}

    /** Percent-encodes for a query value; a space becomes %20 (not +), which is what PostgREST filters expect. */
    public static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }
        catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }   // UTF-8 always exists
    }
}
