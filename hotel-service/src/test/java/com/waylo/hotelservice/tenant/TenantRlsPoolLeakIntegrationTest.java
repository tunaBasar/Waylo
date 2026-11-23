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
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RM-14: proves the RLS tenant context is transaction local and never leaks across a pooled connection.
 * The pool has exactly one physical connection, so every transaction below reuses the same one.
 */
@Testcontainers
class TenantRlsPoolLeakIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID HOTEL_A = UUID.randomUUID();
    static final UUID HOTEL_B = UUID.randomUUID();
    static final UUID HOTEL_LEGACY = UUID.randomUUID();

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
            insertHotel(s, HOTEL_A, "'" + ORG_A + "'");
            insertHotel(s, HOTEL_B, "'" + ORG_B + "'");
            insertHotel(s, HOTEL_LEGACY, "NULL");
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

    private static void insertHotel(Statement s, UUID id, String org) throws Exception {
        s.execute("INSERT INTO hotels (id, name, city, country, address, star_rating, organization_id) VALUES ('"
                + id + "', 'H', 'Antalya', 'TR', 'x', 3, " + org + ")");
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
    private List<UUID> visibleHotels() {
        return tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            return (List<UUID>) em.createNativeQuery("SELECT id FROM hotels ORDER BY name, id").getResultList();
        });
    }

    private String sessionState() {
        return tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            Object[] row = (Object[]) em.createNativeQuery(
                    "SELECT current_user || '|' || app_mode(), pg_backend_pid() || ''").getSingleResult();
            return row[0] + "|" + row[1];
        });
    }

    @Test
    void contextDoesNotLeakAcrossTransactionsOnSameConnection() throws Exception {
        String pid = sessionState().split("\\|")[2];

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThat(visibleHotels()).containsExactly(HOTEL_A);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_B);
        assertThat(visibleHotels()).containsExactly(HOTEL_B);

        // anonymous public request: sees the catalog (public projection is filtered in code), not "B only"
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(visibleHotels()).containsExactlyInAnyOrder(HOTEL_A, HOTEL_B, HOTEL_LEGACY);

        // partner A transaction that fails: rollback must drop the context too
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            assertThat(em.createNativeQuery("SELECT id FROM hotels").getResultList()).containsExactly(HOTEL_A);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_B);
        assertThat(visibleHotels()).containsExactly(HOTEL_B);

        TenantContext.clear();
        assertThat(sessionState()).isEqualTo("waylo_app|service|" + pid);

        // Outside any transaction the pooled connection is back to the login role with no app.* settings.
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT current_user, coalesce(current_setting('app.mode', true), ''), "
                     + "coalesce(current_setting('app.org_ids', true), ''), pg_backend_pid()")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo(POSTGRES.getUsername());
            assertThat(rs.getString(2)).isEmpty();
            assertThat(rs.getString(3)).isEmpty();
            assertThat(rs.getString(4)).isEqualTo(pid);
        }
    }

    @Test
    void partnerCannotWriteOtherOrganizationsRowsEvenWithoutCodeChecks() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        int updated = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE hotels SET name = 'pwned' WHERE id IN (?1, ?2)")
                .setParameter(1, HOTEL_B).setParameter(2, HOTEL_LEGACY).executeUpdate());
        assertThat(updated).isZero();

        TenantContext.set(TenantContext.Mode.PUBLIC);
        int publicUpdate = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE hotels SET name = 'pwned'").executeUpdate());
        assertThat(publicUpdate).isZero();
    }
}
