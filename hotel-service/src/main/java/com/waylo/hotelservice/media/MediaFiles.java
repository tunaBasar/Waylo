package com.waylo.hotelservice.media;

import com.waylo.hotelservice.exception.PublicationException;

import java.io.ByteArrayOutputStream;
import java.util.Set;

/**
 * Upload validation for hotel photos (contract sec. 5). The type is detected from magic bytes only, the client file
 * name and content type are never read. JPEG, PNG and WebP are parsed structurally (bounds checked, no pixel decode)
 * to get the real dimensions, and metadata chunks (EXIF, XMP, text) are stripped from the stored bytes.
 */
public final class MediaFiles {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PER_HOTEL = 20;
    public static final int MIN_WIDTH = 800;
    public static final int MIN_HEIGHT = 600;
    public static final int MAX_SIDE = 8000;

    public record Inspected(String contentType, byte[] cleaned, int width, int height) { }

    private MediaFiles() { }

    public static Inspected inspect(byte[] c) {
        if (c == null || c.length == 0) {
            throw new PublicationException(400, "EMPTY_FILE", "Uploaded file is empty");
        }
        if (c.length > MAX_BYTES) {
            throw new PublicationException(413, "FILE_TOO_LARGE", "Photo exceeds the 5 MB limit");
        }
        String type = detectContentType(c);
        Inspected result;
        try {
            result = switch (type) {
                case "image/jpeg" -> jpeg(c);
                case "image/png" -> png(c);
                default -> webp(c);
            };
        } catch (IndexOutOfBoundsException e) {
            throw corrupt();
        }
        if (result.width() < MIN_WIDTH || result.height() < MIN_HEIGHT) {
            throw new PublicationException(400, "IMAGE_TOO_SMALL",
                    "Photo must be at least " + MIN_WIDTH + "x" + MIN_HEIGHT + " pixels");
        }
        if (result.width() > MAX_SIDE || result.height() > MAX_SIDE) {
            throw new PublicationException(400, "IMAGE_TOO_LARGE",
                    "Photo must be at most " + MAX_SIDE + "x" + MAX_SIDE + " pixels");
        }
        return result;
    }

