package com.waylo.packagebookingservice.tenant;

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

/** RLS on order_items and vouchers: partner isolation, SERVICE/ADMIN/PUBLIC unchanged, no context leak on a pooled connection. */
@Testcontainers
class TenantRlsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID ORDER = UUID.randomUUID();
    static final UUID ITEM_A = UUID.randomUUID();
    static final UUID ITEM_B = UUID.randomUUID();
    static final UUID VOUCHER_A = UUID.randomUUID();
    static final UUID VOUCHER_B = UUID.randomUUID();

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
            s.execute("INSERT INTO orders (id, customer_id, currency, total_amount) VALUES ('" + ORDER + "', '"
                    + CUSTOMER + "', 'TRY', 200)");
            insertItem(s, ITEM_A, ORG_A);
            insertItem(s, ITEM_B, ORG_B);
            insertVoucher(s, VOUCHER_A, ITEM_A);
            insertVoucher(s, VOUCHER_B, ITEM_B);
        }

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("com.waylo.packagebookingservice.domain");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        emf = factory.getObject();

        JpaTransactionManager manager = new JpaTransactionManager(emf);
        manager.setJpaDialect(new TenantAwareJpaDialect());
        tx = new TransactionTemplate(manager);
    }

    private static void insertItem(Statement s, UUID id, UUID org) throws Exception {
        s.execute("INSERT INTO order_items (id, order_id, vertical, supplier_organization_id, product_ref, description, "
                + "unit_amount, quantity, currency) VALUES ('" + id + "', '" + ORDER + "', 'HOTEL', '" + org + "', '"
                + UUID.randomUUID() + "', 'item', 100, 1, 'TRY')");
    }

    private static void insertVoucher(Statement s, UUID id, UUID item) throws Exception {
        s.execute("INSERT INTO vouchers (id, order_item_id, code_hash, valid_from, valid_until) VALUES ('" + id + "', '"
                + item + "', 'h', now(), now() + interval '1 day')");
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
    private List<UUID> visible(String sql) {
        return tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            return (List<UUID>) em.createNativeQuery(sql).getResultList();
        });
    }

    private void asPartner(UUID org) {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(org);
    }

    @Test
    void partnerWithoutAnyOrgFilterSeesOnlyOwnItemsAndVouchers() {
        asPartner(ORG_A);
        assertThat(visible("SELECT id FROM order_items")).containsExactly(ITEM_A);
        assertThat(visible("SELECT id FROM vouchers")).containsExactly(VOUCHER_A);

        asPartner(ORG_B);
        assertThat(visible("SELECT id FROM order_items")).containsExactly(ITEM_B);
        assertThat(visible("SELECT id FROM vouchers")).containsExactly(VOUCHER_B);
    }

    @Test
    void partnerCannotReadOtherOrgVoucherByIdOrJoin() {
        asPartner(ORG_A);
        assertThat(visible("SELECT id FROM vouchers WHERE id = '" + VOUCHER_B + "'")).isEmpty();
        assertThat(visible("SELECT v.id FROM vouchers v JOIN order_items i ON i.id = v.order_item_id")).containsExactly(VOUCHER_A);
    }

    @Test
    void partnerCannotUpdateOrInsertForAnotherOrganization() {
        asPartner(ORG_A);
        int updated = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE vouchers SET status = 'VOID' WHERE id = ?1")
                .setParameter(1, VOUCHER_B).executeUpdate());
        assertThat(updated).isZero();

        asPartner(ORG_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("INSERT INTO order_items (id, order_id, vertical, supplier_organization_id, product_ref, "
                        + "description, unit_amount, quantity, currency) VALUES (?1, ?2, 'HOTEL', ?3, ?4, 'x', 1, 1, 'TRY')")
                .setParameter(1, UUID.randomUUID()).setParameter(2, ORDER).setParameter(3, ORG_B)
                .setParameter(4, UUID.randomUUID()).executeUpdate()))
                .hasMessageContaining("row-level security");
    }

    @Test
    void serviceAdminAndCustomerReadsKeepWorking() {
        TenantContext.set(TenantContext.Mode.SERVICE);
        assertThat(visible("SELECT id FROM order_items")).containsExactlyInAnyOrder(ITEM_A, ITEM_B);
        assertThat(visible("SELECT id FROM vouchers")).containsExactlyInAnyOrder(VOUCHER_A, VOUCHER_B);

        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(visible("SELECT id FROM order_items")).containsExactlyInAnyOrder(ITEM_A, ITEM_B);

        // Customer (PUBLIC) reads: orders is not under RLS, ownership is checked in code on orders.customer_id.
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(visible("SELECT id FROM orders WHERE customer_id = '" + CUSTOMER + "'")).containsExactly(ORDER);
        assertThat(visible("SELECT id FROM order_items WHERE order_id = '" + ORDER + "'")).containsExactlyInAnyOrder(ITEM_A, ITEM_B);
        assertThat(visible("SELECT id FROM vouchers")).containsExactlyInAnyOrder(VOUCHER_A, VOUCHER_B);
    }

    @Test
    void noContextDefaultsToServiceForJobs() {
        TenantContext.clear();
        assertThat(visible("SELECT id FROM vouchers")).containsExactlyInAnyOrder(VOUCHER_A, VOUCHER_B);
    }

    @Test
    void authorizeInsideRunningTransactionNarrowsThatTransaction() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        List<UUID> seen = tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            em.createNativeQuery("SELECT 1").getResultList();
            TenantContext.authorizePartner(ORG_A); // what the access check does inside the service transaction
            @SuppressWarnings("unchecked")
            List<UUID> r = (List<UUID>) em.createNativeQuery("SELECT id FROM order_items").getResultList();
            return r;
        });
        assertThat(seen).containsExactly(ITEM_A);
    }

    @Test
    void contextDoesNotLeakAcrossTransactionsOnSameConnection() throws Exception {
        asPartner(ORG_A);
        assertThat(visible("SELECT id FROM order_items")).containsExactly(ITEM_A);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(visible("SELECT id FROM order_items")).containsExactlyInAnyOrder(ITEM_A, ITEM_B);

        asPartner(ORG_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            assertThat(em.createNativeQuery("SELECT id FROM order_items").getResultList()).containsExactly(ITEM_A);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        asPartner(ORG_B);
        assertThat(visible("SELECT id FROM order_items")).containsExactly(ITEM_B);

        TenantContext.clear();
        assertThat(visible("SELECT id FROM order_items")).containsExactlyInAnyOrder(ITEM_A, ITEM_B);

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT current_user, coalesce(current_setting('app.mode', true), ''), "
                     + "coalesce(current_setting('app.org_ids', true), '')")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo(POSTGRES.getUsername());
            assertThat(rs.getString(2)).isEmpty();
            assertThat(rs.getString(3)).isEmpty();
        }
    }
}
