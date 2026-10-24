package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.dto.HotelMapper;
import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HotelServiceImplTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private HotelMapper hotelMapper;

    @Mock
    private AuthAccessClient authAccessClient;

    @InjectMocks
    private HotelServiceImpl hotelService;

    private final UUID testId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUpDefaultActiveOrg() {
        // Default: the test hotel's org is active, so existing getAll/search tests (which build
        // hotels with orgId via buildHotel) keep passing without each test stubbing this.
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));
    }

    private Hotel buildHotel(UUID id, String name, String city, String country) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName(name);
        hotel.setCity(city);
        hotel.setCountry(country);
        hotel.setAddress("123 Main St");
        hotel.setStarRating(4);
        hotel.setDescription("A great hotel");
        hotel.setActive(true);
        hotel.setOrganizationId(orgId);
        hotel.setLatitude(BigDecimal.valueOf(41.0));
        hotel.setLongitude(BigDecimal.valueOf(29.0));
        hotel.setRooms(List.of());
        return hotel;
    }

    private HotelResponse buildResponse(UUID id) {
        return new HotelResponse(
                id, orgId, "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "A great hotel",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                true, 0, null, null
        );
    }

    @Test
    void create_savesAndMaps() {
        HotelRequest request = new HotelRequest(
                "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "A great hotel",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                orgId
        );
        Hotel savedHotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelMapper.toEntity(request)).thenReturn(new Hotel());
        when(hotelRepository.save(any(Hotel.class))).thenReturn(savedHotel);
        when(hotelMapper.toResponse(savedHotel)).thenReturn(response);

        HotelResponse result = hotelService.create(request);

        assertThat(result).isEqualTo(response);
        verify(hotelRepository).save(any(Hotel.class));
        verify(hotelMapper).toResponse(savedHotel);
    }

    @Test
    void getById_found_returnsResponse() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findById(testId)).thenReturn(Optional.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        HotelResponse result = hotelService.getById(testId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getById_notFound_throwsException() {
        when(hotelRepository.findById(testId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getById(testId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with id");
    }

    @Test
    void getAll_returnsMappedList() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findAll()).thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        List<HotelResponse> result = hotelService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(response);
    }

    @Test
    void search_cityAndCountry() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findByCityAndCountry("Istanbul", "Turkey"))
                .thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        List<HotelResponse> result = hotelService.search("Istanbul", "Turkey", null);

        assertThat(result).hasSize(1);
        verify(hotelRepository).findByCityAndCountry("Istanbul", "Turkey");
    }

    @Test
    void search_cityOnly() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findByCity("Istanbul")).thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        List<HotelResponse> result = hotelService.search("Istanbul", null, null);

        assertThat(result).hasSize(1);
        verify(hotelRepository).findByCity("Istanbul");
    }

    @Test
    void search_countryOnly() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findByCountry("Turkey")).thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        List<HotelResponse> result = hotelService.search(null, "Turkey", null);

        assertThat(result).hasSize(1);
        verify(hotelRepository).findByCountry("Turkey");
    }

    @Test
    void search_none_returnsAll() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findAll()).thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        List<HotelResponse> result = hotelService.search(null, null, null);

        assertThat(result).hasSize(1);
        verify(hotelRepository).findAll();
    }

    @Test
    void search_withMinStars_filter() {
        Hotel hotel4 = buildHotel(testId, "4 Star", "Istanbul", "Turkey");
        hotel4.setStarRating(4);
        Hotel hotel5 = buildHotel(UUID.randomUUID(), "5 Star", "Istanbul", "Turkey");
        hotel5.setStarRating(5);
        HotelResponse response4 = new HotelResponse(
                testId, orgId, "4 Star", "Istanbul", "Turkey",
                "123 Main St", 4, "desc",
                null, null, true, 0, null, null
        );

        when(hotelRepository.findAll()).thenReturn(List.of(hotel4, hotel5));
        when(hotelMapper.toResponse(hotel4)).thenReturn(response4);
        when(hotelMapper.toResponse(hotel5)).thenReturn(mock(HotelResponse.class));

        List<HotelResponse> result = hotelService.search(null, null, 4);

        assertThat(result).hasSize(2);
    }

    @Test
    void search_cityAndCountry_withMinStars_filter() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        hotel.setStarRating(4);
        HotelResponse response = new HotelResponse(
                testId, orgId, "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "desc",
                null, null, true, 0, null, null
        );

        when(hotelRepository.findByCityAndCountry("Istanbul", "Turkey")).thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        List<HotelResponse> result = hotelService.search("Istanbul", "Turkey", 4);

        assertThat(result).hasSize(1);
    }

    @Test
    void update_found_updatesAndReturns() {
        Hotel existing = buildHotel(testId, "Old Name", "Istanbul", "Turkey");
        HotelRequest request = new HotelRequest(
                "New Name", "Istanbul", "Turkey",
                "456 Oak Ave", 5, "Updated hotel",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                orgId
        );
        Hotel savedHotel = buildHotel(testId, "New Name", "Istanbul", "Turkey");
        savedHotel.setStarRating(5);
        HotelResponse response = new HotelResponse(
                testId, orgId, "New Name", "Istanbul", "Turkey",
                "456 Oak Ave", 5, "Updated hotel",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                true, 0, null, null
        );

        when(hotelRepository.findById(testId)).thenReturn(Optional.of(existing));
        when(hotelRepository.save(any(Hotel.class))).thenReturn(savedHotel);
        when(hotelMapper.toResponse(savedHotel)).thenReturn(response);

        HotelResponse result = hotelService.update(testId, request);

        assertThat(result).isEqualTo(response);
        verify(hotelMapper).updateEntity(any(HotelRequest.class), any(Hotel.class));
    }

    @Test
    void delete_found_deletes() {
        when(hotelRepository.existsById(testId)).thenReturn(true);

        hotelService.delete(testId);

        verify(hotelRepository).deleteById(testId);
    }

    @Test
    void delete_notFound_throwsException() {
        when(hotelRepository.existsById(testId)).thenReturn(false);

        assertThatThrownBy(() -> hotelService.delete(testId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with id");
    }

    @Test
    void activate_setsActiveTrue() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        hotel.setActive(false);
        HotelResponse response = new HotelResponse(
                testId, orgId, "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "desc",
                null, null, true, 0, null, null
        );

        when(hotelRepository.findById(testId)).thenReturn(Optional.of(hotel));
        when(hotelRepository.save(any(Hotel.class))).thenReturn(hotel);
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        HotelResponse result = hotelService.activate(testId);

        assertThat(result.active()).isTrue();
        verify(hotelRepository).save(hotel);
    }

    @Test
    void deactivate_setsActiveFalse() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        hotel.setActive(true);
        HotelResponse response = new HotelResponse(
                testId, orgId, "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "desc",
                null, null, false, 0, null, null
        );

        when(hotelRepository.findById(testId)).thenReturn(Optional.of(hotel));
        when(hotelRepository.save(any(Hotel.class))).thenReturn(hotel);
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        HotelResponse result = hotelService.deactivate(testId);

        assertThat(result.active()).isFalse();
    }

    @Test
    void getByOrganization_callsFindByOrganizationId() {
        Hotel hotel = buildHotel(testId, "Grand Hotel", "Istanbul", "Turkey");
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findByOrganizationId(orgId)).thenReturn(List.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        List<HotelResponse> result = hotelService.getByOrganization(orgId);

        assertThat(result).hasSize(1);
        verify(hotelRepository).findByOrganizationId(orgId);
    }

    // --- Public projection: getAll/search exclude inactive and non-ACTIVE-org hotels ---

    @Test
    void getAll_excludesInactiveHotel() {
        Hotel active = buildHotel(testId, "Active Hotel", "Istanbul", "Turkey");
        Hotel inactive = buildHotel(UUID.randomUUID(), "Inactive Hotel", "Istanbul", "Turkey");
        inactive.setActive(false);
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findAll()).thenReturn(List.of(active, inactive));
        when(hotelMapper.toResponse(active)).thenReturn(response);

        List<HotelResponse> result = hotelService.getAll();

        assertThat(result).hasSize(1);
        verify(hotelMapper, never()).toResponse(inactive);
    }

    @Test
    void getAll_excludesHotelOfNonActiveOrganization() {
        UUID suspendedOrgId = UUID.randomUUID();
        Hotel visible = buildHotel(testId, "Visible Hotel", "Istanbul", "Turkey");
        Hotel hidden = buildHotel(UUID.randomUUID(), "Hidden Hotel", "Istanbul", "Turkey");
        hidden.setOrganizationId(suspendedOrgId);
        HotelResponse response = buildResponse(testId);

        // authAccessClient stubbed in @BeforeEach to return only [orgId] as ACTIVE
        when(hotelRepository.findAll()).thenReturn(List.of(visible, hidden));
        when(hotelMapper.toResponse(visible)).thenReturn(response);

        List<HotelResponse> result = hotelService.getAll();

        assertThat(result).hasSize(1);
        verify(hotelMapper, never()).toResponse(hidden);
    }

    @Test
    void getAll_includesLegacyHotelWithoutOrganization() {
        Hotel legacy = buildHotel(testId, "Legacy Hotel", "Istanbul", "Turkey");
        legacy.setOrganizationId(null);
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findAll()).thenReturn(List.of(legacy));
        when(hotelMapper.toResponse(legacy)).thenReturn(response);

        List<HotelResponse> result = hotelService.getAll();

        assertThat(result).hasSize(1);
        // Legacy hotel visible without ever calling the organization lookup (short-circuit when all-legacy)
        verify(authAccessClient, never()).activeOrganizationIds();
    }

    @Test
    void getAll_authServiceUnavailable_hidesOrgOwnedHotelsFailClosed() {
        Hotel orgOwned = buildHotel(testId, "Org Hotel", "Istanbul", "Turkey");
        Hotel legacy = buildHotel(UUID.randomUUID(), "Legacy Hotel", "Istanbul", "Turkey");
        legacy.setOrganizationId(null);
        HotelResponse legacyResponse = buildResponse(legacy.getId());

        when(authAccessClient.activeOrganizationIds()).thenThrow(new RuntimeException("auth-service down"));
        when(hotelRepository.findAll()).thenReturn(List.of(orgOwned, legacy));
        when(hotelMapper.toResponse(legacy)).thenReturn(legacyResponse);

        List<HotelResponse> result = hotelService.getAll();

        assertThat(result).hasSize(1);
        verify(hotelMapper, never()).toResponse(orgOwned);
    }

    @Test
    void search_excludesHotelOfNonActiveOrganization() {
        UUID suspendedOrgId = UUID.randomUUID();
        Hotel visible = buildHotel(testId, "Visible Hotel", "Istanbul", "Turkey");
        Hotel hidden = buildHotel(UUID.randomUUID(), "Hidden Hotel", "Istanbul", "Turkey");
        hidden.setOrganizationId(suspendedOrgId);
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findByCity("Istanbul")).thenReturn(List.of(visible, hidden));
        when(hotelMapper.toResponse(visible)).thenReturn(response);

        List<HotelResponse> result = hotelService.search("Istanbul", null, null);

        assertThat(result).hasSize(1);
        verify(hotelMapper, never()).toResponse(hidden);
    }

    @Test
    void getById_doesNotFilterByOrganizationStatus() {
        // getById is the internal lookup auth-service relies on for role-grant verification
        // before a PENDING organization is ever ACTIVE; it must never apply the public filter.
        UUID pendingOrgId = UUID.randomUUID();
        Hotel hotel = buildHotel(testId, "Pending Org Hotel", "Istanbul", "Turkey");
        hotel.setOrganizationId(pendingOrgId);
        HotelResponse response = buildResponse(testId);

        when(hotelRepository.findById(testId)).thenReturn(Optional.of(hotel));
        when(hotelMapper.toResponse(hotel)).thenReturn(response);

        HotelResponse result = hotelService.getById(testId);

        assertThat(result).isEqualTo(response);
        verify(authAccessClient, never()).activeOrganizationIds();
    }

    @Test
    void isPublic_inactiveHotel_false() {
        Hotel hotel = buildHotel(testId, "Draft", "Istanbul", "Turkey");
        hotel.setActive(false);
        when(hotelRepository.findById(testId)).thenReturn(Optional.of(hotel));
        assertThat(hotelService.isPublic(testId)).isFalse();
    }

    @Test
    void isPublic_activeHotelOfPendingOrg_false() {
        Hotel hotel = buildHotel(testId, "Pending", "Istanbul", "Turkey");
        hotel.setOrganizationId(UUID.randomUUID());
        when(hotelRepository.findById(testId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of());
        assertThat(hotelService.isPublic(testId)).isFalse();
    }

    @Test
    void getPublicByOrganization_hidesInactive() {
        UUID orgId = UUID.randomUUID();
        Hotel hidden = buildHotel(UUID.randomUUID(), "Draft", "Istanbul", "Turkey");
        hidden.setOrganizationId(orgId);
        hidden.setActive(false);
        when(hotelRepository.findByOrganizationId(orgId)).thenReturn(List.of(hidden));
        assertThat(hotelService.getPublicByOrganization(orgId)).isEmpty();
    }
}
