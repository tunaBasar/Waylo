package com.waylo.hotelservice.security;

import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.client.AccessDecision;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.AuthenticationRequiredException;
import com.waylo.hotelservice.exception.AuthorizationUnavailableException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelAccessGuardTest {

    @Mock
    private AuthAccessClient authAccessClient;

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private HotelAccessGuard guard;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID hotelId = UUID.randomUUID();

    @Test
    void requireCreate_allowed_passes() {
        when(authAccessClient.check(eq(userId), eq(orgId), any(), eq("HOTEL_CREATE")))
                .thenReturn(new AccessDecision(true, List.of("ADMIN")));

        guard.requireCreate(userId, orgId);

        verify(authAccessClient).check(userId, orgId, null, "HOTEL_CREATE");
    }

    @Test
    void requireCreate_denied_throwsAccessDenied() {
        when(authAccessClient.check(eq(userId), eq(orgId), any(), eq("HOTEL_CREATE")))
                .thenReturn(new AccessDecision(false, List.of()));

        assertThatThrownBy(() -> guard.requireCreate(userId, orgId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not allowed to perform this action");
    }

    @Test
    void requireCreate_nullUserId_throwsAuthenticationRequired() {
        assertThatThrownBy(() -> guard.requireCreate(null, orgId))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication required");

        verify(authAccessClient, never()).check(any(), any(), any(), any());
    }

    @Test
    void requireCreate_clientException_throwsAuthorizationUnavailable() {
        when(authAccessClient.check(eq(userId), eq(orgId), any(), eq("HOTEL_CREATE")))
                .thenThrow(new RuntimeException("service down"));

        assertThatThrownBy(() -> guard.requireCreate(userId, orgId))
                .isInstanceOf(AuthorizationUnavailableException.class)
                .hasMessage("Authorization service unavailable");
    }

    @Test
    void requireCreate_nullDecision_throwsAccessDenied() {
        when(authAccessClient.check(eq(userId), eq(orgId), any(), eq("HOTEL_CREATE")))
                .thenReturn(null);

        assertThatThrownBy(() -> guard.requireCreate(userId, orgId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not allowed to perform this action");
    }

    @Test
    void requireHotelManage_allowed_passes() {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOrganizationId(orgId);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.check(eq(userId), eq(orgId), eq(hotelId), eq("HOTEL_MANAGE")))
                .thenReturn(new AccessDecision(true, List.of("MANAGER")));

        guard.requireHotelManage(userId, hotelId);

        verify(authAccessClient).check(userId, orgId, hotelId, "HOTEL_MANAGE");
    }

    @Test
    void requireHotelManage_missingHotel_throwsResourceNotFoundException() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireHotelManage(userId, hotelId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel");

        verify(authAccessClient, never()).check(any(), any(), any(), any());
    }

    @Test
    void requireHotelManage_nullOrganizationId_throwsAccessDenied() {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOrganizationId(null);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> guard.requireHotelManage(userId, hotelId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Hotel has no owning organization; changes are locked until it is assigned");

        verify(authAccessClient, never()).check(any(), any(), any(), any());
    }

    @Test
    void requireHotelManage_usesHotelOwnOrganizationId_notClientSupplied() {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOrganizationId(orgId);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.check(eq(userId), eq(orgId), eq(hotelId), eq("HOTEL_MANAGE")))
                .thenReturn(new AccessDecision(true, List.of()));

        guard.requireHotelManage(userId, hotelId);

        // Verify auth service was called with the hotel's orgId, not a hypothetical client-supplied one
        verify(authAccessClient).check(userId, orgId, hotelId, "HOTEL_MANAGE");
    }

    @Test
    void requireRoomManage_allowed_passes() {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOrganizationId(orgId);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.check(eq(userId), eq(orgId), eq(hotelId), eq("ROOM_MANAGE")))
                .thenReturn(new AccessDecision(true, List.of("USER")));

        guard.requireRoomManage(userId, hotelId);

        verify(authAccessClient).check(userId, orgId, hotelId, "ROOM_MANAGE");
    }

    @Test
    void requireRoomManage_nullOrganizationId_throwsAccessDenied() {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setOrganizationId(null);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> guard.requireRoomManage(userId, hotelId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Hotel has no owning organization; changes are locked until it is assigned");

        verify(authAccessClient, never()).check(any(), any(), any(), any());
    }

    @Test
    void canReadOrganization_admin_trueWithoutAuthCall() {
        assertThat(guard.canReadOrganization(userId, "ADMIN", orgId)).isTrue();
        verifyNoInteractions(authAccessClient);
    }

    @Test
    void canReadOrganization_member_true() {
        when(authAccessClient.check(userId, orgId, null, "HOTEL_READ")).thenReturn(new AccessDecision(true, List.of("HOTEL_STAFF")));
        assertThat(guard.canReadOrganization(userId, "USER", orgId)).isTrue();
    }

    @Test
    void canReadOrganization_nonMember_false() {
        when(authAccessClient.check(userId, orgId, null, "HOTEL_READ")).thenReturn(new AccessDecision(false, List.of()));
        assertThat(guard.canReadOrganization(userId, "USER", orgId)).isFalse();
    }

    @Test
    void canReadOrganization_anonymousOrOutage_false() {
        assertThat(guard.canReadOrganization(null, null, orgId)).isFalse();
        when(authAccessClient.check(userId, orgId, null, "HOTEL_READ")).thenThrow(new RuntimeException("down"));
        assertThat(guard.canReadOrganization(userId, "USER", orgId)).isFalse();
    }
}