    /** Magic bytes only. Throws 415 for anything that is not JPEG, PNG or WebP. */
    public static String detectContentType(byte[] c) {
        if (startsWith(c, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(c, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(c, 0, 'R', 'I', 'F', 'F') && startsWith(c, 8, 'W', 'E', 'B', 'P')) return "image/webp";
        throw new PublicationException(415, "UNSUPPORTED_FILE_TYPE", "Only JPEG, PNG and WebP photos are accepted");
    }

    private static PublicationException corrupt() {
        return new PublicationException(400, "CORRUPT_IMAGE", "The file is not a valid image");
    }

    // ---- JPEG: drop APP1 (EXIF/XMP), APP13 (IPTC) and COM segments

    private static Inspected jpeg(byte[] c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(c.length);
        out.write(0xFF);
        out.write(0xD8);
        int i = 2;
        int w = -1, h = -1;
        boolean sos = false;
        while (i + 4 <= c.length) {
            if ((c[i] & 0xFF) != 0xFF) throw corrupt();
            int marker = c[i + 1] & 0xFF;
            if (marker == 0xFF) { i++; continue; }
            int len = ((c[i + 2] & 0xFF) << 8) | (c[i + 3] & 0xFF);
            if (len < 2 || i + 2 + len > c.length) throw corrupt();
            boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) {
                h = ((c[i + 5] & 0xFF) << 8) | (c[i + 6] & 0xFF);
                w = ((c[i + 7] & 0xFF) << 8) | (c[i + 8] & 0xFF);
            }
            if (marker == 0xDA) {
                out.write(c, i, c.length - i);
                sos = true;
                break;
            }
            if (marker != 0xE1 && marker != 0xED && marker != 0xFE) {
                out.write(c, i, 2 + len);
            }
            i += 2 + len;
        }
        if (!sos || w <= 0 || h <= 0) throw corrupt();
        return new Inspected("image/jpeg", out.toByteArray(), w, h);
    }

    // ---- PNG: drop eXIf, tEXt, zTXt, iTXt, tIME chunks

    private static final Set<String> PNG_DROP = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private static Inspected png(byte[] c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(c.length);
        out.write(c, 0, 8);
        int i = 8;
        int w = -1, h = -1;
        boolean iend = false;
        boolean first = true;
        while (i + 12 <= c.length) {
            long len = be32(c, i);
            String type = new String(c, i + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (len > c.length || i + 12L + len > c.length) throw corrupt();
            int total = (int) len + 12;
            if (first) {
                if (!type.equals("IHDR") || len != 13) throw corrupt();
                w = (int) be32(c, i + 8);
                h = (int) be32(c, i + 12);
                first = false;
            }
            if (!PNG_DROP.contains(type)) out.write(c, i, total);
            i += total;
            if (type.equals("IEND")) { iend = true; break; }
        }
        if (!iend || w <= 0 || h <= 0) throw corrupt();
        return new Inspected("image/png", out.toByteArray(), w, h);
    }

    // ---- WebP (RIFF): drop EXIF and XMP chunks, clear their VP8X flags, rewrite the RIFF size

    private static Inspected webp(byte[] c) {
        long riff = le32(c, 4);
        if (riff + 8 > c.length || riff < 12) throw corrupt();
        int end = (int) riff + 8;
        ByteArrayOutputStream body = new ByteArrayOutputStream(c.length);
        int i = 12;
        int w = -1, h = -1;
        while (i + 8 <= end) {
            String type = new String(c, i, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            long len = le32(c, i + 4);
            long padded = len + (len & 1);
            if (i + 8 + len > end) throw corrupt();
            int d = i + 8;
            switch (type) {
                case "VP8X" -> {
                    if (len < 10) throw corrupt();
                    if ((c[d] & 0x02) != 0) {
                        throw new PublicationException(415, "UNSUPPORTED_FILE_TYPE", "Animated WebP is not accepted");
                    }
                    w = 1 + le24(c, d + 4);
                    h = 1 + le24(c, d + 7);
                }
                case "VP8 " -> {
                    if (len < 10 || (c[d + 3] & 0xFF) != 0x9D || (c[d + 4] & 0xFF) != 0x01 || (c[d + 5] & 0xFF) != 0x2A) {
                        throw corrupt();
                    }
                    if (w < 0) {
                        w = (((c[d + 7] & 0x3F) << 8) | (c[d + 6] & 0xFF));
                        h = (((c[d + 9] & 0x3F) << 8) | (c[d + 8] & 0xFF));
                    }
                }
                case "VP8L" -> {
                    if (len < 5 || (c[d] & 0xFF) != 0x2F) throw corrupt();
                    if (w < 0) {
                        long bits = le32(c, d + 1);
                        w = (int) (bits & 0x3FFF) + 1;
                        h = (int) ((bits >> 14) & 0x3FFF) + 1;
                    }
                }
                default -> { }
            }
            if (!type.equals("EXIF") && !type.equals("XMP ")) {
                int chunkLen = (int) Math.min(8 + padded, end - i);
                byte[] chunk = new byte[chunkLen];
                System.arraycopy(c, i, chunk, 0, chunkLen);
                if (type.equals("VP8X")) chunk[8] = (byte) (chunk[8] & ~(0x08 | 0x04));
                body.write(chunk, 0, chunk.length);
            }
            i += 8 + (int) padded;
        }
        if (w <= 0 || h <= 0) throw corrupt();
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.size() + 12);
        out.write(new byte[] {'R', 'I', 'F', 'F'}, 0, 4);
        long size = body.size() + 4L;
        out.write((int) size & 0xFF);
        out.write((int) (size >> 8) & 0xFF);
        out.write((int) (size >> 16) & 0xFF);
        out.write((int) (size >> 24) & 0xFF);
        out.write(new byte[] {'W', 'E', 'B', 'P'}, 0, 4);
        out.write(body.toByteArray(), 0, body.size());
        return new Inspected("image/webp", out.toByteArray(), w, h);
    }

    private static long be32(byte[] c, int o) {
        return ((long) (c[o] & 0xFF) << 24) | ((c[o + 1] & 0xFF) << 16) | ((c[o + 2] & 0xFF) << 8) | (c[o + 3] & 0xFF);
    }

    private static long le32(byte[] c, int o) {
        return ((long) (c[o + 3] & 0xFF) << 24) | ((c[o + 2] & 0xFF) << 16) | ((c[o + 1] & 0xFF) << 8) | (c[o] & 0xFF);
    }

    private static int le24(byte[] c, int o) {
        return ((c[o + 2] & 0xFF) << 16) | ((c[o + 1] & 0xFF) << 8) | (c[o] & 0xFF);
    }

    private static boolean startsWith(byte[] c, int offset, int... magic) {
        if (c.length < offset + magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((c[offset + i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
