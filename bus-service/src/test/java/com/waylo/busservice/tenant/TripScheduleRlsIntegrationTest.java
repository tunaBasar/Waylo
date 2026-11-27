package com.waylo.busservice.tenant;

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

/** V4: trip_schedules and children are isolated per organization (children via app_schedule_in_scope). */
@Testcontainers
class TripScheduleRlsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID SCHED_A = UUID.randomUUID();
    static final UUID SCHED_B = UUID.randomUUID();
    static final UUID TERMINAL = UUID.randomUUID();

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
            s.execute("INSERT INTO terminals (id, city, name) VALUES ('" + TERMINAL + "', 'Ankara', 'ASTI')");
            for (Object[] row : new Object[][] {{ORG_A, SCHED_A, "34A001"}, {ORG_B, SCHED_B, "34B001"}}) {
                UUID bus = UUID.randomUUID();
                s.execute("INSERT INTO buses (id, organization_id, plate_number, brand, model, year, layout, row_count, seat_count) VALUES ('"
                        + bus + "', '" + row[0] + "', '" + row[2] + "', 'Mercedes', 'Tourismo', 2022, 'TWO_PLUS_ONE', 10, 30)");
                s.execute("INSERT INTO trip_schedules (id, organization_id, bus_id, code, currency, weekdays, departure_time, valid_from) VALUES ('"
                        + row[1] + "', '" + row[0] + "', '" + bus + "', 'S1', 'TRY', 127, '22:30', current_date)");
                s.execute("INSERT INTO trip_schedule_stops (id, schedule_id, seq, terminal_id, arrival_offset_minutes, departure_offset_minutes) VALUES ('"
                        + UUID.randomUUID() + "', '" + row[1] + "', 0, '" + TERMINAL + "', 0, 0)");
                s.execute("INSERT INTO trip_schedule_fares (id, schedule_id, from_seq, to_seq, price) VALUES ('"
                        + UUID.randomUUID() + "', '" + row[1] + "', 0, 1, 100)");
            }
        }
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("com.waylo.busservice.domain");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        JpaTransactionManager manager = new JpaTransactionManager(emf);
        manager.setJpaDialect(new TenantAwareJpaDialect());
        tx = new TransactionTemplate(manager);
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) emf.close();
        if (dataSource != null) dataSource.close();
    }

    @AfterEach
    void clearContext() { TenantContext.clear(); }

    @SuppressWarnings("unchecked")
    private List<UUID> visible(String table, String col) {
        return tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            return (List<UUID>) em.createNativeQuery("SELECT DISTINCT " + col + " FROM " + table + " ORDER BY 1").getResultList();
        });
    }

    @Test
    void partnerSeesOnlyOwnSchedulesAndChildren() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThat(visible("trip_schedules", "id")).containsExactly(SCHED_A);
        assertThat(visible("trip_schedule_stops", "schedule_id")).containsExactly(SCHED_A);
        assertThat(visible("trip_schedule_fares", "schedule_id")).containsExactly(SCHED_A);
    }

    @Test
    void serviceModeSeesAllSchedules() {
        TenantContext.clear();
        assertThat(visible("trip_schedules", "id")).containsExactlyInAnyOrder(SCHED_A, SCHED_B);
        assertThat(visible("trip_schedule_stops", "schedule_id")).containsExactlyInAnyOrder(SCHED_A, SCHED_B);
    }
}
