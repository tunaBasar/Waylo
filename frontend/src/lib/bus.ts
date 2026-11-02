/**
 * Bus vertical: types, API client (partner, admin, customer) and pure helpers.
 * Field names mirror bus-service DTOs (see BUS-CONTRACT.md). Partner paths use /api/bus-partner.
 */
import { api, ApiError } from './api';

export type SeatLayout = 'TWO_PLUS_TWO' | 'TWO_PLUS_ONE';
export type Gender = 'FEMALE' | 'MALE';
export type BookingStatus = 'HELD' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';
export type BusDocStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type BusDocType =
  | 'TRANSPORT_AUTHORIZATION' | 'TAX_CERTIFICATE'
  | 'VEHICLE_REGISTRATION' | 'VEHICLE_CARD' | 'TRAFFIC_INSURANCE' | 'SEAT_ACCIDENT_INSURANCE';

export const ORG_BUS_DOC_TYPES: BusDocType[] = ['TRANSPORT_AUTHORIZATION', 'TAX_CERTIFICATE'];
export const BUS_BUS_DOC_TYPES: BusDocType[] = ['VEHICLE_REGISTRATION', 'VEHICLE_CARD', 'TRAFFIC_INSURANCE', 'SEAT_ACCIDENT_INSURANCE'];

export const BUS_DOC_LABEL: Record<BusDocType, string> = {
  TRANSPORT_AUTHORIZATION: 'Yetki belgesi (D1)',
  TAX_CERTIFICATE: 'Vergi levhasi',
  VEHICLE_REGISTRATION: 'Arac ruhsati',
  VEHICLE_CARD: 'Tasit karti',
  TRAFFIC_INSURANCE: 'Zorunlu trafik sigortasi',
  SEAT_ACCIDENT_INSURANCE: 'Zorunlu koltuk ferdi kaza sigortasi',
};

export const LAYOUT_LABEL: Record<SeatLayout, string> = { TWO_PLUS_TWO: '2+2', TWO_PLUS_ONE: '2+1' };
export const GENDER_LABEL: Record<Gender, string> = { FEMALE: 'Kadin', MALE: 'Erkek' };
export const BOOKING_STATUS_LABEL: Record<BookingStatus, string> = {
  HELD: 'Rezerve', CONFIRMED: 'Onayli', CANCELLED: 'Iptal', EXPIRED: 'Suresi doldu',
};

export interface Terminal { id: string; city: string; name: string; active: boolean }
export interface BusUnit {
  id: string; organizationId: string; plateNumber: string; brand: string; model: string; year: number;
  layout: SeatLayout; rowCount: number; seatCount: number; active: boolean; sellable: boolean;
  missingDocuments: BusDocType[];
}
export interface BusRequest {
  plateNumber: string; brand: string; model: string; year: number; layout: SeatLayout; rowCount: number;
}
export interface TripStopInput { terminalId: string; arrivalAt: string; departureAt: string }
export interface TripFareInput { fromSeq: number; toSeq: number; price: number }
export interface TripRequest { busId: string; code: string; currency: string; stops: TripStopInput[]; fares: TripFareInput[] }
export interface TripStop { seq: number; terminalId: string; city: string | null; terminalName: string | null; arrivalAt: string | null; departureAt: string | null }
export interface Trip {
  id: string; organizationId: string; busId: string; code: string; status: 'PUBLISHED' | 'CANCELLED';
  currency: string; stops: TripStop[]; fares: TripFareInput[]; soldSeats: number; seatCount: number;
}
export interface BusDocument {
  id: string; organizationId: string; busId: string | null; type: BusDocType; documentNumber: string;
  validUntil: string | null; status: BusDocStatus; rejectReason: string | null; fileName: string;
}
export interface AdminBusDocument { document: BusDocument; organizationName: string | null; busLabel: string | null }
export interface BusPartnerStatus { sellable: boolean; missingOrgDocuments: BusDocType[]; salesEnabled: boolean }
export interface Ticket { id: string; seatNo: number; passengerName: string; gender: Gender; price: number; status: BookingStatus }
export interface BusBooking {
  id: string; organizationId: string; tripId: string; fromSeq: number; toSeq: number; currency: string;
  totalAmount: number; status: BookingStatus; boardingAt: string | null; tickets: Ticket[]; createdAt: string | null;
}
export interface TripOffer {
  tripId: string; organizationId: string; companyName: string | null; code: string; fromSeq: number; toSeq: number;
  fromCity: string; toCity: string; departureAt: string; arrivalAt: string; price: number; currency: string;
  availableSeats: number; busBrand: string; busModel: string; layout: SeatLayout;
}
export interface SeatState { seatNo: number; available: boolean; occupantGender: Gender | null }
export interface SeatMap { tripId: string; layout: SeatLayout; rowCount: number; fromSeq: number; toSeq: number; seats: SeatState[] }
export interface PassengerInput { seatNo: number; name: string; gender: Gender; identityNumber?: string }
export interface BusOrderRequest { tripId: string; fromSeq: number; toSeq: number; passengers: PassengerInput[]; promoCode?: string }

