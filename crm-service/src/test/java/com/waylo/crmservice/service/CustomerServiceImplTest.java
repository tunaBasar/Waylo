package com.waylo.crmservice.service;

import com.waylo.crmservice.domain.Customer;
import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.dto.CustomerMapper;
import com.waylo.crmservice.dto.CustomerRequest;
import com.waylo.crmservice.dto.CustomerResponse;
import com.waylo.crmservice.exception.ResourceNotFoundException;
import com.waylo.crmservice.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository repository;
    @Mock
    private CustomerMapper mapper;

    private CustomerServiceImpl service;

    private CustomerServiceImpl newService() {
        return new CustomerServiceImpl(repository, mapper);
    }

    private Customer buildCustomer(UUID id, CustomerTier tier, int loyaltyPoints) {
        Customer c = new Customer();
        c.setId(id);
        c.setFirstName("John");
        c.setLastName("Doe");
        c.setEmail("john.doe@example.com");
        c.setPhone("+90123456789");
        c.setPassportNumber("P1234567");
        c.setTier(tier);
        c.setLoyaltyPoints(loyaltyPoints);
        c.setActive(true);
        return c;
    }

    // --- create ---

    @Test
    void create_savesAndReturnsMappedResponse() {
        service = newService();
        CustomerRequest request = new CustomerRequest("John", "Doe", "john.doe@example.com", "+90123456789", "P1234567");
        Customer mapped = new Customer();
        CustomerResponse response = mock(CustomerResponse.class);

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(repository.save(mapped)).thenReturn(mapped);
        when(mapper.toResponse(mapped)).thenReturn(response);

        CustomerResponse result = service.create(request);

        assertThat(result).isEqualTo(response);
        verify(repository).save(mapped);
    }

    // --- getById ---

    @Test
    void getById_found_returnsMappedResponse() {
        service = newService();
        UUID id = UUID.randomUUID();
        Customer customer = buildCustomer(id, CustomerTier.BRONZE, 0);
        CustomerResponse response = mock(CustomerResponse.class);
        when(repository.findById(id)).thenReturn(Optional.of(customer));
        when(mapper.toResponse(customer)).thenReturn(response);

        CustomerResponse result = service.getById(id);

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

    // --- getByEmail ---

    @Test
    void getByEmail_found_returnsMappedResponse() {
        service = newService();
        String email = "john.doe@example.com";
        Customer customer = buildCustomer(UUID.randomUUID(), CustomerTier.BRONZE, 0);
        CustomerResponse response = mock(CustomerResponse.class);
        when(repository.findByEmail(email)).thenReturn(Optional.of(customer));
        when(mapper.toResponse(customer)).thenReturn(response);

        CustomerResponse result = service.getByEmail(email);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getByEmail_notFound_throwsResourceNotFound() {
        service = newService();
        String email = "missing@example.com";
        when(repository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByEmail(email))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll ---

    @Test
    void getAll_mapsAllCustomers() {
        service = newService();
        Customer c1 = buildCustomer(UUID.randomUUID(), CustomerTier.BRONZE, 0);
        Customer c2 = buildCustomer(UUID.randomUUID(), CustomerTier.GOLD, 100);
        when(repository.findAll()).thenReturn(List.of(c1, c2));
        when(mapper.toResponse(any(Customer.class))).thenReturn(mock(CustomerResponse.class));

        List<CustomerResponse> result = service.getAll();

        assertThat(result).hasSize(2);
    }

    // --- getByTier ---

    @Test
    void getByTier_filtersCorrectly() {
        service = newService();
        Customer c = buildCustomer(UUID.randomUUID(), CustomerTier.GOLD, 50);
        when(repository.findByTier(CustomerTier.GOLD)).thenReturn(List.of(c));
        when(mapper.toResponse(c)).thenReturn(mock(CustomerResponse.class));

        List<CustomerResponse> result = service.getByTier(CustomerTier.GOLD);

        assertThat(result).hasSize(1);
        verify(repository).findByTier(CustomerTier.GOLD);
    }

    // --- search ---

    @Test
    void search_passesQueryToOrContainsRepositoryMethod() {
        service = newService();
        String query = "Jo";
        Customer c = buildCustomer(UUID.randomUUID(), CustomerTier.BRONZE, 0);
        when(repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(List.of(c));
        when(mapper.toResponse(c)).thenReturn(mock(CustomerResponse.class));

        List<CustomerResponse> result = service.search(query);

        assertThat(result).hasSize(1);
        verify(repository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    // --- update ---

    @Test
    void update_notFound_throwsResourceNotFound() {
        service = newService();
        UUID id = UUID.randomUUID();
        CustomerRequest request = new CustomerRequest("John", "Doe", "john.doe@example.com", "+90123456789", "P1234567");
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any(Customer.class));
    }

    @Test
    void update_valid_callsUpdateEntityAndSave() {
        service = newService();
        UUID id = UUID.randomUUID();
        Customer existing = buildCustomer(id, CustomerTier.BRONZE, 0);
        CustomerRequest request = new CustomerRequest("Jane", "Smith", "jane.smith@example.com", "+90987654321", "P7654321");
        CustomerResponse response = mock(CustomerResponse.class);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(response);

        CustomerResponse result = service.update(id, request);

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

    // --- addLoyaltyPoints ---

    @Test
    void addLoyaltyPoints_addsNumericallyToExistingPoints() {
        service = newService();
        UUID id = UUID.randomUUID();
        Customer existing = buildCustomer(id, CustomerTier.BRONZE, 100);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Customer.class))).thenReturn(mock(CustomerResponse.class));

        service.addLoyaltyPoints(id, 50);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLoyaltyPoints()).isEqualTo(150);
    }

    // --- upgradeTier ---

    @Test
    void upgradeTier_setsTierField() {
        service = newService();
        UUID id = UUID.randomUUID();
        Customer existing = buildCustomer(id, CustomerTier.BRONZE, 0);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Customer.class))).thenReturn(mock(CustomerResponse.class));

        service.upgradeTier(id, CustomerTier.PLATINUM);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTier()).isEqualTo(CustomerTier.PLATINUM);
    }
}
