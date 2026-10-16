package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CatalogStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for partial PATCH name validation in update DTOs.
 * - null name: VALID (no change)
 * - blank/empty name: INVALID
 * - <=120 chars: VALID
 */
@ExtendWith(MockitoExtension.class)
class CatalogPatchValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // --- RoomTypeUpdateRequest tests ---

    @Test
    void roomTypeUpdate_nullName_isValid() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(null, "Desc", 2, 1, 3, 45, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RoomTypeUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void roomTypeUpdate_blankName_isInvalid() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest("   ", "Desc", 2, 1, 3, 45, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RoomTypeUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("name"));
    }

    @Test
    void roomTypeUpdate_emptyName_isInvalid() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest("", "Desc", 2, 1, 3, 45, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RoomTypeUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void roomTypeUpdate_name120Chars_isValid() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest("a".repeat(120), "Desc", 2, 1, 3, 45, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RoomTypeUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void roomTypeUpdate_name121Chars_isInvalid() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest("a".repeat(121), "Desc", 2, 1, 3, 45, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RoomTypeUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    // --- RatePlanUpdateRequest tests ---

    @Test
    void ratePlanUpdate_nullName_isValid() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, "Desc", null, null, null, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RatePlanUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void ratePlanUpdate_blankName_isInvalid() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest("   ", "Desc", null, null, null, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RatePlanUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("name"));
    }

    @Test
    void ratePlanUpdate_emptyName_isInvalid() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest("", "Desc", null, null, null, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RatePlanUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void ratePlanUpdate_name120Chars_isValid() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest("a".repeat(120), "Desc", null, null, null, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RatePlanUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void ratePlanUpdate_name121Chars_isInvalid() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest("a".repeat(121), "Desc", null, null, null, CatalogStatus.ACTIVE, null);
        Set<ConstraintViolation<RatePlanUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }
}