const enc = encodeURIComponent;
const P = (orgId: string) => `/api/bus-partner/organizations/${enc(orgId)}`;

export const partnerBusApi = {
  status: (orgId: string) => api.get<BusPartnerStatus>(`${P(orgId)}/status`),
  terminals: (orgId: string) => api.get<Terminal[]>(`${P(orgId)}/terminals`),
  buses: (orgId: string) => api.get<BusUnit[]>(`${P(orgId)}/buses`),
  createBus: (orgId: string, body: BusRequest) => api.post<BusUnit>(`${P(orgId)}/buses`, body),
  setBusActive: (orgId: string, id: string, active: boolean) =>
    api.post<BusUnit>(`${P(orgId)}/buses/${enc(id)}/${active ? 'activate' : 'deactivate'}`, {}),
  trips: (orgId: string) => api.get<Trip[]>(`${P(orgId)}/trips`),
  createTrip: (orgId: string, body: TripRequest) => api.post<Trip>(`${P(orgId)}/trips`, body),
  cancelTrip: (orgId: string, id: string) => api.post<Trip>(`${P(orgId)}/trips/${enc(id)}/cancel`, {}),
  documents: (orgId: string) => api.get<BusDocument[]>(`${P(orgId)}/documents`),
  uploadDocument: (orgId: string, body: { file: File; type: BusDocType; documentNumber: string; validUntil: string | null; busId: string | null }) => {
    const fd = new FormData();
    fd.append('file', body.file);
    fd.append('type', body.type);
    fd.append('documentNumber', body.documentNumber);
    if (body.validUntil) fd.append('validUntil', body.validUntil);
    if (body.busId) fd.append('busId', body.busId);
    return api.post<BusDocument>(`${P(orgId)}/documents`, fd as unknown as BusDocument);
  },
  downloadDocument: (orgId: string, id: string) => api.get<Blob>(`${P(orgId)}/documents/${enc(id)}/file`),
  bookings: (orgId: string) => api.get<BusBooking[]>(`${P(orgId)}/bookings`),
};

export const adminBusApi = {
  documents: () => api.get<AdminBusDocument[]>('/api/admin/bus-documents'),
  approve: (id: string) => api.post<BusDocument>(`/api/admin/bus-documents/${enc(id)}/approve`, {}),
  reject: (id: string, reason: string) => api.post<BusDocument>(`/api/admin/bus-documents/${enc(id)}/reject`, { reason }),
  terminals: () => api.get<Terminal[]>('/api/admin/bus-terminals'),
  createTerminal: (city: string, name: string) => api.post<Terminal>('/api/admin/bus-terminals', { city, name }),
};

export const customerBusApi = {
  salesStatus: () => api.get<{ enabled: boolean }>('/api/bus-sales-status'),
  terminals: () => api.get<Terminal[]>('/api/bus-terminals'),
  search: (fromCity: string, toCity: string, date: string) =>
    api.get<TripOffer[]>(`/api/bus-trips?fromCity=${enc(fromCity)}&toCity=${enc(toCity)}&date=${enc(date)}`),
  seats: (tripId: string, fromSeq: number, toSeq: number) =>
    api.get<SeatMap>(`/api/bus-trips/${enc(tripId)}/seats?fromSeq=${fromSeq}&toSeq=${toSeq}`),
  order: (body: BusOrderRequest) => api.post<{ id: string }>('/api/orders/bus', body),
  mine: () => api.get<BusBooking[]>('/api/bus-bookings/me'),
};

