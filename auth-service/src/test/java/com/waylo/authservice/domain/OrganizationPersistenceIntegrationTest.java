package com.waylo.authservice.domain;

import jakarta.persistence.OptimisticLockException;
import org.flywaydb.core.Flyway;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Uses only an explicitly supplied, empty *_test PostgreSQL database. No application/Kafka startup. */
class OrganizationPersistenceIntegrationTest {
    private static String url;
    private static String username;
    private static String password;
    private static SessionFactory sessions;
    private static final UUID LEGACY_USER = UUID.randomUUID();

    @BeforeAll
    static void migrateAndValidateRealMappings() throws Exception {
        url = System.getenv("AUTH_TEST_DB_URL");
        assertNotNull(url, "Run integrationTest with a dedicated PostgreSQL test database");
        assertTrue(url.matches("jdbc:postgresql://[^/]+/[a-zA-Z0-9_]+_test(?:\\?.*)?"),
                "Database name must end in _test");
        username = System.getenv().getOrDefault("AUTH_TEST_DB_USERNAME", "auth_test");
        password = System.getenv().getOrDefault("AUTH_TEST_DB_PASSWORD", "auth_test");
        try (Connection connection = connection();
             ResultSet rows = connection.createStatement().executeQuery(
                     "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")) {
            rows.next();
            assertEquals(0, rows.getInt(1), "Refusing to migrate a non-empty test database");
        }
        Flyway.configure().dataSource(url, username, password).target("1").load().migrate();
        execute("INSERT INTO users(id,first_name,last_name,email,password,role,email_verified) VALUES (?,?,?,?,?,?,?)",
                LEGACY_USER, "Legacy", "User", "legacy@example.invalid", "existing-password-hash", "USER", true);
        Flyway flyway = Flyway.configure().dataSource(url, username, password).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();

        sessions = new Configuration()
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Organization.class)
                .addAnnotatedClass(OrganizationMembership.class)
                .addAnnotatedClass(RoleAssignment.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", username)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .buildSessionFactory();
    }

    @AfterAll
    static void closeMappings() {
        if (sessions != null) sessions.close();
    }

    @Test
    void upgradePreservesExistingUserAndGlobalRole() {
        try (Session session = sessions.openSession()) {
            User user = session.find(User.class, LEGACY_USER);
            assertEquals("legacy@example.invalid", user.getEmail());
            assertEquals("existing-password-hash", user.getPassword());
            assertEquals(UserRole.USER, user.getRole());
            assertTrue(user.isEmailVerified());
        }
    }

    @Test
    void sameUserCanHaveDifferentRolesInTwoOrganizationsAndLazyRelations() {
        UUID membershipId;
        UUID roleId;
        try (Session session = sessions.openSession()) {
            var transaction = session.beginTransaction();
            Organization a = new Organization("Hotel Group A");
            Organization b = new Organization("Hotel Group B");
            session.persist(a);
            session.persist(b);
            User user = session.getReference(User.class, LEGACY_USER);
            OrganizationMembership ma = new OrganizationMembership(user, a);
            OrganizationMembership mb = new OrganizationMembership(user, b);
            session.persist(ma);
            session.persist(mb);
            RoleAssignment owner = new RoleAssignment(ma, Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, a.getId());
            RoleAssignment staff = new RoleAssignment(mb, Role.HOTEL_STAFF, ScopeType.HOTEL, UUID.randomUUID());
            session.persist(owner);
            session.persist(staff);
            session.flush();
            assertNotNull(a.getCreatedAt());
            assertNotNull(a.getUpdatedAt());
            assertNotNull(ma.getCreatedAt());
            assertNotNull(owner.getCreatedAt());
            membershipId = ma.getId();
            roleId = owner.getId();
            transaction.commit();
        }
        try (Session session = sessions.openSession()) {
            RoleAssignment grant = session.find(RoleAssignment.class, roleId);
            assertFalse(Hibernate.isInitialized(grant.getMembership()));
            assertEquals(Role.ORGANIZATION_OWNER, grant.getRole());
            OrganizationMembership membership = session.find(OrganizationMembership.class, membershipId);
            assertFalse(Hibernate.isInitialized(membership.getUser()));
            assertFalse(Hibernate.isInitialized(membership.getOrganization()));
            assertEquals(grant.getOrganizationId(), membership.getOrganization().getId());
        }
    }

