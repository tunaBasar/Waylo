package com.waylo.notificationservice.service;

import com.waylo.notificationservice.domain.Notification;
import com.waylo.notificationservice.domain.NotificationStatus;
import com.waylo.notificationservice.domain.NotificationType;
import com.waylo.notificationservice.dto.NotificationMapper;
import com.waylo.notificationservice.dto.NotificationResponse;
import com.waylo.notificationservice.exception.BusinessRuleException;
import com.waylo.notificationservice.repository.DeviceTokenRepository;
import com.waylo.notificationservice.repository.NotificationRepository;
import io.micrometer.core.instrument.Metrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryRetryTest {

    // Outside Spring the global composite registry has no child registry, so counters are no-ops that read 0.
    private static final io.micrometer.core.instrument.simple.SimpleMeterRegistry REGISTRY =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    @org.junit.jupiter.api.BeforeAll
    static void attachRegistry() {
        Metrics.addRegistry(REGISTRY);
    }

    @org.junit.jupiter.api.AfterAll
    static void detachRegistry() {
        Metrics.removeRegistry(REGISTRY);
        REGISTRY.close();
    }

    @Mock private NotificationRepository repository;
    @Mock private NotificationMapper mapper;
    @Mock private EmailService emailService;
    @Mock private PushSender pushSender;
    @Mock private DeviceTokenRepository deviceTokens;

    private NotificationServiceImpl service() {
        return new NotificationServiceImpl(repository, mapper, emailService, pushSender, deviceTokens);
    }

    private Notification n(NotificationStatus status, int retryCount) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setCustomerId(UUID.randomUUID());
        n.setType(NotificationType.EMAIL);
        n.setRecipient("user@waylo.com");
        n.setSubject("S");
        n.setBody("B");
        n.setStatus(status);
        n.setRetryCount(retryCount);
        return n;
    }

    private double counter(String outcome) {
        var c = Metrics.globalRegistry.find("waylo.notifications.delivery").tag("channel", "EMAIL").tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    // --- backoff ---

    @Test
    void backoffSchedule_is1m5m30m2h12h() {
        assertThat(DeliveryBackoff.delay(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(DeliveryBackoff.delay(1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(DeliveryBackoff.delay(2)).isEqualTo(Duration.ofMinutes(30));
        assertThat(DeliveryBackoff.delay(3)).isEqualTo(Duration.ofHours(2));
        assertThat(DeliveryBackoff.delay(4)).isEqualTo(Duration.ofHours(12));
        assertThat(DeliveryBackoff.delay(99)).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void failedAttempt_setsNextAttemptWithBackoffAndCountsRetry() {
        Notification f = n(NotificationStatus.FAILED, 1);
        when(repository.claimDueForRetry(any(), anyInt(), anyInt())).thenReturn(List.of(f));
        when(emailService.deliver(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("smtp down user@waylo.com"));

        Instant before = Instant.now();
        assertThat(service().retryDue(10)).isEqualTo(1);

        // retry_count was 1, becomes 2, delay = SCHEDULE[2] = 30m
        assertThat(f.getRetryCount()).isEqualTo(2);
        assertThat(f.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(f.getNextAttemptAt()).isAfter(before.plus(Duration.ofMinutes(29)));
        assertThat(f.getLastError()).doesNotContain("@");
        verify(repository).save(f);
    }

    // --- max attempts ---

    @Test
    void maxAttemptsReached_givesUp() {
        Notification f = n(NotificationStatus.FAILED, 4);
        when(repository.claimDueForRetry(any(), anyInt(), anyInt())).thenReturn(List.of(f));
        when(emailService.deliver(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("x"));
        double before = counter("gave_up");

        service().retryDue(10);

        assertThat(f.getRetryCount()).isEqualTo(5);
        assertThat(f.getStatus()).isEqualTo(NotificationStatus.GAVE_UP);
        assertThat(f.getNextAttemptAt()).isNull();
        assertThat(counter("gave_up")).isEqualTo(before + 1);
    }

    // --- never picked ---

    @Test
    void nothingClaimed_nothingSent() {
        when(repository.claimDueForRetry(any(), anyInt(), anyInt())).thenReturn(List.of());

        assertThat(service().retryDue(10)).isZero();

        verifyNoInteractions(emailService, pushSender);
        verify(repository, never()).save(any());
    }

    @Test
    void claimQuery_onlyFailedRows_neverSentOrSuppressed() throws Exception {
        var m = NotificationRepository.class.getMethod("claimDueForRetry", Instant.class, int.class, int.class);
        String sql = m.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertThat(sql).contains("status = 'FAILED'").contains("FOR UPDATE SKIP LOCKED").contains("retry_count < :max")
                .contains("next_attempt_at <= :now").doesNotContain("SUPPRESSED").doesNotContain("'SENT'");
    }

    @Test
    void manualRetry_sentIsRejectedWithoutResend() {
        Notification sent = n(NotificationStatus.SENT, 0);
        when(repository.findById(sent.getId())).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> service().retry(sent.getId())).isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(emailService);
    }

    // --- concurrency ---

    @Test
    void twoInstances_claimDisjointRows_noDoubleSend() {
        Notification a = n(NotificationStatus.FAILED, 0);
        Notification b = n(NotificationStatus.FAILED, 0);
        List<Notification> unlocked = new ArrayList<>(List.of(a, b));
        // simulates SKIP LOCKED: a claim removes the rows it takes, so another instance never sees them
        when(repository.claimDueForRetry(any(), anyInt(), anyInt())).thenAnswer(inv -> {
            synchronized (unlocked) {
                List<Notification> got = new ArrayList<>(unlocked.subList(0, Math.min(1, unlocked.size())));
                unlocked.removeAll(got);
                return got;
            }
        });
        List<String> sends = new CopyOnWriteArrayList<>();
        when(emailService.deliver(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            sends.add("x");
            return EmailService.Delivery.SENT;
        });
        NotificationServiceImpl i1 = service();
        NotificationServiceImpl i2 = service();

        i1.retryDue(1);
        i2.retryDue(1);
        i1.retryDue(1);

        assertThat(sends).hasSize(2);
        assertThat(a.getRetryCount()).isEqualTo(1);
        assertThat(b.getRetryCount()).isEqualTo(1);
        assertThat(a.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(b.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    // --- manual retry of GAVE_UP ---

    @Test
    void manualRetry_gaveUpIsRetriedAndCanBecomeSent() {
        Notification g = n(NotificationStatus.GAVE_UP, 5);
        when(repository.findById(g.getId())).thenReturn(Optional.of(g));
        when(emailService.deliver(anyString(), anyString(), anyString())).thenReturn(EmailService.Delivery.SENT);
        when(mapper.toResponse(any(Notification.class))).thenReturn(mock(NotificationResponse.class));

        service().retry(g.getId());

        assertThat(g.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(g.getRetryCount()).isEqualTo(6);
        assertThat(g.getNextAttemptAt()).isNull();
        verify(repository).save(g);
    }

    @Test
    void manualRetry_gaveUpFailingAgainStaysGaveUp() {
        Notification g = n(NotificationStatus.GAVE_UP, 5);
        when(repository.findById(g.getId())).thenReturn(Optional.of(g));
        when(emailService.deliver(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("x"));
        when(mapper.toResponse(any(Notification.class))).thenReturn(mock(NotificationResponse.class));

        service().retry(g.getId());

        assertThat(g.getStatus()).isEqualTo(NotificationStatus.GAVE_UP);
    }

    // --- metric ---

    @Test
    void metric_sentAndFailedOutcomes() {
        double sent = counter("sent");
        double failed = counter("failed");
        Notification ok = n(NotificationStatus.FAILED, 0);
        Notification bad = n(NotificationStatus.FAILED, 0);
        when(repository.claimDueForRetry(any(), anyInt(), anyInt())).thenReturn(List.of(ok, bad));
        when(emailService.deliver(anyString(), anyString(), anyString()))
                .thenReturn(EmailService.Delivery.SENT)
                .thenThrow(new RuntimeException("x"));

        service().retryDue(10);

        assertThat(counter("sent")).isEqualTo(sent + 1);
        assertThat(counter("failed")).isEqualTo(failed + 1);
    }

    @Test
    void metric_suppressedIsNotCountedAsDelivery() {
        double before = counter("sent") + counter("failed") + counter("gave_up");
        Notification s = n(NotificationStatus.FAILED, 0);
        when(repository.claimDueForRetry(any(), anyInt(), anyInt())).thenReturn(List.of(s));
        when(emailService.deliver(anyString(), anyString(), anyString())).thenReturn(EmailService.Delivery.SUPPRESSED);

        service().retryDue(10);

        assertThat(s.getStatus()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(counter("sent") + counter("failed") + counter("gave_up")).isEqualTo(before);
    }

    @Test
    void getByStatus_delegatesToRepository() {
        Notification g = n(NotificationStatus.GAVE_UP, 5);
        when(repository.findTop500ByStatusOrderByCreatedAtDesc(NotificationStatus.GAVE_UP)).thenReturn(List.of(g));
        NotificationResponse r = mock(NotificationResponse.class);
        when(mapper.toResponse(g)).thenReturn(r);

        assertThat(service().getByStatus(NotificationStatus.GAVE_UP)).containsExactly(r);
    }
}
