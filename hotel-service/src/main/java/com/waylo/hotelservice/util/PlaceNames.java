package com.waylo.hotelservice.util;

import java.text.Normalizer;
import java.util.Locale;

/** Case, Turkish-letter and diacritic insensitive place name comparison. */
public final class PlaceNames {

    private PlaceNames() {
    }

    /**
     * Returns a search key: trimmed, Turkish letters folded to ASCII, lower-cased (Locale.ROOT)
     * and other diacritics stripped. Null or blank input returns null (meaning "no filter").
     */
    public static String normalizeForSearch(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            switch (c) {
                case '\u0130', '\u0131' -> sb.append('i');
                case '\u015E', '\u015F' -> sb.append('s');
                case '\u011E', '\u011F' -> sb.append('g');
                case '\u00DC', '\u00FC' -> sb.append('u');
                case '\u00D6', '\u00F6' -> sb.append('o');
                case '\u00C7', '\u00E7' -> sb.append('c');
                default -> sb.append(c);
            }
        }
        String lower = sb.toString().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** True when query is blank (no filter) or equals the stored value after normalization. */
    public static boolean matches(String stored, String query) {
        String q = normalizeForSearch(query);
        if (q == null) {
            return true;
        }
        return q.equals(normalizeForSearch(stored));
    }
}