    @Test
    void createsAssignmentThroughAnUninitializedManagedMembershipProxy() throws Exception {
        Fixture fixture = fixture();
        UUID assignmentId;
        UUID hotelId = UUID.randomUUID();
        try (Session session = sessions.openSession()) {
            var transaction = session.beginTransaction();
            OrganizationMembership membership = session.getReference(
                    OrganizationMembership.class, fixture.membershipId());
            assertFalse(Hibernate.isInitialized(membership));
            RoleAssignment assignment = new RoleAssignment(
                    membership, Role.HOTEL_STAFF, ScopeType.HOTEL, hotelId);
            session.persist(assignment);
            transaction.commit();
            assignmentId = assignment.getId();
        }
        try (Session session = sessions.openSession()) {
            RoleAssignment assignment = session.find(RoleAssignment.class, assignmentId);
            assertEquals(fixture.organizationId(), assignment.getOrganizationId());
            assertEquals(fixture.membershipId(), assignment.getMembership().getId());
            assertEquals(hotelId, assignment.getScopeId());
        }
    }

    @Test
    void rejectsDuplicateMembership() throws Exception {
        Fixture fixture = fixture();
        fails("23505", "INSERT INTO organization_memberships(user_id,organization_id) VALUES (?,?)",
                LEGACY_USER, fixture.organizationId());
    }

    @Test
    void rejectsDuplicateRoleAndScope() throws Exception {
        Fixture fixture = fixture();
        fails("23505", "INSERT INTO role_assignments(membership_id,organization_id,role,scope_type,scope_id) VALUES (?,?,?,?,?)",
                fixture.membershipId(), fixture.organizationId(), "HOTEL_OWNER", "ORGANIZATION", fixture.organizationId());
    }

    @Test
    void rejectsForgedAssignmentOrganization() throws Exception {
        Fixture fixture = fixture();
        UUID other = UUID.randomUUID();
        fails("23503", "INSERT INTO role_assignments(membership_id,organization_id,role,scope_type,scope_id) VALUES (?,?,?,?,?)",
                fixture.membershipId(), other, "HOTEL_STAFF", "HOTEL", UUID.randomUUID());
    }

    @Test
    void rejectsAnotherOrganizationAsScopeEvenThroughRawSql() throws Exception {
        Fixture fixture = fixture();
        fails("23514", "INSERT INTO role_assignments(membership_id,organization_id,role,scope_type,scope_id) VALUES (?,?,?,?,?)",
                fixture.membershipId(), fixture.organizationId(), "HOTEL_STAFF", "ORGANIZATION", UUID.randomUUID());
    }

    @Test
    void rejectsOrganizationOwnerWithHotelScope() throws Exception {
        Fixture fixture = fixture();
        fails("23514", "INSERT INTO role_assignments(membership_id,organization_id,role,scope_type,scope_id) VALUES (?,?,?,?,?)",
                fixture.membershipId(), fixture.organizationId(), "ORGANIZATION_OWNER", "HOTEL", UUID.randomUUID());
    }

    @Test
    void rejectsNullScopeAndMissingMembership() throws Exception {
        Fixture fixture = fixture();
        fails("23502", "INSERT INTO role_assignments(membership_id,organization_id,role,scope_type,scope_id) VALUES (?,?,?,?,?)",
                fixture.membershipId(), fixture.organizationId(), "HOTEL_STAFF", "HOTEL", null);
        fails("23503", "INSERT INTO role_assignments(membership_id,organization_id,role,scope_type,scope_id) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), fixture.organizationId(), "HOTEL_STAFF", "HOTEL", UUID.randomUUID());
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "scope_type", "status", "permission_version"})
    void rejectsInvalidEnumsAndNegativePermissionVersion(String column) throws Exception {
        Fixture fixture = fixture();
        String table = column.equals("status") || column.equals("permission_version")
                ? "organization_memberships" : "role_assignments";
        String value = column.equals("permission_version") ? "-1" : "'INVALID'";
        UUID id = table.equals("role_assignments") ? fixture.assignmentId() : fixture.membershipId();
        fails("23514", "UPDATE " + table + " SET " + column + "=" + value + " WHERE id=?", id);
    }

