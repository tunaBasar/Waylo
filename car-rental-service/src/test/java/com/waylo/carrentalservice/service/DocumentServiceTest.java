package com.waylo.carrentalservice.service;

import com.waylo.carrentalservice.client.AuthGateway;
import com.waylo.carrentalservice.client.OrgInfo;
import com.waylo.carrentalservice.config.CarSettings;
import com.waylo.carrentalservice.domain.CarDocument;
import com.waylo.carrentalservice.domain.DocumentStatus;
import com.waylo.carrentalservice.domain.DocumentType;
import com.waylo.carrentalservice.exception.ApiException;
import com.waylo.carrentalservice.exception.ForbiddenException;
import com.waylo.carrentalservice.repository.BranchRepository;
import com.waylo.carrentalservice.repository.CarDocumentRepository;
import com.waylo.carrentalservice.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentServiceTest {

    private final AuthGateway auth = mock(AuthGateway.class);
    private final CarDocumentRepository documents = mock(CarDocumentRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final CarSettings settings = mock(CarSettings.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"), ZoneOffset.UTC);
    private final UUID user = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    private DocumentService service(Path dir) {
        when(auth.requireFleetManager(user, orgId))
                .thenReturn(new OrgInfo(orgId, "Acme", "ACTIVE", "CAR_RENTAL", "COMPANY"));
        when(documents.save(any(CarDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(settings.individualPartnersEnabled()).thenReturn(false);
        return new DocumentService(auth, documents, vehicles, branches, clock, settings, dir.toString());
    }

    @Test
    void upload_validPdf_isStoredPendingUnderRandomKey(@TempDir Path dir) throws Exception {
        DocumentService service = service(dir);
        MockMultipartFile file = new MockMultipartFile("file", "../yetki.pdf", "text/plain", "%PDF-1.4 body".getBytes());

        var res = service.upload(user, orgId, file, DocumentType.RENTAL_AUTHORIZATION, "YB-1", LocalDate.of(2028, 1, 1), null);

        assertThat(res.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(res.contentType()).isEqualTo("application/pdf");
        assertThat(res.fileName()).isEqualTo("yetki.pdf");
        try (var files = Files.list(dir)) {
            assertThat(files.toList()).hasSize(1);
        }
    }

    @Test
    void upload_wrongMagicBytes_isRejectedAndNothingStored(@TempDir Path dir) throws Exception {
        DocumentService service = service(dir);
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", "<html></html>".getBytes());

        assertThatThrownBy(() -> service.upload(user, orgId, file, DocumentType.TAX_CERTIFICATE, "1", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        try (var files = Files.list(dir)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void upload_over10MB_is413(@TempDir Path dir) {
        DocumentService service = service(dir);
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);

        assertThatThrownBy(() -> service.upload(user, orgId, file, DocumentType.TAX_CERTIFICATE, "1", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void upload_vehicleDocumentWithoutVehicle_is422(@TempDir Path dir) {
        DocumentService service = service(dir);
        MockMultipartFile file = new MockMultipartFile("file", "r.pdf", "application/pdf", "%PDF-1".getBytes());
        assertThatThrownBy(() -> service.upload(user, orgId, file, DocumentType.VEHICLE_REGISTRATION, "1",
                LocalDate.of(2028, 1, 1), null)).extracting("ruleCode").isEqualTo("INVALID_DOCUMENT");
    }

    @Test
    void upload_identityDocumentForCompany_isNotAllowed(@TempDir Path dir) {
        DocumentService service = service(dir);
        MockMultipartFile file = new MockMultipartFile("file", "r.pdf", "application/pdf", "%PDF-1".getBytes());
        assertThatThrownBy(() -> service.upload(user, orgId, file, DocumentType.IDENTITY_DOCUMENT, "1",
                LocalDate.of(2028, 1, 1), null)).extracting("ruleCode").isEqualTo("DOCUMENT_TYPE_NOT_ALLOWED");
    }

    @Test
    void upload_withoutFleetManage_isDenied(@TempDir Path dir) {
        when(auth.requireFleetManager(user, orgId)).thenThrow(new ForbiddenException("denied"));
        DocumentService service = new DocumentService(auth, documents, vehicles, branches, clock, settings, dir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "r.pdf", "application/pdf", "%PDF-1".getBytes());
        assertThatThrownBy(() -> service.upload(user, orgId, file, DocumentType.TAX_CERTIFICATE, "1", null, null))
                .isInstanceOf(ForbiddenException.class);
        verify(documents, never()).save(any());
    }
}
