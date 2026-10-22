/**
 * Hotel stay search, validation, and API client.
 *
 * Pure functions are free of browser globals so they can be unit tested with Node.
 */
import { api } from '@/lib/api';
import type { Hotel } from '@/lib/types';
import type { MealPlan, CancellationPolicy } from '@/lib/partner';

export interface StayQuery {
  checkIn: string;
  checkOut: string;
  rooms: number;
  adults: number;
  childrenAges: number[];
} // dates are ISO yyyy-mm-dd

export interface NightlyRate {
  stayDate: string;
  amount: number;
  currency: string;
}

export interface AvailableOffer {
  roomTypeId: string;
  roomTypeCode: string;
  roomTypeName: string;
  maxAdults: number;
  maxChildren: number;
  maxOccupancy: number;
  ratePlanId: string;
  ratePlanCode: string;
  ratePlanName: string;
  mealPlan: MealPlan;
  cancellationPolicy: CancellationPolicy;
  freeCancellationDaysBefore: number | null;
  nights: number;
  rooms: number;
  occupancy: number;
  currency: string;
  totalAmount: number;
  nightly: NightlyRate[];
}

export interface Availability {
  hotelId: string;
  checkIn: string;
  checkOut: string;
  nights: number;
  rooms: number;
  adults: number;
  childrenAges: number[];
  occupancy: number;
  offers: AvailableOffer[];
}

export interface Quote {
  quoteId: string;
  hotelId: string;
  roomTypeId: string;
  roomTypeCode: string;
  roomTypeName: string;
  ratePlanId: string;
  ratePlanCode: string;
  ratePlanName: string;
  mealPlan: MealPlan;
  cancellationPolicy: CancellationPolicy;
  freeCancellationDaysBefore: number | null;
  checkIn: string;
  checkOut: string;
  nights: number;
  rooms: number;
  adults: number;
  childrenAges: number[];
  occupancy: number;
  currency: string;
  totalAmount: number;
  nightly: NightlyRate[];
  createdAt: string;
  expiresAt: string;
  expired: boolean;
}

export const MAX_NIGHTS = 30;

/** Local date as yyyy-mm-dd (not UTC). */
export function todayIso(now: Date = new Date()): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Add N days to an ISO yyyy-mm-dd date. Pure, UTC-safe calendar arithmetic. */
export function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() + days);
  const yy = date.getUTCFullYear();
  const mm = String(date.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(date.getUTCDate()).padStart(2, '0');
  return `${yy}-${mm}-${dd}`;
}

/** Number of nights between checkIn and checkOut (both yyyy-mm-dd). */
export function nightsBetween(checkIn: string, checkOut: string): number {
  const [y1, m1, d1] = checkIn.split('-').map(Number);
  const [y2, m2, d2] = checkOut.split('-').map(Number);
  const a = Date.UTC(y1, m1 - 1, d1);
  const b = Date.UTC(y2, m2 - 1, d2);
  return (b - a) / (24 * 60 * 60 * 1000);
}

/**
 * Validate a StayQuery. Returns a Turkish error message string, or null when valid.
 *
 * Rules:
 * - Both dates are valid ISO yyyy-mm-dd strings.
 * - checkIn >= today.
 * - 1..30 nights.
 * - rooms 1..10, adults 1..20, both integers.
 * - childrenAges length <= 10, each integer 0..17.
 */
export function validateStay(q: StayQuery, today: string = todayIso()): string | null {
  // Validate date format
  const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
  if (!dateRegex.test(q.checkIn)) return 'Giris tarihi gecerli bir tarih formatinda olmali.';
  if (!dateRegex.test(q.checkOut)) return 'Cikis tarihi gecerli bir tarih formatinda olmali.';

  // Validate checkIn >= today
  if (q.checkIn < today) return 'Giris tarihi bugunden once olamaz.';

  // Validate nights
  const nights = nightsBetween(q.checkIn, q.checkOut);
  if (nights <= 0) return 'Cikis tarihi giris tarihinden sonra olmalidir.';
  if (nights > MAX_NIGHTS) return `En fazla ${MAX_NIGHTS} gece secilebilir.`;

  // Validate rooms
  if (!Number.isInteger(q.rooms) || q.rooms < 1 || q.rooms > 10) return 'Oda sayisi 1 ile 10 arasinda tam sayi olmalidir.';

  // Validate adults
  if (!Number.isInteger(q.adults) || q.adults < 1 || q.adults > 20) return 'Yetiskin sayisi 1 ile 20 arasinda tam sayi olmalidir.';

  // Validate childrenAges
  if (q.childrenAges.length > 10) return 'Cocuk sayisi en fazla 10 olabilir.';
  for (const age of q.childrenAges) {
    if (!Number.isInteger(age) || age < 0 || age > 17) return 'Cocuk yasleri 0 ile 17 arasinda tam sayi olmalidir.';
  }

  return null;
}

/** Encode a StayQuery as URL search parameters (no leading '?'). */
export function stayQueryString(q: StayQuery): string {
  const params = new URLSearchParams();
  params.set('checkIn', q.checkIn);
  params.set('checkOut', q.checkOut);
  params.set('rooms', String(q.rooms));
  params.set('adults', String(q.adults));
  for (const age of q.childrenAges) {
    params.append('childrenAges', String(age));
  }
  return params.toString();
}

/**
 * Inverse of stayQueryString. Returns null when checkIn or checkOut is missing.
 * Defaults rooms=1, adults=1. Non-numeric child ages are ignored.
 */
export function parseStayQuery(params: URLSearchParams): StayQuery | null {
  const checkIn = params.get('checkIn');
  const checkOut = params.get('checkOut');
  if (!checkIn || !checkOut) return null;

  const rooms = parseInt(params.get('rooms') ?? '1', 10);
  const adults = parseInt(params.get('adults') ?? '1', 10);
  const childrenAges: number[] = [];
  for (const v of params.getAll('childrenAges')) {
    const n = parseInt(v, 10);
    if (!Number.isNaN(n)) {
      childrenAges.push(n);
    }
  }

  return { checkIn, checkOut, rooms: Number.isNaN(rooms) ? 1 : rooms, adults: Number.isNaN(adults) ? 1 : adults, childrenAges };
}

const enc = encodeURIComponent;

export const hotelStayApi = {
  search: (q: StayQuery, filters: { city?: string; minStars?: number } = {}) => {
    const qs = stayQueryString(q);
    const parts: string[] = [qs];
    if (filters.city !== undefined) parts.push(`city=${enc(filters.city)}`);
    if (filters.minStars !== undefined) parts.push(`minStars=${enc(String(filters.minStars))}`);
    return api.get<Hotel[]>(`/api/hotels/search?${parts.join('&')}`);
  },
  availability: (hotelId: string, q: StayQuery) =>
    api.get<Availability>(`/api/hotels/${enc(hotelId)}/availability?${stayQueryString(q)}`),
  createQuote: (body: { hotelId: string; roomTypeId: string; ratePlanId: string } & StayQuery) =>
    api.post<Quote>('/api/quotes', body),
  getQuote: (id: string) =>
    api.get<Quote>(`/api/quotes/${enc(id)}`),
};
