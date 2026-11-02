package com.waylo.carrentalservice.client;

import com.waylo.carrentalservice.exception.ApiException;
import com.waylo.carrentalservice.exception.ForbiddenException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthGatewayTest {

    private final AuthClient client = mock(AuthClient.class);
    private final AuthGateway gateway = new AuthGateway(client);
    private final UUID user = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();

    @Test
    void denied_is403() {
        when(client.check(user, org, "FLEET_MANAGE")).thenReturn(new AccessDecision(false, List.of()));
        assertThatThrownBy(() -> gateway.requireFleetManager(user, org)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void authDown_is503ForPartnerWrites() {
        when(client.check(user, org, "FLEET_MANAGE")).thenThrow(new RuntimeException("connection refused"));
        assertThatThrownBy(() -> gateway.requireFleetManager(user, org))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void nonCarRentalOrg_is403() {
        when(client.check(user, org, "FLEET_MANAGE")).thenReturn(new AccessDecision(true, List.of()));
        when(client.organization(org)).thenReturn(new OrgInfo(org, "Hotel", "ACTIVE", "HOTEL", "COMPANY"));
        assertThatThrownBy(() -> gateway.requireFleetManager(user, org)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void allowedCarRentalOrg_returnsOrg() {
        when(client.check(user, org, "FLEET_MANAGE")).thenReturn(new AccessDecision(true, List.of()));
        when(client.organization(org)).thenReturn(new OrgInfo(org, "Car", "ACTIVE", "CAR_RENTAL", "COMPANY"));
        assertThat(gateway.requireFleetManager(user, org).carRental()).isTrue();
    }

    @Test
    void missingOrganization_is404() {
        when(client.organization(org)).thenReturn(null);
        assertThatThrownBy(() -> gateway.organization(org)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lookupFailure_failsClosedAsEmpty() {
        when(client.organization(org)).thenThrow(new RuntimeException("down"));
        assertThat(gateway.lookupOrEmpty(org)).isEmpty();
    }
}
