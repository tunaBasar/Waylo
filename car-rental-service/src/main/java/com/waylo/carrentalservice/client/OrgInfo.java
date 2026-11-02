package com.waylo.carrentalservice.client;

import java.util.UUID;

public record OrgInfo(UUID id, String name, String status, String businessType, String legalType) {

    public boolean active() { return "ACTIVE".equals(status); }

    public boolean carRental() { return "CAR_RENTAL".equals(businessType); }

    public boolean individual() { return "INDIVIDUAL".equals(legalType); }
}
