package com.bofo.finder.util;

public final class Validate {
    private Validate() {}
    public static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    public static String clean(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
    public static boolean imageOk(byte[] data, long maxBytes) {
        if (data == null || data.length < 12 || data.length > maxBytes) return false;
        boolean jpg = (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
        boolean png = (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
        return jpg || png;
    }

    /* ---------- Stage 3: the same limits as the database CHECK constraints on public.users ---------- */
    /** users.username: 3-20 letters, digits, underscore or dot. */
    public static boolean username(String s) { return s != null && s.matches("[A-Za-z0-9_.]{3,20}"); }
    /** users.age: 10-100. Returns null when the text is not a whole number in range. */
    public static Integer age(String s) {
        if (s == null) return null;
        try { int a = Integer.parseInt(s.trim()); return a >= 10 && a <= 100 ? a : null; }
        catch (NumberFormatException e) { return null; }
    }
    /** Loose phone check: optional +, then 7-15 digits (spaces and dashes allowed). */
    public static boolean phone(String s) {
        if (s == null) return false;
        String d = s.replaceAll("[\\s\\-()]", "");
        return d.matches("\\+?[0-9]{7,15}");
    }
    /** Basic e-mail shape (the server does the real check). */
    public static boolean email(String s) { return s != null && s.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}"); }
}
