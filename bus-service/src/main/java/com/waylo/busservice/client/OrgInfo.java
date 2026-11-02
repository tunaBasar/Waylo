package com.waylo.busservice.client;

import java.util.UUID;

public record OrgInfo(UUID id, String name, String status, String businessType, String legalType) {

    public boolean active() { return "ACTIVE".equals(status); }

    public boolean bus() { return "BUS".equals(businessType); }
}
