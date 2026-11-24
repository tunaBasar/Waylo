package com.waylo.paymentservice.service;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Turkish IBAN validation: format TR + 24 digits and the ISO 13616 mod-97 checksum. Pure, static. */
public final class IbanValidator {

    private static final Pattern TR_IBAN = Pattern.compile("^TR[0-9]{24}$");

    private IbanValidator() {}

    /** Strips spaces and hyphens and uppercases. Null stays null. */
    public static String normalize(String iban) {
        if (iban == null) return null;
        return iban.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    /** Returns the normalized IBAN or throws IllegalArgumentException (no IBAN content in the message). */
    public static String validate(String iban) {
        String n = normalize(iban);
        if (n == null || !TR_IBAN.matcher(n).matches()) throw new IllegalArgumentException("Invalid IBAN format");
        if (!mod97Ok(n)) throw new IllegalArgumentException("Invalid IBAN checksum");
        return n;
    }

    public static boolean isValid(String iban) {
        try {
            validate(iban);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean mod97Ok(String normalized) {
        String rearranged = normalized.substring(4) + normalized.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (c >= '0' && c <= '9') digits.append(c);
            else if (c >= 'A' && c <= 'Z') digits.append(c - 'A' + 10);
            else return false;
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).equals(BigInteger.ONE);
    }
}
