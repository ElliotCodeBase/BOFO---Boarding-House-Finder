package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;
import com.gluonhq.attach.storage.StorageService;
import com.gluonhq.maps.tile.TileRetriever;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * BOFO's own map style: downloads OpenFreeMap vector tiles (OpenMapTiles schema, no key) and paints each one into a
 * 512 px image with a JavaFX Canvas. Gluon Maps shows every tile at 256 px, so the map is sharp on dense screens.
 * Only land, parks, water, buildings and roads are drawn: no place names and no shop / bus-stop icons.
 *
 * Switch in config.properties: {@code map.renderer=vector} (this class) or {@code raster} (old {@link MinimalTileRetriever}).
 * If a vector tile cannot be loaded (no TileJSON yet and offline, bad data, drawing error) that single tile is served by
 * the raster retriever, so the map never stays blank.
 *
 * Verified against the live service (see HANDOFF): TileJSON at https://tiles.openfreemap.org/planet gives a VERSIONED
 * tile URL ({@code .../planet/20261018_080001_pt/{z}/{x}/{y}.pbf}), maxzoom 14; the version changes weekly, so the URL
 * is read from the TileJSON at run time and cached, never hard-coded. Zoom 15+ is drawn by enlarging the zoom 14 tile.
 *
 * No reflection except the ServiceLoader entry (see pom reflectionList). Decoding is {@link Mvt}.
 */
public final class VectorTileRetriever implements TileRetriever {
    private static final int PX = 512;                     // rendered size; Gluon shows it at 256 (TileImageView fitWidth)
    private static final double DP = PX / 256.0;           // style widths below are written in 256 px "display" pixels
    private static final int SRC_MAX_Z = 14;               // TileJSON maxzoom
    private static final long TILE_MAX_AGE_MS = 30L * 24 * 3600 * 1000;
    private static final long JSON_MAX_AGE_MS = 24L * 3600 * 1000;
    private static final long JSON_RETRY_MS = 10L * 60 * 1000;
    private static final byte[] EMPTY = new byte[0];
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    /** OpenFreeMap asks for at most 4 parallel requests. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "bofo-vtiles"); t.setDaemon(true); return t;
    });
    private static final Map<String, CompletableFuture<byte[]>> INFLIGHT = new ConcurrentHashMap<>();
    /** Layers we draw. Everything else (place, poi, *_name, housenumber, boundary ...) is skipped while decoding. */
    private static final Set<String> WANTED = Set.of("landcover", "landuse", "park", "water", "waterway", "aeroway",
            "building", "transportation");

    // ---- palette (edit here) -------------------------------------------------------------------------------------
    static final Color BRAND = Color.web("#5B3BF0");
    private static final Color LAND = Color.web("#E9E5F6");          // faint lavender
    private static final Color GRASS = Color.web("#C4E5B8");
    private static final Color WOOD = Color.web("#B5DBA8");
    private static final Color PARK = Color.web("#BCE2B0");
    private static final Color PITCH = Color.web("#C2E8B8");
    private static final Color WETLAND = Color.web("#B9E0D3");
    private static final Color SAND = Color.web("#EFE6C8");
    private static final Color CEMETERY = Color.web("#CFE3C6");
    private static final Color SCHOOL = Color.web("#DDD3F3");
    private static final Color HOSPITAL = Color.web("#F4D9E6");
    private static final Color WATER = Color.web("#8EC9F2");
    private static final Color AERO = Color.web("#DCD6EC");
    private static final Color BUILDING = Color.web("#D6CDEC");
    private static final Color BUILDING_LINE = Color.web("#B9AEDB");
    private static final Color ROAD = Color.WHITE;
    private static final Color CASING = Color.web("#AFA6CF");                              // thin outline of small roads
    private static final Color CASING_MAJOR = Color.web("#9B88F5");                        // brand purple #5B3BF0 at 30% on land: main-road outline

    /** false when config says map.renderer=raster: every tile is then served by the old raster retriever. */
    private final boolean vector;
    private final MinimalTileRetriever raster;
    private final File cacheDir, jsonFile;
    private volatile String template;
    private volatile long lastRefreshTry;

