package com.waylo.pricingservice.config;

import com.waylo.pricingservice.filter.AdminRoleFilter;
import com.waylo.pricingservice.filter.InternalTokenFilter;
import com.waylo.pricingservice.repository.CampaignRepository;
import com.waylo.pricingservice.repository.PromoCodeRepository;
import com.waylo.pricingservice.repository.PromoReservationRepository;
import com.waylo.pricingservice.repository.TaxRateRepository;
import com.waylo.pricingservice.service.AdminService;
import com.waylo.pricingservice.service.PreviewService;
import com.waylo.pricingservice.service.QuoteLogic;
import com.waylo.pricingservice.service.QuoteService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class PricingServiceConfig {

    @Bean
    public QuoteLogic quoteLogic() {
        return new QuoteLogic(Optional.empty());
    }

    @Bean
    public QuoteService quoteService(QuoteLogic quoteLogic,
                                     CampaignRepository campaignRepo,
                                     PromoCodeRepository promoCodeRepo,
                                     PromoReservationRepository reservationRepo) {
        return new QuoteService(quoteLogic, campaignRepo, promoCodeRepo, reservationRepo);
    }

    @Bean
    public PreviewService previewService(QuoteLogic quoteLogic,
                                         CampaignRepository campaignRepo) {
        return new PreviewService(quoteLogic, campaignRepo);
    }

    @Bean
    public AdminService adminService(CampaignRepository campaignRepo,
                                     PromoCodeRepository promoCodeRepo,
                                     TaxRateRepository taxRateRepo) {
        return new AdminService(campaignRepo, promoCodeRepo, taxRateRepo);
    }

    // InternalTokenFilter and AdminRoleFilter are @Component beans, auto-discovered.
}
