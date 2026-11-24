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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RM-14 bus: RLS isolates organizations (incl. child tables via trip), no pool leak, terminals stay global. */
@Testcontainers
class TenantRlsPoolLeakIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();
    static final UUID BUS_A = UUID.randomUUID();
    static final UUID BUS_B = UUID.randomUUID();
    static final UUID TRIP_A = UUID.randomUUID();
    static final UUID TRIP_B = UUID.randomUUID();
    static final UUID TERMINAL = UUID.randomUUID();
    static final UUID BOOKING_A = UUID.randomUUID();
    static final UUID BOOKING_B = UUID.randomUUID();
    static final UUID TICKET_A = UUID.randomUUID();
    static final UUID TICKET_B = UUID.randomUUID();

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
            s.execute("INSERT INTO terminals (id, city, name) VALUES ('" + TERMINAL + "', 'Ankara', 'AŞTİ')");
            insertBus(s, BUS_A, ORG_A, "34A001");
            insertBus(s, BUS_B, ORG_B, "34B001");
            insertTrip(s, TRIP_A, ORG_A, BUS_A, "A1");
            insertTrip(s, TRIP_B, ORG_B, BUS_B, "B1");
            insertBooking(s, BOOKING_A, TICKET_A, ORG_A, TRIP_A);
            insertBooking(s, BOOKING_B, TICKET_B, ORG_B, TRIP_B);
            for (UUID trip : new UUID[] {TRIP_A, TRIP_B}) {
                s.execute("INSERT INTO trip_stops (id, trip_id, seq, terminal_id) VALUES ('" + UUID.randomUUID() + "', '" + trip + "', 0, '" + TERMINAL + "')");
                s.execute("INSERT INTO trip_fares (id, trip_id, from_seq, to_seq, price) VALUES ('" + UUID.randomUUID() + "', '" + trip + "', 0, 1, 100)");
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

    private static void insertBus(Statement s, UUID id, UUID org, String plate) throws Exception {
        s.execute("INSERT INTO buses (id, organization_id, plate_number, brand, model, year, layout, row_count, seat_count) VALUES ('"
                + id + "', '" + org + "', '" + plate + "', 'Mercedes', 'Tourismo', 2022, 'TWO_PLUS_ONE', 10, 30)");
    }

    private static void insertTrip(Statement s, UUID id, UUID org, UUID bus, String code) throws Exception {
        s.execute("INSERT INTO trips (id, organization_id, bus_id, code, status, departure_at, currency) VALUES ('"
                + id + "', '" + org + "', '" + bus + "', '" + code + "', 'DRAFT', now(), 'TRY')");
    }

    private static void insertBooking(Statement s, UUID id, UUID ticket, UUID org, UUID trip) throws Exception {
        s.execute("INSERT INTO bookings (id, organization_id, customer_id, trip_id, from_seq, to_seq, currency, total_amount, "
                + "status, hold_expires_at, idempotency_key) VALUES ('" + id + "', '" + org + "', '" + UUID.randomUUID()
                + "', '" + trip + "', 0, 1, 'TRY', 100, 'HELD', now() + interval '1 hour', 'rls-" + id + "')");
        s.execute("INSERT INTO tickets (id, booking_id, trip_id, seat_no, from_seq, to_seq, passenger_name, gender, price, "
                + "status, hold_expires_at) VALUES ('" + ticket + "', '" + id + "', '" + trip
                + "', 1, 0, 1, 'Pax', 'MALE', 100, 'HELD', now() + interval '1 hour')");
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
    private List<UUID> visible(String table, String col) {
        return tx.execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            return (List<UUID>) em.createNativeQuery("SELECT DISTINCT " + col + " FROM " + table + " ORDER BY 1").getResultList();
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
        assertThat(visible("buses", "id")).containsExactly(BUS_A);
        assertThat(visible("trips", "id")).containsExactly(TRIP_A);
        assertThat(visible("trip_stops", "trip_id")).containsExactly(TRIP_A);
        assertThat(visible("trip_fares", "trip_id")).containsExactly(TRIP_A);
        assertThat(visible("terminals", "id")).containsExactly(TERMINAL);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_B);
        assertThat(visible("trips", "id")).containsExactly(TRIP_B);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(visible("trips", "id")).containsExactlyInAnyOrder(TRIP_A, TRIP_B);
        assertThat(visible("trip_stops", "trip_id")).containsExactlyInAnyOrder(TRIP_A, TRIP_B);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            assertThat(em.createNativeQuery("SELECT id FROM trips").getResultList()).containsExactly(TRIP_A);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_B);
        assertThat(visible("trips", "id")).containsExactly(TRIP_B);

        TenantContext.clear();
        assertThat(sessionState()).isEqualTo("waylo_app|service|" + pid);
        assertThat(visible("trips", "id")).containsExactlyInAnyOrder(TRIP_A, TRIP_B);

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
            TenantContext.authorizePartner(ORG_A);
            @SuppressWarnings("unchecked")
            List<UUID> r = (List<UUID>) em.createNativeQuery("SELECT id FROM trips").getResultList();
            return r;
        });
        assertThat(seen).containsExactly(TRIP_A);
    }

    @Test
    void partnerAndPublicCannotWriteOtherRowsEvenWithoutCodeChecks() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        int updated = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE trips SET code = 'pwned' WHERE id = ?1")
                .setParameter(1, TRIP_B).executeUpdate());
        assertThat(updated).isZero();
        int fares = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE trip_fares SET price = 1 WHERE trip_id = ?1")
                .setParameter(1, TRIP_B).executeUpdate());
        assertThat(fares).isZero();

        TenantContext.set(TenantContext.Mode.PUBLIC);
        int publicUpdate = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE buses SET brand = 'pwned'").executeUpdate());
        assertThat(publicUpdate).isZero();

        TenantContext.set(TenantContext.Mode.SERVICE);
        int service = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE buses SET brand = 'Mercedes'").executeUpdate());
        assertThat(service).isEqualTo(2);
    }

    @Test
    void partnerSeesAndUpdatesOnlyOwnBookingsAndTicketsWithoutWhereClause() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThat(visible("bookings", "id")).containsExactly(BOOKING_A);
        assertThat(visible("tickets", "id")).containsExactly(TICKET_A);
        int bookings = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE bookings SET status = 'CANCELLED'").executeUpdate());
        assertThat(bookings).isEqualTo(1);
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        int tickets = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE tickets SET status = 'CANCELLED'").executeUpdate());
        assertThat(tickets).isEqualTo(1);
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        int other = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("UPDATE tickets SET status = 'HELD' WHERE booking_id = ?1")
                .setParameter(1, BOOKING_B).executeUpdate());
        assertThat(other).isZero();

        TenantContext.set(TenantContext.Mode.SERVICE);
        tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            em.createNativeQuery("UPDATE bookings SET status = 'HELD'").executeUpdate();
            em.createNativeQuery("UPDATE tickets SET status = 'HELD'").executeUpdate();
        });
    }

    @Test
    void publicServiceAndAdminSeeAllBookingsAndTickets() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        assertThat(visible("bookings", "id")).containsExactlyInAnyOrder(BOOKING_A, BOOKING_B);
        assertThat(visible("tickets", "id")).containsExactlyInAnyOrder(TICKET_A, TICKET_B);
        TenantContext.set(TenantContext.Mode.ADMIN);
        assertThat(visible("bookings", "id")).containsExactlyInAnyOrder(BOOKING_A, BOOKING_B);
        TenantContext.clear(); // no context = SERVICE (HoldExpiryScheduler)
        assertThat(visible("bookings", "id")).containsExactlyInAnyOrder(BOOKING_A, BOOKING_B);
        assertThat(visible("tickets", "id")).containsExactlyInAnyOrder(TICKET_A, TICKET_B);
    }

    @Test
    void partnerCannotInsertBookingOrTicketForAnotherOrganization() {
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(ORG_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                .createNativeQuery("INSERT INTO tickets (id, booking_id, trip_id, seat_no, from_seq, to_seq, passenger_name, "
                        + "gender, price, status, hold_expires_at) VALUES (?1, ?2, ?3, 2, 0, 1, 'Evil', 'MALE', 1, 'HELD', now())")
                .setParameter(1, UUID.randomUUID()).setParameter(2, BOOKING_B).setParameter(3, TRIP_B).executeUpdate()))
                .hasMessageContaining("row-level security");
    }
}
