package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.domain.Vehicle;
import com.waylo.carrentalservice.domain.VehicleStatus;
import com.waylo.carrentalservice.domain.VehicleType;
import com.waylo.carrentalservice.dto.VehicleMapper;
import com.waylo.carrentalservice.dto.VehicleRequest;
import com.waylo.carrentalservice.dto.VehicleResponse;
import com.waylo.carrentalservice.exception.DuplicateResourceException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import com.waylo.carrentalservice.repository.VehicleRepository;
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
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private com.waylo.carrentalservice.repository.RentalRepository rentalRepository;

    private VehicleServiceImpl service;

    private final UUID vehicleId = UUID.randomUUID();

    private VehicleServiceImpl newService() {
        return new VehicleServiceImpl(vehicleRepository, vehicleMapper, rentalRepository);
    }

    private Vehicle buildVehicle(UUID id, VehicleStatus status) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setPlateNumber("34ABC123");
        v.setBrand("Toyota");
        v.setModel("Corolla");
        v.setYear(2022);
        v.setVehicleType(VehicleType.MIDSIZE);
        v.setStatus(status);
        v.setColor("White");
        v.setSeatCount(5);
        v.setDailyRate(new BigDecimal("50.00"));
        v.setLocation("Istanbul");
        v.setMileage(10000);
        v.setActive(true);
        return v;
    }

    private VehicleRequest buildRequest() {
        return new VehicleRequest("34ABC123", "Toyota", "Corolla", 2022, VehicleType.MIDSIZE,
                "White", 5, new BigDecimal("50.00"), "Istanbul", 10000);
    }

    // --- create ---

    @Test
    void create_duplicatePlateNumber_throwsAndDoesNotSave() {
        service = newService();
        VehicleRequest request = buildRequest();
        when(vehicleRepository.existsByPlateNumber("34ABC123")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void create_valid_setsAvailableStatusAndSaves() {
        service = newService();
        VehicleRequest request = buildRequest();
        Vehicle mapped = new Vehicle();
        mapped.setPlateNumber("34ABC123");

        when(vehicleRepository.existsByPlateNumber("34ABC123")).thenReturn(false);
        when(vehicleMapper.toEntity(request)).thenReturn(mapped);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(response);

        VehicleResponse result = service.create(request);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleMapper).toResponse(any(Vehicle.class));
    }

    // --- getById ---

    @Test
    void getById_found_returnsResponse() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(response);

        VehicleResponse result = service.getById(vehicleId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getById_notFound_throws() {
        service = newService();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(vehicleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getByPlateNumber ---

    @Test
    void getByPlateNumber_found_returnsResponse() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleRepository.findByPlateNumber("34ABC123")).thenReturn(Optional.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(response);

        VehicleResponse result = service.getByPlateNumber("34ABC123");

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getByPlateNumber_notFound_throws() {
        service = newService();
        when(vehicleRepository.findByPlateNumber("34ABC123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByPlateNumber("34ABC123"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAll ---

    @Test
    void getAll_returnsMappedList() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleRepository.findByActiveTrue()).thenReturn(List.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(response);

        List<VehicleResponse> result = service.getAll();

        assertThat(result).containsExactly(response);
    }

    // --- search ---

    @Test
    void search_locationAndTypeAndStatus_usesCombinedQuery() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByLocationAndVehicleTypeAndStatus("Istanbul", VehicleType.MIDSIZE, VehicleStatus.AVAILABLE))
                .thenReturn(List.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(mock(VehicleResponse.class));

        List<VehicleResponse> result = service.search("Istanbul", VehicleType.MIDSIZE, VehicleStatus.AVAILABLE, null);

        assertThat(result).hasSize(1);
        verify(vehicleRepository).findByLocationAndVehicleTypeAndStatus("Istanbul", VehicleType.MIDSIZE, VehicleStatus.AVAILABLE);
    }

    @Test
    void search_locationAndStatus_usesLocationStatusQuery() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByLocationAndStatus("Istanbul", VehicleStatus.AVAILABLE))
                .thenReturn(List.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(mock(VehicleResponse.class));

        List<VehicleResponse> result = service.search("Istanbul", null, VehicleStatus.AVAILABLE, null);

        assertThat(result).hasSize(1);
        verify(vehicleRepository).findByLocationAndStatus("Istanbul", VehicleStatus.AVAILABLE);
    }

    @Test
    void search_locationOnly_usesLocationQuery() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByLocation("Istanbul")).thenReturn(List.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(mock(VehicleResponse.class));

        List<VehicleResponse> result = service.search("Istanbul", null, null, null);

        assertThat(result).hasSize(1);
        verify(vehicleRepository).findByLocation("Istanbul");
    }

    @Test
    void search_typeOnly_usesTypeQuery() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByVehicleType(VehicleType.MIDSIZE)).thenReturn(List.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(mock(VehicleResponse.class));

        List<VehicleResponse> result = service.search(null, VehicleType.MIDSIZE, null, null);

        assertThat(result).hasSize(1);
        verify(vehicleRepository).findByVehicleType(VehicleType.MIDSIZE);
    }

    @Test
    void search_statusOnly_usesStatusQuery() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(mock(VehicleResponse.class));

        List<VehicleResponse> result = service.search(null, null, VehicleStatus.AVAILABLE, null);

        assertThat(result).hasSize(1);
        verify(vehicleRepository).findByStatus(VehicleStatus.AVAILABLE);
    }

    @Test
    void search_noFilters_usesFindAll() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle));
        when(vehicleMapper.toResponse(vehicle)).thenReturn(mock(VehicleResponse.class));

        List<VehicleResponse> result = service.search(null, null, null, null);

        assertThat(result).hasSize(1);
        verify(vehicleRepository).findAll();
    }

    @Test
    void search_maxDailyRate_filtersResults() {
        service = newService();
        Vehicle cheap = buildVehicle(UUID.randomUUID(), VehicleStatus.AVAILABLE);
        cheap.setDailyRate(new BigDecimal("40.00"));
        Vehicle expensive = buildVehicle(UUID.randomUUID(), VehicleStatus.AVAILABLE);
        expensive.setDailyRate(new BigDecimal("100.00"));
        when(vehicleRepository.findAll()).thenReturn(List.of(cheap, expensive));
        when(vehicleMapper.toResponse(cheap)).thenReturn(mock(VehicleResponse.class));

        List<VehicleResponse> result = service.search(null, null, null, new BigDecimal("50.00"));

        assertThat(result).hasSize(1);
        verify(vehicleMapper, never()).toResponse(expensive);
    }

    // --- update ---

    @Test
    void update_notFound_throws() {
        service = newService();
        VehicleRequest request = buildRequest();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(vehicleId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void update_valid_updatesEntityAndSaves() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        VehicleRequest request = buildRequest();
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(response);

        VehicleResponse result = service.update(vehicleId, request);

        assertThat(result).isEqualTo(response);
        verify(vehicleMapper).updateEntity(request, vehicle);
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    // --- delete ---

    @Test
    void delete_notFound_throwsAndNeverDeletes() {
        service = newService();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(vehicleId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(vehicleRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void delete_found_deletes() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        vehicle.setActive(false);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));

        service.delete(vehicleId);

        verify(vehicleRepository).deleteById(vehicleId);
    }

    // --- updateStatus ---

    @Test
    void updateStatus_setsStatusAndSaves() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(response);

        VehicleResponse result = service.updateStatus(vehicleId, VehicleStatus.AVAILABLE);

        assertThat(result).isEqualTo(response);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    // --- deactivate ---

    @Test
    void deactivate_setsActiveFalseAndSaves() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(response);

        VehicleResponse result = service.deactivate(vehicleId);

        assertThat(result).isEqualTo(response);
        assertThat(vehicle.isActive()).isFalse();
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    // --- activate ---

    @Test
    void activate_setsActiveTrueAndSaves() {
        service = newService();
        Vehicle vehicle = buildVehicle(vehicleId, VehicleStatus.AVAILABLE);
        vehicle.setActive(false);
        VehicleResponse response = mock(VehicleResponse.class);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleMapper.toResponse(any(Vehicle.class))).thenReturn(response);

        VehicleResponse result = service.activate(vehicleId);

        assertThat(result).isEqualTo(response);
        assertThat(vehicle.isActive()).isTrue();
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    // --- getAvailableCount ---

    @Test
    void getAvailableCount_delegatesToRepositoryWithAvailableStatus() {
        service = newService();
        when(vehicleRepository.countByLocationAndStatusAndActiveTrue("Istanbul", VehicleStatus.AVAILABLE)).thenReturn(5L);

        long result = service.getAvailableCount("Istanbul");

        assertThat(result).isEqualTo(5L);
        verify(vehicleRepository).countByLocationAndStatusAndActiveTrue("Istanbul", VehicleStatus.AVAILABLE);
    }
}
