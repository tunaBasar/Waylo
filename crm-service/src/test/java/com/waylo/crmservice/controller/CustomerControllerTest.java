package com.waylo.crmservice.controller;

import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.dto.ApiResponse;
import com.waylo.crmservice.dto.CustomerRequest;
import com.waylo.crmservice.dto.CustomerResponse;
import com.waylo.crmservice.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;
    @InjectMocks
    private CustomerController controller;

    private final UUID customerId = UUID.randomUUID();

    private CustomerResponse buildResponse() {
        return new CustomerResponse(customerId, "John", "Doe", "john.doe@example.com", "+90123456789",
                "P1234567", CustomerTier.BRONZE, 0, true, Instant.now(), Instant.now());
    }

    @Test
    void create_returns201() {
        CustomerRequest request = new CustomerRequest("John", "Doe", "john.doe@example.com", "+90123456789", "P1234567");
        CustomerResponse response = buildResponse();
        when(customerService.create(any(CustomerRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<CustomerResponse>> result = controller.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        CustomerResponse response = buildResponse();
        when(customerService.getById(customerId)).thenReturn(response);

        ResponseEntity<ApiResponse<CustomerResponse>> result = controller.getById(customerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAll_returns200() {
        when(customerService.getAll()).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<CustomerResponse>>> result = controller.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void getByEmail_returns200() {
        String email = "john.doe@example.com";
        CustomerResponse response = buildResponse();
        when(customerService.getByEmail(email)).thenReturn(response);

        ResponseEntity<ApiResponse<CustomerResponse>> result = controller.getByEmail(email);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(customerService).getByEmail(email);
    }

    @Test
    void getByTier_returns200() {
        when(customerService.getByTier(CustomerTier.GOLD)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<CustomerResponse>>> result = controller.getByTier(CustomerTier.GOLD);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(customerService).getByTier(CustomerTier.GOLD);
    }

    @Test
    void search_returns200() {
        String query = "Jo";
        when(customerService.search(query)).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<CustomerResponse>>> result = controller.search(query);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
        verify(customerService).search(query);
    }

    @Test
    void update_returns200() {
        CustomerRequest request = new CustomerRequest("Jane", "Smith", "jane.smith@example.com", "+90987654321", "P7654321");
        CustomerResponse response = buildResponse();
        when(customerService.update(customerId, request)).thenReturn(response);

        ResponseEntity<ApiResponse<CustomerResponse>> result = controller.update(customerId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void delete_returns204() {
        ResponseEntity<Void> result = controller.delete(customerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(customerService).delete(customerId);
    }

    @Test
    void addLoyaltyPoints_returns200() {
        CustomerResponse response = buildResponse();
        when(customerService.addLoyaltyPoints(customerId, 50)).thenReturn(response);

        ResponseEntity<ApiResponse<CustomerResponse>> result = controller.addLoyaltyPoints(customerId, 50);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(customerService).addLoyaltyPoints(customerId, 50);
    }
}
