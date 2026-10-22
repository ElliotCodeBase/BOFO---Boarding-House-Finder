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
}
