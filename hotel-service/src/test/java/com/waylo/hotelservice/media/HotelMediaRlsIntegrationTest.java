package com.waylo.hotelservice.media;

import com.waylo.hotelservice.tenant.TenantAwareJpaDialect;
import com.waylo.hotelservice.tenant.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
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
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V11 on top of V1..V10 (same Testcontainers pattern as the V10 test): hotel_media RLS isolation. */
@Testcontainers
class HotelMediaRlsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID HOTEL_A = UUID.randomUUID();
    static final UUID HOTEL_B = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final UUID M_A_APPROVED = UUID.randomUUID();
    static final UUID M_A_PENDING = UUID.randomUUID();
    static final UUID M_A_REJECTED = UUID.randomUUID();
    static final UUID M_B_APPROVED = UUID.randomUUID();

    static HikariDataSource dataSource;
    static EntityManagerFactory emf;
    static TransactionTemplate tx;

    @BeforeAll
    static void setUp() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(1);

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            hotel(s, HOTEL_A, ORG_A);
            hotel(s, HOTEL_B, ORG_B);
            media(s, M_A_APPROVED, HOTEL_A, ORG_A, "CLEAN", "APPROVED", null);
            media(s, M_A_PENDING, HOTEL_A, ORG_A, "CLEAN", "PENDING", null);
            media(s, M_A_REJECTED, HOTEL_A, ORG_A, "CLEAN", "REJECTED", "Bulanik");
            media(s, M_B_APPROVED, HOTEL_B, ORG_B, "CLEAN", "APPROVED", null);
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

    private static void hotel(Statement s, UUID id, UUID org) throws Exception {
        s.execute("INSERT INTO hotels (id, name, city, country, address, star_rating, active, organization_id, publication_status, published_at) VALUES ('"
                + id + "','H','Antalya','TR','x',3,true,'" + org + "','PUBLISHED', now())");
    }

    private static void media(Statement s, UUID id, UUID hotel, UUID org, String scan, String review, String reason) throws Exception {
        s.execute("INSERT INTO hotel_media (id, hotel_id, organization_id, rights_source, storage_key, content_type, size_bytes, width, height, scan_status, review_status, review_reason, uploaded_by) VALUES ('"
                + id + "','" + hotel + "','" + org + "','OWNED','" + UUID.randomUUID() + "','image/png',1000,800,600,'" + scan + "','" + review + "',"
                + (reason == null ? "NULL" : "'" + reason + "'") + ",'" + USER + "')");
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) emf.close();
        if (dataSource != null) dataSource.close();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void partner(UUID org) {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(org);
    }

    @SuppressWarnings("unchecked")
    private List<UUID> ids() {
        return tx.execute(st -> (List<UUID>) EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("SELECT id FROM hotel_media").getResultList());
    }

    private int exec(String sql) {
        return tx.execute(st -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf).createNativeQuery(sql).executeUpdate());
    }

    private long count(String sql) {
        return ((Number) tx.execute(st -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery(sql).getSingleResult())).longValue();
    }

    @Test
    void partnerSeesOnlyOwnOrganizationMedia_inEveryReviewState() {
        partner(ORG_A);
        assertThat(ids()).containsExactlyInAnyOrder(M_A_APPROVED, M_A_PENDING, M_A_REJECTED);
        partner(ORG_B);
        assertThat(ids()).containsExactly(M_B_APPROVED);
    }

    @Test
    void publicSelectSeesOnlyCleanApprovedRows() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(ids()).containsExactlyInAnyOrder(M_A_APPROVED, M_B_APPROVED);
    }

    @Test
    void adminAndServiceSeeEverything() {
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(ids()).hasSize(4);
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThat(ids()).hasSize(4);
    }

    @Test
    void partnerCannotTouchAnotherOrganizationsRows() {
        partner(ORG_B);
        assertThat(exec("UPDATE hotel_media SET caption = 'x' WHERE id = '" + M_A_PENDING + "'")).isZero();
        partner(ORG_B);
        assertThat(exec("DELETE FROM hotel_media WHERE id = '" + M_A_PENDING + "'")).isZero();
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(exec("UPDATE hotel_media SET caption = 'x'")).isZero();
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(exec("DELETE FROM hotel_media")).isZero();
    }

    @Test
    void insertOnlyForOwnOrganization_asPendingForPartner() {
        String forB = "INSERT INTO hotel_media (id, hotel_id, organization_id, rights_source, storage_key, content_type, size_bytes, width, height, uploaded_by) VALUES ('"
                + UUID.randomUUID() + "','" + HOTEL_B + "','" + ORG_B + "','OWNED','" + UUID.randomUUID() + "','image/png',1000,800,600,'" + USER + "')";
        partner(ORG_A);
        assertThatThrownBy(() -> exec(forB)).hasMessageContaining("row-level security");
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThatThrownBy(() -> exec(forB)).hasMessageContaining("row-level security");

        String approvedByPartner = "INSERT INTO hotel_media (id, hotel_id, organization_id, rights_source, storage_key, content_type, size_bytes, width, height, review_status, uploaded_by) VALUES ('"
                + UUID.randomUUID() + "','" + HOTEL_A + "','" + ORG_A + "','OWNED','" + UUID.randomUUID() + "','image/png',1000,800,600,'APPROVED','" + USER + "')";
        partner(ORG_A);
        assertThatThrownBy(() -> exec(approvedByPartner)).hasMessageContaining("row-level security");
    }

    @Test
    void partnerCannotApproveItsOwnPhoto_orForgeScanStatus() {
        partner(ORG_A);
        assertThatThrownBy(() -> exec("UPDATE hotel_media SET review_status = 'APPROVED' WHERE id = '" + M_A_PENDING + "'"))
                .hasMessageContaining("partner may not change");
        partner(ORG_A);
        assertThatThrownBy(() -> exec("UPDATE hotel_media SET scan_status = 'PENDING' WHERE id = '" + M_A_PENDING + "'"))
                .hasMessageContaining("partner may not change");
        // caption edits and going back to PENDING are fine
        partner(ORG_A);
        assertThat(exec("UPDATE hotel_media SET caption = 'ok' WHERE id = '" + M_A_PENDING + "'")).isEqualTo(1);
        // admin can approve
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(exec("UPDATE hotel_media SET review_status = 'APPROVED' WHERE id = '" + M_A_PENDING + "'")).isEqualTo(1);
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(exec("UPDATE hotel_media SET review_status = 'PENDING' WHERE id = '" + M_A_PENDING + "'")).isEqualTo(1);
    }

    @Test
    void partnerCannotDeleteLiveApprovedPhotoOfPublishedHotel_butCanDeleteOthers() {
        UUID temp = UUID.randomUUID();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            media(s, temp, HOTEL_A, ORG_A, "CLEAN", "PENDING", null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        partner(ORG_A);
        assertThat(exec("DELETE FROM hotel_media WHERE id = '" + M_A_APPROVED + "'")).isZero();
        partner(ORG_A);
        assertThat(exec("DELETE FROM hotel_media WHERE id = '" + temp + "'")).isEqualTo(1);
    }

    @Test
    void decisionsTable_partnerReadsOwnOnly_onlyAdminOrServiceInsert_nobodyUpdatesOrDeletes() {
        String insert = "INSERT INTO hotel_media_decisions (id, media_id, hotel_id, organization_id, decision, from_status, to_status, actor_user_id) VALUES (gen_random_uuid(),'"
                + M_A_APPROVED + "','" + HOTEL_A + "','" + ORG_A + "','APPROVE','PENDING','APPROVED','" + USER + "')";
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(exec(insert)).isEqualTo(1);

        partner(ORG_A);
        assertThat(count("SELECT count(*) FROM hotel_media_decisions")).isGreaterThanOrEqualTo(1);
        partner(ORG_B);
        assertThat(count("SELECT count(*) FROM hotel_media_decisions")).isZero();
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(count("SELECT count(*) FROM hotel_media_decisions")).isZero();

        partner(ORG_A);
        assertThatThrownBy(() -> exec(insert)).hasMessageContaining("row-level security");

        for (TenantContext.Mode mode : TenantContext.Mode.values()) {
            if (mode == TenantContext.Mode.PARTNER) partner(ORG_A); else TenantContext.set(mode);
            assertThat(exec("UPDATE hotel_media_decisions SET reason = 'tampered'")).as(mode.name()).isZero();
            if (mode == TenantContext.Mode.PARTNER) partner(ORG_A); else TenantContext.set(mode);
            assertThat(exec("DELETE FROM hotel_media_decisions")).as(mode.name()).isZero();
        }
    }

    @Test
    void checkConstraints() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            // below the 800x600 minimum
            assertThatThrownBy(() -> s.execute("UPDATE hotel_media SET width = 799 WHERE id = '" + M_A_PENDING + "'"))
                    .isInstanceOf(java.sql.SQLException.class);
            // REJECTED needs a reason
            assertThatThrownBy(() -> s.execute("UPDATE hotel_media SET review_status = 'REJECTED', review_reason = NULL WHERE id = '" + M_A_PENDING + "'"))
                    .isInstanceOf(java.sql.SQLException.class);
            // bad content type
            assertThatThrownBy(() -> s.execute("UPDATE hotel_media SET content_type = 'application/pdf' WHERE id = '" + M_A_PENDING + "'"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    /** V12: a reviewed photo keeps its decision rows, so deletion is a soft delete that the partner cannot undo. */
    @Test
    void softDelete_keepsDecisionHistory_hidesFromPublic_andCannotBeUndoneByPartner() throws Exception {
        UUID reviewed = UUID.randomUUID();
        UUID approvedDeleted = UUID.randomUUID();
        try {
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                media(s, reviewed, HOTEL_A, ORG_A, "CLEAN", "REJECTED", "Bulanik foto");
            }
            TenantContext.set(TenantContext.Mode.ADMIN);
            exec("INSERT INTO hotel_media_decisions (id, media_id, hotel_id, organization_id, decision, from_status, to_status, reason, actor_user_id) "
                    + "VALUES (gen_random_uuid(),'" + reviewed + "','" + HOTEL_A + "','" + ORG_A + "','REJECT','PENDING','REJECTED','Bulanik foto','" + USER + "')");

            // the append-only history blocks a hard delete (ON DELETE RESTRICT), which is why the service soft-deletes
            partner(ORG_A);
            assertThatThrownBy(() -> exec("DELETE FROM hotel_media WHERE id = '" + reviewed + "'")).isNotNull();

            partner(ORG_A);
            assertThat(exec("UPDATE hotel_media SET deleted_at = now() WHERE id = '" + reviewed + "'")).isEqualTo(1);
            partner(ORG_A);
            assertThatThrownBy(() -> exec("UPDATE hotel_media SET deleted_at = NULL WHERE id = '" + reviewed + "'"))
                    .hasMessageContaining("partner may not change");
            TenantContext.set(TenantContext.Mode.ADMIN);
            assertThat(count("SELECT count(*) FROM hotel_media_decisions WHERE media_id = '" + reviewed + "'")).isEqualTo(1);

            // a live photo (APPROVED on a PUBLISHED hotel) cannot be soft-deleted by the partner either
            partner(ORG_A);
            assertThatThrownBy(() -> exec("UPDATE hotel_media SET deleted_at = now() WHERE id = '" + M_A_APPROVED + "'"))
                    .hasMessageContaining("live photo");

            // public never sees a deleted row, even an approved one
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                media(s, approvedDeleted, HOTEL_A, ORG_A, "CLEAN", "APPROVED", null);
                s.execute("UPDATE hotel_media SET deleted_at = now() WHERE id = '" + approvedDeleted + "'");
            }
            TenantContext.set(TenantContext.Mode.PUBLIC);
            assertThat(ids()).doesNotContain(approvedDeleted, reviewed);
        } finally {
            // other cases count rows: remove this test's rows as the container superuser (bypasses RLS)
            TenantContext.clear();
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.execute("DELETE FROM hotel_media_decisions WHERE media_id = '" + reviewed + "'");
                s.execute("DELETE FROM hotel_media WHERE id IN ('" + reviewed + "','" + approvedDeleted + "')");
            }
        }
    }
}
