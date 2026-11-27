package com.waylo.hotelservice.automation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Set-based inserts with ON CONFLICT DO NOTHING (rows are written in chunks, never one round trip per night).
 * Runs inside the caller's transaction, so the RLS context of that transaction applies.
 */
@Component
public class RuleRowWriter {

    private static final int CHUNK = 500;

    private final EntityManager entityManager;

    public RuleRowWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record InventoryInsert(UUID roomTypeId, LocalDate stayDate, int allotment) {}

    public record RateInsert(UUID roomTypeId, UUID ratePlanId, LocalDate stayDate, int occupancy,
                             BigDecimal amount, String currency) {}

    /** @return rows actually inserted */
    public int insertInventory(UUID hotelId, List<InventoryInsert> rows) {
        int inserted = 0;
        for (int from = 0; from < rows.size(); from += CHUNK) {
            List<InventoryInsert> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK));
            int hotelParam = chunk.size() * 3 + 1;
            StringBuilder sql = new StringBuilder(
                    "INSERT INTO daily_inventory (id, hotel_id, room_type_id, stay_date, allotment, out_of_service, "
                            + "held, confirmed, stop_sell, version, source) VALUES ");
            for (int i = 0; i < chunk.size(); i++) {
                int p = i * 3;
                sql.append(i == 0 ? "" : ",")
                        .append("(gen_random_uuid(), CAST(?").append(hotelParam).append(" AS uuid), CAST(?")
                        .append(p + 1).append(" AS uuid), CAST(?").append(p + 2).append(" AS date), ?")
                        .append(p + 3).append(", 0, 0, 0, FALSE, 0, 'RULE')");
            }
            sql.append(" ON CONFLICT (hotel_id, room_type_id, stay_date) DO NOTHING");
            Query q = entityManager.createNativeQuery(sql.toString());
            for (int i = 0; i < chunk.size(); i++) {
                InventoryInsert r = chunk.get(i);
                q.setParameter(i * 3 + 1, r.roomTypeId().toString());
                q.setParameter(i * 3 + 2, r.stayDate().toString());
                q.setParameter(i * 3 + 3, r.allotment());
            }
            q.setParameter(hotelParam, hotelId.toString());
            inserted += q.executeUpdate();
        }
        return inserted;
    }

    /** @return rows actually inserted */
    public int insertRates(UUID hotelId, List<RateInsert> rows) {
        int inserted = 0;
        for (int from = 0; from < rows.size(); from += CHUNK) {
            List<RateInsert> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK));
            int hotelParam = chunk.size() * 6 + 1;
            StringBuilder sql = new StringBuilder(
                    "INSERT INTO daily_rates (id, hotel_id, room_type_id, rate_plan_id, stay_date, occupancy, "
                            + "amount, currency, version, source) VALUES ");
            for (int i = 0; i < chunk.size(); i++) {
                int p = i * 6;
                sql.append(i == 0 ? "" : ",")
                        .append("(gen_random_uuid(), CAST(?").append(hotelParam).append(" AS uuid), CAST(?")
                        .append(p + 1).append(" AS uuid), CAST(?").append(p + 2).append(" AS uuid), CAST(?")
                        .append(p + 3).append(" AS date), ?").append(p + 4).append(", CAST(?").append(p + 5)
                        .append(" AS numeric), ?").append(p + 6).append(", 0, 'RULE')");
            }
            sql.append(" ON CONFLICT (hotel_id, room_type_id, rate_plan_id, stay_date, occupancy) DO NOTHING");
            Query q = entityManager.createNativeQuery(sql.toString());
            for (int i = 0; i < chunk.size(); i++) {
                RateInsert r = chunk.get(i);
                int p = i * 6;
                q.setParameter(p + 1, r.roomTypeId().toString());
                q.setParameter(p + 2, r.ratePlanId().toString());
                q.setParameter(p + 3, r.stayDate().toString());
                q.setParameter(p + 4, r.occupancy());
                q.setParameter(p + 5, r.amount().toPlainString());
                q.setParameter(p + 6, r.currency());
            }
            q.setParameter(hotelParam, hotelId.toString());
            inserted += q.executeUpdate();
        }
        return inserted;
    }
}
