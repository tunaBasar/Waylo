package com.waylo.hotelservice.media;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Tiny valid image builders (structure only, enough for the parsers). */
final class TestImages {

    private TestImages() { }

    static byte[] png(int w, int h, boolean withTextChunk) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        ihdr.writeBytes(be(w));
        ihdr.writeBytes(be(h));
        ihdr.writeBytes(new byte[] {8, 2, 0, 0, 0});
        chunk(o, "IHDR", ihdr.toByteArray());
        if (withTextChunk) chunk(o, "tEXt", "Comment\0secret-gps".getBytes(StandardCharsets.ISO_8859_1));
        Deflater d = new Deflater();
        d.setInput(new byte[] {0, 1, 2, 3});
        d.finish();
        byte[] buf = new byte[64];
        int n = d.deflate(buf);
        chunk(o, "IDAT", java.util.Arrays.copyOf(buf, n));
        chunk(o, "IEND", new byte[0]);
        return o.toByteArray();
    }

    static byte[] jpeg(int w, int h, boolean withExif) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8});
        if (withExif) {
            byte[] exif = "Exif\0\0GPS-DATA".getBytes(StandardCharsets.ISO_8859_1);
            o.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xE1, 0, (byte) (exif.length + 2)});
            o.writeBytes(exif);
        }
        // SOF0: len 11, precision 8, height, width, 1 component
        o.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xC0, 0, 11, 8, (byte) (h >> 8), (byte) h, (byte) (w >> 8), (byte) w, 1, 1, 0x11, 0});
        // SOS + fake entropy data + EOI
        o.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xDA, 0, 8, 1, 1, 0, 0, 63, 0, 1, 2, 3, (byte) 0xFF, (byte) 0xD9});
        return o.toByteArray();
    }

    /** Lossless WebP (VP8L) with optional EXIF chunk inside a VP8X container. */
    static byte[] webp(int w, int h, boolean withExif) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (withExif) {
            byte[] vp8x = new byte[10];
            vp8x[0] = 0x08; // EXIF flag
            put24(vp8x, 4, w - 1);
            put24(vp8x, 7, h - 1);
            riffChunk(body, "VP8X", vp8x);
        }
        long bits = (w - 1) | ((long) (h - 1) << 14);
        byte[] vp8l = new byte[] {0x2F, (byte) bits, (byte) (bits >> 8), (byte) (bits >> 16), (byte) (bits >> 24), 0, 0, 0};
        riffChunk(body, "VP8L", vp8l);
        if (withExif) riffChunk(body, "EXIF", "Exif-GPS".getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes("RIFF".getBytes(StandardCharsets.ISO_8859_1));
        o.writeBytes(le(body.size() + 4));
        o.writeBytes("WEBP".getBytes(StandardCharsets.ISO_8859_1));
        o.writeBytes(body.toByteArray());
        return o.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream o, String type, byte[] data) {
        o.writeBytes(be(data.length));
        byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
        o.writeBytes(t);
        o.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        o.writeBytes(be((int) crc.getValue()));
    }

    private static void riffChunk(ByteArrayOutputStream o, String type, byte[] data) {
        o.writeBytes(type.getBytes(StandardCharsets.ISO_8859_1));
        o.writeBytes(le(data.length));
        o.writeBytes(data);
        if ((data.length & 1) == 1) o.write(0);
    }

    private static void put24(byte[] a, int o, int v) {
        a[o] = (byte) v;
        a[o + 1] = (byte) (v >> 8);
        a[o + 2] = (byte) (v >> 16);
    }

    private static byte[] be(int v) {
        return new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    private static byte[] le(int v) {
        return new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    static boolean contains(byte[] hay, String needle) {
        return new String(hay, StandardCharsets.ISO_8859_1).contains(needle);
    }
}
