package com.waylo.carrentalservice.tenant;

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

/** RM-14 car-rental: RLS isolates organizations, context never leaks across a single pooled connection. */
@Testcontainers
class TenantRlsPoolLeakIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID BRANCH_A = UUID.randomUUID();
    static final UUID BRANCH_B = UUID.randomUUID();
    static final UUID VEHICLE_A = UUID.randomUUID();
    static final UUID VEHICLE_B = UUID.randomUUID();

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
            insertBranch(s, BRANCH_A, ORG_A);
            insertBranch(s, BRANCH_B, ORG_B);
            insertVehicle(s, VEHICLE_A, ORG_A, BRANCH_A, "34AAA01");
            insertVehicle(s, VEHICLE_B, ORG_B, BRANCH_B, "34BBB01");
        }

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("com.waylo.carrentalservice.domain");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        emf = factory.getObject();

        JpaTransactionManager manager = new JpaTransactionManager(emf);
        manager.setJpaDialect(new TenantAwareJpaDialect());
        tx = new TransactionTemplate(manager);
    }

    private static void insertBranch(Statement s, UUID id, UUID org) throws Exception {
        s.execute("INSERT INTO branches (id, organization_id, name, city, address, open_time, close_time) VALUES ('"
                + id + "', '" + org + "', 'B', 'Antalya', 'x', '08:00', '20:00')");
    }

    private static void insertVehicle(Statement s, UUID id, UUID org, UUID branch, String plate) throws Exception {
        s.execute("INSERT INTO vehicles (id, organization_id, home_branch_id, plate_number, brand, model, year, category, "
                + "transmission, fuel_type, seat_count) VALUES ('" + id + "', '" + org + "', '" + branch + "', '" + plate
                + "', 'Fiat', 'Egea', 2022, 'ECONOMY', 'MANUAL', 'DIESEL', 5)");
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
    private List<UUID> visible(String table) {
        return tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            return (List<UUID>) em.createNativeQuery("SELECT id FROM " + table + " ORDER BY id").getResultList();
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
        assertThat(visible("vehicles")).containsExactly(VEHICLE_A);
        assertThat(visible("branches")).containsExactly(BRANCH_A);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_B);
        assertThat(visible("vehicles")).containsExactly(VEHICLE_B);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(visible("vehicles")).containsExactlyInAnyOrder(VEHICLE_A, VEHICLE_B);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            assertThat(em.createNativeQuery("SELECT id FROM vehicles").getResultList()).containsExactly(VEHICLE_A);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_B);
        assertThat(visible("vehicles")).containsExactly(VEHICLE_B);

        TenantContext.clear();
        assertThat(sessionState()).isEqualTo("waylo_app|service|" + pid);
        assertThat(visible("vehicles")).containsExactlyInAnyOrder(VEHICLE_A, VEHICLE_B);

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
    void authorizeInsideRunningTransactionNarrowsThatTransaction() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        List<UUID> seen = tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            em.createNativeQuery("SELECT 1").getResultList();
            TenantContext.authorizePartner(ORG_A); // what AuthGateway does inside the service transaction
            @SuppressWarnings("unchecked")
            List<UUID> r = (List<UUID>) em.createNativeQuery("SELECT id FROM vehicles").getResultList();
            return r;
        });
        assertThat(seen).containsExactly(VEHICLE_A);
    }

    @Test
    void partnerAndPublicCannotWriteOtherRowsEvenWithoutCodeChecks() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        int updated = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE vehicles SET brand = 'pwned' WHERE id = ?1")
                .setParameter(1, VEHICLE_B).executeUpdate());
        assertThat(updated).isZero();

        TenantContext.set(TenantContext.Mode.PUBLIC);
        int publicUpdate = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE branches SET name = 'pwned'").executeUpdate());
        assertThat(publicUpdate).isZero();

        TenantContext.set(TenantContext.Mode.SERVICE);
        int service = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE vehicles SET brand = 'Fiat'").executeUpdate());
        assertThat(service).isEqualTo(2);
    }

    @Test
    void partnerCannotInsertRowForAnotherOrganization() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("INSERT INTO branches (id, organization_id, name, city, address, open_time, close_time) "
                        + "VALUES (?1, ?2, 'x', 'x', 'x', '08:00', '20:00')")
                .setParameter(1, UUID.randomUUID()).setParameter(2, ORG_B).executeUpdate()))
                .hasMessageContaining("row-level security");
    }
}
