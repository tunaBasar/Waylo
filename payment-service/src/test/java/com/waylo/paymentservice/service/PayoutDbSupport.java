package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.AuthAccessClient;
import com.waylo.paymentservice.config.PayoutSettings;
import com.waylo.paymentservice.event.OutboxWriter;
import com.waylo.paymentservice.event.PaymentEventPublisher;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real PostgreSQL 16 (Testcontainers) with the real Flyway migrations V1..V9, a lightweight Spring context with the real
 * payout services and repositories (no web layer, no Kafka). Only auth-service access is a Mockito mock.
 * Every test uses fresh organization ids, so tests never touch each other's rows (the payout tables are append-only
 * and cannot be cleaned).
 */
public abstract class PayoutDbSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    protected static final String IBAN_A = "TR33 0006 1005 1978 6457 8413 26";
    /** A second valid TR IBAN (checksum computed by the helper below at class init). */
    protected static final String IBAN_B = TrIbans.make("000610051978645784132");

    static AnnotationConfigApplicationContext ctx;

    protected static JdbcTemplate jdbc;
    protected static PayoutService payouts;
    protected static BankAccountService bankAccounts;
    protected static PayoutBalanceService balances;
    protected static AuthAccessClient auth;
    protected static PayoutSettings settings;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.waylo.paymentservice.repository")
    public static class TestConfig {

        @Bean DataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(POSTGRES.getJdbcUrl());
            ds.setUsername(POSTGRES.getUsername());
            ds.setPassword(POSTGRES.getPassword());
            ds.setMaximumPoolSize(20);
            return ds;
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            LocalContainerEntityManagerFactoryBean f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds);
            f.setPersistenceProvider(new HibernatePersistenceProvider());
            f.setPackagesToScan("com.waylo.paymentservice.domain");
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return f;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean AuthAccessClient authAccessClient() { return Mockito.mock(AuthAccessClient.class); }
        @Bean PayoutSettings payoutSettings() { return new PayoutSettings(); }
        @Bean IbanCrypto ibanCrypto() { return new IbanCrypto(KEY); }
        @Bean PayoutAccess payoutAccess(AuthAccessClient c) { return new PayoutAccess(c); }
        @Bean OutboxWriter outboxWriter(JdbcTemplate j) { return new OutboxWriter(j); }
        @Bean PaymentEventPublisher paymentEventPublisher(OutboxWriter w) { return new PaymentEventPublisher(w); }
        @Bean PayoutBalanceService payoutBalanceService(com.waylo.paymentservice.repository.EscrowItemRepository e,
                                                        com.waylo.paymentservice.repository.PayoutLedgerRepository l,
                                                        com.waylo.paymentservice.repository.PartnerPayoutRepository p,
                                                        com.waylo.paymentservice.repository.PartnerBankAccountRepository b, PayoutSettings s) {
            return new PayoutBalanceService(e, l, p, b, s);
        }
        @Bean PayoutService payoutService(PayoutAccess a, com.waylo.paymentservice.repository.PartnerPayoutRepository p,
                                          com.waylo.paymentservice.repository.PayoutLedgerRepository l,
                                          com.waylo.paymentservice.repository.PayoutEventRepository e,
                                          com.waylo.paymentservice.repository.PartnerBankAccountRepository b,
                                          com.waylo.paymentservice.repository.PayoutWalletRepository w, PayoutBalanceService bs,
                                          PayoutSettings s, PaymentEventPublisher pub) {
            return new PayoutService(a, p, l, e, b, w, bs, s, pub);
        }
        @Bean BankAccountService bankAccountService(PayoutAccess a, com.waylo.paymentservice.repository.PartnerBankAccountRepository b,
                                                    com.waylo.paymentservice.repository.BankAccountChangeRepository c,
                                                    com.waylo.paymentservice.repository.PartnerPayoutRepository p, IbanCrypto k) {
            return new BankAccountService(a, b, c, p, k);
        }
        @Bean com.waylo.paymentservice.repository.PayoutWalletRepository payoutWalletRepository(jakarta.persistence.EntityManager em) {
            return new com.waylo.paymentservice.repository.PayoutWalletRepository(em);
        }
    }

    @BeforeAll
    static synchronized void startContext() {
        if (ctx != null) return;
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        ctx = new AnnotationConfigApplicationContext(TestConfig.class);
        jdbc = ctx.getBean(JdbcTemplate.class);
        payouts = ctx.getBean(PayoutService.class);
        bankAccounts = ctx.getBean(BankAccountService.class);
        balances = ctx.getBean(PayoutBalanceService.class);
        auth = ctx.getBean(AuthAccessClient.class);
        settings = ctx.getBean(PayoutSettings.class);
    }

    @AfterAll
    static void noop() { /* the container and context live for the whole test JVM (Ryuk stops the container) */ }

    // ------------------------------------------------------------------ fixtures

    public static final class Org {
        public final UUID id = UUID.randomUUID();
        public final UUID owner = UUID.randomUUID();
        public final UUID staff = UUID.randomUUID();
    }

    /** Owner has ORGANIZATION_OWNER, staff has RESERVATION_READ only, the organization is ACTIVE. */
    protected Org newOrg() {
        Org o = new Org();
        Mockito.when(auth.check(o.owner, o.id, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(true, List.of("ORGANIZATION_OWNER")));
        Mockito.when(auth.check(o.staff, o.id, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(true, List.of("HOTEL_STAFF")));
        activate(o.id);
        return o;
    }

    private static final java.util.Set<UUID> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    protected void activate(UUID orgId) {
        ACTIVE.add(orgId);
        Mockito.when(auth.activeOrganizationIds()).thenAnswer(i -> List.copyOf(ACTIVE));
    }

    protected void deactivate(UUID orgId) { ACTIVE.remove(orgId); }

    /** A RELEASED escrow item of the organization (supplier_amount = gross - commission) released now minus ageDays. */
    protected UUID releasedItem(UUID org, String currency, String gross, String commission, int ageDays) {
        return insertItem(org, currency, gross, commission, null, null, "RELEASED", Instant.now().minusSeconds(ageDays * 86400L + 5));
    }

    protected UUID heldItem(UUID org, String currency, String gross, String commission) {
        return insertItem(org, currency, gross, commission, null, null, "HELD", null);
    }

    protected UUID refundedItem(UUID org, String currency, String gross, String commission) {
        return insertItem(org, currency, gross, commission, null, null, "REFUNDED", null);
    }

    /** PARTNER-funded discount item: supplier + commission = charged = gross - discount. */
    protected UUID partnerDiscountItem(UUID org, String gross, String discount, String commission) {
        return insertItem(org, "TRY", gross, commission, discount, "PARTNER", "RELEASED", Instant.now().minusSeconds(5));
    }

    private UUID insertItem(UUID org, String currency, String gross, String commission, String discount, String fundedBy, String status, Instant releasedAt) {
        BigDecimal g = new BigDecimal(gross), c = new BigDecimal(commission), d = discount == null ? BigDecimal.ZERO : new BigDecimal(discount);
        BigDecimal charged = g.subtract(d);
        BigDecimal base = "PARTNER".equals(fundedBy) ? charged : g;
        BigDecimal supplier = base.subtract(c);
        UUID intent = UUID.randomUUID(), order = UUID.randomUUID(), item = UUID.randomUUID();
        jdbc.update("INSERT INTO payment_intents (id, order_id, customer_id, amount, currency, status, provider, idempotency_key) VALUES (?,?,?,?,?,'AUTHORIZED','TEST',?)",
                intent, order, UUID.randomUUID(), charged, currency, "it-" + intent);
        jdbc.update("INSERT INTO escrow_items (id, payment_intent_id, order_id, order_item_id, vertical, supplier_organization_id, gross_amount, charged_amount, discount_amount, discount_funded_by, "
                        + "commission_rate, commission_amount, supplier_amount, currency, status, release_trigger, held_at, released_at) "
                        + "VALUES (?,?,?,?,'HOTEL',?,?,?,?,?,0.1000,?,?,?,?,'REDEMPTION',now(),?)",
                UUID.randomUUID(), intent, order, item, org, g, charged, d, fundedBy, c, supplier, currency, status,
                releasedAt == null ? null : java.sql.Timestamp.from(releasedAt));
        if ("RELEASED".equals(status)) {
            jdbc.update("INSERT INTO ledger_entries (entry_id, group_id, occurred_at, order_id, order_item_id, account, credit_amount, currency, correlation_id) VALUES (?,?,now(),?,?,'SUPPLIER_PAYABLE',?,?,?)",
                    UUID.randomUUID(), UUID.randomUUID(), order, item, supplier, currency, UUID.randomUUID());
        }
        return item;
    }

    /** A fresh valid TR IBAN (the live IBAN hash is unique across organizations, so tests must not share one). */
    protected static String uniqueIban() {
        StringBuilder sb = new StringBuilder("00061");
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) sb.append(r.nextInt(10));
        return TrIbans.make(sb.toString());
    }

    protected UUID verifiedAccount(Org o) { return verifiedAccount(o, uniqueIban()); }

    /** Creates and verifies a bank account for the organization, returns the account id. */
    protected UUID verifiedAccount(Org o, String iban) {
        var r = bankAccounts.put(o.owner, o.id, new com.waylo.paymentservice.dto.PayoutDtos.BankAccountRequest("Acme Otel AS", iban));
        bankAccounts.verify(UUID.randomUUID(), "ADMIN", r.id());
        return r.id();
    }

    protected PayoutService.RequestResult request(Org o, String key, String amount) {
        return payouts.request(o.owner, "owner@example.test", o.id, key, "TRY", new BigDecimal(amount));
    }

    protected static BigDecimal payable(UUID org, String currency) {
        return balances.rawFor(org, currency, Instant.now()).payable();
    }

    protected static final class TrIbans {
        private TrIbans() {}

        /** Builds TRkk + 22 digits... from the 21 digit tail so the ISO 7064 mod-97 checksum is valid. TR IBAN = TR + 2 check + 5 bank + 1 reserved + 16 account. */
        static String make(String body22MinusOne) {
            String bban = body22MinusOne + "0"; // 22 digits
            String rearranged = bban + "292700"; // T=29 R=27 then 00
            int check = 98 - new java.math.BigInteger(rearranged).mod(java.math.BigInteger.valueOf(97)).intValue();
            return "TR" + String.format("%02d", check) + bban;
        }
    }
}
