package com.waylo.notificationservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/** auth-service internal lookup of partner recipients. Requires a signed X-Service-Token (added globally, like CrmServiceClient). */
@FeignClient(name = "auth-service", contextId = "authOrganizationClient", path = "/internal/organizations")
public interface AuthOrganizationClient {

    /** E-mails of ACTIVE ORGANIZATION_OWNER / HOTEL_OWNER members. Plain body, no ApiResponse wrapper. */
    @GetMapping("/{id}/contacts")
    OrganizationContacts contacts(@PathVariable("id") UUID organizationId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrganizationContacts(List<String> emails) {}
}