// ── pure helpers (no DOM, unit tested) ───────────────────────────────────────

/** Seats per row for a layout. */
export function seatsPerRow(layout: SeatLayout): number {
  return layout === 'TWO_PLUS_TWO' ? 4 : 3;
}

/** Splits seats into rows. Each row is [left block, right block]; a 2+1 right block has one seat. */
export function seatRows(layout: SeatLayout, seats: SeatState[]): SeatState[][][] {
  const per = seatsPerRow(layout);
  const rows: SeatState[][][] = [];
  for (let i = 0; i < seats.length; i += per) {
    const row = seats.slice(i, i + per);
    rows.push([row.slice(0, 2), row.slice(2)]);
  }
  return rows;
}

/** The seat sharing a 2-seat block, or null (single seat of a 2+1 row). */
export function neighborSeat(layout: SeatLayout, seatNo: number): number | null {
  const per = seatsPerRow(layout);
  const idx = (seatNo - 1) % per;
  if (layout === 'TWO_PLUS_ONE' && idx === 2) return null;
  return idx % 2 === 0 ? seatNo + 1 : seatNo - 1;
}

/**
 * Client side mirror of the backend gender rule, for early feedback only (the server decides).
 * A seat is blocked for `gender` when its neighbour is taken by the other gender and is not
 * one of the seats the user already picked in this booking.
 */
export function seatBlockedByGender(
  layout: SeatLayout, seats: SeatState[], seatNo: number, gender: Gender, picked: number[],
): boolean {
  const n = neighborSeat(layout, seatNo);
  if (n === null || picked.includes(n)) return false;
  const other = seats.find(s => s.seatNo === n);
  return !!other && !other.available && other.occupantGender !== null && other.occupantGender !== gender;
}

export function formatTripTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  return m ? `${m[3]}.${m[2]}.${m[1]} ${m[4]}:${m[5]}` : iso;
}

export function formatClock(iso: string | null | undefined): string {
  const m = (iso ?? '').match(/T(\d{2}):(\d{2})/);
  return m ? `${m[1]}:${m[2]}` : '';
}

/** Turkish lira style amount, no Intl dependency so tests are locale independent. */
export function formatMoney(amount: number, currency = 'TRY'): string {
  return `${amount.toFixed(2)} ${currency}`;
}

/** Fare table sanity check mirroring backend TripRules.validateFares; returns an error text or null. */
export function validateFares(stopCount: number, fares: TripFareInput[]): string | null {
  const seen = new Set<string>();
  let full = false;
  for (const f of fares) {
    if (!(f.price > 0)) return 'Fiyat 0dan buyuk olmali.';
    if (f.fromSeq >= f.toSeq || f.toSeq > stopCount - 1) return `Gecersiz fiyat araligi ${f.fromSeq}->${f.toSeq}.`;
    const k = `${f.fromSeq}>${f.toSeq}`;
    if (seen.has(k)) return `Tekrar eden fiyat ${k}.`;
    seen.add(k);
    if (f.fromSeq === 0 && f.toSeq === stopCount - 1) full = true;
  }
  return full ? null : 'Tum guzergah icin fiyat gerekli.';
}

const ERROR_TEXT: Record<string, string> = {
  BUS_SALES_DISABLED: 'Otobus bileti satisi henuz acik degil.',
  SEAT_TAKEN: 'Secilen koltuk az once satildi. Baska koltuk secin.',
  SEAT_GENDER_CONFLICT: 'Yan koltukta farkli cinsiyetten yolcu var. Baska koltuk secin.',
  SALES_CLOSED: 'Bu sefer icin bilet satisi kapandi.',
  PARTNER_NOT_SELLABLE: 'Bu firmanin seferi su an satisa kapali.',
  INVALID_SEAT: 'Gecersiz koltuk.',
  SEGMENT_NOT_OFFERED: 'Bu guzergah icin fiyat yok.',
  ADMIN_CANNOT_PURCHASE: 'Platform yoneticisi hesabi ile satin alma yapilamaz.',
  COMMISSION_RATE_MISSING: 'Bu satis icin komisyon orani tanimli degil.',
};

export function busErrorMessage(err: unknown, fallback: string): string {
  const code = err instanceof ApiError ? err.code : null;
  return (code && ERROR_TEXT[code]) || (err instanceof Error && err.message) || fallback;
}
