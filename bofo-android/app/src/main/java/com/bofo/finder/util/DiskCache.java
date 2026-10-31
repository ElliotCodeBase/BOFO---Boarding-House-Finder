package com.bofo.finder.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Tiny text-file cache on the device (survives closing the app). Used for the last good server answers (Offline) and the
 * jeepney lines so trips can be planned without internet after the first time. The base folder is the app's private
 * files folder (set once by BofoApp via {@link #setBase}). Never throws: a failure just means "not cached".
 */
public final class DiskCache {
    private DiskCache() {}

    private static volatile File base;

    /** Call once at start-up with the app's private storage folder. */
    public static void setBase(File dir) { if (dir != null) base = dir; }

    /** Parent of the cache folder (private app storage; a temp folder when running as a plain JVM unit test). */
    public static File root() { return base != null ? base : new File(System.getProperty("java.io.tmpdir"), "bofo"); }

    private static File dir() { File d = new File(root(), "cache"); d.mkdirs(); return d; }

    private static File file(String name) { return new File(dir(), name.replaceAll("[^A-Za-z0-9._-]", "_")); }

    public static String read(String name) {
        try {
            File f = file(name);
            if (!f.isFile()) return null;
            try (InputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Throwable t) { return null; }
    }

    /** Milliseconds since the file was written, or Long.MAX_VALUE when it does not exist. */
    public static long ageMs(String name) {
        try { File f = file(name); return f.isFile() ? Math.max(0, System.currentTimeMillis() - f.lastModified()) : Long.MAX_VALUE; } catch (Throwable t) { return Long.MAX_VALUE; }
    }

    public static void write(String name, String text) {
        try {
            File f = file(name), tmp = new File(f.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }     // never leaves a half-written file
        } catch (Throwable ignored) { }
    }

    /** Deletes every cached file whose name starts with {@code prefix} (used on sign-out for the user's own data). */
    public static void deletePrefix(String prefix) {
        try { File[] all = dir().listFiles((d, n) -> n.startsWith(prefix)); if (all != null) for (File f : all) f.delete(); } catch (Throwable ignored) { }
    }

    /** Keeps only the {@code keep} newest files whose name starts with {@code prefix}. */
    public static void trim(String prefix, int keep) {
        try {
            File[] all = dir().listFiles((d, n) -> n.startsWith(prefix) && !n.endsWith(".tmp"));
            if (all == null || all.length <= keep) return;
            Arrays.sort(all, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = keep; i < all.length; i++) all[i].delete();
        } catch (Throwable ignored) { }
    }
}
