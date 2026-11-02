package com.waylo.busservice.rules;

import com.waylo.busservice.client.OrgInfo;
import com.waylo.busservice.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SellabilityTest {

    private final LocalDate on = LocalDate.of(2027, 3, 1);

    private BusDocument doc(DocumentType type, DocumentStatus status, LocalDate until) {
        BusDocument d = new BusDocument();
        d.type = type;
        d.status = status;
        d.validUntil = until;
        d.createdAt = Instant.now();
        return d;
    }

    @Test
    void orgNeedsApprovedAuthorizationAndTax() {
        OrgInfo org = new OrgInfo(UUID.randomUUID(), "Acme", "ACTIVE", "BUS", "COMPANY");
        assertThat(Sellability.orgSellable(org, List.of(), on)).isFalse();
        List<BusDocument> docs = List.of(doc(DocumentType.TRANSPORT_AUTHORIZATION, DocumentStatus.APPROVED, LocalDate.of(2030, 1, 1)),
                doc(DocumentType.TAX_CERTIFICATE, DocumentStatus.APPROVED, null));
        assertThat(Sellability.orgSellable(org, docs, on)).isTrue();
        assertThat(Sellability.orgSellable(new OrgInfo(org.id(), "A", "SUSPENDED", "BUS", "COMPANY"), docs, on)).isFalse();
        assertThat(Sellability.orgSellable(new OrgInfo(org.id(), "A", "ACTIVE", "HOTEL", "COMPANY"), docs, on)).isFalse();
    }

    @Test
    void expiredOrPendingDocumentsAreMissing() {
        List<BusDocument> docs = List.of(
                doc(DocumentType.VEHICLE_REGISTRATION, DocumentStatus.APPROVED, LocalDate.of(2027, 2, 28)),
                doc(DocumentType.VEHICLE_CARD, DocumentStatus.PENDING, LocalDate.of(2030, 1, 1)),
                doc(DocumentType.TRAFFIC_INSURANCE, DocumentStatus.APPROVED, LocalDate.of(2030, 1, 1)),
                doc(DocumentType.SEAT_ACCIDENT_INSURANCE, DocumentStatus.APPROVED, LocalDate.of(2027, 3, 1)));
        assertThat(Sellability.missingBusDocuments(docs, on))
                .containsExactly(DocumentType.VEHICLE_REGISTRATION, DocumentType.VEHICLE_CARD);
    }
}