    /** Public no-arg constructor: required by ServiceLoader. */
    public VectorTileRetriever() {
        vector = !"raster".equalsIgnoreCase(AppConfig.mapRenderer());
        raster = new MinimalTileRetriever();
        File base = StorageService.create().flatMap(StorageService::getPrivateStorage)
                .orElse(new File(System.getProperty("user.home"), ".bofo"));
        cacheDir = new File(base, "tiles" + File.separator + "ofm");
        jsonFile = new File(cacheDir, "tilejson.json");
        if (vector) {
            try { if (jsonFile.isFile()) template = parseTemplate(new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8)); }
            catch (Exception ignored) { /* fetched again on first tile */ }
        }
    }

    @Override public CompletableFuture<Image> loadTile(int zoom, long i, long j) {
        if (!vector) return raster.loadTile(zoom, i, j);
        CompletableFuture<Image> out = new CompletableFuture<>();
        POOL.execute(() -> {
            try {
                int dz = Math.max(0, zoom - SRC_MAX_Z);
                int sz = zoom - dz;
                byte[] pbf = source(sz, i >> dz, j >> dz);
                List<Mvt.Layer> layers = pbf.length == 0 ? new ArrayList<>() : Mvt.decode(Mvt.maybeGunzip(pbf), WANTED);
                enqueueDraw(() -> {                             // Canvas must be used on the FX thread; one tile per turn, newest first (see enqueueDraw)
                    try { out.complete(draw(layers, zoom, i, j)); }
                    catch (Throwable t) { fallback(out, zoom, i, j); }
                });
            } catch (Throwable t) { fallback(out, zoom, i, j); }
        });
        return out;
    }

    // Drawing a dense city tile takes tens of milliseconds on the FX thread. Doing every waiting tile in one go froze the whole app while
    // zooming, so tiles are drawn one per turn (the screen repaints in between) and the most recently asked tile goes first (it is the one on screen).
    private static final java.util.ArrayDeque<Runnable> DRAWQ = new java.util.ArrayDeque<>();
    private static boolean drawing;

    private static void enqueueDraw(Runnable r) {
        synchronized (DRAWQ) { DRAWQ.addFirst(r); if (drawing) return; drawing = true; }
        Platform.runLater(VectorTileRetriever::drawNext);
    }

    private static void drawNext() {
        Runnable r;
        synchronized (DRAWQ) { r = DRAWQ.pollFirst(); if (r == null) { drawing = false; return; } }
        try { r.run(); } finally { Platform.runLater(VectorTileRetriever::drawNext); }
    }

    private void fallback(CompletableFuture<Image> out, int z, long x, long y) {
        raster.loadTile(z, x, y).whenComplete((img, e) -> { if (e != null) out.completeExceptionally(e); else out.complete(img); });
    }

    // ---- download + disk cache (same rules as MinimalTileRetriever: 30 days fresh, older copy used when offline) ----

    /** Raw tile bytes of a source tile (zoom <= 14); {@link #EMPTY} when the server has no tile there. */
    private byte[] source(int z, long x, long y) throws Exception {
        String key = z + "/" + x + "/" + y;
        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> other = INFLIGHT.putIfAbsent(key, mine);
        if (other != null) {                                    // zoom 15+ tiles share one z14 tile: download it once
            try { return other.get(40, TimeUnit.SECONDS); }
            catch (ExecutionException e) { throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e; }
        }
        try {
            byte[] b = download(z, x, y);
            mine.complete(b);
            return b;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            INFLIGHT.remove(key);
        }
    }

    private byte[] download(int z, long x, long y) throws Exception {
        File f = new File(cacheDir, z + File.separator + x + File.separator + y + ".pbf");
        boolean have = f.isFile() && f.length() > 0;
        if (have && System.currentTimeMillis() - f.lastModified() < TILE_MAX_AGE_MS) {
            try { return Files.readAllBytes(f.toPath()); } catch (Exception ignored) { /* download again */ }
        }
        String t = template();
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpResponse<byte[]> r;
            try {
                r = HTTP.send(HttpRequest.newBuilder(URI.create(url(t, z, x, y))).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "BOFO-Finder/1.2 (boarding house finder app)").GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
            } catch (java.io.IOException offline) {
                if (have) return Files.readAllBytes(f.toPath());          // offline: an older tile beats a blank square
                throw offline;
            }
            int s = r.statusCode();
            if (s == 200 && r.body().length > 0) {
                byte[] b = r.body();
                try { f.getParentFile().mkdirs(); Files.write(f.toPath(), b); } catch (Exception ignored) { /* cache is optional */ }
                return b;
            }
            if (s == 404 || s == 410) {
                // Either a truly empty tile, or the weekly build in our cached TileJSON was removed: refresh once and retry.
                String fresh = refreshTemplate();
                if (attempt == 0 && fresh != null && !fresh.equals(t)) { t = fresh; continue; }
                return EMPTY;
            }
            if (s == 204) return EMPTY;
            if (have) return Files.readAllBytes(f.toPath());
            throw new java.io.IOException("Tile server answered HTTP " + s);
        }
        return EMPTY;
    }

    private static String url(String template, int z, long x, long y) {
        return template.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
    }

    /** Current tile URL template; reads the TileJSON when we have none or it is older than a day (failure keeps the old one). */
    private String template() throws Exception {
        String t = template;
        boolean stale = t == null || !jsonFile.isFile() || System.currentTimeMillis() - jsonFile.lastModified() > JSON_MAX_AGE_MS;
        if (stale) {
            String fresh = refreshTemplate();
            if (fresh != null) return fresh;
            if (t == null) throw new java.io.IOException("OpenFreeMap TileJSON not available");
        }
        return t;
    }

    private synchronized String refreshTemplate() {
        long now = System.currentTimeMillis();
        if (lastRefreshTry != 0 && now - lastRefreshTry < (template != null ? JSON_RETRY_MS : 15_000L)) return template;
        lastRefreshTry = now;
        try {
            HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(AppConfig.vectorTileJson())).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "BOFO-Finder/1.2 (boarding house finder app)").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) return template;
            String json = new String(r.body(), StandardCharsets.UTF_8);
            String t = parseTemplate(json);
            template = t;
            try { jsonFile.getParentFile().mkdirs(); Files.write(jsonFile.toPath(), json.getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) { }
            return t;
        } catch (Exception e) {
            return template;                                    // offline: keep what we had (may be null)
        }
    }

    private static String parseTemplate(String json) {
        JSONArray tiles = new JSONObject(json).getJSONArray("tiles");
        String t = tiles.getString(0);
        if (!t.contains("{z}") || !t.contains("{x}") || !t.contains("{y}")) throw new IllegalArgumentException("bad TileJSON tiles url");
        return t;
    }

    // ---- drawing (FX thread) -------------------------------------------------------------------------------------

    /** Source units -> canvas pixels. For zoom > 14 only a part of the zoom 14 tile is shown, enlarged. */
    private static final class Xf {
        final double s, ox, oy;
        Xf(double s, double ox, double oy) { this.s = s; this.ox = ox; this.oy = oy; }
        double x(int gx) { return (gx - ox) * s; }
        double y(int gy) { return (gy - oy) * s; }
    }

    private static Image draw(List<Mvt.Layer> layers, int z, long x, long y) {
        Map<String, Mvt.Layer> by = new HashMap<>();
        for (Mvt.Layer l : layers) by.put(l.name, l);
        int dz = Math.max(0, z - SRC_MAX_Z);
        long n = 1L << dz;
        int extent = layers.isEmpty() ? 4096 : layers.get(0).extent;
        double s = (double) PX * n / extent;
        Xf t = new Xf(s, (x - ((x >> dz) << dz)) * (double) extent / n, (y - ((y >> dz) << dz)) * (double) extent / n);

        Canvas c = new Canvas(PX, PX);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFillRule(FillRule.EVEN_ODD);                       // holes in polygons without caring about winding
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setFill(LAND);
        g.fillRect(0, 0, PX, PX);

        landcover(g, by.get("landcover"), t, z);
        landuse(g, by.get("landuse"), t);
        polys(g, by.get("park"), t, PARK);
        water(g, by.get("water"), t);
        waterways(g, by.get("waterway"), t, z);
        aeroway(g, by.get("aeroway"), t, z);
        // buildings are not drawn any more: thousands of tiny shapes per city tile (slow) and the main reason the map looked cluttered
        roads(g, by.get("transportation"), t, z);

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        WritableImage img = new WritableImage(PX, PX);
        c.snapshot(sp, img);
        return img;
    }

    private static void landcover(GraphicsContext g, Mvt.Layer l, Xf t, int z) {
        if (l == null) return;
        for (String cls : new String[]{"sand", "wetland", "grass", "wood"}) {          // drawn in this order (wood on top)
            if (cls.equals("wetland") && z < 12) continue;                            // same as OpenFreeMap's own style
            Color col = cls.equals("sand") ? SAND : cls.equals("wetland") ? WETLAND : cls.equals("grass") ? GRASS : WOOD;
            g.setFill(col);
            for (Mvt.Feature f : l.features) if (f.type == Mvt.POLYGON && cls.equals(f.str("class"))) poly(g, f, t, false);
        }
    }

    private static void landuse(GraphicsContext g, Mvt.Layer l, Xf t) {
        if (l == null) return;
        for (Mvt.Feature f : l.features) {
            if (f.type != Mvt.POLYGON) continue;
            Color col;
            switch (f.str("class")) {
                case "pitch": col = PITCH; break;
                case "cemetery": col = CEMETERY; break;
                case "school": col = SCHOOL; break;
                case "hospital": col = HOSPITAL; break;
                default: continue;                                                    // residential etc. stay plain land
            }
            g.setFill(col);
            poly(g, f, t, false);
        }
    }

    private static void polys(GraphicsContext g, Mvt.Layer l, Xf t, Color col) {
        if (l == null) return;
        g.setFill(col);
        for (Mvt.Feature f : l.features) if (f.type == Mvt.POLYGON) poly(g, f, t, false);
    }

    private static void water(GraphicsContext g, Mvt.Layer l, Xf t) {
        if (l == null) return;
        g.setFill(WATER);
        for (Mvt.Feature f : l.features) if (f.type == Mvt.POLYGON && !"tunnel".equals(f.str("brunnel"))) poly(g, f, t, false);
    }

    private static void waterways(GraphicsContext g, Mvt.Layer l, Xf t, int z) {
        if (l == null || z < 8) return;
        List<Mvt.Feature> rivers = new ArrayList<>(), other = new ArrayList<>();
        for (Mvt.Feature f : l.features) {
            if (f.type != Mvt.LINE || "tunnel".equals(f.str("brunnel"))) continue;
            (f.str("class").equals("river") ? rivers : other).add(f);
        }
        g.setStroke(WATER);
        if (z >= 12) { g.setLineWidth(clamp(0.9 * Math.pow(1.5, z - 13), 0.8, 14) * DP); stroke(g, other, t, 20); }
        g.setLineWidth(clamp(1.6 * Math.pow(1.5, z - 12), 0.9, 30) * DP);
        stroke(g, rivers, t, 40);
    }

    private static void aeroway(GraphicsContext g, Mvt.Layer l, Xf t, int z) {
        if (l == null || z < 11) return;
        g.setFill(AERO);
        List<Mvt.Feature> run = new ArrayList<>();
        for (Mvt.Feature f : l.features) {
            if (f.type == Mvt.POLYGON) poly(g, f, t, false);
            else if (f.type == Mvt.LINE) run.add(f);
        }
        g.setStroke(ROAD);
        g.setLineWidth(clamp(3.0 * Math.pow(1.5, z - 14), 1, 60) * DP);
        stroke(g, run, t, 60);
    }

    private static void buildings(GraphicsContext g, Mvt.Layer l, Xf t, int z) {
        if (l == null) return;
        g.setFill(BUILDING);
        g.setStroke(BUILDING_LINE);
        g.setLineWidth(0.8 * DP);
        boolean outline = z >= 15;                              // below that the outline only makes small roofs look dirty
        for (Mvt.Feature f : l.features) if (f.type == Mvt.POLYGON) poly(g, f, t, outline);
    }

    // Road classes -> rank (draw order) and width in 256 px display pixels at zoom 14. Anything else (rail, ferry, ...) is skipped.
    private static int rank(String cls) {
        switch (cls) {
            case "service": case "track": return 0;
            case "path": case "pedestrian": return 1;
            case "minor": case "street": case "street_limited": return 2;
            case "secondary": case "tertiary": return 3;
            case "primary": case "trunk": return 4;
            case "motorway": return 5;
            default: return -1;
        }
    }
    private static final double[] W14 = {1.2, 1.1, 2.3, 3.1, 4.0, 4.6};

    private static void roads(GraphicsContext g, Mvt.Layer l, Xf t, int z) {
        if (l == null) return;
        // bucket index = rank * 2 + (ramp ? 1 : 0); three sets: tunnel, ground, bridge
        @SuppressWarnings("unchecked") List<Mvt.Feature>[][] set = new List[3][12];
        List<Mvt.Feature> areas = new ArrayList<>();
        for (Mvt.Feature f : l.features) {
            int r = rank(f.str("class"));
            if (r < 0) continue;
            if (r <= 1 && z < 15) continue;                     // alleys, service roads, tracks and footpaths: only when zoomed in (the walking route uses them, so they must be visible there)
            if (f.type == Mvt.POLYGON) continue;
            if (f.type != Mvt.LINE) continue;
            if (r == 2 && z < 14) continue;                     // small streets only when zoomed in a bit
            String bt = f.str("brunnel");
            int layer = bt.equals("tunnel") ? 0 : bt.equals("bridge") ? 2 : 1;
            int idx = r * 2 + (f.num("ramp", 0) == 1 ? 1 : 0);
            if (set[layer][idx] == null) set[layer][idx] = new ArrayList<>();
            set[layer][idx].add(f);
        }
        if (!areas.isEmpty()) {
            g.setFill(ROAD);
            g.setStroke(CASING);
            g.setLineWidth(0.6 * DP);
            for (Mvt.Feature f : areas) poly(g, f, t, true);
        }
        for (int layer = 0; layer < 3; layer++) {
            g.setGlobalAlpha(layer == 0 ? 0.55 : 1.0);          // tunnels are drawn faded
            if (z >= 12) {
                for (int idx = 0; idx < 12; idx++) {            // 1) all outlines (so crossings merge cleanly) ...
                    if (set[layer][idx] == null || idx / 2 < 3) continue;   // outlines only on main roads
                    g.setStroke(idx / 2 >= 4 ? CASING_MAJOR : CASING);
                    g.setLineWidth((width(idx, z) + 2 * 1.0) * DP);
                    stroke(g, set[layer][idx], t, 40);
                }
            }
            g.setStroke(ROAD);
            for (int idx = 0; idx < 12; idx++) {                // 2) ... then all white road bodies
                if (set[layer][idx] == null) continue;
                g.setGlobalAlpha((idx / 2 <= 1 ? 0.85 : 1.0) * (layer == 0 ? 0.55 : 1.0));   // footpaths a little softer than streets
                g.setLineWidth(width(idx, z) * DP);
                stroke(g, set[layer][idx], t, 40);
            }
        }
        g.setGlobalAlpha(1.0);
    }

    private static double width(int idx, int z) {
        double w = W14[idx / 2] * Math.pow(1.5, z - 14);
        if ((idx & 1) == 1) w *= 0.6;
        return clamp(w, 0.6, 40);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---- geometry helpers ----------------------------------------------------------------------------------------

    /** True when the part lies completely outside the tile (plus {@code pad} pixels) and can be skipped. */
    private static boolean outside(int[] p, Xf t, double pad) {
        int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < p.length; i += 2) {
            if (p[i] < minx) minx = p[i];
            if (p[i] > maxx) maxx = p[i];
            if (p[i + 1] < miny) miny = p[i + 1];
            if (p[i + 1] > maxy) maxy = p[i + 1];
        }
        return t.x(maxx) < -pad || t.x(minx) > PX + pad || t.y(maxy) < -pad || t.y(miny) > PX + pad;
    }

    /** Fills one polygon feature (all its rings in one path, even-odd) and optionally outlines it. Colours are set by the caller. */
    private static void poly(GraphicsContext g, Mvt.Feature f, Xf t, boolean outline) {
        boolean any = false;
        g.beginPath();
        for (int[] p : f.parts) {
            if (p.length < 6 || outside(p, t, 4)) continue;
            g.moveTo(t.x(p[0]), t.y(p[1]));
            for (int i = 2; i + 1 < p.length; i += 2) g.lineTo(t.x(p[i]), t.y(p[i + 1]));
            g.closePath();
            any = true;
        }
        if (!any) return;
        g.fill();
        if (outline) g.stroke();
    }

    /** Strokes all given line features as one path (width / colour set by the caller). */
    private static void stroke(GraphicsContext g, List<Mvt.Feature> fs, Xf t, double pad) {
        if (fs.isEmpty()) return;
        boolean any = false;
        g.beginPath();
        for (Mvt.Feature f : fs) {
            for (int[] p : f.parts) {
                if (p.length < 4 || outside(p, t, pad)) continue;
                g.moveTo(t.x(p[0]), t.y(p[1]));
                for (int i = 2; i + 1 < p.length; i += 2) g.lineTo(t.x(p[i]), t.y(p[i + 1]));
                any = true;
            }
        }
        if (any) g.stroke();
    }
}
