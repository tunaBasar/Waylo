package com.waylo.busservice.rules;

import com.waylo.busservice.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFilesTest {

    @Test
    void acceptsPdfPngJpegByMagicBytes() {
        assertThat(DocumentFiles.detectContentType("%PDF-1.4 x".getBytes())).isEqualTo("application/pdf");
        assertThat(DocumentFiles.detectContentType(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0})).isEqualTo("image/png");
        assertThat(DocumentFiles.detectContentType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsOtherContentRegardlessOfName() {
        assertThatThrownBy(() -> DocumentFiles.detectContentType("<html>evil.pdf</html>".getBytes()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsEmptyAndOversized() {
        assertThatThrownBy(() -> DocumentFiles.detectContentType(new byte[0]))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        byte[] big = new byte[(int) DocumentFiles.MAX_BYTES + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        assertThatThrownBy(() -> DocumentFiles.detectContentType(big))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void sanitizeStripsPathsAndControlChars() {
        assertThat(DocumentFiles.sanitizeName("../../etc/pass\"wd.pdf")).isEqualTo("pass_wd.pdf");
        assertThat(DocumentFiles.sanitizeName(null)).isEqualTo("document");
    }
}
