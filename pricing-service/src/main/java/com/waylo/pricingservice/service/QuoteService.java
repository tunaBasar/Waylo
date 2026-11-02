package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.Campaign;
import com.waylo.pricingservice.domain.PromoCode;
import com.waylo.pricingservice.domain.PromoReservation;
import com.waylo.pricingservice.domain.ReservationStatus;
import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.dto.QuoteResponse;
import com.waylo.pricingservice.exception.BusinessRuleException;
import com.waylo.pricingservice.repository.CampaignRepository;
import com.waylo.pricingservice.repository.PromoCodeRepository;
import com.waylo.pricingservice.repository.PromoReservationRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates quote computation and promo reservation.
 * Uses Europe/Istanbul timezone (§1) for validity checks.
 */
@Transactional
public class QuoteService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final QuoteLogic quoteLogic;
    private final CampaignRepository campaignRepo;
    private final PromoCodeRepository promoCodeRepo;
    private final PromoReservationRepository reservationRepo;

    public QuoteService(QuoteLogic quoteLogic,
                        CampaignRepository campaignRepo,
                        PromoCodeRepository promoCodeRepo,
                        PromoReservationRepository reservationRepo) {
        this.quoteLogic = quoteLogic;
        this.campaignRepo = campaignRepo;
        this.promoCodeRepo = promoCodeRepo;
        this.reservationRepo = reservationRepo;
    }

    /**
     * Compute quote and optionally reserve promo usage atomically (§3).
     * Idempotent on same orderId.
     */
    public QuoteResponse computeQuote(QuoteRequest request) {
        // No promo code -> zero discount, no reservation
        if (request.promoCode() == null || request.promoCode().isEmpty()) {
            return zeroDiscountQuote(request);
        }

        // Look up promo code and campaign
        PromoCode promoCode = promoCodeRepo.findByCode(request.promoCode())
                .orElseThrow(() -> new BusinessRuleException(
                        "PROMO_CODE_INVALID", "Promo code not found: " + request.promoCode()));

        Campaign campaign = promoCode.getCampaign();

        // Validate vertical match
        if (!campaign.getVertical().equals(request.vertical())) {
            throw new BusinessRuleException(
                    "PROMO_CODE_INVALID",
                    "Promo code vertical " + campaign.getVertical()
                            + " does not match request vertical " + request.vertical());
        }

        // Validate partner scope match
        if (campaign.getScopeOrgId() != null
                && !campaign.getScopeOrgId().equals(request.supplierOrganizationId())) {
            throw new BusinessRuleException(
                    "PROMO_CODE_INVALID",
                    "Promo code partner scope does not match request org");
        }

        // Validate validity window (Europe/Istanbul)
        LocalDateTime now = LocalDateTime.now(ISTANBUL);
        if (now.isBefore(promoCode.getValidFrom()) || now.isAfter(promoCode.getValidUntil())) {
            throw new BusinessRuleException("PROMO_CODE_EXPIRED", "Promo code is not valid");
        }

        // Validate campaign is still active
        if (now.isBefore(campaign.getStartAt()) || now.isAfter(campaign.getEndAt())) {
            throw new BusinessRuleException("PROMO_CODE_EXPIRED", "Campaign is not active");
        }

        // Validate min order amount
        if (request.grossAmount().compareTo(campaign.getMinOrderAmount()) < 0) {
            throw new BusinessRuleException(
                    "MIN_ORDER_NOT_MET",
                    "Gross amount " + request.grossAmount()
                            + " is below minimum " + campaign.getMinOrderAmount());
        }

        // Validate per-user limit
        if (campaign.getMaxUsesPerUser() != null) {
            long userCount = reservationRepo.countByCustomerIdAndPromoCodeId(
                    request.customerId(), promoCode.getId());
            if (userCount >= campaign.getMaxUsesPerUser()) {
                throw new BusinessRuleException(
                        "PROMO_CODE_MAX_USES_REACHED", "Per-user limit reached");
            }
        }

        // Validate global campaign limit
        if (campaign.getMaxUsesGlobal() != null) {
            long totalReservations = reservationRepo.countActiveByCampaignId(
                    promoCode.getCampaign().getId());
            if (totalReservations >= campaign.getMaxUsesGlobal()) {
                throw new BusinessRuleException(
                        "PROMO_CODE_MAX_USES_REACHED", "Global campaign limit reached");
            }
        }

        // Check idempotency: same orderId already has a reservation
        if (request.orderId() != null) {
            Optional<PromoReservation> existing = reservationRepo.findByOrderId(request.orderId());
            if (existing.isPresent()) {
                PromoReservation pr = existing.get();
                return buildResponseFromReservation(pr);
            }
        }

        // Compute the quote
        QuoteResponse quote = quoteLogic.computeQuote(request, campaign);

        // Reserve if orderId is present
        UUID reservationId = null;
        if (request.orderId() != null) {
            reservationId = reservePromoUsage(request, promoCode, quote);
        }

        return new QuoteResponse(
                quote.netAmount(),
                quote.discountLines(),
                quote.taxAmount(),
                reservationId);
    }

    /**
     * Release a promo reservation idempotently.
     */
    public void releaseReservation(UUID reservationId) {
        PromoReservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new BusinessRuleException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return; // Already released, idempotent
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepo.save(reservation);

        // Decrement uses_count
        PromoCode pc = reservation.getPromoCode();
        if (pc.getUsesCount() != null && pc.getUsesCount() > 0) {
            pc.setUsesCount(pc.getUsesCount() - 1);
        }
        promoCodeRepo.save(pc);
    }

    /**
     * Release a promo reservation by orderId. Idempotent: 200 even if no reservation.
     */
    public void releaseReservationByOrderId(UUID orderId) {
        PromoReservation reservation = reservationRepo.findByOrderId(orderId)
                .orElse(null);

        if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED) {
            return; // Already released or no reservation, idempotent
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepo.save(reservation);

        // Decrement uses_count
        PromoCode pc = reservation.getPromoCode();
        if (pc.getUsesCount() != null && pc.getUsesCount() > 0) {
            pc.setUsesCount(pc.getUsesCount() - 1);
        }
        promoCodeRepo.save(pc);
    }

    // -- private helpers --

    private QuoteResponse zeroDiscountQuote(QuoteRequest request) {
        return new QuoteResponse(
                request.grossAmount(),
                Collections.emptyList(),
                null,
                null);
    }

    private QuoteResponse buildResponseFromReservation(PromoReservation pr) {
        List<QuoteResponse.DiscountLine> discountLines;
        if (pr.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            discountLines = List.of(new QuoteResponse.DiscountLine(
                    "discount",
                    pr.getDiscountAmount(),
                    pr.getDiscountFundedBy()));
        } else {
            discountLines = List.of();
        }
        return new QuoteResponse(pr.getNetAmount(), discountLines, pr.getTaxAmount(), pr.getId());
    }

    private UUID reservePromoUsage(QuoteRequest request, PromoCode promoCode,
                                   QuoteResponse quote) {
        // Lock the campaign row first: the global limit counts reservations across ALL codes of the campaign,
        // so the promo-code row lock alone does not serialize them. Lock order campaign -> promo code everywhere.
        Campaign lockedCampaign = campaignRepo.findByIdForUpdate(promoCode.getCampaign().getId())
                .orElseThrow(() -> new BusinessRuleException(
                        "PROMO_CODE_INVALID", "Campaign disappeared"));

        // Acquire pessimistic lock on the promo code row
        PromoCode locked = promoCodeRepo.findByCodeWithLock(request.promoCode())
                .orElseThrow(() -> new BusinessRuleException(
                        "PROMO_CODE_INVALID", "Promo code disappeared"));

        // Re-check limits under the lock (the checks in computeQuote are only a fast path)
        Optional<PromoReservation> already = reservationRepo.findByOrderId(request.orderId());
        if (already.isPresent()) {
            return already.get().getId();
        }
        if (lockedCampaign.getMaxUsesGlobal() != null
                && reservationRepo.countActiveByCampaignId(lockedCampaign.getId()) >= lockedCampaign.getMaxUsesGlobal()) {
            throw new BusinessRuleException(
                    "PROMO_CODE_MAX_USES_REACHED", "Global campaign limit reached");
        }
        if (lockedCampaign.getMaxUsesPerUser() != null
                && reservationRepo.countByCustomerIdAndPromoCodeId(request.customerId(), locked.getId())
                        >= lockedCampaign.getMaxUsesPerUser()) {
            throw new BusinessRuleException(
                    "PROMO_CODE_MAX_USES_REACHED", "Per-user limit reached");
        }

        // Check promo code own limit
        if (locked.getMaxUsesTotal() != null
                && locked.getUsesCount() >= locked.getMaxUsesTotal()) {
            throw new BusinessRuleException(
                    "PROMO_CODE_MAX_USES_REACHED", "Promo code max uses reached");
        }

        // Atomically increment uses_count with the lock held
        int updated = promoCodeRepo.incrementUsageWithLock(request.promoCode());
        if (updated == 0) {
            throw new BusinessRuleException(
                    "PROMO_CODE_MAX_USES_REACHED", "Promo code max uses reached");
        }

        // Create reservation record
        PromoReservation reservation = new PromoReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setPromoCode(locked);
        reservation.setOrderId(request.orderId());
        reservation.setCustomerId(request.customerId());
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setGrossAmount(request.grossAmount());
        reservation.setDiscountAmount(quote.discountLines().isEmpty()
                ? BigDecimal.ZERO
                : quote.discountLines().get(0).amount());
        reservation.setNetAmount(quote.netAmount());
        reservation.setDiscountFundedBy(
                quote.discountLines().isEmpty() ? null
                        : quote.discountLines().get(0).fundedBy());
        reservation.setTaxAmount(quote.taxAmount());
        reservationRepo.save(reservation);

        return reservation.getId();
    }
}
