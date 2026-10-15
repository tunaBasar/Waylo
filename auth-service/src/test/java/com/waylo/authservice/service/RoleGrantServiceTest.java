package com.waylo.authservice.service;

import com.waylo.authservice.client.HotelCatalogClient;
import com.waylo.authservice.client.HotelData;
import com.waylo.authservice.client.HotelLookupResponse;
import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationMembership;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.domain.Role;
import com.waylo.authservice.domain.RoleAssignment;
import com.waylo.authservice.domain.ScopeType;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.dto.RoleGrantRequest;
import com.waylo.authservice.dto.RoleGrantResult;
import com.waylo.authservice.repository.OrganizationMembershipRepository;
import com.waylo.authservice.repository.OrganizationRepository;
import com.waylo.authservice.repository.RoleAssignmentRepository;
import com.waylo.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleGrantServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID MEMBERSHIP_ID = UUID.randomUUID();
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final UUID OTHER_HOTEL_ID = UUID.randomUUID();
    private static final String TARGET_EMAIL = "target@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private HotelCatalogClient hotelCatalogClient;

    @InjectMocks
    private RoleGrantService roleGrantService;

    private User actor;
    private User target;
    private Organization organization;
    private OrganizationMembership actorMembership;
    private OrganizationMembership targetMembership;

    private java.lang.reflect.Field organizationIdField;
    private java.lang.reflect.Field membershipIdField;

    @BeforeEach
    void setUp() throws Exception {
        organizationIdField = Organization.class.getDeclaredField("id");
        organizationIdField.setAccessible(true);
        membershipIdField = OrganizationMembership.class.getDeclaredField("id");
        membershipIdField.setAccessible(true);

        actor = new User();
        actor.setId(ACTOR_ID);
        actor.setEmail("actor@example.com");

        target = new User();
        target.setId(USER_ID);
        target.setEmail(TARGET_EMAIL);

        organization = new Organization("Test Org");
        setOrganizationId(organization, ORGANIZATION_ID);

        actorMembership = new OrganizationMembership(actor, organization);
        setMembershipId(actorMembership, MEMBERSHIP_ID);

        targetMembership = new OrganizationMembership(target, organization);
        setMembershipId(targetMembership, UUID.randomUUID());
    }

    private void setOrganizationId(Organization org, UUID id) throws Exception {
        organizationIdField.set(org, id);
    }

    private void setMembershipId(OrganizationMembership mem, UUID id) throws Exception {
        membershipIdField.set(mem, id);
    }

    private RoleAssignment createActorOrgOwnerAssignment(OrganizationMembership mem) throws Exception {
        OrganizationMembership m = mem;
        UUID id = UUID.randomUUID();
        organizationIdField.set(m.getOrganization(), ORGANIZATION_ID);
        RoleAssignment a = new RoleAssignment(m, Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORGANIZATION_ID);
        return a;
    }

    private RoleAssignment createActorHotelOwnerAssignment(OrganizationMembership mem) throws Exception {
        RoleAssignment a = new RoleAssignment(mem, Role.HOTEL_OWNER, ScopeType.HOTEL, HOTEL_ID);
        return a;
    }

    private void mockSuccessfulHotelLookup() {
        when(hotelCatalogClient.getHotel(HOTEL_ID)).thenReturn(
                new HotelLookupResponse(true, new HotelData(HOTEL_ID, ORGANIZATION_ID)));
    }

    private void mockSuccessfulOtherHotelLookup(UUID otherHotelId) {
        when(hotelCatalogClient.getHotel(eq(otherHotelId))).thenReturn(
                new HotelLookupResponse(true, new HotelData(otherHotelId, ORGANIZATION_ID)));
    }

    private void mockHotelNotFound() {
        when(hotelCatalogClient.getHotel(any())).thenThrow(new RuntimeException("Hotel service unavailable"));
    }

    private void mockHotelOtherOrgLookup() {
        when(hotelCatalogClient.getHotel(HOTEL_ID)).thenReturn(
                new HotelLookupResponse(true, new HotelData(HOTEL_ID, UUID.randomUUID())));
    }

    // --- GRANT TESTS ---

    @Test
    void grantRole_organizationOwner_grants_MANAGER_ok() throws Exception {
        // Arrange
        RoleAssignment orgOwnerAssign = createActorOrgOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(orgOwnerAssign));
        mockSuccessfulHotelLookup();
        when(userRepository.findByEmail(TARGET_EMAIL)).thenReturn(Optional.of(target));
        when(membershipRepository.findByUserIdAndOrganizationId(USER_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(inv -> {
            OrganizationMembership saved = inv.getArgument(0);
            setMembershipId(saved, UUID.randomUUID());
            return saved;
        });
        when(roleAssignmentRepository.existsByMembershipIdAndRoleAndScopeTypeAndScopeId(any(), any(), any(), any()))
                .thenReturn(false);
        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act
        RoleGrantResult result = roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req);

        // Assert
        assertEquals(Role.HOTEL_MANAGER, result.role());
        assertEquals(HOTEL_ID, result.scopeId());
        assertEquals(ScopeType.HOTEL, result.scopeType());
        assertEquals(USER_ID, result.userId());
        verify(membershipRepository, times(2)).save(any(OrganizationMembership.class)); // membership create + permissionVersion bump
    }

    @Test
    void grantRole_hotelOwner_grants_MANAGER_on_own_hotel_ok() throws Exception {
        // Arrange
        RoleAssignment hotelOwnerAssign = createActorHotelOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(hotelOwnerAssign));
        mockSuccessfulHotelLookup();
        when(userRepository.findByEmail(TARGET_EMAIL)).thenReturn(Optional.of(target));
        when(membershipRepository.findByUserIdAndOrganizationId(USER_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(inv -> {
            OrganizationMembership saved = inv.getArgument(0);
            setMembershipId(saved, UUID.randomUUID());
            return saved;
        });
        when(roleAssignmentRepository.existsByMembershipIdAndRoleAndScopeTypeAndScopeId(any(), any(), any(), any()))
                .thenReturn(false);
        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act
        RoleGrantResult result = roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req);

        // Assert
        assertEquals(Role.HOTEL_MANAGER, result.role());
    }

    @Test
    void grantRole_hotelOwner_grants_HOTEL_OWNER_on_own_hotel_forbidden() throws Exception {
        // Arrange
        RoleAssignment hotelOwnerAssign = createActorHotelOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(hotelOwnerAssign));

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_OWNER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Cannot grant a role beyond your own authority", ex.getReason());
    }

    @Test
    void grantRole_hotelOwner_grants_on_other_hotel_forbidden() throws Exception {
        // Arrange
        RoleAssignment hotelOwnerAssign = createActorHotelOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(hotelOwnerAssign));
        // No hotel lookup needed - auth check fails before reaching hotel ownership check

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, OTHER_HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Cannot grant a role beyond your own authority", ex.getReason());
    }

    @Test
    void grantRole_manager_grants_anything_forbidden() throws Exception {
        // Arrange - actor has no HOTEL_OWNER or ORGANIZATION_OWNER assignment
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of());

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void grantRole_nonMember_forbidden() throws Exception {
        // Arrange
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void grantRole_organizationsOwnerRole_returns400() {
        // ORGANIZATION_OWNER is not in GRANTABLE_ROLES, so it's rejected before any repository call
        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.ORGANIZATION_OWNER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("ORGANIZATION_OWNER cannot be granted here", ex.getReason());
    }

    @Test
    void grantRole_hotelOfAnotherOrg_forbidden() throws Exception {
        // Arrange
        RoleAssignment orgOwnerAssign = createActorOrgOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(orgOwnerAssign));
        mockHotelOtherOrgLookup();

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Hotel does not belong to this organization", ex.getReason());
    }

    @Test
    void grantRole_hotelServiceThrows_forbidden() throws Exception {
        // Arrange
        RoleAssignment orgOwnerAssign = createActorOrgOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(orgOwnerAssign));
        mockHotelNotFound();

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Hotel does not belong to this organization", ex.getReason());
    }

    @Test
    void grantRole_suspendedOrg_returns409() throws Exception {
        // Arrange
        organization.setStatus(OrganizationStatus.SUSPENDED);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void grantRole_unknownEmail_returns404() throws Exception {
        // Arrange
        RoleAssignment orgOwnerAssign = createActorOrgOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(orgOwnerAssign));
        mockSuccessfulHotelLookup();
        when(userRepository.findByEmail(TARGET_EMAIL)).thenReturn(Optional.empty());

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void grantRole_duplicate_returns409() throws Exception {
        // Arrange
        RoleAssignment orgOwnerAssign = createActorOrgOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(orgOwnerAssign));
        mockSuccessfulHotelLookup();
        when(userRepository.findByEmail(TARGET_EMAIL)).thenReturn(Optional.of(target));
        when(membershipRepository.findByUserIdAndOrganizationId(USER_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(targetMembership));
        when(roleAssignmentRepository.existsByMembershipIdAndRoleAndScopeTypeAndScopeId(any(), any(), any(), any()))
                .thenReturn(true);

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void grantRole_permissionVersionIncremented() throws Exception {
        // Arrange
        RoleAssignment orgOwnerAssign = createActorOrgOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(orgOwnerAssign));
        mockSuccessfulHotelLookup();
        when(userRepository.findByEmail(TARGET_EMAIL)).thenReturn(Optional.of(target));
        when(membershipRepository.findByUserIdAndOrganizationId(USER_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<OrganizationMembership> memberCaptor = ArgumentCaptor.forClass(OrganizationMembership.class);
        when(membershipRepository.save(memberCaptor.capture())).thenReturn(targetMembership);
        when(roleAssignmentRepository.existsByMembershipIdAndRoleAndScopeTypeAndScopeId(any(), any(), any(), any()))
                .thenReturn(false);
        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_MANAGER, HOTEL_ID);

        // Act
        roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req);

        // Assert - membership was saved twice: once for creation, once for permissionVersion bump
        assertEquals(2, memberCaptor.getAllValues().size());
    }

    // --- REVOKE TESTS ---

    @Test
    void revokeRole_organizationsOwnerRole_returns400() throws Exception {
        // Arrange
        UUID grantId = UUID.randomUUID();
        RoleAssignment orgOwnerAssign = createActorOrgOwnerAssignment(actorMembership);
        RoleAssignment grant = new RoleAssignment(actorMembership, Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORGANIZATION_ID);
        when(roleAssignmentRepository.findByIdAndOrganizationId(grantId, ORGANIZATION_ID)).thenReturn(grant);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roleGrantService.revokeRole(ACTOR_ID, ORGANIZATION_ID, grantId));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void revokeRole_hotelOwner_revokes_MANAGER_on_own_hotel_ok() throws Exception {
        // Arrange
        UUID grantId = UUID.randomUUID();
        RoleAssignment actorHotelOwnerAssign = createActorHotelOwnerAssignment(actorMembership);
        RoleAssignment grant = new RoleAssignment(targetMembership, Role.HOTEL_MANAGER, ScopeType.HOTEL, HOTEL_ID);
        setOrganizationId(grant.getMembership().getOrganization(), ORGANIZATION_ID);

        when(roleAssignmentRepository.findByIdAndOrganizationId(grantId, ORGANIZATION_ID)).thenReturn(grant);
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(actorHotelOwnerAssign));
        ArgumentCaptor<OrganizationMembership> memberCaptor = ArgumentCaptor.forClass(OrganizationMembership.class);
        when(membershipRepository.save(memberCaptor.capture())).thenReturn(actorMembership);

        // Act
        roleGrantService.revokeRole(ACTOR_ID, ORGANIZATION_ID, grantId);

        // Assert
        verify(roleAssignmentRepository, times(1)).delete(grant);
    }

    @Test
    void grantRole_hotelOwner_grants_STAFF_on_own_hotel_ok() throws Exception {
        // Arrange
        RoleAssignment hotelOwnerAssign = createActorHotelOwnerAssignment(actorMembership);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
        when(membershipRepository.findByUserIdAndOrganizationId(ACTOR_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(actorMembership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(hotelOwnerAssign));
        mockSuccessfulHotelLookup();
        when(userRepository.findByEmail(TARGET_EMAIL)).thenReturn(Optional.of(target));
        when(membershipRepository.findByUserIdAndOrganizationId(USER_ID, ORGANIZATION_ID)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(inv -> {
            OrganizationMembership saved = inv.getArgument(0);
            setMembershipId(saved, UUID.randomUUID());
            return saved;
        });
        when(roleAssignmentRepository.existsByMembershipIdAndRoleAndScopeTypeAndScopeId(any(), any(), any(), any()))
                .thenReturn(false);
        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleGrantRequest req = new RoleGrantRequest(TARGET_EMAIL, Role.HOTEL_STAFF, HOTEL_ID);

        // Act
        RoleGrantResult result = roleGrantService.grantRole(ACTOR_ID, ORGANIZATION_ID, req);

        // Assert
        assertEquals(Role.HOTEL_STAFF, result.role());
    }
}
