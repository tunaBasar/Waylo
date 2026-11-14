package com.waylo.observability;

import java.util.UUID;
import java.util.regex.Pattern;

/** X-Request-Id contract: a safe token of at most 64 chars [A-Za-z0-9-]; anything else is replaced by a fresh id. */
public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private RequestId() {
    }

    /** Returns the value when it is a safe token, otherwise null. */
    public static String sanitize(String value) {
        return value != null && SAFE.matcher(value).matches() ? value : null;
    }

    /** The given value when safe, otherwise a new random id. */
    public static String sanitizeOrGenerate(String value) {
        String safe = sanitize(value);
        return safe != null ? safe : UUID.randomUUID().toString();
    }
}
