package com.waylo.pricingservice.controller;

import com.waylo.pricingservice.domain.PricingType;
import com.waylo.pricingservice.dto.ApiResponse;
import com.waylo.pricingservice.dto.PricingRuleRequest;
import com.waylo.pricingservice.dto.PricingRuleResponse;
import com.waylo.pricingservice.service.PricingRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingRuleControllerTest {

    @Mock
    private PricingRuleService pricingRuleService;
    @InjectMocks
    private PricingRuleController controller;

    private final UUID ruleId = UUID.randomUUID();

    private PricingRuleResponse buildResponse() {
        return new PricingRuleResponse(ruleId, "Standard Flight Fare", PricingType.FLIGHT,
                new BigDecimal("150.00"), new BigDecimal("10.00"), "TRY", true, "desc",
                Instant.now(), Instant.now());
    }

    @Test
    void create_returns201() {
        PricingRuleRequest request = new PricingRuleRequest("Standard Flight Fare", PricingType.FLIGHT,
                new BigDecimal("150.00"), new BigDecimal("10.00"), "TRY", "desc");
        PricingRuleResponse response = buildResponse();
        when(pricingRuleService.create(any(PricingRuleRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<PricingRuleResponse>> result = controller.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        PricingRuleResponse response = buildResponse();
        when(pricingRuleService.getById(ruleId)).thenReturn(response);

        ResponseEntity<ApiResponse<PricingRuleResponse>> result = controller.getById(ruleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAll_returns200() {
        when(pricingRuleService.getAll()).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<PricingRuleResponse>>> result = controller.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void getByType_returns200() {
        when(pricingRuleService.getByType(PricingType.FLIGHT)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<PricingRuleResponse>>> result = controller.getByType(PricingType.FLIGHT);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pricingRuleService).getByType(PricingType.FLIGHT);
    }

    @Test
    void getActiveByType_returns200() {
        when(pricingRuleService.getActiveByType(PricingType.HOTEL)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<PricingRuleResponse>>> result = controller.getActiveByType(PricingType.HOTEL);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pricingRuleService).getActiveByType(PricingType.HOTEL);
    }

    @Test
    void update_returns200() {
        PricingRuleRequest request = new PricingRuleRequest("Updated Fare", PricingType.FLIGHT,
                new BigDecimal("200.00"), new BigDecimal("5.00"), "USD", "updated");
        PricingRuleResponse response = buildResponse();
        when(pricingRuleService.update(ruleId, request)).thenReturn(response);

        ResponseEntity<ApiResponse<PricingRuleResponse>> result = controller.update(ruleId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void delete_returns204() {
        ResponseEntity<Void> result = controller.delete(ruleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(pricingRuleService).delete(ruleId);
    }

    @Test
    void deactivate_returns200() {
        PricingRuleResponse response = buildResponse();
        when(pricingRuleService.deactivate(ruleId)).thenReturn(response);

        ResponseEntity<ApiResponse<PricingRuleResponse>> result = controller.deactivate(ruleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void activate_returns200() {
        PricingRuleResponse response = buildResponse();
        when(pricingRuleService.activate(ruleId)).thenReturn(response);

        ResponseEntity<ApiResponse<PricingRuleResponse>> result = controller.activate(ruleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }
}
