package com.bofo.finder.service;

import com.gluonhq.attach.storage.StorageService;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * Persists the Supabase session ENCRYPTED (AES-256-GCM) in the app's private sandbox.
 * The AES key lives in a separate private file. For hardware-backed protection, swap
 * {@link #loadOrCreateKey()} for an Android Keystore-wrapped key (needs a small native
 * Attach service) - the rest of the app only depends on save/load/clear.
 */
public final class SessionStore {
    private final File dir;
    private final File dataFile, keyFile;

    public SessionStore() {
        File d = StorageService.create().flatMap(StorageService::getPrivateStorage)
                .orElse(new File(System.getProperty("user.home"), ".bofo"));
        this.dir = new File(d, "session" + com.bofo.finder.util.DiskCache.slot());
        dir.mkdirs();
        dataFile = new File(dir, "s.bin");
        keyFile = new File(dir, "k.bin");
    }

    private byte[] loadOrCreateKey() throws Exception {
        if (keyFile.exists()) return Files.readAllBytes(keyFile.toPath());
        byte[] k = new byte[32]; new SecureRandom().nextBytes(k);
        Files.write(keyFile.toPath(), k);
        keyFile.setReadable(false, false); keyFile.setReadable(true, true);
        keyFile.setWritable(false, false); keyFile.setWritable(true, true);
        return k;
    }

    public void save(JSONObject session) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(loadOrCreateKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(session.toString().getBytes("UTF-8"));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            Files.write(dataFile.toPath(), out);
        } catch (Exception e) { throw new IllegalStateException("Cannot save session", e); }
    }

    public Optional<JSONObject> load() {
        try {
            if (!dataFile.exists() || !keyFile.exists()) return Optional.empty();
            byte[] all = Files.readAllBytes(dataFile.toPath());
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(loadOrCreateKey(), "AES"), new GCMParameterSpec(128, all, 0, 12));
            return Optional.of(new JSONObject(new String(c.doFinal(all, 12, all.length - 12), "UTF-8")));
        } catch (Exception e) { clear(); return Optional.empty(); }
    }

    public void clear() { dataFile.delete(); keyFile.delete(); }
}
