package com.bofo.finder.service;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Persists the Supabase session ENCRYPTED (AES-256-GCM) in the app's private files folder. The key is created inside the
 * Android Keystore and never leaves it (the desktop version kept the key in a sibling file). Same API as before:
 * save / load / clear. Uninstalling the app (or a lost key) simply means "signed out".
 */
public final class SessionStore {
    private static final String KEY_ALIAS = "bofo_session_key";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final int IV_BYTES = 12;

    private final File dataFile;

    public SessionStore(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "session");
        dir.mkdirs();
        dataFile = new File(dir, "s.bin");
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return (SecretKey) ks.getKey(KEY_ALIAS, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    public synchronized void save(JSONObject session) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());                       // the Keystore picks a fresh random IV
            byte[] iv = c.getIV();
            byte[] enc = c.doFinal(session.toString().getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            File tmp = new File(dataFile.getPath() + ".tmp");
            try (OutputStream o = new FileOutputStream(tmp)) { o.write(out); }
            if (!tmp.renameTo(dataFile)) { dataFile.delete(); tmp.renameTo(dataFile); }
        } catch (Exception e) { throw new IllegalStateException("Cannot save session", e); }
    }

    public synchronized Optional<JSONObject> load() {
        try {
            if (!dataFile.exists()) return Optional.empty();
            byte[] all;
            try (InputStream in = new FileInputStream(dataFile)) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[4096]; int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                all = buf.toByteArray();
            }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, IV_BYTES));
            return Optional.of(new JSONObject(new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8)));
        } catch (Exception e) { clear(); return Optional.empty(); }
    }

    public synchronized void clear() { dataFile.delete(); }
}
