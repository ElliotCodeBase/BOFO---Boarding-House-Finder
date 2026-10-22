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

    public static Optional<Image> camera() {
        return PicturesService.create().flatMap(s -> s.takePhoto(false));
    }
    public static Optional<Image> gallery() {
        return PicturesService.create().flatMap(PicturesService::loadImageFromGallery);
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
