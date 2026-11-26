package com.waylo.paymentservice.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.exception.BadRequestException;
import com.waylo.paymentservice.exception.ConflictException;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IBAN storage, masking, audit and reveal rules (PAYOUT-CONTRACT.md sec. 4) against PostgreSQL 16. */
class BankAccountServiceTest extends PayoutDbSupport {

    static final UUID ADMIN = UUID.randomUUID();
    static final String MIDDLE_DIGITS = "0006 1005 1978 6457 8413";

    private PayoutDtos.BankAccountRequest req(String iban) { return new PayoutDtos.BankAccountRequest("Acme Otel AS", iban); }

    @Test
    void create_storesCiphertextLast4AndHash_neverThePlainIban() {
        Org o = newOrg();
        // a fresh IBAN: the live IBAN hash is unique across organizations and other tests register IBAN_A
        String iban = uniqueIban();
        String plain = iban.replace(" ", "");
        String last4 = plain.substring(plain.length() - 4);
        String middle = plain.substring(4, 12);
        var r = bankAccounts.put(o.owner, o.id, req(iban));

        assertThat(r.status().name()).isEqualTo("PENDING_VERIFICATION");
        assertThat(r.ibanLast4()).isEqualTo(last4);
        assertThat(r.maskedIban()).isEqualTo("TR** **** **** **** **** **" + plain.substring(plain.length() - 2));
        Map<String, Object> row = jdbc.queryForMap("SELECT iban_ciphertext, iban_last4, iban_hash FROM partner_bank_accounts WHERE organization_id = ?", o.id);
        assertThat((String) row.get("iban_ciphertext")).doesNotContain(plain.substring(0, 4)).doesNotContain(middle).isNotBlank();
        assertThat(((String) row.get("iban_last4")).trim()).isEqualTo(last4);
        assertThat(((String) row.get("iban_hash")).trim()).hasSize(64).matches("[0-9a-f]{64}");
        // the audit row carries last4 and hash only
        Map<String, Object> change = jdbc.queryForMap("SELECT * FROM partner_bank_account_changes WHERE organization_id = ?", o.id);
        assertThat(change.get("change_type")).isEqualTo("CREATED");
        assertThat(change.toString()).doesNotContain(middle).doesNotContain(plain.substring(0, 4));
    }

    @Test
    void invalidIban_is400_INVALID_IBAN_andNothingIsStored() {
        Org o = newOrg();
        BadRequestException ex = org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class, () -> bankAccounts.put(o.owner, o.id, req("TR33 0006 1005 1978 6457 8413 27")));
        assertThat(ex.getCode()).isEqualTo("INVALID_IBAN");
        assertThat(ex.getMessage()).doesNotContain("00061005").doesNotContain("0006 1005");
        assertThatThrownBy(() -> bankAccounts.put(o.owner, o.id, req("DE89 3704 0044 0532 0130 00"))).isInstanceOf(BadRequestException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_bank_accounts WHERE organization_id = ?", Integer.class, o.id)).isZero();
    }

    @Test
    void replace_setsPendingVerification_bumpsVersion_writesREPLACED_withBothLast4AndHashes_noFullIban() {
        Org o = newOrg();
        UUID id = verifiedAccount(o, uniqueIban());
        long versionBefore = jdbc.queryForObject("SELECT version FROM partner_bank_accounts WHERE id = ?", Long.class, id);
        String oldHash = jdbc.queryForObject("SELECT iban_hash FROM partner_bank_accounts WHERE id = ?", String.class, id).trim();
        String newIban = uniqueIban();

        var r = bankAccounts.put(o.owner, o.id, req(newIban));

        assertThat(r.id()).isEqualTo(id);                                   // replaced in place
        assertThat(r.status().name()).isEqualTo("PENDING_VERIFICATION");
        assertThat(r.verifiedAt()).isNull();
        assertThat(r.version()).isGreaterThan(versionBefore);
        Map<String, Object> change = jdbc.queryForList("SELECT * FROM partner_bank_account_changes WHERE bank_account_id = ? AND change_type = 'REPLACED'", id).get(0);
        assertThat(((String) change.get("old_iban_hash")).trim()).isEqualTo(oldHash);
        assertThat(((String) change.get("new_iban_hash")).trim()).isNotEqualTo(oldHash).hasSize(64);
        assertThat(change.get("old_last4")).isNotNull();
        assertThat(change.get("new_last4")).isEqualTo(IbanMask.last4(IbanValidator.normalize(newIban)));
        assertThat(change.toString()).doesNotContain(IbanValidator.normalize(newIban));
    }

