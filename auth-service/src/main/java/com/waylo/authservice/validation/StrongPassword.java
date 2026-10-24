package com.waylo.authservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field level: length/letter/digit rule. Type level: set {@link #passwordField()} and
 * {@link #emailField()} to also reject a password equal to the email.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default PasswordPolicy.MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String passwordField() default "";
    String emailField() default "";
}
