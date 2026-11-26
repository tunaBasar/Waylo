package com.waylo.hotelservice.media;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the exact @Query text of HotelMediaRepository.findCovers against real Postgres (one statement, many hotels). */
@Testcontainers
class HotelMediaCoverQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID ORG = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final UUID H_MANY = UUID.randomUUID();
    static final UUID H_NONE = UUID.randomUUID();
    static final UUID H_ONLY_BAD = UUID.randomUUID();
    static final UUID H_DRAFT = UUID.randomUUID();
    static final UUID M_FIRST = UUID.randomUUID();
    static final UUID M_DRAFT = UUID.randomUUID();

    @BeforeAll
    static void seed() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            hotel(s, H_MANY, "PUBLISHED");
            hotel(s, H_NONE, "PUBLISHED");
            hotel(s, H_ONLY_BAD, "PUBLISHED");
            hotel(s, H_DRAFT, "DRAFT");
            media(s, UUID.randomUUID(), H_MANY, 0, "CLEAN", "PENDING", null);
            media(s, UUID.randomUUID(), H_MANY, 0, "CLEAN", "REJECTED", "Bulanik foto");
            media(s, UUID.randomUUID(), H_MANY, 0, "REJECTED", "APPROVED", null);
            media(s, UUID.randomUUID(), H_MANY, 5, "CLEAN", "APPROVED", null);
            media(s, M_FIRST, H_MANY, 1, "CLEAN", "APPROVED", null);
            media(s, UUID.randomUUID(), H_ONLY_BAD, 0, "CLEAN", "PENDING", null);
            media(s, M_DRAFT, H_DRAFT, 0, "CLEAN", "APPROVED", null);
        }
    }

    static Connection conn() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    static void hotel(Statement s, UUID id, String status) throws Exception {
        s.execute("INSERT INTO hotels (id, name, city, country, address, star_rating, active, organization_id, publication_status, published_at) VALUES ('"
                + id + "','H','Antalya','TR','x',3,true,'" + ORG + "','" + status + "', now())");
    }

    static void media(Statement s, UUID id, UUID hotel, int sort, String scan, String review, String reason) throws Exception {
        s.execute("INSERT INTO hotel_media (id, hotel_id, organization_id, sort_order, rights_source, storage_key, content_type, size_bytes, width, height, scan_status, review_status, review_reason, uploaded_by) VALUES ('"
                + id + "','" + hotel + "','" + ORG + "'," + sort + ",'OWNED','" + UUID.randomUUID() + "','image/png',1000,800,600,'" + scan + "','" + review + "',"
                + (reason == null ? "NULL" : "'" + reason + "'") + ",'" + USER + "')");
    }

    @Test
    void coverIsFirstApprovedCleanBySortOrder_othersIgnored_oneStatementForAllHotels() throws Exception {
        String sql = HotelMediaRepository.class.getMethod("findCovers", java.util.Collection.class)
                .getAnnotation(Query.class).value().replace("(:hotelIds)", "(?, ?, ?, ?)");
        Map<UUID, UUID> covers = new HashMap<>();
        try (Connection c = conn(); var ps = c.prepareStatement(sql)) {
            ps.setObject(1, H_MANY);
            ps.setObject(2, H_NONE);
            ps.setObject(3, H_ONLY_BAD);
            ps.setObject(4, H_DRAFT);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) covers.put(rs.getObject("hotelId", UUID.class), rs.getObject("mediaId", UUID.class));
            }
        }
        assertThat(covers).containsOnlyKeys(H_MANY);
        assertThat(covers.get(H_MANY)).isEqualTo(M_FIRST);
        assertThat(covers).doesNotContainKeys(H_NONE, H_ONLY_BAD, H_DRAFT);
    }
}
