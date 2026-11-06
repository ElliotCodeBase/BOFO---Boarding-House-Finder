package com.bofo.admin;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Writes a CSV into the app cache (exports/) and opens the share sheet through the app's FileProvider. Old files are deleted before each export and at app start. */
final class CsvShare {
    private CsvShare() {}

    static void clean(Context c) {
        File[] f = new File(c.getCacheDir(), "exports").listFiles();
        if (f != null) for (File x : f) { //noinspection ResultOfMethodCallIgnored
            x.delete(); }
    }

    static void share(MainActivity a, String fileName, String content) throws java.io.IOException {
        clean(a);
        File dir = new File(a.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("no folder");
        File file = new File(dir, fileName);
        try (OutputStream o = new FileOutputStream(file)) { o.write(content.getBytes(StandardCharsets.UTF_8)); }
        Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".files", file);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i, "Share CSV"));
    }
}
