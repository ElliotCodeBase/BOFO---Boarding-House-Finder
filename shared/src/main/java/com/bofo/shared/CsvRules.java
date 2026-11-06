package com.bofo.shared;

import java.util.List;

/** Moved to shared in 2.9.10 (admin app). Builds the CSV text for the admin exports (pure, unit tested). UTF-8 BOM so Excel opens it; cells that could run as a formula are neutralised. */
public final class CsvRules {
    public static final String BOM = "\uFEFF";
    private CsvRules() {}

    /** One cell: line breaks become spaces; a cell starting with = + - @ or a tab gets a leading apostrophe; commas and quotes are escaped. */
    public static String cell(String s) {
        String v = s == null ? "" : s.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty()) {
            char c = v.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t') v = "'" + v;
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0) v = "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    public static String line(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) { if (i > 0) sb.append(','); sb.append(cell(cells[i])); }
        return sb.append("\r\n").toString();
    }

    public static String document(String[] header, List<String[]> rows) {
        StringBuilder sb = new StringBuilder(BOM).append(line(header));
        for (String[] r : rows) sb.append(line(r));
        return sb.toString();
    }
}
