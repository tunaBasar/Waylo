package com.waylo.hotelservice.tenant;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I1 + I2 (contract sec. 8.2): V10 applies on top of V1..V9 with existing hotels, the backfill keeps them
 * PUBLISHED, constraints hold, and the new tables are RLS protected (partner org B cannot see org A rows,
 * public sees nothing, decisions are append only).
 */
@Testcontainers
class HotelPublicationMigrationRlsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID HOTEL_A = UUID.randomUUID();
    static final UUID HOTEL_B = UUID.randomUUID();
    static final UUID HOTEL_LEGACY = UUID.randomUUID();
    static final UUID HOTEL_INACTIVE = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();

    static final UUID REQ_A = UUID.randomUUID();
    static final UUID REQ_B = UUID.randomUUID();
    static final UUID REV_A = UUID.randomUUID();
    static final UUID REV_B = UUID.randomUUID();
    static final UUID MEDIA_A = UUID.randomUUID();
    static final UUID MEDIA_B = UUID.randomUUID();

    static long legacyBefore;
    static long hotelsBefore;
    static HikariDataSource dataSource;
    static EntityManagerFactory emf;
    static TransactionTemplate tx;

    @BeforeAll
    static void setUp() throws Exception {
        // 1. schema as it is in production today: V1..V9 only
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("9").load().migrate();

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(1);

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            insertHotel(s, HOTEL_A, "'" + ORG_A + "'", true);
            insertHotel(s, HOTEL_B, "'" + ORG_B + "'", true);
            insertHotel(s, HOTEL_LEGACY, "NULL", true);
            insertHotel(s, HOTEL_INACTIVE, "'" + ORG_A + "'", false);
            legacyBefore = count(s, "SELECT count(*) FROM hotels WHERE organization_id IS NULL");
            hotelsBefore = count(s, "SELECT count(*) FROM hotels");
        }

        // 2. apply V10 on top of the populated schema
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO hotel_content_revisions (id, hotel_id, organization_id, revision_no, content, base_content_version, created_by) VALUES ('"
                    + REV_A + "','" + HOTEL_A + "','" + ORG_A + "',1,'{}'::jsonb,0,'" + USER + "')");
            s.execute("INSERT INTO hotel_content_revisions (id, hotel_id, organization_id, revision_no, content, base_content_version, created_by) VALUES ('"
                    + REV_B + "','" + HOTEL_B + "','" + ORG_B + "',1,'{}'::jsonb,0,'" + USER + "')");
            insertRequest(s, REQ_A, HOTEL_A, ORG_A);
            insertRequest(s, REQ_B, HOTEL_B, ORG_B);
            s.execute("INSERT INTO hotel_publication_decisions (id, hotel_id, organization_id, request_id, decision, to_status, actor_user_id, actor_role) VALUES (gen_random_uuid(),'"
                    + HOTEL_A + "','" + ORG_A + "','" + REQ_A + "','SUBMIT','SUBMITTED','" + USER + "','PARTNER')");
            s.execute("INSERT INTO hotel_publication_decisions (id, hotel_id, organization_id, request_id, decision, to_status, actor_user_id, actor_role) VALUES (gen_random_uuid(),'"
                    + HOTEL_B + "','" + ORG_B + "','" + REQ_B + "','SUBMIT','SUBMITTED','" + USER + "','PARTNER')");
        }

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            insertMedia(s, MEDIA_A, HOTEL_A, ORG_A);
            insertMedia(s, MEDIA_B, HOTEL_B, ORG_B);
            s.execute("INSERT INTO hotel_publication_request_media (request_id, media_id, hotel_id, organization_id) VALUES ('"
                    + REQ_A + "','" + MEDIA_A + "','" + HOTEL_A + "','" + ORG_A + "')");
            s.execute("INSERT INTO hotel_publication_request_media (request_id, media_id, hotel_id, organization_id) VALUES ('"
                    + REQ_B + "','" + MEDIA_B + "','" + HOTEL_B + "','" + ORG_B + "')");
        }

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("com.waylo.hotelservice.domain");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        emf = factory.getObject();

        JpaTransactionManager manager = new JpaTransactionManager(emf);
        manager.setJpaDialect(new TenantAwareJpaDialect());
        tx = new TransactionTemplate(manager);
    }

    private static void insertHotel(Statement s, UUID id, String org, boolean active) throws SQLException {
        s.execute("INSERT INTO hotels (id, name, city, country, address, star_rating, active, organization_id) VALUES ('"
                + id + "', 'H', 'Antalya', 'TR', 'x', 3, " + active + ", " + org + ")");
    }

    private static void insertMedia(Statement s, UUID id, UUID hotel, UUID org) throws SQLException {
        s.execute("INSERT INTO hotel_media (id, hotel_id, organization_id, rights_source, storage_key, content_type, size_bytes, width, height, uploaded_by) VALUES ('"
                + id + "','" + hotel + "','" + org + "','OWNED','" + UUID.randomUUID() + "','image/jpeg',1000,800,600,'" + USER + "')");
    }

    private static void insertRequest(Statement s, UUID id, UUID hotel, UUID org) throws SQLException {
        s.execute("INSERT INTO hotel_publication_requests (id, hotel_id, organization_id, kind, submitted_by) VALUES ('"
                + id + "','" + hotel + "','" + org + "','INITIAL','" + USER + "')");
    }

    private static long count(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) emf.close();
        if (dataSource != null) dataSource.close();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> ids(String table) {
        return tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            return (List<UUID>) em.createNativeQuery("SELECT id FROM " + table).getResultList();
        });
    }

    private long countRows(String sql) {
        return ((Number) tx.execute(st -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery(sql).getSingleResult())).longValue();
    }

    private int exec(String sql) {
        return tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery(sql).executeUpdate());
    }

    private void partner(UUID org) {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(org);
    }

    // ---- I1

    @Test
    void v10_backfill_keepsEveryExistingHotelPublished_andRecordsMigrationDecision() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThat(count(s, "SELECT count(*) FROM hotels")).isEqualTo(hotelsBefore);
            assertThat(count(s, "SELECT count(*) FROM hotels WHERE publication_status = 'PUBLISHED' AND published_at IS NOT NULL"))
                    .isEqualTo(hotelsBefore);
            assertThat(count(s, "SELECT count(*) FROM hotels WHERE organization_id IS NULL")).isEqualTo(legacyBefore);
            assertThat(count(s, "SELECT count(*) FROM hotels WHERE organization_id IS NULL AND publication_status = 'PUBLISHED'"))
                    .isEqualTo(legacyBefore);
            // active flag untouched by the migration
            assertThat(count(s, "SELECT count(*) FROM hotels WHERE id = '" + HOTEL_INACTIVE + "' AND active = false")).isEqualTo(1);
            assertThat(count(s, "SELECT count(*) FROM hotels WHERE content_version = 0 AND source_type = 'DIRECT' AND published_revision_id IS NULL"))
                    .isEqualTo(hotelsBefore);
            assertThat(count(s, "SELECT count(*) FROM hotel_publication_decisions WHERE decision = 'MIGRATION' AND actor_role = 'SYSTEM' AND actor_user_id IS NULL AND to_status = 'PUBLISHED'"))
                    .isEqualTo(hotelsBefore);
            assertThat(count(s, "SELECT count(*) FROM hotel_publication_decisions WHERE decision = 'MIGRATION' AND organization_id IS NULL"))
                    .isEqualTo(legacyBefore);
        }
    }

    @Test
    void v10_constraints() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("UPDATE hotels SET publication_status = 'X' WHERE id = '" + HOTEL_A + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("UPDATE hotels SET publication_status = 'PUBLISHED', published_at = NULL WHERE id = '" + HOTEL_B + "'"))
                    .isInstanceOf(SQLException.class);
            // second open request for the same hotel
            assertThatThrownBy(() -> insertRequest(s, UUID.randomUUID(), HOTEL_A, ORG_A)).isInstanceOf(SQLException.class);
            // second working revision for the same hotel
            assertThatThrownBy(() -> s.execute("INSERT INTO hotel_content_revisions (id, hotel_id, organization_id, revision_no, content, base_content_version, created_by) VALUES ('"
                    + UUID.randomUUID() + "','" + HOTEL_A + "','" + ORG_A + "',2,'{}'::jsonb,0,'" + USER + "')")).isInstanceOf(SQLException.class);
            // REJECT without a reason
            assertThatThrownBy(() -> s.execute("INSERT INTO hotel_publication_decisions (id, hotel_id, organization_id, decision, to_status, actor_user_id, actor_role) VALUES (gen_random_uuid(),'"
                    + HOTEL_A + "','" + ORG_A + "','REJECT','REJECTED','" + USER + "','ADMIN')")).isInstanceOf(SQLException.class);
            // hotel with history cannot be deleted (RESTRICT)
            assertThatThrownBy(() -> s.execute("DELETE FROM hotels WHERE id = '" + HOTEL_A + "'")).isInstanceOf(SQLException.class);
        }
    }

    // ---- I2

    @Test
    void partnerSeesOnlyOwnOrganizationRows_inAllThreeTables() {
        partner(ORG_A);
        assertThat(ids("hotel_publication_requests")).containsExactly(REQ_A);
        assertThat(ids("hotel_content_revisions")).containsExactly(REV_A);
        partner(ORG_A);
        assertThat(countRows("SELECT count(*) FROM hotel_publication_decisions WHERE hotel_id = '" + HOTEL_B + "'")).isZero();

        partner(ORG_B);
        assertThat(ids("hotel_publication_requests")).containsExactly(REQ_B);
        assertThat(ids("hotel_content_revisions")).containsExactly(REV_B);

        // partner B can not touch A's request even by id
        partner(ORG_B);
        assertThat(exec("UPDATE hotel_publication_requests SET submit_note = 'x' WHERE id = '" + REQ_A + "'")).isZero();
        partner(ORG_B);
        assertThat(exec("DELETE FROM hotel_content_revisions WHERE id = '" + REV_A + "'")).isZero();
    }

    @Test
    void publicModeSeesNothing_serviceAndAdminSeeEverything() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(ids("hotel_publication_requests")).isEmpty();
        assertThat(ids("hotel_content_revisions")).isEmpty();
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(ids("hotel_publication_decisions")).isEmpty();

        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(ids("hotel_publication_requests")).containsExactlyInAnyOrder(REQ_A, REQ_B);
        TenantContext.set(TenantContext.Mode.ADMIN);
        // legacy MIGRATION rows (organization_id NULL) are visible to admin
        assertThat(ids("hotel_publication_decisions")).hasSizeGreaterThanOrEqualTo((int) hotelsBefore + 2);
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThat(ids("hotel_content_revisions")).containsExactlyInAnyOrder(REV_A, REV_B);

        // partner never sees organization-less (legacy MIGRATION) decisions
        partner(ORG_A);
        assertThat(countRows("SELECT count(*) FROM hotel_publication_decisions WHERE organization_id IS NULL")).isZero();
    }

    @Test
    void partnerCannotInsertForAnotherOrganization_publicCannotInsertAtAll() {
        String insertForB = "INSERT INTO hotel_publication_requests (id, hotel_id, organization_id, kind, submitted_by, status, decided_at) VALUES ('"
                + UUID.randomUUID() + "','" + HOTEL_B + "','" + ORG_B + "','INITIAL','" + USER + "','REJECTED', now())";
        partner(ORG_A);
        assertThatThrownBy(() -> exec(insertForB)).hasMessageContaining("row-level security");

        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThatThrownBy(() -> exec(insertForB)).hasMessageContaining("row-level security");

        partner(ORG_A);
        assertThatThrownBy(() -> exec("INSERT INTO hotel_publication_decisions (id, hotel_id, organization_id, decision, to_status, actor_user_id, actor_role) VALUES (gen_random_uuid(),'"
                + HOTEL_B + "','" + ORG_B + "','SUBMIT','SUBMITTED','" + USER + "','PARTNER')")).hasMessageContaining("row-level security");
    }

    @Test
    void decisionsAreAppendOnly_forEveryMode() {
        for (TenantContext.Mode mode : TenantContext.Mode.values()) {
            if (mode == TenantContext.Mode.PARTNER) {
                partner(ORG_A);
            } else {
                TenantContext.set(mode);
            }
            assertThat(exec("UPDATE hotel_publication_decisions SET reason = 'tampered'")).as(mode.name()).isZero();
            if (mode == TenantContext.Mode.PARTNER) {
                partner(ORG_A);
            } else {
                TenantContext.set(mode);
            }
            assertThat(exec("DELETE FROM hotel_publication_decisions")).as(mode.name()).isZero();
        }
    }

    @Test
    void requestsCannotBeDeletedExceptByService() {
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(exec("DELETE FROM hotel_publication_requests")).isZero();
        partner(ORG_A);
        assertThat(exec("DELETE FROM hotel_publication_requests")).isZero();
    }

    @Test
    void contextDoesNotLeakAfterPartnerTransaction() {
        partner(ORG_A);
        assertThat(ids("hotel_publication_requests")).containsExactly(REQ_A);
        TenantContext.clear();
        // back to SERVICE default on the same single pooled connection: sees both
        assertThat(ids("hotel_publication_requests")).containsExactlyInAnyOrder(REQ_A, REQ_B);
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(ids("hotel_publication_requests")).isEmpty();
    }

    // ---- V13 link table

    @Test
    void requestMedia_partnerSeesOwnOrgOnly_publicSeesNothing() {
        partner(ORG_A);
        assertThat(countRows("SELECT count(*) FROM hotel_publication_request_media")).isEqualTo(1);
        partner(ORG_A);
        assertThat(countRows("SELECT count(*) FROM hotel_publication_request_media WHERE media_id = '" + MEDIA_B + "'")).isZero();
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(countRows("SELECT count(*) FROM hotel_publication_request_media")).isZero();
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(countRows("SELECT count(*) FROM hotel_publication_request_media")).isEqualTo(2);
    }

    @Test
    void requestMedia_partnerCannotInsertForAnotherOrg_publicCannotInsert() {
        String insertForB = "INSERT INTO hotel_publication_request_media (request_id, media_id, hotel_id, organization_id) VALUES ('"
                + REQ_B + "','" + MEDIA_A + "','" + HOTEL_B + "','" + ORG_B + "')";
        partner(ORG_A);
        assertThatThrownBy(() -> exec(insertForB)).hasMessageContaining("row-level security");
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThatThrownBy(() -> exec(insertForB)).hasMessageContaining("row-level security");
    }

    @Test
    void requestMedia_rowsAreImmutable_deleteOnlyByService() {
        partner(ORG_A);
        assertThat(exec("UPDATE hotel_publication_request_media SET created_at = now()")).isZero();
        partner(ORG_A);
        assertThat(exec("DELETE FROM hotel_publication_request_media")).isZero();
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(exec("UPDATE hotel_publication_request_media SET created_at = now()")).isZero();
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(exec("DELETE FROM hotel_publication_request_media")).isZero();
    }
}
