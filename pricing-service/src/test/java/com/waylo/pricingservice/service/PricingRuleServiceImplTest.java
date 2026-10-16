package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.PricingRule;
import com.waylo.pricingservice.domain.PricingType;
import com.waylo.pricingservice.dto.PricingRuleMapper;
import com.waylo.pricingservice.dto.PricingRuleRequest;
import com.waylo.pricingservice.dto.PricingRuleResponse;
import com.waylo.pricingservice.exception.ResourceNotFoundException;
import com.waylo.pricingservice.repository.PricingRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingRuleServiceImplTest {

    @Mock
    private PricingRuleRepository repository;
    @Mock
    private PricingRuleMapper mapper;

    private PricingRuleServiceImpl service;

    private PricingRuleServiceImpl newService() {
        return new PricingRuleServiceImpl(repository, mapper);
    }

    private PricingRule buildRule(UUID id, PricingType type, boolean active) {
        PricingRule rule = new PricingRule();
        rule.setId(id);
        rule.setName("Standard Flight Fare");
        rule.setPricingType(type);
        rule.setBasePrice(new BigDecimal("150.00"));
        rule.setDiscountPercentage(new BigDecimal("10.00"));
        rule.setCurrency("TRY");
        rule.setActive(active);
        rule.setDescription("desc");
        return rule;
    }

    private PricingRuleRequest buildRequest() {
        return new PricingRuleRequest("Standard Flight Fare", PricingType.FLIGHT,
                new BigDecimal("150.00"), new BigDecimal("10.00"), "TRY", "desc");
    }

    // --- create ---

    @Test
    void create_mapsAndSaves() {
        service = newService();
        PricingRuleRequest request = buildRequest();
        PricingRule mapped = new PricingRule();
        mapped.setName(request.name());
        PricingRule saved = buildRule(UUID.randomUUID(), PricingType.FLIGHT, true);
        PricingRuleResponse response = mock(PricingRuleResponse.class);

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(repository.save(mapped)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        PricingRuleResponse result = service.create(request);

        assertThat(result).isEqualTo(response);
        verify(repository).save(mapped);
    }

    // --- getById ---

    @Test
    void getById_found_returnsMappedResponse() {
        service = newService();
        UUID id = UUID.randomUUID();
        PricingRule rule = buildRule(id, PricingType.FLIGHT, true);
        PricingRuleResponse response = mock(PricingRuleResponse.class);
        when(repository.findById(id)).thenReturn(Optional.of(rule));
        when(mapper.toResponse(rule)).thenReturn(response);

        PricingRuleResponse result = service.getById(id);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll ---

    @Test
    void getAll_mapsAllRules() {
        service = newService();
        PricingRule r1 = buildRule(UUID.randomUUID(), PricingType.FLIGHT, true);
        PricingRule r2 = buildRule(UUID.randomUUID(), PricingType.HOTEL, true);
        when(repository.findAll()).thenReturn(List.of(r1, r2));
        when(mapper.toResponse(any(PricingRule.class))).thenReturn(mock(PricingRuleResponse.class));

        List<PricingRuleResponse> result = service.getAll();

        assertThat(result).hasSize(2);
    }

    // --- getByType ---

    @Test
    void getByType_filtersCorrectly() {
        service = newService();
        PricingRule r = buildRule(UUID.randomUUID(), PricingType.CAR_RENTAL, true);
        when(repository.findByPricingType(PricingType.CAR_RENTAL)).thenReturn(List.of(r));
        when(mapper.toResponse(r)).thenReturn(mock(PricingRuleResponse.class));

        List<PricingRuleResponse> result = service.getByType(PricingType.CAR_RENTAL);

        assertThat(result).hasSize(1);
        verify(repository).findByPricingType(PricingType.CAR_RENTAL);
    }

    // --- getActiveByType ---

    @Test
    void getActiveByType_filtersCorrectly() {
        service = newService();
        PricingRule r = buildRule(UUID.randomUUID(), PricingType.PACKAGE_DISCOUNT, true);
        when(repository.findByPricingTypeAndActiveTrue(PricingType.PACKAGE_DISCOUNT)).thenReturn(List.of(r));
        when(mapper.toResponse(r)).thenReturn(mock(PricingRuleResponse.class));

        List<PricingRuleResponse> result = service.getActiveByType(PricingType.PACKAGE_DISCOUNT);

        assertThat(result).hasSize(1);
        verify(repository).findByPricingTypeAndActiveTrue(PricingType.PACKAGE_DISCOUNT);
    }

    // --- update ---

    @Test
    void update_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        PricingRuleRequest request = buildRequest();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any(PricingRule.class));
    }

    @Test
    void update_valid_callsUpdateEntityAndSave() {
        service = newService();
        UUID id = UUID.randomUUID();
        PricingRuleRequest request = buildRequest();
        PricingRule existing = buildRule(id, PricingType.FLIGHT, true);
        PricingRuleResponse response = mock(PricingRuleResponse.class);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(response);

        PricingRuleResponse result = service.update(id, request);

        assertThat(result).isEqualTo(response);
        verify(mapper).updateEntity(request, existing);
        verify(repository).save(existing);
    }

    // --- delete ---

    @Test
    void delete_notFound_throwsAndNeverDeletes() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).deleteById(any(UUID.class));
    }

    @Test
    void delete_found_deletes() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(repository).deleteById(id);
    }

    // --- deactivate ---

    @Test
    void deactivate_setsActiveFalse() {
        service = newService();
        UUID id = UUID.randomUUID();
        PricingRule rule = buildRule(id, PricingType.FLIGHT, true);
        PricingRuleResponse response = mock(PricingRuleResponse.class);

        when(repository.findById(id)).thenReturn(Optional.of(rule));
        when(repository.save(rule)).thenReturn(rule);
        when(mapper.toResponse(rule)).thenReturn(response);

        PricingRuleResponse result = service.deactivate(id);

        assertThat(result).isEqualTo(response);
        assertThat(rule.isActive()).isFalse();

        ArgumentCaptor<PricingRule> captor = ArgumentCaptor.forClass(PricingRule.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    // --- activate ---

    @Test
    void activate_setsActiveTrue() {
        service = newService();
        UUID id = UUID.randomUUID();
        PricingRule rule = buildRule(id, PricingType.FLIGHT, false);
        PricingRuleResponse response = mock(PricingRuleResponse.class);

        when(repository.findById(id)).thenReturn(Optional.of(rule));
        when(repository.save(rule)).thenReturn(rule);
        when(mapper.toResponse(rule)).thenReturn(response);

        PricingRuleResponse result = service.activate(id);

        assertThat(result).isEqualTo(response);
        assertThat(rule.isActive()).isTrue();

        ArgumentCaptor<PricingRule> captor = ArgumentCaptor.forClass(PricingRule.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }
}
