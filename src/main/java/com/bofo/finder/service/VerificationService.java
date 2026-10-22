package com.bofo.finder.service;

import com.bofo.finder.util.Validate;
import org.json.JSONArray;
import org.json.JSONObject;

/** Owner identity verification (manual review). Images go to the PRIVATE bucket 'id-verification'. */
public final class VerificationService {
    public static final long MAX_BYTES = 5 * 1024 * 1024;
    private final SupabaseClient api = SupabaseClient.get();

    public void submit(String ownerId, String idType, byte[] front, byte[] back, byte[] selfie, String consentVersion) throws Exception {
        for (byte[] b : new byte[][]{front, back, selfie})
            if (!Validate.imageOk(b, MAX_BYTES)) throw new IllegalArgumentException("Image missing, too large (max 5 MB) or not JPEG/PNG");
        long ts = System.currentTimeMillis();
        String base = ownerId + "/" + ts;
        api.upload("id-verification", base + "-front.png", front, "image/png");
        api.upload("id-verification", base + "-back.png", back, "image/png");
        api.upload("id-verification", base + "-selfie.png", selfie, "image/png");
        api.insert("user_agreements", new JSONObject().put("user_id", ownerId).put("document_type", "verification_consent").put("document_version", consentVersion));
        api.insert("owner_verifications", new JSONObject().put("owner_id", ownerId).put("id_type", idType)
                .put("id_front_url", base + "-front.png").put("id_back_url", base + "-back.png")
                .put("selfie_url", base + "-selfie.png").put("status", "pending"));
    }

    public JSONObject myLatest(String ownerId) throws Exception {
        JSONArray a = api.select("owner_verifications", "owner_id=eq." + ownerId + "&order=submitted_at.desc&limit=1&select=status,rejection_reason,submitted_at");
        return a.isEmpty() ? new JSONObject().put("status", "unverified") : a.getJSONObject(0);
    }

    /** Cheap blur check without ML: variance of a Laplacian over a downsampled luminance grid. */
    public static boolean looksSharp(javafx.scene.image.Image img) {
        if (img == null || img.getPixelReader() == null) return false;
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        if (w < 600 || h < 400) return false;
        var pr = img.getPixelReader();
        double sum = 0, sumSq = 0; int n = 0;
        for (int y = 2; y < h - 2; y += 4) for (int x = 2; x < w - 2; x += 4) {
            double c = lum(pr.getArgb(x, y)), l = lum(pr.getArgb(x - 1, y)), r = lum(pr.getArgb(x + 1, y)),
                   u = lum(pr.getArgb(x, y - 1)), d = lum(pr.getArgb(x, y + 1));
            double lap = l + r + u + d - 4 * c; sum += lap; sumSq += lap * lap; n++;
        }
        double mean = sum / n; return (sumSq / n - mean * mean) > 40; // tune on real devices
    }
    private static double lum(int argb) { return 0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255); }

    /* ---------- admin ---------- */
    public JSONArray pending() throws Exception {
        return api.select("owner_verifications", "status=eq.pending&order=submitted_at.asc&select=*");
    }
    public String signed(String path) throws Exception { return api.signedUrl("id-verification", path, 120); }
    public void decide(String verificationId, boolean approve, String reason) throws Exception {
        api.rpc("review_verification", new JSONObject().put("vid", verificationId).put("approve", approve).put("reason", reason == null ? "" : reason));
    }
}
