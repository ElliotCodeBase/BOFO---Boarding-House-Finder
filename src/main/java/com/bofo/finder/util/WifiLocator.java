package com.bofo.finder.util;

import com.bofo.finder.config.AppConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PC location the way Google Maps / Chrome do it: scan the Wi-Fi networks around the PC (Windows {@code netsh wlan}),
 * send their hardware IDs and signal strength to a Wi-Fi location service and get back where those routers are.
 * Typically 20-200 m, much closer than Windows' own guess (often just the internet address).
 * <p>Service: Google Geolocation API when {@code geolocation.key} is set in config.local.properties, otherwise the
 * free open beacondb.net service (same request format, no key). Needs at least 2 networks in range.
 */
public final class WifiLocator {
    private WifiLocator() {}
    public record Fix(double lat, double lng, double accuracy) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Pattern BSSID = Pattern.compile("^\\s*BSSID\\s+\\d+\\s*:\\s*([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})");
    private static final Pattern SIGNAL = Pattern.compile("^\\s*Signal\\s*:\\s*(\\d+)\\s*%");
    private static final Pattern CHANNEL = Pattern.compile("^\\s*Channel\\s*:\\s*(\\d+)");

    /** @return the Wi-Fi based position, or null (not Windows, no Wi-Fi, fewer than 2 networks, offline, service does not know them). */
    public static Fix locate() {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
            JSONArray aps = scan();
            if (aps.length() < 2) return null;
            String key = AppConfig.get("geolocation.key").trim();
            String url = key.isEmpty() ? "https://api.beacondb.net/v1/geolocate"
                    : "https://www.googleapis.com/geolocation/v1/geolocate?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            JSONObject body = new JSONObject().put("considerIp", false).put("wifiAccessPoints", aps);
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return null;
            JSONObject o = new JSONObject(r.body());
            JSONObject loc = o.optJSONObject("location");
            if (loc == null) return null;
            double acc = o.optDouble("accuracy", 9999);
            if (acc > 5000) return null;                       // too vague (an IP-style guess): let the caller use something better
            return new Fix(loc.getDouble("lat"), loc.getDouble("lng"), acc);
        } catch (Throwable any) { return null; }
    }

    /** Reads the cached Wi-Fi scan of Windows: one entry per access point (up to 30, strongest first as Windows lists them). */
    private static JSONArray scan() throws Exception {
        Process p = new ProcessBuilder("netsh", "wlan", "show", "networks", "mode=bssid").redirectErrorStream(true).start();
        List<JSONObject> out = new ArrayList<>();
        JSONObject cur = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = BSSID.matcher(line);
                if (m.find()) { if (cur != null) out.add(cur); cur = new JSONObject().put("macAddress", m.group(1).toLowerCase()); continue; }
                if (cur == null) continue;
                m = SIGNAL.matcher(line);
                if (m.find()) { cur.put("signalStrength", Integer.parseInt(m.group(1)) / 2 - 100); continue; }   // % -> dBm
                m = CHANNEL.matcher(line);
                if (m.find()) cur.put("channel", Integer.parseInt(m.group(1)));
            }
        }
        if (cur != null) out.add(cur);
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        JSONArray arr = new JSONArray();
        for (int i = 0; i < out.size() && i < 30; i++) arr.put(out.get(i));
        return arr;
    }
}
