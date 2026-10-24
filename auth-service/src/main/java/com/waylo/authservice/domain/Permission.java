package com.waylo.authservice.domain;

/** Internal permissions evaluated by the access-check endpoint. */
public enum Permission {
    HOTEL_CREATE,
    HOTEL_MANAGE,
    ROOM_MANAGE,
    /** Read non-public hotels of the organization: any active member. */
    HOTEL_READ,
    /** See reservations/order items of the organization: ORGANIZATION_OWNER, HOTEL_OWNER, HOTEL_MANAGER, HOTEL_STAFF. */
    RESERVATION_READ
}
