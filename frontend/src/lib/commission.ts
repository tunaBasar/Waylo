import { parseTrAmount } from './amount';

export type RateVertical = 'HOTEL' | 'CAR' | 'FLIGHT' | 'BUS';
export interface RateRow { id: string; vertical: RateVertical; supplierOrganizationId: string | null; rate: number }

export const ALL_VERTICALS: RateVertical[] = ['HOTEL', 'CAR', 'FLIGHT', 'BUS'];
export const VERTICAL_TR: Record<RateVertical, string> = { HOTEL: 'Otel', CAR: 'Arac', FLIGHT: 'Ucus', BUS: 'Otobus' };

/** Organization business type -> commission vertical. */
export function verticalOf(businessType: string | null | undefined): RateVertical | null {
  if (businessType === 'HOTEL') return 'HOTEL';
  if (businessType === 'CAR_RENTAL') return 'CAR';
  if (businessType === 'BUS') return 'BUS';
  return null;
}

/** Percent text for the field: 0.1 -> "10", 0.125 -> "12,5". */
export function ratePercentText(rate: number): string {
  return String(Math.round(rate * 10000) / 100).replace('.', ',');
}

/** Prefill: the vertical default if it exists, else the org's own override, else empty. */
export function prefillPercent(rows: RateRow[], vertical: RateVertical | null, orgId: string): string {
  if (!vertical) return '';
  const def = rows.find(r => r.vertical === vertical && r.supplierOrganizationId == null);
  if (def) return ratePercentText(def.rate);
  const own = rows.find(r => r.vertical === vertical && r.supplierOrganizationId === orgId);
  return own ? ratePercentText(own.rate) : '';
}

/** The rate suggestion arrives after the approve form opened: it only fills an empty field, typed input wins. */
export function keepTypedPercent(current: string, suggestion: string): string {
  return current.trim() ? current : suggestion;
}

/** 0 < x <= 50, Turkish decimal comma allowed. Returns the fraction (x/100, 4 decimals) or null. */
export function parseCommissionPercent(raw: string): number | null {
  const n = parseTrAmount(raw);
  if (n == null || n > 50) return null;
  return Number((n / 100).toFixed(4));
}

/** True when the org already has an override with exactly this rate (the POST is skipped then). */
export function hasIdenticalOverride(rows: RateRow[], vertical: RateVertical, orgId: string, fraction: number): boolean {
  return rows.some(r => r.vertical === vertical && r.supplierOrganizationId === orgId && Math.abs(r.rate - fraction) < 0.00005);
}

/** Verticals without a default row (supplierOrganizationId null). */
export function verticalsWithoutDefault(rows: RateRow[]): RateVertical[] {
  return ALL_VERTICALS.filter(v => !rows.some(r => r.vertical === v && r.supplierOrganizationId == null));
}
