package com.waylo.authservice.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void acceptsValid() {
        assertTrue(PasswordPolicy.isValid("abcdefgh12"));
        assertTrue(PasswordPolicy.isValid("a".repeat(127) + "1"));
        assertTrue(PasswordPolicy.isValid("Sifre-cok-guclu9"));
    }

    @Test
    void rejectsLengthViolations() {
        assertFalse(PasswordPolicy.isValid("abcdefg12"));
        assertFalse(PasswordPolicy.isValid("a".repeat(128) + "1"));
        assertFalse(PasswordPolicy.isValid(null));
    }

    @Test
    void requiresLetterAndDigit() {
        assertFalse(PasswordPolicy.isValid("abcdefghijk"));
        assertFalse(PasswordPolicy.isValid("12345678901"));
    }

    @Test
    void rejectsEmailEqualCaseInsensitive() {
        assertFalse(PasswordPolicy.isValid("User12345@x.io", "user12345@X.io"));
        assertTrue(PasswordPolicy.isValid("User12345@x.io", "other@x.io"));
    }
}
