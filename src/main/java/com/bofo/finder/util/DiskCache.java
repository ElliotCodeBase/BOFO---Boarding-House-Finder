package com.bofo.finder.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Tiny text-file cache on the device (survives closing the app). Used for the jeepney lines so trips can be planned
 * without internet after the first time. The base folder is the app's private storage on a phone (set once by the UI via
 * {@link #setBase}), else ~/.bofo. Never throws: a failure just means "not cached".
 */
public final class DiskCache {
    private DiskCache() {}

    private static volatile File base;

    /** Call once at start-up with the app's private storage folder (null keeps the default). */
    public static void setBase(File dir) { if (dir != null) base = dir; }

    /** The folder the user can drop local-transit.json into (the cache folder's parent). */
    public static File root() { return base != null ? base : new File(System.getProperty("user.home"), ".bofo"); }

    private static File dir() { File d = new File(root(), "cache"); d.mkdirs(); return d; }

    private static File file(String name) { return new File(dir(), name.replaceAll("[^A-Za-z0-9._-]", "_")); }

    public static String read(String name) {
        try { File f = file(name); return f.isFile() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : null; } catch (Throwable t) { return null; }
    }

    /** Milliseconds since the file was written, or Long.MAX_VALUE when it does not exist. */
    public static long ageMs(String name) {
        try { File f = file(name); return f.isFile() ? Math.max(0, System.currentTimeMillis() - f.lastModified()) : Long.MAX_VALUE; } catch (Throwable t) { return Long.MAX_VALUE; }
    }

    public static void write(String name, String text) {
        try {
            File f = file(name), tmp = new File(f.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);   // never leaves a half-written file
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
