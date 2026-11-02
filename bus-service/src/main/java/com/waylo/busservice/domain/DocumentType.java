package com.waylo.busservice.domain;

import java.util.List;

public enum DocumentType {
    TRANSPORT_AUTHORIZATION(false, true),
    TAX_CERTIFICATE(false, false),
    VEHICLE_REGISTRATION(true, true),
    VEHICLE_CARD(true, true),
    TRAFFIC_INSURANCE(true, true),
    SEAT_ACCIDENT_INSURANCE(true, true);

    private final boolean busLevel;
    private final boolean validUntilRequired;

    DocumentType(boolean busLevel, boolean validUntilRequired) {
        this.busLevel = busLevel;
        this.validUntilRequired = validUntilRequired;
    }

    public boolean isBusLevel() { return busLevel; }

    public boolean isValidUntilRequired() { return validUntilRequired; }

    public static List<DocumentType> requiredForOrganization() { return List.of(TRANSPORT_AUTHORIZATION, TAX_CERTIFICATE); }

    public static List<DocumentType> busTypes() {
        return List.of(VEHICLE_REGISTRATION, VEHICLE_CARD, TRAFFIC_INSURANCE, SEAT_ACCIDENT_INSURANCE);
    }
}
