package com.waylo.busservice.rules;

import com.waylo.busservice.client.OrgInfo;
import com.waylo.busservice.domain.BusDocument;
import com.waylo.busservice.domain.DocumentStatus;
import com.waylo.busservice.domain.DocumentType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pure sellable rules, evaluated at query time so expiry closes listings automatically. */
public final class Sellability {

    private Sellability() { }

    /** Newest APPROVED document of the type wins. */
    public static Optional<BusDocument> effective(Collection<BusDocument> docs, DocumentType type) {
        return docs.stream()
                .filter(d -> d.type == type && d.status == DocumentStatus.APPROVED)
                .max(Comparator.comparing((BusDocument d) -> d.createdAt));
    }

    /** Required types lacking an APPROVED document with validUntil null or >= on. */
    public static List<DocumentType> missing(Collection<DocumentType> required, Collection<BusDocument> docs, LocalDate on) {
        return required.stream().filter(t -> {
            Optional<BusDocument> doc = effective(docs, t);
            return doc.isEmpty() || (doc.get().validUntil != null && doc.get().validUntil.isBefore(on));
        }).toList();
    }

    public static List<DocumentType> missingOrgDocuments(Collection<BusDocument> orgLevelDocs, LocalDate on) {
        return missing(DocumentType.requiredForOrganization(), orgLevelDocs, on);
    }

    public static boolean orgSellable(OrgInfo org, Collection<BusDocument> orgLevelDocs, LocalDate on) {
        return org.active() && org.bus() && missingOrgDocuments(orgLevelDocs, on).isEmpty();
    }

    public static List<DocumentType> missingBusDocuments(Collection<BusDocument> busDocs, LocalDate on) {
        return missing(DocumentType.busTypes(), busDocs, on);
    }
}
