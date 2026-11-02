package com.waylo.carrentalservice.rules;

import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.domain.CarDocument;
import com.waylo.carrentalservice.domain.DocumentStatus;
import com.waylo.carrentalservice.domain.DocumentType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pure sellable rules, evaluated at query time so expiry closes listings automatically. */
public final class Sellability {

    private Sellability() { }

    /** Newest APPROVED document of the type wins. */
    public static Optional<CarDocument> effective(Collection<CarDocument> docs, DocumentType type) {
        return docs.stream()
                .filter(d -> d.type == type && d.status == DocumentStatus.APPROVED)
                .max(Comparator.comparing((CarDocument d) -> d.createdAt));
    }

    /** Required types lacking an APPROVED document with validUntil null or >= on. */
    public static List<DocumentType> missing(Collection<DocumentType> required, Collection<CarDocument> docs, LocalDate on) {
        return required.stream().filter(t -> {
            Optional<CarDocument> doc = effective(docs, t);
            return doc.isEmpty() || (doc.get().validUntil != null && doc.get().validUntil.isBefore(on));
        }).toList();
    }

    public static boolean individualGateOpen(OrgInfo org, boolean individualEnabled) {
        return !org.individual() || individualEnabled;
    }

    public static List<DocumentType> missingOrgDocuments(OrgInfo org, Collection<CarDocument> orgLevelDocs, LocalDate on) {
        return missing(DocumentType.requiredForOrganization(org.legalType()), orgLevelDocs, on);
    }

    public static boolean orgSellable(OrgInfo org, Collection<CarDocument> orgLevelDocs, LocalDate on, boolean individualEnabled) {
        return org.active() && org.carRental() && individualGateOpen(org, individualEnabled)
                && missingOrgDocuments(org, orgLevelDocs, on).isEmpty();
    }

    public static List<DocumentType> missingVehicleDocuments(Collection<CarDocument> vehicleDocs, LocalDate on) {
        return missing(DocumentType.vehicleTypes(), vehicleDocs, on);
    }
}
