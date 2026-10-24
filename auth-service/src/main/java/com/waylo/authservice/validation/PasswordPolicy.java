package com.waylo.authservice.validation;

/** Single password rule: length 10..128, at least one letter and one digit, not equal to the email. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;
    public static final String MESSAGE =
            "Sifre 10-128 karakter olmali, en az bir harf ve bir rakam icermeli ve e-posta ile ayni olmamali";

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        return isValid(password, null);
    }

    public static boolean isValid(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        if (!letter || !digit) {
            return false;
        }
        return email == null || !password.equalsIgnoreCase(email.trim());
    }
}
