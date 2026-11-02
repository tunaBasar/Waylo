/**
 * Flight offers, orders and supplier balance (FLIGHT-SUPPLIER-CONTRACT.md).
 * Pure helpers live at the bottom so tests can import them without the api dependency.
 */
import { api, ADMIN_CANNOT_PURCHASE_MESSAGE } from './api';

// ── Types ───────────────────────────────────────────────────────────────────

export interface FlightOffer {
  offerId: string;
  supplier: string;
  flightNumber: string;
  airline: string;
  departureAirport: string;
  arrivalAirport: string;
  departureTime: string;
  arrivalTime: string;
  cabin: string;
  currency: string;
  totalAmount: number;
  availableSeats: number;
  expiresAt: string;
}

export interface FlightOrderRequest {
  offerId: string;
  seatNumber?: string;
  passengerFirstName: string;
  passengerLastName: string;
}

export interface FlightOrderResponse {
  id: string;
  status: string;
  totalAmount: number;
  currency: string;
  holdExpiresAt: string | null;
}

export interface AvailableSeat {
  id: string;
  flightNumber: string;
  seatNumber: string;
  seatType: string;
  price: number;
  available: boolean;
}

export interface SupplierAccount {
  supplier: string;
  currency: string;
  balance: number;
  updatedAt: string;
}

export interface SupplierLedgerRow {
  id: string;
  entryType: 'TOPUP' | 'TICKET_PAYOUT' | 'REFUND_CREDIT' | 'ADJUSTMENT';
  amount: number;
  balanceAfter: number;
  currency: string;
  orderId: string | null;
  orderItemId: string | null;
  reference: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface SupplierLedgerPage {
  items: SupplierLedgerRow[];
  total: number;
  page: number;
  size: number;
}

// ── API functions ───────────────────────────────────────────────────────────

const enc = encodeURIComponent;

export function searchFlightOffers(params: { from: string; to: string; date: string }): Promise<{ data: FlightOffer[] }> {
  const qs = new URLSearchParams({ from: params.from, to: params.to, date: params.date, adults: '1' }).toString();
  return api.get<FlightOffer[]>(`/api/flight-offers?${qs}`);
}

export function getFlightOffer(offerId: string): Promise<{ data: FlightOffer }> {
  return api.get<FlightOffer>(`/api/flight-offers/${enc(offerId)}`);
}

export function getAvailableSeats(flightNumber: string): Promise<{ data: AvailableSeat[] }> {
  return api.get<AvailableSeat[]>(`/api/seat-inventory/seats/${enc(flightNumber)}/available`);
}

export function postFlightOrder(body: FlightOrderRequest): Promise<{ data: FlightOrderResponse }> {
  return api.post<FlightOrderResponse>('/api/orders/flight', body);
}

export function getSupplierAccounts(): Promise<{ data: SupplierAccount[] }> {
  return api.get<SupplierAccount[]>('/api/admin/supplier-accounts');
}

export function topUpSupplier(supplier: string, body: { amount: number; currency: string; reference: string; idempotencyKey: string }): Promise<{ data: SupplierAccount }> {
  return api.post<SupplierAccount>(`/api/admin/supplier-accounts/${enc(supplier)}/topups`, body);
}

export function getSupplierLedger(supplier: string, page = 0, size = 20): Promise<{ data: SupplierLedgerPage }> {
  return api.get<SupplierLedgerPage>(`/api/admin/supplier-accounts/${enc(supplier)}/ledger?page=${page}&size=${size}`);
}

// ── Pure helpers ────────────────────────────────────────────────────────────

export const CABIN_LABEL: Record<string, string> = {
  ECONOMY: 'Ekonomi',
  PREMIUM_ECONOMY: 'Premium Ekonomi',
  BUSINESS: 'Business',
  FIRST: 'First',
};

export const LEDGER_TYPE_LABEL: Record<string, string> = {
  TOPUP: 'Bakiye yukleme',
  TICKET_PAYOUT: 'Bilet odemesi',
  REFUND_CREDIT: 'Tedarikci iadesi',
  ADJUSTMENT: 'Duzeltme',
};

const ERROR_MESSAGES: Record<string, string> = {
  OFFER_EXPIRED: 'Teklifin suresi doldu, lutfen yeniden arama yapin.',
  INVALID_OFFER: 'Teklif gecersiz, lutfen yeniden arama yapin.',
  FARE_CHANGED: 'Ucret degisti, lutfen yeniden arama yapin.',
  NO_SEAT_AVAILABLE: 'Bu kabinde bos koltuk kalmadi.',
  SEAT_ALREADY_RESERVED: 'Secilen koltuk baska bir yolcu tarafindan alindi, baska koltuk secin.',
  SEAT_CABIN_MISMATCH: 'Secilen koltuk bu teklifin kabininde degil.',
  FLIGHT_NOT_BOOKABLE: 'Bu ucus satisa kapali.',
  FLIGHT_SELLER_NOT_CONFIGURED: 'Ucus satisi su an acik degil.',
  ADMIN_CANNOT_PURCHASE: ADMIN_CANNOT_PURCHASE_MESSAGE,
  SUPPLIER_BALANCE_INSUFFICIENT: 'Tedarikci bakiyesi yetersiz.',
  SUPPLIER_CURRENCY_MISMATCH: 'Para birimi tedarikci hesabiyla uyusmuyor.',
  VALIDATION_ERROR: 'Girdiginiz bilgileri kontrol edin.',
};

/** Customer-facing message for a flight API error code, falling back to `fallback`. */
export function flightErrorMessage(code: string | null | undefined, fallback: string): string {
  return (code && ERROR_MESSAGES[code]) || fallback;
}

/** Offer codes after which the customer must search again. */
export function mustSearchAgain(code: string | null | undefined): boolean {
  return code === 'OFFER_EXPIRED' || code === 'INVALID_OFFER' || code === 'FARE_CHANGED';
}

/** Seconds left until an ISO instant, never negative. */
export function secondsLeft(expiresAt: string, now: number): number {
  const ms = new Date(expiresAt).getTime() - now;
  return Number.isFinite(ms) ? Math.max(0, Math.floor(ms / 1000)) : 0;
}

/** m:ss for a countdown. */
export function formatCountdown(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** Names as on the ID: letters (any script), space, apostrophe, hyphen. Mirrors the backend rule. */
export function isValidPassengerName(name: string): boolean {
  const v = name.trim();
  return v.length > 0 && v.length <= 80 && /^[\p{L} '-]+$/u.test(v);
}

/** Idempotency key for an admin top-up form submission. */
export function newIdempotencyKey(): string {
  return `topup-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/** Cabins present in the seat list that match the offered cabin, sorted by seat number (row then letter). */
export function seatsForCabin(seats: AvailableSeat[], cabin: string): AvailableSeat[] {
  return seats
    .filter(s => s.available && s.seatType === cabin)
    .sort((a, b) => {
      const ra = parseInt(a.seatNumber, 10), rb = parseInt(b.seatNumber, 10);
      return ra - rb || a.seatNumber.localeCompare(b.seatNumber);
    });
}
