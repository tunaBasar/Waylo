package com.waylo.authservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.RecordComponent;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, Object> {

    private String passwordField;
    private String emailField;

    @Override
    public void initialize(StrongPassword a) {
        this.passwordField = a.passwordField();
        this.emailField = a.emailField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return true; // @NotBlank reports missing values
        }
        if (value instanceof String s) {
            return PasswordPolicy.isValid(s);
        }
        // type level: only the password == email check (field level reports the other rules)
        String password = read(value, passwordField);
        String email = read(value, emailField);
        if (password == null || email == null) {
            return true;
        }
        return !password.equalsIgnoreCase(email.trim());
    }

    private static String read(Object record, String name) {
        try {
            for (RecordComponent c : record.getClass().getRecordComponents()) {
                if (c.getName().equals(name)) {
                    return (String) c.getAccessor().invoke(record);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return null;
    }
}
