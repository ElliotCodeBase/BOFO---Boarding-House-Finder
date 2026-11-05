package com.bofo.finder.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** v2.8.0: writes a CSV into the app cache (exports/, overwritten each time) and opens the share sheet through the existing FileProvider. */
final class CsvShare {
    private CsvShare() {}

    static void share(Fragment f, String fileName, String content) {
        if (!f.isAdded()) return;
        try {
            Context c = f.requireContext();
            File dir = new File(c.getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("no folder");
            File file = new File(dir, fileName);
            try (OutputStream o = new FileOutputStream(file)) { o.write(content.getBytes(StandardCharsets.UTF_8)); }
            Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".files", file);
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            f.startActivity(Intent.createChooser(i, f.getString(R.string.export_share)));
        } catch (Exception e) {
            ListArea.toast(f, f.getString(R.string.export_failed));
        }
    }
}
