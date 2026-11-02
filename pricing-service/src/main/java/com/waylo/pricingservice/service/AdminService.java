package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.Campaign;
import com.waylo.pricingservice.domain.DiscountType;
import com.waylo.pricingservice.domain.FundedBy;
import com.waylo.pricingservice.domain.PromoCode;
import com.waylo.pricingservice.domain.TaxRate;
import com.waylo.pricingservice.dto.CampaignRequest;
import com.waylo.pricingservice.dto.CampaignResponse;
import com.waylo.pricingservice.dto.CampaignUpdateRequest;
import com.waylo.pricingservice.dto.PromoCodeRequest;
import com.waylo.pricingservice.dto.PromoCodeResponse;
import com.waylo.pricingservice.dto.TaxRateRequest;
import com.waylo.pricingservice.dto.TaxRateResponse;
import com.waylo.pricingservice.exception.BusinessRuleException;
import com.waylo.pricingservice.exception.ResourceNotFoundException;
import com.waylo.pricingservice.repository.CampaignRepository;
import com.waylo.pricingservice.repository.PromoCodeRepository;
import com.waylo.pricingservice.repository.TaxRateRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin service for CRUD on campaigns, promo codes, and tax rates.
 */
public class AdminService {

    private final CampaignRepository campaignRepo;
    private final PromoCodeRepository promoCodeRepo;
    private final TaxRateRepository taxRateRepo;

    public AdminService(CampaignRepository campaignRepo,
                        PromoCodeRepository promoCodeRepo,
                        TaxRateRepository taxRateRepo) {
        this.campaignRepo = campaignRepo;
        this.promoCodeRepo = promoCodeRepo;
        this.taxRateRepo = taxRateRepo;
    }

    // --- Campaigns ---

    public CampaignResponse createCampaign(CampaignRequest req) {
        if ("PARTNER".equals(req.fundedBy())) {
            throw new BusinessRuleException(
                    "PARTNER_FUNDING_REQUIRES_CONSENT",
                    "Admin can only create PLATFORM-funded campaigns");
        }

        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName(req.name());
        campaign.setVertical(req.vertical());
        campaign.setScopeOrgId(req.scopeOrgId());
        campaign.setDiscountType(DiscountType.valueOf(req.discountType()));
        campaign.setDiscountValue(req.discountValue());
        campaign.setMinOrderAmount(req.minOrderAmount() != null ? req.minOrderAmount() : java.math.BigDecimal.ZERO);
        campaign.setStartAt(req.startAt());
        campaign.setEndAt(req.endAt());
        campaign.setMaxUsesGlobal(req.maxUsesGlobal());
        campaign.setMaxUsesPerUser(req.maxUsesPerUser());
        campaign.setFundedBy(FundedBy.valueOf(req.fundedBy()));
        campaign.setDescription(req.description());

        campaign = campaignRepo.save(campaign);
        return toCampaignResponse(campaign);
    }

    public CampaignResponse getCampaign(UUID id) {
        Campaign campaign = campaignRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", id));
        return toCampaignResponse(campaign);
    }

    public List<CampaignResponse> listCampaigns() {
        return campaignRepo.findAll().stream()
                .map(this::toCampaignResponse)
                .toList();
    }

    public CampaignResponse updateCampaign(UUID id, CampaignUpdateRequest req) {
        Campaign campaign = campaignRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", id));

        if (req.name() != null) {
            campaign.setName(req.name());
        }
        if (req.description() != null) {
            campaign.setDescription(req.description());
        }
        if (req.endAt() != null) {
            campaign.setEndAt(req.endAt());
            // Soft delete: if endAt is in the past, treat as soft-delete
            if (req.endAt().isBefore(LocalDateTime.now())) {
                campaign.setEndAt(LocalDateTime.now());
            }
        }

        campaign = campaignRepo.save(campaign);
        return toCampaignResponse(campaign);
    }

    public void deleteCampaign(UUID id) {
        Campaign campaign = campaignRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", id));
        // Soft delete: set endAt to now instead of hard delete
        campaign.setEndAt(LocalDateTime.now());
        campaignRepo.save(campaign);
    }

