package com.bofo.finder.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Minimal Mapbox Vector Tile (spec 2.1) decoder. No library, no reflection and no generated protobuf classes, so it is
 * safe for GraalVM native-image (Android). Only the fields BOFO needs are read; layers not in {@code only} are skipped
 * without being parsed.
 *
 * Wire layout (mapbox/vector-tile-spec 2.1): Tile.layers = 3; Layer: name 1, features 2, keys 3, values 4, extent 5,
 * version 15; Feature: id 1, tags 2 (packed), type 3, geometry 4 (packed); Value: string 1, float 2, double 3, int 4,
 * uint 5, sint 6, bool 7.
 */
public final class Mvt {
    public static final int POINT = 1, LINE = 2, POLYGON = 3;

    private Mvt() {}

    public static final class Layer {
        public String name = "";
        public int extent = 4096;
        public final List<String> keys = new ArrayList<>();
        public final List<Object> values = new ArrayList<>();
        public final List<Feature> features = new ArrayList<>();
    }

    public static final class Feature {
        public int type;
        public int[] tags = new int[0];
        /** Each part is x0,y0,x1,y1,... in tile units. Polygon rings do NOT repeat the first point (close them yourself). */
        public int[][] parts = new int[0][];
        Layer layer;

        private Object raw(String key) {
            for (int i = 0; i + 1 < tags.length; i += 2) {
                int k = tags[i], v = tags[i + 1];
                if (k >= 0 && k < layer.keys.size() && v >= 0 && v < layer.values.size() && key.equals(layer.keys.get(k)))
                    return layer.values.get(v);
            }
            return null;
        }
        /** Property as text, or "" when missing. */
        public String str(String key) { Object o = raw(key); return o == null ? "" : o.toString(); }
        /** Property as a number, or {@code def} when missing / not numeric. */
        public double num(String key, double def) {
            Object o = raw(key);
            if (o instanceof Number) return ((Number) o).doubleValue();
            if (o instanceof Boolean) return ((Boolean) o) ? 1 : 0;
            return def;
        }
    }

    /** Gzip-compressed tile bytes are unpacked first (detected by the 1F 8B magic), plain bytes are used as they are. */
    public static byte[] maybeGunzip(byte[] b) throws IOException {
        if (b.length > 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(b.length * 3);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        }
        return b;
    }

    /** @param only layer names to decode, or null for all */
    public static List<Layer> decode(byte[] data, Set<String> only) {
        List<Layer> out = new ArrayList<>();
        Reader r = new Reader(data, 0, data.length);
        while (r.more()) {
            int tag = (int) r.varint();
            int field = tag >>> 3, wire = tag & 7;
            if (field == 3 && wire == 2) {
                int len = (int) r.varint();
                int end = r.p + len;
                Layer l = layer(new Reader(data, r.p, end), only);
                if (l != null) out.add(l);
                r.p = end;
            } else r.skip(wire);
        }
        return out;
    }

    private static Layer layer(Reader r, Set<String> only) {
        // name may come after features in theory, so peek it first
        String name = null;
        Reader scan = new Reader(r.b, r.p, r.end);
        while (scan.more()) {
            int tag = (int) scan.varint();
            if ((tag >>> 3) == 1 && (tag & 7) == 2) { name = scan.string(); break; }
            scan.skip(tag & 7);
        }
        if (name == null || (only != null && !only.contains(name))) return null;
        Layer l = new Layer();
        l.name = name;
        while (r.more()) {
            int tag = (int) r.varint();
            int field = tag >>> 3, wire = tag & 7;
            if (field == 2 && wire == 2) {
                int len = (int) r.varint();
                int end = r.p + len;
                Feature f = feature(new Reader(r.b, r.p, end));
                f.layer = l;
                l.features.add(f);
                r.p = end;
            } else if (field == 3 && wire == 2) l.keys.add(r.string());
            else if (field == 4 && wire == 2) {
                int len = (int) r.varint();
                int end = r.p + len;
                l.values.add(value(new Reader(r.b, r.p, end)));
                r.p = end;
            } else if (field == 5 && wire == 0) l.extent = (int) r.varint();
            else r.skip(wire);
        }
        return l;
    }

