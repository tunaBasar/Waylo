package com.waylo.hotelservice.security;

import com.waylo.hotelservice.client.AccessDecision;
import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.AuthenticationRequiredException;
import com.waylo.hotelservice.exception.AuthorizationUnavailableException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HotelAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(HotelAccessGuard.class);

    private final AuthAccessClient authAccessClient;
    private final HotelRepository hotelRepository;

    public HotelAccessGuard(AuthAccessClient authAccessClient, HotelRepository hotelRepository) {
        this.authAccessClient = authAccessClient;
        this.hotelRepository = hotelRepository;
    }

    public void requireCreate(UUID userId, UUID organizationId) {
        check(userId, organizationId, null, "HOTEL_CREATE");
    }

    public void requireHotelManage(UUID userId, UUID hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));

        if (hotel.getOrganizationId() == null) {
            throw new AccessDeniedException("Hotel has no owning organization; changes are locked until it is assigned");
        }

        check(userId, hotel.getOrganizationId(), hotelId, "HOTEL_MANAGE");
    }

    public void requireRoomManage(UUID userId, UUID hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));

        if (hotel.getOrganizationId() == null) {
            throw new AccessDeniedException("Hotel has no owning organization; changes are locked until it is assigned");
        }

        check(userId, hotel.getOrganizationId(), hotelId, "ROOM_MANAGE");
    }

    /** True for ADMIN or any active member of the organization. Fails closed (false) on missing identity or auth outage. */
    public boolean canReadOrganization(UUID userId, String role, UUID organizationId) {
        if ("ADMIN".equals(role)) {
            return true;
        }
        if (userId == null || organizationId == null) {
            return false;
        }
        try {
            AccessDecision decision = authAccessClient.check(userId, organizationId, null, "HOTEL_READ");
            return decision != null && decision.allowed();
        } catch (RuntimeException e) {
            log.warn("Authorization service unavailable for userId={}, permission=HOTEL_READ", userId);
            return false;
        }
    }

    private void check(UUID userId, UUID organizationId, UUID hotelId, String permission) {
        if (userId == null) {
            throw new AuthenticationRequiredException("Authentication required");
        }

        AccessDecision decision;
        try {
            decision = authAccessClient.check(userId, organizationId, hotelId, permission);
        } catch (RuntimeException e) {
            log.warn("Authorization service unavailable for userId={}, permission={}", userId, permission);
            throw new AuthorizationUnavailableException("Authorization service unavailable");
        }

        if (decision == null || !decision.allowed()) {
            throw new AccessDeniedException("Not allowed to perform this action");
        }
    }
}
