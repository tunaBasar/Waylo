package com.waylo.crmservice.service;

import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.dto.CustomerRequest;
import com.waylo.crmservice.dto.CustomerResponse;

import java.util.List;
import java.util.UUID;

public interface CustomerService {
    CustomerResponse create(CustomerRequest request);
    CustomerResponse getById(UUID id);
    CustomerResponse getByEmail(String email);
    List<CustomerResponse> getAll();
    List<CustomerResponse> getByTier(CustomerTier tier);
    List<CustomerResponse> search(String query);
    CustomerResponse update(UUID id, CustomerRequest request);
    void delete(UUID id);
    CustomerResponse addLoyaltyPoints(UUID id, int points);
    CustomerResponse upgradeTier(UUID id, CustomerTier tier);
}