    // --- Promo Codes ---

    public List<PromoCodeResponse> createPromoCodes(PromoCodeRequest req) {
        // Verify campaign exists
        campaignRepo.findById(req.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", req.campaignId()));

        List<PromoCode> promoCodes = req.codes().stream().map(item -> {
            PromoCode pc = new PromoCode();
            pc.setId(UUID.randomUUID());
            pc.setCode(item.code().toUpperCase());
            pc.setMaxUsesTotal(item.maxUsesTotal());
            pc.setUsesCount(0);
            pc.setValidFrom(item.validFrom());
            pc.setValidUntil(item.validUntil());

            // Load campaign eagerly via repo to set the relationship
            Campaign campaign = campaignRepo.findById(req.campaignId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", req.campaignId()));
            pc.setCampaign(campaign);

            return pc;
        }).toList();

        promoCodes = promoCodeRepo.saveAll(promoCodes);

        return promoCodes.stream().map(pc -> new PromoCodeResponse(
                pc.getId(),
                pc.getCampaign().getId(),
                pc.getCode(),
                pc.getMaxUsesTotal(),
                pc.getUsesCount(),
                pc.getValidFrom(),
                pc.getValidUntil()
        )).toList();
    }

    public List<PromoCodeResponse> listPromoCodes(UUID campaignId) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", campaignId));

        return promoCodeRepo.findByCampaignId(campaignId).stream()
                .map(pc -> new PromoCodeResponse(
                        pc.getId(),
                        pc.getCampaign().getId(),
                        pc.getCode(),
                        pc.getMaxUsesTotal(),
                        pc.getUsesCount(),
                        pc.getValidFrom(),
                        pc.getValidUntil()))
                .toList();
    }

    public void deletePromoCode(UUID id) {
        if (!promoCodeRepo.existsById(id)) {
            throw new ResourceNotFoundException("PromoCode", "id", id);
        }
        promoCodeRepo.deleteById(id);
    }

    // --- Tax Rates ---

    public TaxRateResponse createTaxRate(TaxRateRequest req) {
        TaxRate taxRate = new TaxRate();
        taxRate.setId(UUID.randomUUID());
        taxRate.setVertical(req.vertical());
        taxRate.setScopeOrgId(req.scopeOrgId());
        taxRate.setTaxType(req.taxType());
        taxRate.setRate(req.rate());
        taxRate.setInclusive(req.inclusive());
        taxRate.setValidFrom(req.validFrom());

        taxRate = taxRateRepo.save(taxRate);
        return toTaxRateResponse(taxRate);
    }

    public List<TaxRateResponse> listTaxRates() {
        return taxRateRepo.findAll().stream()
                .map(this::toTaxRateResponse)
                .toList();
    }

    public TaxRateResponse getTaxRate(UUID id) {
        TaxRate taxRate = taxRateRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaxRate", "id", id));
        return toTaxRateResponse(taxRate);
    }

    public void deleteTaxRate(UUID id) {
        if (!taxRateRepo.existsById(id)) {
            throw new ResourceNotFoundException("TaxRate", "id", id);
        }
        taxRateRepo.deleteById(id);
    }

    // -- private helpers --

    private CampaignResponse toCampaignResponse(Campaign c) {
        return new CampaignResponse(
                c.getId(),
                c.getName(),
                c.getVertical(),
                c.getScopeOrgId(),
                c.getDiscountType() != null ? c.getDiscountType().name() : null,
                c.getDiscountValue(),
                c.getMinOrderAmount(),
                c.getStartAt(),
                c.getEndAt(),
                c.getMaxUsesGlobal(),
                c.getMaxUsesPerUser(),
                c.getFundedBy() != null ? c.getFundedBy().name() : null,
                c.getDescription(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private TaxRateResponse toTaxRateResponse(TaxRate t) {
        return new TaxRateResponse(
                t.getId(),
                t.getVertical(),
                t.getScopeOrgId(),
                t.getTaxType(),
                t.getRate(),
                t.getInclusive(),
                t.getValidFrom()
        );
    }
}
