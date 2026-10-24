package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;
import com.gluonhq.attach.storage.StorageService;
import com.gluonhq.maps.tile.TileRetriever;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Map tiles in the app's own "plain" style: roads, water, parks and building shapes only, no place labels and no
 * shop / restaurant / bus-stop icons. Gluon Maps finds this class through META-INF/services (ServiceLoader) and uses it
 * instead of the default OpenStreetMap tiles. The tile server is set in config.properties (map.tiles.url, map.tiles.key).
 * Tiles are cached on disk for 30 days, and old cached tiles are still used when the phone is offline.
 */
public final class MinimalTileRetriever implements TileRetriever {
    private static final String OSM = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    private static final long MAX_AGE_MS = 30L * 24 * 3600 * 1000;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "bofo-tiles"); t.setDaemon(true); return t;
    });
    /** Set when the styled server refuses us (missing / wrong key): plain OpenStreetMap tiles keep the map usable. */
    private static volatile boolean fallback;

    private final String template, key, subdomains, styleId;
    private final File cacheDir;

    /** Public no-arg constructor: required by ServiceLoader. */
    public MinimalTileRetriever() {
        template = AppConfig.tileUrl();
        key = AppConfig.tileKey();
        subdomains = AppConfig.get("map.tiles.subdomains").trim();
        styleId = Integer.toHexString(template.hashCode());
        File base = StorageService.create().flatMap(StorageService::getPrivateStorage)
                .orElse(new File(System.getProperty("user.home"), ".bofo"));
        cacheDir = new File(base, "tiles");
    }

    @Override public CompletableFuture<Image> loadTile(int zoom, long i, long j) {
        CompletableFuture<Image> out = new CompletableFuture<>();
        POOL.execute(() -> {
            try { out.complete(new Image(new ByteArrayInputStream(read(zoom, i, j)))); }
            catch (Throwable t) { out.completeExceptionally(t); }
        });
        return out;
    }

    private byte[] read(int z, long x, long y) throws Exception {
        boolean osm = fallback;
        File f = new File(cacheDir, (osm ? "osm" : styleId) + File.separator + z + File.separator + x + File.separator + y + ".png");
        boolean have = f.isFile() && f.length() > 0;
        if (have && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS) {
            try { return Files.readAllBytes(f.toPath()); } catch (Exception ignored) { /* download again */ }
        }
        HttpResponse<byte[]> r;
        try {
            r = HTTP.send(HttpRequest.newBuilder(URI.create(url(osm, z, x, y))).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "BOFO-Finder/1.2 (boarding house finder app)").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.io.IOException offline) {
            if (have) return Files.readAllBytes(f.toPath());            // offline: an older tile beats a blank square
            throw offline;
        }
        int s = r.statusCode();
        if ((s == 401 || s == 403) && !osm) { fallback = true; return read(z, x, y); }   // bad / missing tile key
        if (s != 200) throw new java.io.IOException("Tile server answered HTTP " + s);
        byte[] b = r.body();
        try { f.getParentFile().mkdirs(); Files.write(f.toPath(), b); } catch (Exception ignored) { /* cache is optional */ }
        return b;
    }

    private String url(boolean osm, int z, long x, long y) {
        String u = osm ? OSM : template;
        if (!subdomains.isEmpty()) u = u.replace("{s}", String.valueOf(subdomains.charAt((int) ((x + y) % subdomains.length()))));
        u = u.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
        if (!osm && !key.isEmpty()) {
            String k = URLEncoder.encode(key, StandardCharsets.UTF_8);
            if (u.contains("{key}")) u = u.replace("{key}", k);
            else u += (u.contains("?") ? "&" : "?") + "key=" + k;
        }
        return u;
    }
}
