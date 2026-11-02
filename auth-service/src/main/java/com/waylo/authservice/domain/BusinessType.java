package com.waylo.authservice.domain;

/**
 * Business category of the organization.
 * Existing (pre-migration) rows default to HOTEL.
 */
public enum BusinessType {
    HOTEL,
    CAR_RENTAL,
    BUS
}