    private static Object value(Reader r) {
        Object v = "";
        while (r.more()) {
            int tag = (int) r.varint();
            int field = tag >>> 3, wire = tag & 7;
            if (field == 1 && wire == 2) v = r.string();
            else if (field == 2 && wire == 5) v = (double) Float.intBitsToFloat((int) r.fixed32());
            else if (field == 3 && wire == 1) v = Double.longBitsToDouble(r.fixed64());
            else if ((field == 4 || field == 5) && wire == 0) v = r.varint();
            else if (field == 6 && wire == 0) { long z = r.varint(); v = (z >>> 1) ^ -(z & 1); }
            else if (field == 7 && wire == 0) v = r.varint() != 0;
            else r.skip(wire);
        }
        return v;
    }

    private static Feature feature(Reader r) {
        Feature f = new Feature();
        IntBuf tags = new IntBuf(), geom = new IntBuf();
        while (r.more()) {
            int tag = (int) r.varint();
            int field = tag >>> 3, wire = tag & 7;
            if (field == 2 || field == 4) {
                IntBuf dst = field == 2 ? tags : geom;
                if (wire == 2) {                              // packed (normal case)
                    int len = (int) r.varint();
                    int end = r.p + len;
                    while (r.p < end) dst.add((int) r.varint());
                } else if (wire == 0) dst.add((int) r.varint());   // unpacked, also legal protobuf
                else r.skip(wire);
            } else if (field == 3 && wire == 0) f.type = (int) r.varint();
            else r.skip(wire);
        }
        f.tags = tags.toArray();
        f.parts = geometry(geom.a, geom.n, f.type);
        return f;
    }

    /** Decodes MoveTo (1) / LineTo (2) / ClosePath (7) commands with zig-zag deltas into absolute coordinates. */
    static int[][] geometry(int[] g, int len, int type) {
        List<int[]> parts = new ArrayList<>();
        IntBuf cur = null;
        int x = 0, y = 0, i = 0;
        while (i < len) {
            int cmd = g[i++];
            int id = cmd & 7, count = cmd >>> 3;
            if (id == 1) {
                for (int k = 0; k < count && i + 1 < len; k++) {
                    x += (g[i] >>> 1) ^ -(g[i] & 1);
                    y += (g[i + 1] >>> 1) ^ -(g[i + 1] & 1);
                    i += 2;
                    if (type == POINT) { if (cur == null) cur = new IntBuf(); }
                    else { if (cur != null && cur.n > 0) parts.add(cur.toArray()); cur = new IntBuf(); }
                    cur.add(x); cur.add(y);
                }
            } else if (id == 2) {
                if (cur == null) cur = new IntBuf();
                for (int k = 0; k < count && i + 1 < len; k++) {
                    x += (g[i] >>> 1) ^ -(g[i] & 1);
                    y += (g[i + 1] >>> 1) ^ -(g[i + 1] & 1);
                    i += 2;
                    cur.add(x); cur.add(y);
                }
            } else if (id == 7) {
                if (cur != null && cur.n > 0) parts.add(cur.toArray());
                cur = null;
            } else break;                                      // unknown command: stop, keep what we have
        }
        if (cur != null && cur.n > 0) parts.add(cur.toArray());
        return parts.toArray(new int[0][]);
    }

    private static final class IntBuf {
        int[] a = new int[16];
        int n;
        void add(int v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = v; }
        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    private static final class Reader {
        final byte[] b;
        int p;
        final int end;
        Reader(byte[] b, int p, int end) { this.b = b; this.p = p; this.end = Math.min(end, b.length); }
        boolean more() { return p < end; }
        long varint() {
            long v = 0;
            int shift = 0;
            while (p < end) {
                int c = b[p++];
                v |= (long) (c & 0x7F) << shift;
                if ((c & 0x80) == 0) return v;
                shift += 7;
                if (shift > 63) break;
            }
            return v;
        }
        long fixed32() { long v = 0; for (int i = 0; i < 4 && p < end; i++) v |= (long) (b[p++] & 0xFF) << (8 * i); return v; }
        long fixed64() { long v = 0; for (int i = 0; i < 8 && p < end; i++) v |= (long) (b[p++] & 0xFF) << (8 * i); return v; }
        String string() {
            int len = (int) varint();
            len = Math.max(0, Math.min(len, end - p));
            String s = new String(b, p, len, StandardCharsets.UTF_8);
            p += len;
            return s;
        }
        void skip(int wire) {
            if (wire == 0) varint();
            else if (wire == 1) p += 8;
            else if (wire == 2) { int n = (int) varint(); p += n; }   // not "p += varint()": p is read before varint() moves it
            else if (wire == 5) p += 4;
            else p = end;                                      // groups etc. are not used by MVT
        }
    }
}