    @Test
    void sameIbanForASecondOrganization_is409_IBAN_ALREADY_IN_USE_butCanBeReusedAfterDisable() {
        Org a = newOrg();
        Org b = newOrg();
        String iban = uniqueIban();
        bankAccounts.put(a.owner, a.id, req(iban));

        ConflictException ex = org.junit.jupiter.api.Assertions.assertThrows(ConflictException.class, () -> bankAccounts.put(b.owner, b.id, req(iban)));
        assertThat(ex.getCode()).isEqualTo("IBAN_ALREADY_IN_USE");
        assertThat(ex.getMessage()).doesNotContain(a.id.toString());

        bankAccounts.delete(a.owner, a.id);
        assertThat(bankAccounts.put(b.owner, b.id, req(iban)).status().name()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void delete_withAnOpenPayout_is409_OPEN_PAYOUT_EXISTS_otherwiseDisables() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "0.00", 0);
        verifiedAccount(o);
        UUID payout = request(o, "del", "300.00").payout().id();

        ConflictException ex = org.junit.jupiter.api.Assertions.assertThrows(ConflictException.class, () -> bankAccounts.delete(o.owner, o.id));
        assertThat(ex.getCode()).isEqualTo("OPEN_PAYOUT_EXISTS");

        payouts.partnerCancel(o.owner, o.id, payout);
        assertThat(bankAccounts.delete(o.owner, o.id).status().name()).isEqualTo("DISABLED");
        assertThatThrownBy(() -> bankAccounts.get(o.owner, o.id)).isInstanceOf(com.waylo.paymentservice.exception.ResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_bank_account_changes WHERE organization_id = ? AND change_type = 'DISABLED'", Integer.class, o.id)).isEqualTo(1);
    }

    @Test
    void getWithoutAccount_is404() {
        Org o = newOrg();
        assertThatThrownBy(() -> bankAccounts.get(o.owner, o.id)).isInstanceOf(com.waylo.paymentservice.exception.ResourceNotFoundException.class);
    }

    @Test
    void verifyAndReject_requireAdmin_pendingStatus_andReasonOnReject() {
        Org o = newOrg();
        UUID id = bankAccounts.put(o.owner, o.id, req(uniqueIban())).id();
        assertThatThrownBy(() -> bankAccounts.verify(UUID.randomUUID(), "CUSTOMER", id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bankAccounts.reject(ADMIN, "ADMIN", id, "no")).isInstanceOf(BadRequestException.class);

        var rejected = bankAccounts.reject(ADMIN, "ADMIN", id, "holder name does not match");
        assertThat(rejected.status().name()).isEqualTo("REJECTED");
        assertThat(rejected.rejectReason()).isEqualTo("holder name does not match");
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(ConflictException.class, () -> bankAccounts.verify(ADMIN, "ADMIN", id)).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");

        bankAccounts.put(o.owner, o.id, req(uniqueIban()));            // fix and resubmit
        var verified = bankAccounts.verify(ADMIN, "ADMIN", id);
        assertThat(verified.status().name()).isEqualTo("VERIFIED");
        assertThat(verified.verifiedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_bank_account_changes WHERE bank_account_id = ? AND change_type IN ('REJECTED','VERIFIED')", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void adminCannotVerifyAnAccountTheySubmittedThemselves() {
        Org o = newOrg();
        UUID adminOwner = UUID.randomUUID();
        org.mockito.Mockito.when(auth.check(adminOwner, o.id, "RESERVATION_READ")).thenReturn(new com.waylo.paymentservice.client.AuthAccessClient.AccessDecision(true, List.of("ORGANIZATION_OWNER")));
        UUID id = bankAccounts.put(adminOwner, o.id, req(uniqueIban())).id();
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class, () -> bankAccounts.verify(adminOwner, "ADMIN", id)).getCode())
                .isEqualTo("SELF_VERIFICATION_NOT_ALLOWED");
    }

    @Test
    void reveal_returnsTheIban_onlyWhileApprovedOrProcessing_writesREVEALED_andIs409Otherwise() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "0.00", 0);
        String iban = uniqueIban();
        bankAccounts.verify(ADMIN, "ADMIN", bankAccounts.put(o.owner, o.id, req(iban)).id());
        UUID payout = request(o, "rev", "300.00").payout().id();

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(ConflictException.class, () -> bankAccounts.reveal(ADMIN, "ADMIN", payout)).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");                                 // REQUESTED
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_bank_account_changes WHERE organization_id = ? AND change_type = 'REVEALED'", Integer.class, o.id)).isZero();

