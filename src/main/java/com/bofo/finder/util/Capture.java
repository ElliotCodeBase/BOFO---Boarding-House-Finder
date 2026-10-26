package com.bofo.finder.util;

import com.gluonhq.attach.pictures.PicturesService;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Camera / gallery capture via Gluon Attach Pictures + a tiny pure-Java PNG encoder (no ImageIO needed). */
public final class Capture {
    private Capture() {}

    /** Phone: opens the camera. PC (no Attach service, no webcam support in JavaFX): opens a file picker instead. */
    public static Optional<Image> camera() {
        try {
            Optional<PicturesService> svc = PicturesService.create();
            if (svc.isPresent()) return svc.get().takePhoto(false);
        } catch (Throwable t) { System.err.println("Camera failed: " + t); return Optional.empty(); }
        return pickFile("Choose a photo");
    }

    /** Phone: opens the gallery. PC: opens a file picker. */
    public static Optional<Image> gallery() {
        try {
            Optional<PicturesService> svc = PicturesService.create();
            if (svc.isPresent()) return svc.get().loadImageFromGallery();
        } catch (Throwable t) { System.err.println("Gallery failed: " + t); return Optional.empty(); }
        return pickFile("Choose a photo");
    }

    private static Optional<Image> pickFile(String title) {
        try {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle(title);
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.webp"));
            java.io.File f = fc.showOpenDialog(null);
            if (f == null) return Optional.empty();
            Image img = new Image(f.toURI().toString(), 1600, 1600, true, true);        // smooth, capped size to save memory
            return img.isError() || img.getWidth() < 1 ? Optional.empty() : Optional.of(img);
        } catch (Throwable t) { System.err.println("File picker failed: " + t); return Optional.empty(); }
    }

    public static byte[] png(Image img, int maxWidth) {
        try {
            int sw = (int) img.getWidth(), sh = (int) img.getHeight();
            double scale = sw > maxWidth ? (double) maxWidth / sw : 1.0;
            int w = Math.max(1, (int) (sw * scale)), h = Math.max(1, (int) (sh * scale));
            PixelReader pr = img.getPixelReader();
            ByteArrayOutputStream raw = new ByteArrayOutputStream(w * h * 3 + h);
            for (int y = 0; y < h; y++) {
                raw.write(0);
                for (int x = 0; x < w; x++) {
                    int p = pr.getArgb((int) (x / scale), (int) (y / scale));
                    raw.write((p >> 16) & 255); raw.write((p >> 8) & 255); raw.write(p & 255);
                }
            }
            ByteArrayOutputStream z = new ByteArrayOutputStream();
            try (DeflaterOutputStream d = new DeflaterOutputStream(z, new Deflater(6))) { d.write(raw.toByteArray()); }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream di = new DataOutputStream(ihdr);
            di.writeInt(w); di.writeInt(h); di.writeByte(8); di.writeByte(2); di.writeByte(0); di.writeByte(0); di.writeByte(0);
            chunk(out, "IHDR", ihdr.toByteArray()); chunk(out, "IDAT", z.toByteArray()); chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static void chunk(ByteArrayOutputStream o, String type, byte[] data) throws Exception {
        DataOutputStream d = new DataOutputStream(o);
        d.writeInt(data.length);
        byte[] t = type.getBytes("US-ASCII");
        d.write(t); d.write(data);
        CRC32 c = new CRC32(); c.update(t); c.update(data);
        d.writeInt((int) c.getValue());
    }
}
