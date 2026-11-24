package com.waylo.paymentservice.service;

/** The only code that formats an IBAN for display: never shows check digits or the middle of the number. */
public final class IbanMask {

    private static final String PREFIX = "TR** **** **** **** **** **";

    private IbanMask() {}

    /** last4 is the last four characters of the IBAN, only its last two are shown. */
    public static String mask(String last4) {
        if (last4 == null || last4.length() != 4) return PREFIX + "**";
        return PREFIX + last4.substring(2);
    }

    public static String last4(String normalizedIban) {
        return normalizedIban.substring(normalizedIban.length() - 4);
    }
}
