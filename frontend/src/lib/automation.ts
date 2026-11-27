/**
 * Pure helpers for the hotel automation page ("Otomatik takvim"). No imports on purpose: unit-tested with a bare
 * TypeScript transpile (test/automation.test.mjs).
 */

export interface DeriveInput {
  maxOccupancy: number;
  baseOccupancy: number;
  baseAmount: number;
  /** Used instead of baseAmount when `weekend` is true. */
  weekendBaseAmount?: number | null;
  extraPersonAmount: number;
  reducedPersonAmount: number;
}

export interface DerivedPrices {
  /** Occupancies that get a price, in order 1..max. */
  prices: { occupancy: number; amount: number }[];
  /** Occupancies whose amount is zero or negative: the backend does not generate them. */
  unpriced: number[];
}

/**
 * Same formula as the backend RateRuleMath (contract 2.2): above the base occupancy `base + (o-b)*extra`, below it
 * `base - (b-o)*reduced`. Works in whole cents (inputs have at most 2 decimals) so rounding is exact, HALF_UP.
 */
export function deriveOccupancyPrices(input: DeriveInput, weekend = false): DerivedPrices {
  const cents = (n: number) => Math.round(n * 100);
  const base = cents(weekend && input.weekendBaseAmount != null ? input.weekendBaseAmount : input.baseAmount);
  const extra = cents(input.extraPersonAmount);
  const reduced = cents(input.reducedPersonAmount);
  const prices: DerivedPrices['prices'] = [];
  const unpriced: number[] = [];
  for (let o = 1; o <= input.maxOccupancy; o++) {
    let c = base;
    if (o > input.baseOccupancy) c = base + (o - input.baseOccupancy) * extra;
    else if (o < input.baseOccupancy) c = base - (input.baseOccupancy - o) * reduced;
    if (c <= 0) unpriced.push(o);
    else prices.push({ occupancy: o, amount: c / 100 });
  }
  return { prices, unpriced };
}

/** Turkish text per backend error code of the automation endpoints. */
export const AUTOMATION_ERROR_TEXT: Record<string, string> = {
  RULE_OVERLAP: 'Bu oda tipi ve fiyat plani icin tarih araligi baska bir kuralla cakisiyor. Her zaman gecerli kural en fazla bir tane olabilir.',
  INVALID_DATE_RANGE: 'Tarih araligi gecersiz. Bitis tarihi baslangictan once olamaz, baslangic bugunden once olamaz ve aralik en fazla 730 gun olabilir.',
  OCCUPANCY_RULE: 'Temel kisi sayisi odanin azami kisi sayisindan buyuk olamaz.',
  CURRENCY_MISMATCH: 'Para birimi otelin mevcut para birimiyle ayni olmali.',
  ROOM_TYPE_INACTIVE: 'Bu oda tipi aktif degil.',
  RATE_PLAN_INACTIVE: 'Bu fiyat plani aktif degil.',
  CONCURRENT_MODIFICATION: 'Bu kayit siz duzenlerken baska biri tarafindan degistirildi. Guncel bilgiler yuklendi, tekrar deneyin.',
};

/** Maps an ApiError-like value (`code`, `status`, `message`) to Turkish; unknown errors keep their message. */
export function automationErrorMessage(err: unknown, fallback: string): string {
  const e = err as { code?: string | null; status?: number; message?: string } | null | undefined;
  if (e && e.code && AUTOMATION_ERROR_TEXT[e.code]) return AUTOMATION_ERROR_TEXT[e.code];
  if (e && e.status === 403) return 'Bu islem icin yetkiniz yok.';
  return (e && e.message) || fallback;
}

export interface AutomationSummaryLike {
  created: number;
  updated: number;
  skippedManual: number;
  inventoryCreated?: number;
  ratesCreated?: number;
}

const trInt = (n: number) => String(Math.trunc(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');

/** Toast text after "Simdi uygula", e.g. "120 gece stok, 1.440 fiyat olusturuldu; 12 elle degistirilmis gun korundu". */
export function summaryMessage(s: AutomationSummaryLike): string {
  const inv = s.inventoryCreated ?? 0;
  const rates = s.ratesCreated ?? 0;
  const parts: string[] = [];
  if (inv > 0 || rates > 0) parts.push(`${trInt(inv)} gece stok, ${trInt(rates)} fiyat olusturuldu`);
  if (s.updated > 0) parts.push(`${trInt(s.updated)} kayit guncellendi`);
  if (parts.length === 0) parts.push('Yeni olusturulacak gun yok, her sey guncel');
  if (s.skippedManual > 0) parts.push(`${trInt(s.skippedManual)} elle degistirilmis gun korundu`);
  return parts.join('; ');
}

/**
 * Errors to show in the rate rule form: a field's error appears only after the user edited that field or pressed
 * save (an empty new form must not open with red "gerekli" texts). The save button still uses all errors.
 */
export function visibleFormErrors(
  errors: Record<string, string>, touched: ReadonlySet<string>, submitted: boolean,
): Record<string, string> {
  if (submitted) return errors;
  const out: Record<string, string> = {};
  for (const [field, message] of Object.entries(errors)) {
    if (touched.has(field)) out[field] = message;
  }
  return out;
}
