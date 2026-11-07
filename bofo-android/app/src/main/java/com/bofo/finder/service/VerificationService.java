package com.bofo.finder.service;

import android.graphics.Bitmap;

import com.bofo.finder.util.BanRules;
import com.bofo.finder.util.Validate;
import com.bofo.finder.util.VerificationRules;
import org.json.JSONArray;
import org.json.JSONObject;

/** Owner identity verification (manual review). Images go to the PRIVATE bucket 'id-verification'. */
public final class VerificationService {
    public static final long MAX_BYTES = 5 * 1024 * 1024;
    private final SupabaseClient api = SupabaseClient.get();

    /** v2.1.9: thrown when the ID number belongs to a banned identity. The screen shows exactly "You are banned." (no hint about any account). */
    public static final class BannedException extends Exception {
        public BannedException() { super(BanRules.BANNED_TEXT); }
    }

    /**
     * v2.1.9 order: (1) check the ID number with the server BEFORE anything is uploaded (banned -> BannedException, nothing is sent),
     * (2) upload the three photos, (3) insert the pending request, (4) send the number once more for the new row (server stores only a keyed hash;
     * if the ID turned banned in between the row is rejected and BannedException is thrown). The number is never written into owner_verifications by the app.
     */
    public void submit(String ownerId, String idType, String idNumber, byte[] front, byte[] back, byte[] selfie, String consentVersion) throws Exception {
        for (byte[] b : new byte[][]{front, back, selfie})
            if (!Validate.imageOk(b, MAX_BYTES)) throw new IllegalArgumentException("Image missing, too large (max 5 MB) or not JPEG/PNG");
        if (!BanRules.validNumber(idNumber)) throw new IllegalArgumentException("Type the ID number exactly as printed on your ID (4 to 30 letters or digits).");
        final String number = BanRules.forServer(idNumber);
        if (api.rpc("check_id_number", new JSONObject().put("id_type", idType).put("id_number", number)).optBoolean("banned", false)) throw new BannedException();
        long ts = System.currentTimeMillis();
        String base = ownerId + "/" + ts;                                   // the first folder must be the owner id (storage policy idv_insert)
        // Android sends JPEG, the desktop sent PNG: name and type follow the bytes (the bucket allows both)
        String f = base + "-front" + VerificationRules.ext(front), k = base + "-back" + VerificationRules.ext(back), s = base + "-selfie" + VerificationRules.ext(selfie);
        api.upload("id-verification", f, front, VerificationRules.mime(front));
        api.upload("id-verification", k, back, VerificationRules.mime(back));
        api.upload("id-verification", s, selfie, VerificationRules.mime(selfie));
        api.insert("user_agreements", new JSONObject().put("user_id", ownerId).put("document_type", "verification_consent").put("document_version", consentVersion));
        JSONArray row = api.insert("owner_verifications", new JSONObject().put("owner_id", ownerId).put("id_type", idType)
                .put("id_front_url", f).put("id_back_url", k).put("selfie_url", s).put("status", "pending"));
        String vid = row.length() == 0 ? "" : row.getJSONObject(0).optString("id", "");
        if (vid.isEmpty()) throw new IllegalStateException("The request was saved but its number could not be attached. Send it again.");
        if (api.rpc("submit_id_number", new JSONObject().put("vid", vid).put("id_type", idType).put("id_number", number)).optBoolean("banned", false)) throw new BannedException();
    }

    public JSONObject myLatest(String ownerId) throws Exception {
        JSONArray a = api.select("owner_verifications", "owner_id=eq." + ownerId + "&order=submitted_at.desc&limit=1&select=status,rejection_reason,submitted_at");
        return a.length() == 0 ? new JSONObject().put("status", "unverified") : a.getJSONObject(0);
    }

    /** Cheap blur check without ML: variance of a Laplacian over a downsampled luminance grid (ARGB_8888 Bitmap). */
    public static boolean looksSharp(Bitmap img) {
        if (img == null) return false;
        int w = img.getWidth(), h = img.getHeight();
        if (w < 600 || h < 400) return false;
        double sum = 0, sumSq = 0; int n = 0;
        for (int y = 2; y < h - 2; y += 4) for (int x = 2; x < w - 2; x += 4) {
            double c = lum(img.getPixel(x, y)), l = lum(img.getPixel(x - 1, y)), r = lum(img.getPixel(x + 1, y)),
                   u = lum(img.getPixel(x, y - 1)), d = lum(img.getPixel(x, y + 1));
            double lap = l + r + u + d - 4 * c; sum += lap; sumSq += lap * lap; n++;
        }
        double mean = sum / n; return (sumSq / n - mean * mean) > 40; // tune on real devices
    }
    private static double lum(int argb) { return 0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255); }
}