    @Test
    void rejectsEmptyNameAndInvalidOrganizationStatus() throws Exception {
        fails("23514", "INSERT INTO organizations(name) VALUES (?)", " ");
        fails("23514", "INSERT INTO organizations(name,status) VALUES (?,?)", "Example", "INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "organizations", "organization_memberships"})
    void deletionCannotCascadeAwayMembershipOrGrants(String table) throws Exception {
        Fixture fixture = fixture();
        UUID id = switch (table) {
            case "users" -> LEGACY_USER;
            case "organizations" -> fixture.organizationId();
            default -> fixture.membershipId();
        };
        fails("23503", "DELETE FROM " + table + " WHERE id=?", id);
    }

    @Test
    void membershipWithExistingGrantCannotBeMovedToAnotherOrganization() throws Exception {
        Fixture fixture = fixture();
        UUID other = UUID.randomUUID();
        execute("INSERT INTO organizations(id,name) VALUES (?,?)", other, "Other organization");
        fails("23503", "UPDATE organization_memberships SET organization_id=? WHERE id=?", other, fixture.membershipId());
    }

    @Test
    void statusAndPermissionVersionRoundTripThroughJpa() throws Exception {
        Fixture fixture = fixture();
        try (Session session = sessions.openSession()) {
            var transaction = session.beginTransaction();
            OrganizationMembership membership = session.find(OrganizationMembership.class, fixture.membershipId());
            membership.setStatus(MembershipStatus.REVOKED);
            transaction.commit();
        }
        try (Session session = sessions.openSession()) {
            OrganizationMembership membership = session.find(OrganizationMembership.class, fixture.membershipId());
            assertEquals(MembershipStatus.REVOKED, membership.getStatus());
            assertEquals(1, membership.getPermissionVersion());
            assertEquals(1, membership.getVersion());
            assertNotNull(membership.getUpdatedAt());
        }
    }

    @Test
    void concurrentMembershipUpdatesCannotSilentlyLosePermissionChanges() throws Exception {
        Fixture fixture = fixture();
        try (Session first = sessions.openSession(); Session second = sessions.openSession()) {
            var firstTransaction = first.beginTransaction();
            var secondTransaction = second.beginTransaction();
            OrganizationMembership a = first.find(OrganizationMembership.class, fixture.membershipId());
            OrganizationMembership b = second.find(OrganizationMembership.class, fixture.membershipId());
            a.setStatus(MembershipStatus.SUSPENDED);
            b.setStatus(MembershipStatus.REVOKED);
            firstTransaction.commit();
            assertThrows(OptimisticLockException.class, secondTransaction::commit);
            if (secondTransaction.isActive()) secondTransaction.rollback();
        }
    }

    private static Fixture fixture() throws SQLException {
        UUID org = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        execute("INSERT INTO organizations(id,name) VALUES (?,?)", org, "Example " + org);
        execute("INSERT INTO organization_memberships(id,user_id,organization_id) VALUES (?,?,?)", membership, LEGACY_USER, org);
        execute("INSERT INTO role_assignments(id,membership_id,organization_id,role,scope_type,scope_id) VALUES (?,?,?,?,?,?)",
                assignment, membership, org, "HOTEL_OWNER", "ORGANIZATION", org);
        return new Fixture(org, membership, assignment);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private static void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            statement.executeUpdate();
        }
    }

    private static void fails(String sqlState, String sql, Object... parameters) {
        SQLException exception = assertThrows(SQLException.class, () -> execute(sql, parameters));
        assertEquals(sqlState, exception.getSQLState());
    }

    private record Fixture(UUID organizationId, UUID membershipId, UUID assignmentId) {}
}
