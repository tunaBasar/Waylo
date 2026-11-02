package com.waylo.authservice.domain;

/** Internal permissions evaluated by the access-check endpoint. */
public enum Permission {
    HOTEL_CREATE,
    HOTEL_MANAGE,
    ROOM_MANAGE,
    /** Read non-public hotels of the organization: any active member. */
    HOTEL_READ,
    /** See reservations/order items of the organization: ORGANIZATION_OWNER, HOTEL_OWNER, HOTEL_MANAGER, HOTEL_STAFF. */
    RESERVATION_READ,
    /** Redeem a customer voucher (releases escrow): same roles as RESERVATION_READ. */
    RESERVATION_REDEEM,
    /** Manage vehicles for CAR_RENTAL orgs: ORGANIZATION_OWNER of a CAR_RENTAL org only. */
    FLEET_MANAGE,
    /** Manage buses, trips and documents for BUS orgs: ORGANIZATION_OWNER of a BUS org only. */
    BUS_MANAGE
}
