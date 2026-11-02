package com.waylo.carrentalservice.rules;

import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.domain.CarDocument;
import com.waylo.carrentalservice.domain.DocumentStatus;
import com.waylo.carrentalservice.domain.DocumentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SellabilityTest {

    private static final LocalDate ON = LocalDate.of(2027, 3, 10);

    private static OrgInfo org(String status, String legal) {
        return new OrgInfo(UUID.randomUUID(), "Acme", status, "CAR_RENTAL", legal);
    }

    private static CarDocument doc(DocumentType type, DocumentStatus status, LocalDate until, long created) {
        CarDocument d = new CarDocument();
        d.type = type;
        d.status = status;
        d.validUntil = until;
        d.createdAt = Instant.ofEpochSecond(created);
        return d;
    }

    private static List<CarDocument> companyDocs() {
        return List.of(doc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, ON, 1),
                doc(DocumentType.TAX_CERTIFICATE, DocumentStatus.APPROVED, null, 1));
    }

    @Test
    void companyWithApprovedValidDocuments_isSellable() {
        assertThat(Sellability.orgSellable(org("ACTIVE", "COMPANY"), companyDocs(), ON, false)).isTrue();
    }

    @Test
    void inactiveOrg_isNotSellable() {
        assertThat(Sellability.orgSellable(org("PENDING", "COMPANY"), companyDocs(), ON, false)).isFalse();
    }

    @Test
    void expiredDocument_isNotSellable() {
        List<CarDocument> docs = List.of(doc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, ON.minusDays(1), 1),
                doc(DocumentType.TAX_CERTIFICATE, DocumentStatus.APPROVED, null, 1));
        assertThat(Sellability.orgSellable(org("ACTIVE", "COMPANY"), docs, ON, false)).isFalse();
    }

    @Test
    void rejectedOrPendingDocument_doesNotCount() {
        List<CarDocument> docs = List.of(doc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.REJECTED, ON, 1),
                doc(DocumentType.TAX_CERTIFICATE, DocumentStatus.PENDING, null, 1));
        assertThat(Sellability.missingOrgDocuments(org("ACTIVE", "COMPANY"), docs, ON))
                .containsExactly(DocumentType.RENTAL_AUTHORIZATION, DocumentType.TAX_CERTIFICATE);
    }

    @Test
    void missingDocument_isReported() {
        List<CarDocument> docs = List.of(doc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, ON, 1));
        assertThat(Sellability.missingOrgDocuments(org("ACTIVE", "COMPANY"), docs, ON)).containsExactly(DocumentType.TAX_CERTIFICATE);
    }

    @Test
    void newestApprovedDocumentWins() {
        List<CarDocument> docs = List.of(doc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, ON.minusDays(5), 1),
                doc(DocumentType.RENTAL_AUTHORIZATION, DocumentStatus.APPROVED, ON.plusDays(5), 2));
        assertThat(Sellability.missing(List.of(DocumentType.RENTAL_AUTHORIZATION), docs, ON)).isEmpty();
    }

    @Test
    void individualGate_closedBlocksSellable() {
        List<CarDocument> docs = List.of(doc(DocumentType.IDENTITY_DOCUMENT, DocumentStatus.APPROVED, ON, 1));
        OrgInfo individual = org("ACTIVE", "INDIVIDUAL");
        assertThat(Sellability.orgSellable(individual, docs, ON, false)).isFalse();
        assertThat(Sellability.orgSellable(individual, docs, ON, true)).isTrue();
    }

    @Test
    void vehicleNeedsAllThreeDocuments() {
        List<CarDocument> docs = List.of(doc(DocumentType.VEHICLE_REGISTRATION, DocumentStatus.APPROVED, ON, 1),
                doc(DocumentType.RENTAL_INSURANCE, DocumentStatus.APPROVED, ON.minusDays(1), 1));
        assertThat(Sellability.missingVehicleDocuments(docs, ON))
                .containsExactly(DocumentType.RENTAL_INSURANCE, DocumentType.TRAFFIC_INSURANCE);
    }
}
