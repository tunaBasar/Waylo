package com.waylo.carrentalservice.rules;

import com.waylo.carrentalservice.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Upload validation: PDF, PNG and JPEG only, by magic bytes, up to 10 MB. */
public final class DocumentFiles {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private DocumentFiles() { }

    /** Returns the detected content type or throws 413 / 415 / 400. */
    public static String detectContentType(byte[] content) {
        if (content.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Uploaded file is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the 10 MB limit");
        }
        if (startsWith(content, 0x25, 0x50, 0x44, 0x46)) return "application/pdf";
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47)) return "image/png";
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                "Only PDF, JPEG and PNG files are accepted");
    }

    /** Strips path parts and control characters from the client supplied name. */
    public static String sanitizeName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").trim();
        if (name.isEmpty()) name = "document";
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static boolean startsWith(byte[] c, int... magic) {
        if (c.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((c[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
