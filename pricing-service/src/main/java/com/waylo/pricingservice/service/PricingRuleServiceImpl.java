package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.PricingRule;
import com.waylo.pricingservice.domain.PricingType;
import com.waylo.pricingservice.dto.PricingRuleMapper;
import com.waylo.pricingservice.dto.PricingRuleRequest;
import com.waylo.pricingservice.dto.PricingRuleResponse;
import com.waylo.pricingservice.exception.ResourceNotFoundException;
import com.waylo.pricingservice.repository.PricingRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository repository;
    private final PricingRuleMapper mapper;

    public PricingRuleServiceImpl(PricingRuleRepository repository, PricingRuleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PricingRuleResponse create(PricingRuleRequest request) {
        PricingRule rule = mapper.toEntity(request);
        rule = repository.save(rule);
        return mapper.toResponse(rule);
    }

    @Override @Transactional(readOnly = true)
    public PricingRuleResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override @Transactional(readOnly = true)
    public List<PricingRuleResponse> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<PricingRuleResponse> getByType(PricingType pricingType) {
        return repository.findByPricingType(pricingType).stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<PricingRuleResponse> getActiveByType(PricingType pricingType) {
        return repository.findByPricingTypeAndActiveTrue(pricingType).stream().map(mapper::toResponse).toList();
    }

    @Override
    public PricingRuleResponse update(UUID id, PricingRuleRequest request) {
        PricingRule rule = findOrThrow(id);
        mapper.updateEntity(request, rule);
        rule = repository.save(rule);
        return mapper.toResponse(rule);
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("PricingRule", "id", id);
        repository.deleteById(id);
    }

    @Override
    public PricingRuleResponse deactivate(UUID id) {
        PricingRule rule = findOrThrow(id);
        rule.setActive(false);
        return mapper.toResponse(repository.save(rule));
    }

    @Override
    public PricingRuleResponse activate(UUID id) {
        PricingRule rule = findOrThrow(id);
        rule.setActive(true);
        return mapper.toResponse(repository.save(rule));
    }

    private PricingRule findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("PricingRule", "id", id));
    }
}
