package com.bofo.finder.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Turns a camera / gallery picture into a small square JPEG for the avatar. Blocking: use Async. */
public final class Images {
    private Images() {}
    public static final int AVATAR_PX = 512;

    /** Listing photo: keeps the shape, longest side at most 1600 px, rotated by EXIF, JPEG 82. Blocking: use Async. */
    public static byte[] listingJpeg(ContentResolver cr, Uri uri) throws Exception {
        final int max = 1600;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IllegalArgumentException("That file is not a picture.");
        int sample = 1;
        while (o.outWidth / (sample * 2) >= max && o.outHeight / (sample * 2) >= max) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src;
        try (InputStream in = cr.openInputStream(uri)) { src = BitmapFactory.decodeStream(in, null, o); }
        if (src == null) throw new IllegalArgumentException("That picture could not be read.");
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = cr.openInputStream(uri)) {
            orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) { /* no EXIF */ }
        Matrix m = new Matrix();
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
        float k = Math.min(1f, (float) max / Math.max(src.getWidth(), src.getHeight()));
        m.postScale(k, k);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 82, bos);
        return bos.toByteArray();
    }

    public static byte[] avatarJpeg(ContentResolver cr, Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IllegalArgumentException("That file is not a picture.");
        int sample = 1;
        while (o.outWidth / (sample * 2) >= AVATAR_PX * 2 && o.outHeight / (sample * 2) >= AVATAR_PX * 2) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src;
        try (InputStream in = cr.openInputStream(uri)) { src = BitmapFactory.decodeStream(in, null, o); }
        if (src == null) throw new IllegalArgumentException("That picture could not be read.");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = cr.openInputStream(uri)) {
            orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) { /* no EXIF: keep as is */ }
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
            default: break;
        }
        // centre square crop + rotation in one step, then scale down
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side, m, true);
        Bitmap out = side > AVATAR_PX ? Bitmap.createScaledBitmap(sq, AVATAR_PX, AVATAR_PX, true) : sq;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 85, bos);
        return bos.toByteArray();
    }
}
