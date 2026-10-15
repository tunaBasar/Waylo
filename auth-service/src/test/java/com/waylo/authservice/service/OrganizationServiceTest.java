package com.waylo.authservice.service;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationMembership;
import com.waylo.authservice.domain.Role;
import com.waylo.authservice.domain.RoleAssignment;
import com.waylo.authservice.domain.ScopeType;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.dto.CreateOrganizationRequest;
import com.waylo.authservice.dto.MembershipResponse;
import com.waylo.authservice.dto.OrganizationResponse;
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MEMBERSHIP_ID = UUID.randomUUID();
    private static final String ORG_NAME = "Acme Corp";

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @InjectMocks
    private OrganizationService organizationService;

    private User user;
    private Organization organization;
    private OrganizationMembership membership;
    private RoleAssignment assignment;

    private Field organizationIdField;
    private Field membershipIdField;

    @BeforeEach
    void setUp() throws Exception {
        user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");

        // Set up cached field references FIRST
        organizationIdField = Organization.class.getDeclaredField("id");
        organizationIdField.setAccessible(true);
        membershipIdField = OrganizationMembership.class.getDeclaredField("id");
        membershipIdField.setAccessible(true);

        organization = new Organization(ORG_NAME);
        setOrganizationId(organization, ORG_ID);

        membership = new OrganizationMembership(user, organization);
        setMembershipId(membership, MEMBERSHIP_ID);
        assignment = new RoleAssignment(membership, Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, ORG_ID);
    }

    private void setOrganizationId(Organization org, UUID id) throws Exception {
        organizationIdField.set(org, id);
    }

    private void setMembershipId(OrganizationMembership mem, UUID id) throws Exception {
        membershipIdField.set(mem, id);
    }

    @Test
    void createOrganization_savesOrgMembershipAndOwner() throws Exception {
        // Arrange
        CreateOrganizationRequest req = new CreateOrganizationRequest(" Acme Corp ");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(membershipRepository.countOwnedOrganizations(USER_ID)).thenReturn(0L);
        when(organizationRepository.saveAndFlush(any(Organization.class))).thenAnswer(invocation -> {
            Organization saved = invocation.getArgument(0);
            setOrganizationId(saved, ORG_ID);
            return saved;
        });
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(invocation -> {
            OrganizationMembership saved = invocation.getArgument(0);
            setMembershipId(saved, MEMBERSHIP_ID);
            return saved;
        });
        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        OrganizationResponse response = organizationService.createOrganization(USER_ID, req);

        // Assert
        assertNotNull(response);
        assertEquals(ORG_ID, response.id());
        assertEquals(ORG_NAME, response.name());
        assertEquals(com.waylo.authservice.domain.OrganizationStatus.PENDING, response.status());

        // Verify persistence order: org first via saveAndFlush, then membership, then assignment
        verify(organizationRepository, times(1)).saveAndFlush(any(Organization.class));
        verify(membershipRepository, times(1)).save(any(OrganizationMembership.class));
        verify(roleAssignmentRepository, times(1)).save(any(RoleAssignment.class));
    }

    @Test
    void createOrganization_unknownUser_returns404() {
        // Arrange
        CreateOrganizationRequest req = new CreateOrganizationRequest("Test Org");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationService.createOrganization(USER_ID, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());

        verifyNoInteractions(organizationRepository, membershipRepository, roleAssignmentRepository);
    }

    @Test
    void createOrganization_assignsORGANIZATION_OWNERWithOrganizationScope() throws Exception {
        // Arrange
        CreateOrganizationRequest req = new CreateOrganizationRequest("Test Org");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(membershipRepository.countOwnedOrganizations(USER_ID)).thenReturn(0L);
        when(organizationRepository.saveAndFlush(any(Organization.class))).thenAnswer(invocation -> {
            Organization saved = invocation.getArgument(0);
            setOrganizationId(saved, ORG_ID);
            return saved;
        });
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(invocation -> {
            OrganizationMembership saved = invocation.getArgument(0);
            setMembershipId(saved, MEMBERSHIP_ID);
            return saved;
        });
        ArgumentCaptor<RoleAssignment> assignmentCaptor = ArgumentCaptor.forClass(RoleAssignment.class);

        // Act
        organizationService.createOrganization(USER_ID, req);

        // Assert
        verify(roleAssignmentRepository).save(assignmentCaptor.capture());
        RoleAssignment captured = assignmentCaptor.getValue();
        assertEquals(Role.ORGANIZATION_OWNER, captured.getRole());
        assertEquals(ScopeType.ORGANIZATION, captured.getScopeType());
        assertEquals(ORG_ID, captured.getScopeId());
    }

    @Test
    void listMemberships_mapsRolesCorrectly() {
        // Arrange
        when(membershipRepository.findByUserId(USER_ID)).thenReturn(List.of(membership));
        when(roleAssignmentRepository.findByMembershipId(MEMBERSHIP_ID)).thenReturn(List.of(assignment));

        // Act
        List<MembershipResponse> results = organizationService.listMemberships(USER_ID);

        // Assert
        assertEquals(1, results.size());
        MembershipResponse resp = results.get(0);
        assertEquals(MEMBERSHIP_ID, resp.membershipId());
        assertEquals(ORG_ID, resp.organizationId());
        assertEquals(ORG_NAME, resp.organizationName());
        assertEquals(com.waylo.authservice.domain.OrganizationStatus.PENDING, resp.organizationStatus());
        assertEquals(com.waylo.authservice.domain.MembershipStatus.ACTIVE, resp.status());
        assertEquals(1, resp.roles().size());
        assertEquals(Role.ORGANIZATION_OWNER, resp.roles().get(0).role());
        assertEquals(ScopeType.ORGANIZATION, resp.roles().get(0).scopeType());
        assertEquals(ORG_ID, resp.roles().get(0).scopeId());
    }

    @Test
    void listMemberships_emptyListForUser() {
        // Arrange
        when(membershipRepository.findByUserId(USER_ID)).thenReturn(List.of());

        // Act
        List<MembershipResponse> results = organizationService.listMemberships(USER_ID);

        // Assert
        assertTrue(results.isEmpty());
        verifyNoInteractions(roleAssignmentRepository);
    }

    @Test
    void createOrganization_limitReached_returns409() {
        // Arrange
        CreateOrganizationRequest req = new CreateOrganizationRequest("Test Org");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(membershipRepository.countOwnedOrganizations(USER_ID)).thenReturn(5L);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationService.createOrganization(USER_ID, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Organization limit reached", ex.getReason());

        verify(organizationRepository, never()).saveAndFlush(any());
        verify(membershipRepository, never()).save(any());
        verify(roleAssignmentRepository, never()).save(any());
    }
}
