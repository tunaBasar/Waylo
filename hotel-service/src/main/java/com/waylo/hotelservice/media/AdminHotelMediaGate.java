package com.waylo.hotelservice.media;

import com.waylo.hotelservice.exception.AccessDeniedException;

final class AdminHotelMediaGate {

    private AdminHotelMediaGate() { }

    static void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("Administrator role required");
        }
    }
}
