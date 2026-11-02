package com.waylo.carrentalservice.domain;

import java.util.List;

public enum DocumentType {
    RENTAL_AUTHORIZATION(false, true),
    TAX_CERTIFICATE(false, false),
    IDENTITY_DOCUMENT(false, true),
    VEHICLE_REGISTRATION(true, true),
    RENTAL_INSURANCE(true, true),
    TRAFFIC_INSURANCE(true, true);

    private final boolean vehicleLevel;
    private final boolean validUntilRequired;

    DocumentType(boolean vehicleLevel, boolean validUntilRequired) {
        this.vehicleLevel = vehicleLevel;
        this.validUntilRequired = validUntilRequired;
    }

    public boolean isVehicleLevel() { return vehicleLevel; }

    public boolean isValidUntilRequired() { return validUntilRequired; }

    /** Org-level types required for the given legal type (COMPANY or INDIVIDUAL). */
    public static List<DocumentType> requiredForOrganization(String legalType) {
        return "INDIVIDUAL".equals(legalType)
                ? List.of(IDENTITY_DOCUMENT)
                : List.of(RENTAL_AUTHORIZATION, TAX_CERTIFICATE);
    }

    public static List<DocumentType> vehicleTypes() {
        return List.of(VEHICLE_REGISTRATION, RENTAL_INSURANCE, TRAFFIC_INSURANCE);
    }
}
