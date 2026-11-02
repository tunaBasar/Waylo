/**
 * Pricing-service client (PRICING-CONTRACT.md): promo preview for checkouts and admin campaign management.
 * Preview is advisory, the binding price is computed by package-booking at order creation.
 */
import { api } from './api';

export type PricingVertical = 'OTEL' | 'ARAÇ' | 'UÇUŞ' | 'OTOBÜS';
export type FundedBy = 'PLATFORM' | 'PARTNER';
export type DiscountType = 'PERCENTAGE' | 'FIXED';

export const VERTICAL_LABEL: Record<PricingVertical, string> = {
  OTEL: 'Otel', 'ARAÇ': 'Arac', 'UÇUŞ': 'Ucus', 'OTOBÜS': 'Otobus',
};
export const FUNDED_BY_LABEL: Record<FundedBy, string> = { PLATFORM: 'Platform', PARTNER: 'Isletme' };

export interface PreviewRequest {
  vertical: PricingVertical;
  supplierOrganizationId?: string;
  grossAmount: number;
  currency: string;
  promoCode: string;
}
export interface DiscountLine { description: string; amount: number; fundedBy: FundedBy }
export interface PreviewResponse { netAmount: number; discountLines: DiscountLine[]; taxAmount: number | null }

export interface Campaign {
  id: string; name: string; vertical: PricingVertical; scopeOrgId: string | null;
  discountType: DiscountType; discountValue: number; minOrderAmount: number | null;
  startAt: string; endAt: string; maxUsesGlobal: number | null; maxUsesPerUser: number | null;
  fundedBy: FundedBy; description: string | null;
}
export interface CampaignRequest {
  name: string; vertical: PricingVertical; scopeOrgId?: string; discountType: DiscountType; discountValue: number;
  minOrderAmount?: number; startAt: string; endAt: string; maxUsesGlobal?: number; maxUsesPerUser?: number;
  fundedBy: FundedBy; description?: string;
}
export interface PromoCode {
  id: string; campaignId: string; code: string; maxUsesTotal: number | null; usesCount: number;
  validFrom: string; validUntil: string;
}
export interface TaxRate {
  id: string; vertical: string; scopeOrgId: string | null; taxType: string; rate: number; inclusive: boolean; validFrom: string;
}

/** Sum of discount line amounts (the order stores the same total as discountAmount). */
export function totalDiscount(lines: DiscountLine[] | null | undefined): number {
  return (lines ?? []).reduce((sum, l) => sum + l.amount, 0);
}

/** A campaign is active when now is in [startAt, endAt). Dates are offsetless Istanbul local times. */
export function campaignActive(c: Pick<Campaign, 'startAt' | 'endAt'>, now: Date = new Date()): boolean {
  const t = now.getTime();
  return new Date(c.startAt).getTime() <= t && t < new Date(c.endAt).getTime();
}

/** "10%" or "50.00 TRY". */
export function discountLabel(c: Pick<Campaign, 'discountType' | 'discountValue'>, currency = 'TRY'): string {
  return c.discountType === 'PERCENTAGE' ? `%${c.discountValue}` : `${c.discountValue.toFixed(2)} ${currency}`;
}

/** Promo codes are case-insensitive: uppercase and trim before sending. */
export function normalizePromoCode(code: string): string {
  return code.trim().toUpperCase();
}

/** Order create bodies carry promoCode only when the customer typed one. */
export function withPromo<T extends object>(body: T, code: string): T & { promoCode?: string } {
  const c = normalizePromoCode(code);
  return c ? { ...body, promoCode: c } : body;
}

export const pricingApi = {
  preview: (body: PreviewRequest) => api.post<PreviewResponse>('/api/pricing/preview', body),
  campaigns: () => api.get<Campaign[]>('/api/admin/campaigns'),
  createCampaign: (body: CampaignRequest) => api.post<Campaign>('/api/admin/campaigns', body),
  endCampaign: (id: string) => api.del(`/api/admin/campaigns/${encodeURIComponent(id)}`),
  promoCodes: (campaignId: string) => api.get<PromoCode[]>(`/api/admin/campaigns/${encodeURIComponent(campaignId)}/promo-codes`),
  createPromoCode: (campaignId: string, code: string, validFrom: string, validUntil: string, maxUsesTotal?: number) =>
    api.post<PromoCode[]>('/api/admin/promo-codes', { campaignId, codes: [{ code, validFrom, validUntil, maxUsesTotal }] }),
  taxRates: () => api.get<TaxRate[]>('/api/admin/tax-rates'),
};

/** Turkish customer message for a promo related order error code, or null when the code is not promo related. */
export function promoErrorMessage(code: string | null | undefined): string | null {
  switch (code) {
    case 'PROMO_CODE_INVALID': return 'Promosyon kodu gecersiz veya bu urun icin kullanilamaz.';
    case 'PROMO_CODE_EXPIRED': return 'Promosyon kodunun suresi dolmus.';
    case 'PROMO_CODE_MAX_USES_REACHED': return 'Promosyon kodunun kullanim hakki dolmus.';
    case 'MIN_ORDER_NOT_MET': return 'Siparis tutari bu kod icin gereken minimum tutarin altinda.';
    case 'DISCOUNT_EXCEEDS_PARTNER_SHARE': return 'Bu kod bu siparis icin uygulanamiyor.';
    case 'PRICING_UNAVAILABLE': return 'Promosyon kodu su an dogrulanamiyor, tekrar deneyin veya kodu kaldirin.';
    default: return null;
  }
}
