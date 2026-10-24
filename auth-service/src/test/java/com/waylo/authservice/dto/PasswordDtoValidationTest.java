package com.waylo.authservice.dto;

import com.waylo.authservice.validation.PasswordPolicy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordDtoValidationTest {

    private final Validator v = Validation.buildDefaultValidatorFactory().getValidator();

    private RegisterRequest reg(String email, String pw) {
        return new RegisterRequest("A", "B", email, pw);
    }

    @Test
    void registerAcceptsStrong() {
        assertTrue(v.validate(reg("a@b.com", "abcdefgh12")).isEmpty());
    }

    @Test
    void registerRejectsWeak() {
        var errs = v.validate(reg("a@b.com", "abc123"));
        assertEquals(1, errs.size());
        assertEquals(PasswordPolicy.MESSAGE, errs.iterator().next().getMessage());
        assertEquals(1, v.validate(reg("a@b.com", "abcdefghijk")).size());
    }

    @Test
    void registerRejectsPasswordEqualToEmail() {
        assertEquals(1, v.validate(reg("user12345@x.io", "USER12345@x.io")).size());
    }

    @Test
    void resetPasswordRules() {
        assertTrue(v.validate(new ResetPasswordRequest("t", "abcdefgh12")).isEmpty());
        assertEquals(1, v.validate(new ResetPasswordRequest("t", "short1")).size());
        assertEquals(1, v.validate(new ResetPasswordRequest("t", "a".repeat(129))).size());
    }

    @Test
    void changePasswordRules() {
        assertTrue(v.validate(new ChangePasswordRequest("old", "abcdefgh12")).isEmpty());
        assertEquals(1, v.validate(new ChangePasswordRequest("old", "1234567890")).size());
    }
}