        payouts.approve(ADMIN, "ADMIN", payout, null);
        var details = bankAccounts.reveal(ADMIN, "ADMIN", payout);                      // APPROVED
        assertThat(details.iban()).isEqualTo(IbanValidator.normalize(iban));
        assertThat(details.accountHolderName()).isEqualTo("Acme Otel AS");
        assertThat(details.toString()).doesNotContain(details.iban());
        Map<String, Object> audit = jdbc.queryForList("SELECT * FROM partner_bank_account_changes WHERE organization_id = ? AND change_type = 'REVEALED'", o.id).get(0);
        assertThat(audit.get("reason")).isEqualTo("payout:" + payout);
        assertThat(audit.get("actor_user_id")).isEqualTo(ADMIN);
        assertThat(audit.toString()).doesNotContain(details.iban());

        payouts.startProcessing(ADMIN, "ADMIN", payout, null);
        assertThat(bankAccounts.reveal(ADMIN, "ADMIN", payout).iban()).isEqualTo(details.iban());   // PROCESSING

        payouts.markPaid(ADMIN, "ADMIN", payout, "REF-" + UUID.randomUUID(), null, null);
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(ConflictException.class, () -> bankAccounts.reveal(ADMIN, "ADMIN", payout)).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");                                 // PAID

        assertThatThrownBy(() -> bankAccounts.reveal(UUID.randomUUID(), "CUSTOMER", payout)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bankAccounts.reveal(UUID.randomUUID(), "PARTNER", payout)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revealAccount_adminOnly_anyStatus_writesREVEALEDRow_andUnknownIs404() {
        Org o = newOrg();
        String iban = uniqueIban();
        UUID id = bankAccounts.put(o.owner, o.id, req(iban)).id();                       // PENDING_VERIFICATION

        assertThatThrownBy(() -> bankAccounts.revealAccount(UUID.randomUUID(), "PARTNER", id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bankAccounts.revealAccount(null, "ADMIN", id)).isInstanceOf(AuthenticationRequiredException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_bank_account_changes WHERE organization_id = ? AND change_type = 'REVEALED'", Integer.class, o.id)).isZero();

        var r = bankAccounts.revealAccount(ADMIN, "ADMIN", id);
        assertThat(r.iban()).isEqualTo(IbanValidator.normalize(iban));
        assertThat(r.accountHolderName()).isEqualTo("Acme Otel AS");
        assertThat(r.toString()).doesNotContain(r.iban());
        Map<String, Object> audit = jdbc.queryForList("SELECT * FROM partner_bank_account_changes WHERE organization_id = ? AND change_type = 'REVEALED'", o.id).get(0);
        assertThat(audit.get("reason")).isEqualTo("bank-account:" + id);
        assertThat(audit.get("actor_user_id")).isEqualTo(ADMIN);
        assertThat(audit.toString()).doesNotContain(r.iban());

        bankAccounts.verify(ADMIN, "ADMIN", id);                                           // VERIFIED also works
        assertThat(bankAccounts.revealAccount(ADMIN, "ADMIN", id).iban()).isEqualTo(r.iban());

        assertThatThrownBy(() -> bankAccounts.revealAccount(ADMIN, "ADMIN", UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revealPath_isTheOnlyDtoWithAFullIbanAccessor_andNoOtherJsonContainsTheMiddleDigits() throws Exception {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "0.00", 0);
        String iban = "TR33 0006 1005 1978 6457 8413 26";
        // this IBAN may already be registered by another test class run: skip the fixture when taken
        UUID acc;
        try {
            acc = bankAccounts.put(o.owner, o.id, req(iban)).id();
        } catch (ConflictException taken) {
            acc = bankAccounts.put(o.owner, o.id, req(uniqueIban())).id();
            iban = null;
        }
        bankAccounts.verify(ADMIN, "ADMIN", acc);
        UUID payout = request(o, "dto", "300.00").payout().id();

        JsonMapper mapper = JsonMapper.builder().build();
        List<Object> responses = List.of(
                bankAccounts.get(o.owner, o.id),
                payouts.get(o.owner, o.id, payout),
                payouts.list(o.owner, o.id, null, 0, 20),
                payouts.balance(o.owner, o.id),
                payouts.adminDetail(ADMIN, "ADMIN", payout),
                payouts.adminQueue(ADMIN, "ADMIN", null, o.id, null, 0, 20),
                payouts.adminBalance(ADMIN, "ADMIN", o.id),
                bankAccounts.adminList(ADMIN, "ADMIN", null, 50));
        for (Object response : responses) {
            String json = mapper.writeValueAsString(response);
            assertThat(json).doesNotContain(MIDDLE_DIGITS).doesNotContain("00061005").doesNotContain("ciphertext").doesNotContain("ibanHash").doesNotContain("iban_hash");
            if (iban != null) assertThat(json).doesNotContain(IbanValidator.normalize(iban));
        }

        for (Class<?> dto : PayoutDtos.class.getDeclaredClasses()) {
            // The two audited admin reveal DTOs and the partner's input are the only records allowed to carry a full IBAN.
            if (!dto.isRecord() || dto == PayoutDtos.BankDetailsResponse.class || dto == PayoutDtos.BankAccountIbanResponse.class
                    || dto == PayoutDtos.BankAccountRequest.class) continue;
            for (RecordComponent c : dto.getRecordComponents()) {
                String n = c.getName().toLowerCase();
                assertThat(n).as("%s.%s", dto.getSimpleName(), c.getName()).isNotIn("iban", "ibanciphertext", "ibanhash", "ciphertext", "hash");
            }
        }
    }

    @Test
    void noLogEventDuringCreateOrReplace_containsTheIban() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level old = root.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.setLevel(Level.TRACE);
        root.addAppender(appender);
        try {
            Org o = newOrg();
            String first = uniqueIban();
            String second = uniqueIban();
            bankAccounts.put(o.owner, o.id, req(first));
            bankAccounts.put(o.owner, o.id, req(second));
            assertThatThrownBy(() -> bankAccounts.put(o.owner, o.id, req("TR33 0006 1005 1978 6457 8413 27"))).isInstanceOf(BadRequestException.class);
            Org other = newOrg();
            assertThatThrownBy(() -> bankAccounts.put(other.owner, other.id, req(second))).isInstanceOf(ConflictException.class);

            for (ILoggingEvent e : appender.list) {
                String text = e.getFormattedMessage() + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage());
                for (String iban : List.of(first, second, "TR33 0006 1005 1978 6457 8413 27")) {
                    assertThat(text).doesNotContain(IbanValidator.normalize(iban)).doesNotContain(iban);
                }
                assertThat(text).doesNotContain(MIDDLE_DIGITS);
            }
        } finally {
            root.detachAppender(appender);
            root.setLevel(old);
        }
    }

    @Test
    void requestRecordToString_neverPrintsTheIban() {
        String text = req("TR33 0006 1005 1978 6457 8413 26").toString();
        assertThat(text).doesNotContain("00061005").doesNotContain("TR33").doesNotContain("0006 1005").contains("accountHolderName=Acme Otel AS");
        assertThat(new PayoutDtos.BankDetailsResponse(UUID.randomUUID(), "A", "TR330006100519786457841326").toString()).doesNotContain("00061005");
        assertThat(Instant.now()).isNotNull();
        assertThat(BigDecimal.ONE).isNotNull();
    }
}
