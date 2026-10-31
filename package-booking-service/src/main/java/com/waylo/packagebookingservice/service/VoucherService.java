package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.dto.VoucherResponse;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Voucher lifecycle. The secret code is a 128-bit value derived as HMAC-SHA256(server secret, voucherId) truncated to
 * 16 bytes (base64url, 22 chars), so it is unguessable without the server secret yet re-derivable for the owner. Only
 * its SHA-256 is stored and the code is never logged.
 *
 * Validity rule: hotel items are valid from check-in day 00:00 to check-out day 12:00 in Europe/Istanbul (hotel-service
 * has no per-hotel timezone yet). Items without stay dates are valid from creation for 365 days.
 */
@Service
public class VoucherService {

    public static final String PREFIX = "WAYLO1";
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");
    static final LocalTime CHECK_OUT_TIME = LocalTime.NOON;
    static final Duration NON_STAY_VALIDITY = Duration.ofDays(365);

    private final VoucherRepository voucherRepository;
    private final OrderItemRepository orderItemRepository;
    private final byte[] secret;

    public VoucherService(VoucherRepository voucherRepository, OrderItemRepository orderItemRepository,
                          @Value("${waylo.voucher.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("waylo.voucher.secret must be at least 32 characters");
        }
        this.voucherRepository = voucherRepository;
        this.orderItemRepository = orderItemRepository;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Idempotent: items that already have a voucher are skipped. */
    @Transactional
    public void createForItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (voucherRepository.findByOrderItemId(item.getId()).isPresent()) continue;
            Voucher v = new Voucher();
            v.setId(UUID.randomUUID());
            v.setOrderItemId(item.getId());
            v.setCodeHash(hash(deriveCode(v.getId())));
            v.setStatus(VoucherStatus.ACTIVE);
            Instant[] window = window(item);
            v.setValidFrom(window[0]);
            v.setValidUntil(window[1]);
            voucherRepository.save(v);
        }
    }

    static Instant[] window(OrderItem item) {
        if (item.getVertical() == OrderItemVertical.HOTEL && item.getCheckIn() != null && item.getCheckOut() != null) {
            return new Instant[] {
                    item.getCheckIn().atStartOfDay(DEFAULT_ZONE).toInstant(),
                    item.getCheckOut().atTime(CHECK_OUT_TIME).atZone(DEFAULT_ZONE).toInstant()};
        }
        Instant now = Instant.now();
        return new Instant[] {now, now.plus(NON_STAY_VALIDITY)};
    }

    @Transactional
    public void voidForOrder(UUID orderId) {
        List<UUID> itemIds = orderItemRepository.findByOrderId(orderId).stream().map(OrderItem::getId).toList();
        for (Voucher v : voucherRepository.findByOrderItemIdIn(itemIds)) {
            if (v.getStatus() == VoucherStatus.ACTIVE) {
                v.setStatus(VoucherStatus.VOID);
                voucherRepository.save(v);
            }
        }
    }

    /** Caller must have verified ownership. Payload is only exposed while the voucher is ACTIVE. */
    @Transactional(readOnly = true)
    public List<VoucherResponse> listForOrder(UUID orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, OrderItem> byId = items.stream().collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        return voucherRepository.findByOrderItemIdIn(byId.keySet()).stream()
                .map(v -> new VoucherResponse(v.getId(), v.getOrderItemId(), byId.get(v.getOrderItemId()).getDescription(),
                        v.getStatus() == VoucherStatus.ACTIVE ? payload(v.getId()) : null, v.getStatus().name(),
                        v.getValidFrom(), v.getValidUntil(), v.getRedeemedAt()))
                .toList();
    }

    public String payload(UUID voucherId) {
        return PREFIX + ":" + voucherId + ":" + deriveCode(voucherId);
    }

    String deriveCode(UUID voucherId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] full = mac.doFinal(voucherId.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(full, 16));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
