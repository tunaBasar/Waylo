package com.waylo.authservice.service;

import com.waylo.authservice.domain.*;
import com.waylo.authservice.dto.AccessDecision;
import com.waylo.authservice.repository.AccessQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {

    @Mock
    private AccessQueryRepository accessQueryRepository;

    @InjectMocks
    private AccessService accessService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOTEL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_HOTEL_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    // ---- Null-input tests ----

    @Nested
    @DisplayName("Null input handling")
    class NullInputTests {

        @Test
        @DisplayName("null userId -> denied")
        void nullUserId_denied() {
            AccessDecision decision = accessService.check(null, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertFalse(decision.allowed());
            assertTrue(decision.roles().isEmpty());
        }

        @Test
        @DisplayName("null organizationId -> denied")
        void nullOrganizationId_denied() {
            AccessDecision decision = accessService.check(USER_ID, null, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertFalse(decision.allowed());
            assertTrue(decision.roles().isEmpty());
        }

        @Test
        @DisplayName("null hotelId with ORGANIZATION_OWNER -> allowed")
        void nullHotelId_orgOwner_allowed() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, null, Permission.HOTEL_CREATE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("null hotelId with HOTEL_MANAGER -> denied")
        void nullHotelId_hotelManager_denied() {
            var grant = makeGrant(Role.HOTEL_MANAGER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, null, Permission.HOTEL_MANAGE);
            assertFalse(decision.allowed());
        }

        @Test
        @DisplayName("null permission -> denied")
        void nullPermission_denied() {
            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, null);
            assertFalse(decision.allowed());
            assertTrue(decision.roles().isEmpty());
        }
    }

    // ---- ORGANIZATION_OWNER tests ----

    @Nested
    @DisplayName("ORGANIZATION_OWNER")
    class OrganizationOwnerTests {

        @Test
        @DisplayName("ORGANIZATION_OWNER allows HOTEL_CREATE")
        void orgOwner_allowsHotelCreate() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_CREATE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("ORGANIZATION_OWNER allows HOTEL_MANAGE for any hotel")
        void orgOwner_allowsHotelManageAnyHotel() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("ORGANIZATION_OWNER allows ROOM_MANAGE for any hotel")
        void orgOwner_allowsRoomManageAnyHotel() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.ROOM_MANAGE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("ORGANIZATION_OWNER allows with null hotelId")
        void orgOwner_nullHotelId_allowsCreate() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, null, Permission.HOTEL_MANAGE);
            assertTrue(decision.allowed());
        }
    }

    // ---- HOTEL_MANAGER tests ----

    @Nested
    @DisplayName("HOTEL_MANAGER")
    class HotelManagerTests {

        @Test
        @DisplayName("HOTEL_MANAGER allowed on own hotel for HOTEL_MANAGE")
        void hotelManager_ownHotel_allowsManage() {
            var grant = makeGrant(Role.HOTEL_MANAGER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("HOTEL_MANAGER allowed on own hotel for ROOM_MANAGE")
        void hotelManager_ownHotel_allowsRoomManage() {
            var grant = makeGrant(Role.HOTEL_MANAGER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.ROOM_MANAGE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("HOTEL_MANAGER denied HOTEL_CREATE even on own hotel")
        void hotelManager_deniedHotelCreate() {
            var grant = makeGrant(Role.HOTEL_MANAGER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_CREATE);
            assertFalse(decision.allowed());
        }

        @Test
        @DisplayName("HOTEL_MANAGER denied on another hotel")
        void hotelManager_anotherHotel_denied() {
            var grant = makeGrant(Role.HOTEL_MANAGER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, OTHER_HOTEL_ID, Permission.HOTEL_MANAGE);
            assertFalse(decision.allowed());
        }

        @Test
        @DisplayName("HOTEL_MANAGER denied on another hotel for ROOM_MANAGE")
        void hotelManager_anotherHotel_deniedRoomManage() {
            var grant = makeGrant(Role.HOTEL_MANAGER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, OTHER_HOTEL_ID, Permission.ROOM_MANAGE);
            assertFalse(decision.allowed());
        }
    }

    // ---- HOTEL_OWNER tests ----

    @Nested
    @DisplayName("HOTEL_OWNER")
    class HotelOwnerTests {

        @Test
        @DisplayName("HOTEL_OWNER allowed on own hotel for HOTEL_MANAGE")
        void hotelOwner_ownHotel_allowsManage() {
            var grant = makeGrant(Role.HOTEL_OWNER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("HOTEL_OWNER allowed on own hotel for ROOM_MANAGE")
        void hotelOwner_ownHotel_allowsRoomManage() {
            var grant = makeGrant(Role.HOTEL_OWNER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.ROOM_MANAGE);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("HOTEL_OWNER denied HOTEL_CREATE")
        void hotelOwner_deniedHotelCreate() {
            var grant = makeGrant(Role.HOTEL_OWNER, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_CREATE);
            assertFalse(decision.allowed());
        }
    }

    // ---- HOTEL_STAFF tests ----

    @Nested
    @DisplayName("HOTEL_STAFF")
    class HotelStaffTests {

        @Test
        @DisplayName("HOTEL_STAFF denied all permissions")
        void hotelStaff_deniedAll() {
            var grant = makeGrant(Role.HOTEL_STAFF, ScopeType.HOTEL, HOTEL_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            assertFalse(accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_CREATE).allowed());
            assertFalse(accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE).allowed());
            assertFalse(accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.ROOM_MANAGE).allowed());
        }
    }

    // ---- No grants tests ----

    @Nested
    @DisplayName("No grants (empty result)")
    class NoGrantsTests {

        @Test
        @DisplayName("No active grants -> denied")
        void noGrants_denied() {
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of());

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertFalse(decision.allowed());
            assertTrue(decision.roles().isEmpty());
        }
    }

    // ---- Repository exception tests ----

    @Nested
    @DisplayName("Repository exception -> fail closed")
    class RepositoryExceptionTests {

        @Test
        @DisplayName("Repository throws -> denied")
        void repositoryThrows_denied() {
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID))
                    .thenThrow(new RuntimeException("DB connection failed"));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertFalse(decision.allowed());
            assertTrue(decision.roles().isEmpty());
        }
    }

    // ---- Role names test ----

    @Nested
    @DisplayName("Role names in response")
    class RoleNamesTests {

        @Test
        @DisplayName("Response includes role names when allowed")
        void responseContainsRoleNames() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of(grant));

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_CREATE);
            assertTrue(decision.allowed());
            assertEquals(List.of("ORGANIZATION_OWNER"), decision.roles());
        }

        @Test
        @DisplayName("Empty roles when denied due to no grants")
        void emptyRolesOnDenied() {
            when(accessQueryRepository.findActiveGrants(USER_ID, ORG_ID)).thenReturn(List.of());

            AccessDecision decision = accessService.check(USER_ID, ORG_ID, HOTEL_ID, Permission.HOTEL_MANAGE);
            assertFalse(decision.allowed());
            assertTrue(decision.roles().isEmpty());
        }
    }

    // ---- Direct evaluation tests (static method) ----

    @Nested
    @DisplayName("Direct evaluation logic")
    class DirectEvaluationTests {

        @Test
        @DisplayName("evaluate returns true for org owner")
        void evaluate_orgOwner_true() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
            assertTrue(AccessService.evaluate(List.of(grant), HOTEL_ID, Permission.HOTEL_CREATE));
        }

        @Test
        @DisplayName("evaluate returns false for org owner without org scope")
        void evaluate_orgOwner_noOrgScope_false() {
            var grant = makeGrant(Role.ORGANIZATION_OWNER, ScopeType.HOTEL, HOTEL_ID);
            assertFalse(AccessService.evaluate(List.of(grant), HOTEL_ID, Permission.HOTEL_CREATE));
        }
    }

    // ---- Helper: create a mocked RoleAssignment ----

    private RoleAssignment makeGrant(Role role, ScopeType scopeType, UUID scopeId) {
        RoleAssignment grant = org.mockito.Mockito.mock(RoleAssignment.class);
        org.mockito.Mockito.when(grant.getRole()).thenReturn(role);
        org.mockito.Mockito.when(grant.getScopeType()).thenReturn(scopeType);
        org.mockito.Mockito.when(grant.getScopeId()).thenReturn(scopeId);

        // membership and organization mocks are lenient — only needed to avoid constructor
        // exceptions if real entities were used; tests use pure mocks.
        var membership = org.mockito.Mockito.mock(com.waylo.authservice.domain.OrganizationMembership.class);
        var organization = org.mockito.Mockito.mock(com.waylo.authservice.domain.Organization.class);
        org.mockito.Mockito.lenient().when(organization.getId()).thenReturn(ORG_ID);
        org.mockito.Mockito.lenient().when(membership.getOrganization()).thenReturn(organization);
        org.mockito.Mockito.lenient().when(grant.getMembership()).thenReturn(membership);

        return grant;
    }
}
