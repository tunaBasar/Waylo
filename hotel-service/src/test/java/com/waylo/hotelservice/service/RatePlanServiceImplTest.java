package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.MealPlan;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.RatePlanCreateRequest;
import com.waylo.hotelservice.dto.RatePlanResponse;
import com.waylo.hotelservice.dto.RatePlanUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.DuplicateResourceException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatePlanServiceImplTest {

    @Mock
    private RatePlanRepository ratePlanRepository;

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private RatePlanServiceImpl ratePlanService;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID ratePlanId = UUID.randomUUID();
    private final UUID otherHotelId = UUID.randomUUID();
    private Hotel testHotel;

    @BeforeEach
    void setUp() {
        testHotel = new Hotel();
        testHotel.setId(hotelId);
        testHotel.setName("Grand Hotel");
    }

    private RatePlan buildRatePlan(UUID id, String code) {
        RatePlan entity = new RatePlan();
        entity.setId(id);
        entity.setHotelId(hotelId);
        entity.setCode(code);
        entity.setName("Standard Rate");
        entity.setDescription("Standard room rate");
        entity.setMealPlan(MealPlan.BREAKFAST);
        entity.setCancellationPolicy(CancellationPolicy.FREE_CANCELLATION);
        entity.setFreeCancellationDaysBefore(7);
        entity.setStatus(CatalogStatus.ACTIVE);
        entity.setVersion(0L);
        return entity;
    }

    private RatePlanResponse buildResponse(UUID id) {
        return new RatePlanResponse(
                id, hotelId, "STD", "Standard Rate", "Standard room rate",
                MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7,
                CatalogStatus.ACTIVE, 0L, Instant.now(), Instant.now()
        );
    }

    // --- CREATE tests ---

    @Test
    void create_hotelMissing_throwsException() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7);
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> ratePlanService.create(hotelId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ratePlanRepository, never()).save(any());
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.existsByHotelIdAndCode(hotelId, "STD")).thenReturn(true);

        assertThatThrownBy(() -> ratePlanService.create(hotelId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_freeCancellationNoDays_throwsException() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, null);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.existsByHotelIdAndCode(hotelId, "STD")).thenReturn(false);

        assertThatThrownBy(() -> ratePlanService.create(hotelId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("CANCELLATION_RULE");
    }

    @Test
    void create_nonRefundableWithDays_throwsException() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.NON_REFUNDABLE, 5);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.existsByHotelIdAndCode(hotelId, "STD")).thenReturn(false);

        assertThatThrownBy(() -> ratePlanService.create(hotelId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("CANCELLATION_RULE");
    }

    @Test
    void create_success() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7);
        RatePlan saved = buildRatePlan(ratePlanId, "STD");
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.lockHotelById(hotelId)).thenReturn(Optional.of(testHotel));
        when(ratePlanRepository.countByHotelId(hotelId)).thenReturn(0L);
        when(ratePlanRepository.existsByHotelIdAndCode(hotelId, "STD")).thenReturn(false);
        when(ratePlanRepository.saveAndFlush(any())).thenReturn(saved);

        RatePlanResponse result = ratePlanService.create(hotelId, request);

        assertThat(result.id()).isEqualTo(ratePlanId);
        assertThat(result.code()).isEqualTo("STD");
        assertThat(result.name()).isEqualTo("Standard Rate");
        assertThat(result.mealPlan()).isEqualTo(MealPlan.BREAKFAST);
        assertThat(result.cancellationPolicy()).isEqualTo(CancellationPolicy.FREE_CANCELLATION);
        assertThat(result.freeCancellationDaysBefore()).isEqualTo(7);
        assertThat(result.status()).isEqualTo(CatalogStatus.ACTIVE);
        verify(ratePlanRepository).saveAndFlush(any());
    }

    @Test
    void create_limitReached_throwsException() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.countByHotelId(hotelId)).thenReturn(50L);

        assertThatThrownBy(() -> ratePlanService.create(hotelId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("LIMIT_REACHED");
    }

    // --- GET BY ID tests ---

    @Test
    void getById_found_returnsResponse() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));

        RatePlanResponse result = ratePlanService.getById(hotelId, ratePlanId, false);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(ratePlanId);
    }

    @Test
    void getById_notFound_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratePlanService.getById(hotelId, ratePlanId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_inactiveHidden_defaultReturns404() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setStatus(CatalogStatus.INACTIVE);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> ratePlanService.getById(hotelId, ratePlanId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_inactiveShown_includeInactiveTrue() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setStatus(CatalogStatus.INACTIVE);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));

        RatePlanResponse result = ratePlanService.getById(hotelId, ratePlanId, true);

        assertThat(result.status()).isEqualTo(CatalogStatus.INACTIVE);
    }

    @Test
    void getById_crossHotelSecurity_returns404() {
        when(hotelRepository.existsById(otherHotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, otherHotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratePlanService.getById(otherHotelId, ratePlanId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_hotelMissing_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> ratePlanService.getById(hotelId, ratePlanId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- GET BY HOTEL tests ---

    @Test
    void getByHotelId_activeOnly_default() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE))
                .thenReturn(List.of(entity));

        List<RatePlanResponse> result = ratePlanService.getByHotelId(hotelId, false);

        assertThat(result).hasSize(1);
    }

    @Test
    void getByHotelId_includeInactive_returnsAll() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        RatePlan active = buildRatePlan(ratePlanId, "STD");
        RatePlan inactive = buildRatePlan(UUID.randomUUID(), "ADV");
        inactive.setStatus(CatalogStatus.INACTIVE);
        when(ratePlanRepository.findByHotelIdOrderByNameAsc(hotelId))
                .thenReturn(List.of(active, inactive));

        List<RatePlanResponse> result = ratePlanService.getByHotelId(hotelId, true);

        assertThat(result).hasSize(2);
    }

    @Test
    void getByHotelId_hotelMissing_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> ratePlanService.getByHotelId(hotelId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- UPDATE tests ---

    @Test
    void update_notFound_throwsException() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest("New Name", null, null, null, null, null, null);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratePlanService.update(hotelId, ratePlanId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_success_partialUpdate_keepsUnspecified() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));
        when(ratePlanRepository.saveAndFlush(any())).thenReturn(entity);

        RatePlanUpdateRequest request = new RatePlanUpdateRequest("Updated Name", null, null, null, null, null, null);

        RatePlanResponse result = ratePlanService.update(hotelId, ratePlanId, request);

        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.code()).isEqualTo("STD");
        assertThat(result.mealPlan()).isEqualTo(MealPlan.BREAKFAST);
    }

    @Test
    void update_versionMismatch_throwsLockingFailure() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setVersion(0L);
        RatePlanUpdateRequest request = new RatePlanUpdateRequest("Name", null, null, null, null, CatalogStatus.INACTIVE, 99L);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> ratePlanService.update(hotelId, ratePlanId, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void update_changeToNonRefundable_clearsFreeDays() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setCancellationPolicy(CancellationPolicy.FREE_CANCELLATION);
        entity.setFreeCancellationDaysBefore(7);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));
        when(ratePlanRepository.saveAndFlush(any())).thenReturn(entity);

        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, null, null, CancellationPolicy.NON_REFUNDABLE, null, null, null);

        RatePlanResponse result = ratePlanService.update(hotelId, ratePlanId, request);

        assertThat(result.cancellationPolicy()).isEqualTo(CancellationPolicy.NON_REFUNDABLE);
        assertThat(result.freeCancellationDaysBefore()).isNull();
    }

    @Test
    void update_changeToFreeCancellation_requiresDays() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setCancellationPolicy(CancellationPolicy.NON_REFUNDABLE);
        entity.setFreeCancellationDaysBefore(null);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));

        // Change to FREE_CANCELLATION without providing days -> should fail
        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, null, null, CancellationPolicy.FREE_CANCELLATION, null, null, null);

        assertThatThrownBy(() -> ratePlanService.update(hotelId, ratePlanId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("CANCELLATION_RULE");
    }

    @Test
    void update_changeToFreeCancellation_withDays_success() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setCancellationPolicy(CancellationPolicy.NON_REFUNDABLE);
        entity.setFreeCancellationDaysBefore(null);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));
        when(ratePlanRepository.saveAndFlush(any())).thenReturn(entity);

        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, null, null, CancellationPolicy.FREE_CANCELLATION, 14, null, null);

        RatePlanResponse result = ratePlanService.update(hotelId, ratePlanId, request);

        assertThat(result.cancellationPolicy()).isEqualTo(CancellationPolicy.FREE_CANCELLATION);
        assertThat(result.freeCancellationDaysBefore()).isEqualTo(14);
    }

    @Test
    void update_expectedVersionMatch_success() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setVersion(1L);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));
        when(ratePlanRepository.saveAndFlush(any())).thenReturn(entity);

        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, null, null, null, null, null, 1L);

        RatePlanResponse result = ratePlanService.update(hotelId, ratePlanId, request);

        assertThat(result).isNotNull();
    }

    @Test
    void update_expectedVersion500_success() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setVersion(500L);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));
        when(ratePlanRepository.saveAndFlush(any())).thenReturn(entity);

        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, null, null, null, null, null, 500L);

        RatePlanResponse result = ratePlanService.update(hotelId, ratePlanId, request);

        assertThat(result).isNotNull();
    }

    @Test
    void update_nonRefundableAlready_nullDays_keepsNull() {
        RatePlan entity = buildRatePlan(ratePlanId, "STD");
        entity.setCancellationPolicy(CancellationPolicy.NON_REFUNDABLE);
        entity.setFreeCancellationDaysBefore(null);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)).thenReturn(Optional.of(entity));
        when(ratePlanRepository.saveAndFlush(any())).thenReturn(entity);

        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, null, null, CancellationPolicy.NON_REFUNDABLE, null, null, null);

        RatePlanResponse result = ratePlanService.update(hotelId, ratePlanId, request);

        assertThat(result.cancellationPolicy()).isEqualTo(CancellationPolicy.NON_REFUNDABLE);
        assertThat(result.freeCancellationDaysBefore()).isNull();
        verify(ratePlanRepository).saveAndFlush(any());
    }
}
