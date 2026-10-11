package com.waylo.crmservice.service;

import com.waylo.crmservice.domain.Customer;
import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.dto.CustomerMapper;
import com.waylo.crmservice.dto.CustomerRequest;
import com.waylo.crmservice.dto.CustomerResponse;
import com.waylo.crmservice.exception.ResourceNotFoundException;
import com.waylo.crmservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public CustomerServiceImpl(CustomerRepository repository, CustomerMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = mapper.toEntity(request);
        customer = repository.save(customer);
        return mapper.toResponse(customer);
    }

    @Override @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override @Transactional(readOnly = true)
    public CustomerResponse getByEmail(String email) {
        Customer customer = repository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "email", email));
        return mapper.toResponse(customer);
    }

    @Override @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<CustomerResponse> getByTier(CustomerTier tier) {
        return repository.findByTier(tier).stream().map(mapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<CustomerResponse> search(String query) {
        return repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = findOrThrow(id);
        mapper.updateEntity(request, customer);
        customer = repository.save(customer);
        return mapper.toResponse(customer);
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Customer", "id", id);
        repository.deleteById(id);
    }

    @Override
    public CustomerResponse addLoyaltyPoints(UUID id, int points) {
        Customer customer = findOrThrow(id);
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        customer = repository.save(customer);
        return mapper.toResponse(customer);
    }

    @Override
    public CustomerResponse upgradeTier(UUID id, CustomerTier tier) {
        Customer customer = findOrThrow(id);
        customer.setTier(tier);
        customer = repository.save(customer);
        return mapper.toResponse(customer);
    }

    private Customer findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
    }
}
