package com.waylo.hotelservice.automation;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V14 on top of V1..V13: source defaults and checks, rule table constraints, RLS isolation (same pattern as the V11 test). */
@Testcontainers
class AutomationMigrationRlsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID HOTEL_A = UUID.randomUUID();
    static final UUID HOTEL_B = UUID.randomUUID();
    static final UUID RT_A = UUID.randomUUID();
    static final UUID RT_B = UUID.randomUUID();
    static final UUID PLAN_A = UUID.randomUUID();

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
            roomType(s, RT_A, HOTEL_A, "A1");
            roomType(s, RT_B, HOTEL_B, "B1");
            s.execute("INSERT INTO rate_plans (id, hotel_id, code, name, meal_plan, cancellation_policy) VALUES ('"
                    + PLAN_A + "','" + HOTEL_A + "','BB','BB','BED_AND_BREAKFAST','NON_REFUNDABLE')");
            s.execute(invRuleSql(HOTEL_A, RT_A));
            s.execute(invRuleSql(HOTEL_B, RT_B));
            s.execute("INSERT INTO daily_inventory (id, hotel_id, room_type_id, stay_date, allotment) VALUES ('"
                    + UUID.randomUUID() + "','" + HOTEL_A + "','" + RT_A + "','2027-06-04',3)");
        }

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("com.waylo.hotelservice.domain", "com.waylo.hotelservice.automation");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
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

    private static void roomType(Statement s, UUID id, UUID hotel, String code) throws Exception {
        s.execute("INSERT INTO room_types (id, hotel_id, code, name, max_adults, max_children, max_occupancy) VALUES ('"
                + id + "','" + hotel + "','" + code + "','Oda',2,1,3)");
    }

    private static String invRuleSql(UUID hotel, UUID roomType) {
        return "INSERT INTO hotel_inventory_rules (hotel_id, room_type_id, default_allotment) VALUES ('" + hotel + "','" + roomType + "',5)";
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

    private int exec(String sql) {
        return tx.execute(st -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf).createNativeQuery(sql).executeUpdate());
    }

    private long count(String sql) {
        return ((Number) tx.execute(st -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery(sql).getSingleResult())).longValue();
    }

    @Test
    void existingRowsAreManualAndSourceIsChecked() {
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThat(count("SELECT count(*) FROM daily_inventory WHERE source = 'MANUAL'")).isEqualTo(1);
        assertThatThrownBy(() -> exec("UPDATE daily_inventory SET source = 'OTHER'")).hasMessageContaining("chk_daily_inventory_source");
        assertThat(exec("UPDATE daily_inventory SET source = 'RULE'")).isEqualTo(1);
    }

    @Test
    void rateRuleChecksRejectBadSeasonsAndAmounts() {
        TenantContext.set(TenantContext.Mode.SERVICE);
        String head = "INSERT INTO hotel_rate_rules (hotel_id, room_type_id, rate_plan_id, name, valid_from, valid_to, currency, base_occupancy, base_amount) VALUES ('"
                + HOTEL_A + "','" + RT_A + "','" + PLAN_A + "','n',";
        assertThatThrownBy(() -> exec(head + "'2027-06-01', NULL, 'TRY', 2, 100)")).hasMessageContaining("chk_hrr_season_pair");
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThatThrownBy(() -> exec(head + "'2027-06-10', '2027-06-01', 'TRY', 2, 100)")).hasMessageContaining("chk_hrr_season_order");
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThatThrownBy(() -> exec(head + "NULL, NULL, 'TRY', 2, 0)")).hasMessageContaining("base_amount");
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThat(exec(head + "NULL, NULL, 'TRY', 2, 100)")).isEqualTo(1);
    }

    @Test
    void partnerSeesAndWritesOnlyOwnHotelRules() {
        partner(ORG_A);
        assertThat(count("SELECT count(*) FROM hotel_inventory_rules")).isEqualTo(1);
        partner(ORG_A);
        assertThat(count("SELECT count(*) FROM hotel_inventory_rules WHERE hotel_id = '" + HOTEL_B + "'")).isZero();
        partner(ORG_A);
        assertThat(exec("UPDATE hotel_inventory_rules SET default_allotment = 9 WHERE hotel_id = '" + HOTEL_B + "'")).isZero();
        partner(ORG_A);
        assertThatThrownBy(() -> exec(invRuleSql(HOTEL_B, RT_B))).hasMessageContaining("row-level security");
    }

    @Test
    void serviceModeSeesAllRules() {
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThat(count("SELECT count(*) FROM hotel_inventory_rules")).isEqualTo(2);
    }
}
