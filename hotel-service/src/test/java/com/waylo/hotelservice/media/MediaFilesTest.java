package com.waylo.hotelservice.media;

import com.waylo.hotelservice.exception.PublicationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaFilesTest {

    private static void assertRejected(byte[] data, int status, String code) {
        assertThatThrownBy(() -> MediaFiles.inspect(data))
                .isInstanceOfSatisfying(PublicationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getCode()).isEqualTo(code);
                });
    }

    @Test
    void detectsAllThreeTypesByMagicBytes() {
        assertThat(MediaFiles.detectContentType(TestImages.jpeg(800, 600, false))).isEqualTo("image/jpeg");
        assertThat(MediaFiles.detectContentType(TestImages.png(800, 600, false))).isEqualTo("image/png");
        assertThat(MediaFiles.detectContentType(TestImages.webp(800, 600, false))).isEqualTo("image/webp");
    }

    @Test
    void fakeExtensionOrPdfBytesAreRejectedBecauseOnlyBytesCount() {
        // a PDF (or script) renamed to .jpg is just bytes here: no magic, no entry
        assertRejected("%PDF-1.7 pretend".getBytes(), 415, "UNSUPPORTED_FILE_TYPE");
        assertRejected("<?php echo 1; ?>".getBytes(), 415, "UNSUPPORTED_FILE_TYPE");
        assertRejected("GIF89a....".getBytes(), 415, "UNSUPPORTED_FILE_TYPE");
        // RIFF but not WEBP (e.g. WAV)
        assertRejected("RIFF\u0010\0\0\0WAVEfmt ........".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), 415, "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void emptyAndOversizedFilesAreRejected() {
        assertRejected(new byte[0], 400, "EMPTY_FILE");
        byte[] big = new byte[(int) MediaFiles.MAX_BYTES + 1];
        assertRejected(big, 413, "FILE_TOO_LARGE");
    }

    @Test
    void exactlyFiveMegabytesIsNotRejectedForSize() {
        byte[] exact = new byte[(int) MediaFiles.MAX_BYTES];
        // passes the size gate, then fails on type (zeros), proving the limit is inclusive
        assertRejected(exact, 415, "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void minimumAndMaximumDimensions() {
        assertThat(MediaFiles.inspect(TestImages.png(800, 600, false)).width()).isEqualTo(800);
        assertRejected(TestImages.png(799, 600, false), 400, "IMAGE_TOO_SMALL");
        assertRejected(TestImages.png(800, 599, false), 400, "IMAGE_TOO_SMALL");
        assertRejected(TestImages.jpeg(640, 480, false), 400, "IMAGE_TOO_SMALL");
        assertRejected(TestImages.webp(100, 100, false), 400, "IMAGE_TOO_SMALL");
        assertThat(MediaFiles.inspect(TestImages.png(8000, 8000, false)).height()).isEqualTo(8000);
        assertRejected(TestImages.png(8001, 800, false), 400, "IMAGE_TOO_LARGE");
        assertRejected(TestImages.jpeg(800, 8001, false), 400, "IMAGE_TOO_LARGE");
    }

    @Test
    void readsRealDimensionsFromEveryFormat() {
        MediaFiles.Inspected j = MediaFiles.inspect(TestImages.jpeg(1024, 768, false));
        assertThat(j.contentType()).isEqualTo("image/jpeg");
        assertThat(j.width()).isEqualTo(1024);
        assertThat(j.height()).isEqualTo(768);
        MediaFiles.Inspected p = MediaFiles.inspect(TestImages.png(1200, 900, false));
        assertThat(p.width()).isEqualTo(1200);
        assertThat(p.height()).isEqualTo(900);
        MediaFiles.Inspected w = MediaFiles.inspect(TestImages.webp(1600, 1200, false));
        assertThat(w.contentType()).isEqualTo("image/webp");
        assertThat(w.width()).isEqualTo(1600);
        assertThat(w.height()).isEqualTo(1200);
    }

    @Test
    void stripsMetadataFromStoredBytes() {
        byte[] jpeg = TestImages.jpeg(900, 700, true);
        assertThat(TestImages.contains(jpeg, "GPS-DATA")).isTrue();
        assertThat(TestImages.contains(MediaFiles.inspect(jpeg).cleaned(), "GPS-DATA")).isFalse();

        byte[] png = TestImages.png(900, 700, true);
        assertThat(TestImages.contains(png, "secret-gps")).isTrue();
        assertThat(TestImages.contains(MediaFiles.inspect(png).cleaned(), "secret-gps")).isFalse();

        byte[] webp = TestImages.webp(900, 700, true);
        assertThat(TestImages.contains(webp, "Exif-GPS")).isTrue();
        MediaFiles.Inspected cleaned = MediaFiles.inspect(webp);
        assertThat(TestImages.contains(cleaned.cleaned(), "Exif-GPS")).isFalse();
        // cleaned output is still a valid image of the same size
        MediaFiles.Inspected again = MediaFiles.inspect(cleaned.cleaned());
        assertThat(again.width()).isEqualTo(900);
        assertThat(again.height()).isEqualTo(700);
    }

    @Test
    void truncatedAndCorruptFilesAreRejectedNotCrashed() {
        byte[] png = TestImages.png(900, 700, false);
        assertRejected(java.util.Arrays.copyOf(png, 20), 400, "CORRUPT_IMAGE");
        assertRejected(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 400, "CORRUPT_IMAGE");
        byte[] webp = TestImages.webp(900, 700, false);
        assertRejected(java.util.Arrays.copyOf(webp, 14), 400, "CORRUPT_IMAGE");
    }
}
