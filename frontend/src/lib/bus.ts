/**
 * Bus vertical: types, API client (partner, admin, customer) and pure helpers.
 * Field names mirror bus-service DTOs (see BUS-CONTRACT.md). Partner paths use /api/bus-partner.
 */
import { api, ApiError, SALES_CLOSED_MESSAGE } from './api';

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

/** Banner text for missing company documents (F70): Turkish labels, never the raw enum. */
export function missingBusDocumentsText(types: string[]): string {
  const labels = types.map(t => BUS_DOC_LABEL[t as BusDocType] ?? t);
  return `Eksik firma belgeleri: ${labels.join(', ')}. Belgeler sayfasindan yukleyin.`;
}

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
  scheduleId: string | null;
}
export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export interface ScheduleStopInput { terminalId: string; arrivalOffsetMinutes: number; departureOffsetMinutes: number }
export interface ScheduleRequest {
  busId: string; code: string; currency: string; weekdays: Weekday[]; departureTime: string; validFrom: string;
  validTo: string | null; horizonDays: number; stops: ScheduleStopInput[]; fares: TripFareInput[];
  expectedVersion?: number | null;
}
export interface TripSchedule {
  id: string; organizationId: string; busId: string; code: string; currency: string; weekdays: Weekday[];
  departureTime: string; validFrom: string; validTo: string | null; horizonDays: number; active: boolean;
  version: number; stops: (ScheduleStopInput & { seq: number; city: string | null; terminalName: string | null })[];
  fares: TripFareInput[]; nextDepartures: string[]; generatedCount: number;
}
export interface ScheduleSummary { created: number; updated: number; skippedExisting: number; skippedManual: number; skippedPast: number }
export interface ScheduleResult { schedule: TripSchedule; summary: ScheduleSummary }
export interface BusDocument {
  id: string; organizationId: string; busId: string | null; type: BusDocType; documentNumber: string;
  validUntil: string | null; status: BusDocStatus; rejectReason: string | null; fileName: string;
}
export interface AdminBusDocument { document: BusDocument; organizationName: string | null; busLabel: string | null }
export interface BusPartnerStatus { sellable: boolean; missingOrgDocuments: BusDocType[]; salesEnabled: boolean }

/**
 * Trip badge (D-BUS-TRIP, 2026-11-26): a PUBLISHED trip is only on sale when the company is sellable and its bus
 * is sellable; otherwise customers do not see it, so the partner must not read "Yayinda" as "selling".
 */
export function tripBadge(tripStatus: string, companySellable: boolean | null, busSellable: boolean | null):
  { label: string; tone: 'success' | 'warning' | 'neutral'; hint: string | null } {
  if (tripStatus !== 'PUBLISHED') return { label: 'Iptal', tone: 'neutral', hint: null };
  if (companySellable === false) {
    return { label: 'Satista degil', tone: 'warning', hint: 'Firma onayi ve firma belgeleri tamamlanmadan sefer musterilere gosterilmez.' };
  }
  if (busSellable === false) {
    return { label: 'Satista degil', tone: 'warning', hint: 'Otobusun belgeleri eksik veya onay bekliyor.' };
  }
  return { label: 'Satista', tone: 'success', hint: null };
}
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
  schedules: (orgId: string) => api.get<TripSchedule[]>(`${P(orgId)}/trip-schedules`),
  createSchedule: (orgId: string, body: ScheduleRequest) => api.post<ScheduleResult>(`${P(orgId)}/trip-schedules`, body),
  updateSchedule: (orgId: string, id: string, body: ScheduleRequest) =>
    api.put<ScheduleResult>(`${P(orgId)}/trip-schedules/${enc(id)}`, body),
  setScheduleActive: (orgId: string, id: string, active: boolean) =>
    api.post<ScheduleResult | { schedule: TripSchedule }>(`${P(orgId)}/trip-schedules/${enc(id)}/${active ? 'activate' : 'deactivate'}`, {}),
  applySchedules: (orgId: string) => api.post<ScheduleSummary>(`${P(orgId)}/trip-schedules/apply`, {}),
  documents: (orgId: string) => api.get<BusDocument[]>(`${P(orgId)}/documents`),
  uploadDocument: (orgId: string, body: { file: File; type: BusDocType; documentNumber: string; validUntil: string | null; busId: string | null }) => {
    const fd = new FormData();
    fd.append('file', body.file);
    fd.append('type', body.type);
    fd.append('documentNumber', body.documentNumber);
    if (body.validUntil) fd.append('validUntil', body.validUntil);
    if (body.busId) fd.append('busId', body.busId);
    return api.postForm<BusDocument>(`${P(orgId)}/documents`, fd);
  },
  downloadDocument: (orgId: string, id: string) => api.getBlob(`${P(orgId)}/documents/${enc(id)}/file`),
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

/** Turkish lira style amount, no Intl dependency so tests are locale independent. "850,00 TL", not "850.00 TRY" (F72). */
export function formatMoney(amount: number, currency = 'TRY'): string {
  const [int, dec] = (Math.round(amount * 100) / 100).toFixed(2).split('.');
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${grouped},${dec} ${currency === 'TRY' ? 'TL' : currency}`;
}

const DAY_ORDER: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const DAY_SHORT: Record<Weekday, string> = {
  MONDAY: 'Pzt', TUESDAY: 'Sal', WEDNESDAY: 'Car', THURSDAY: 'Per', FRIDAY: 'Cum', SATURDAY: 'Cmt', SUNDAY: 'Paz',
};
export const WEEKDAYS: { value: Weekday; label: string }[] = DAY_ORDER.map(value => ({ value, label: DAY_SHORT[value] }));

/** "Pzt Cum Paz": week order, duplicates dropped, unknown names ignored. */
export function weekdayLabel(days: readonly string[]): string {
  return DAY_ORDER.filter(d => days.includes(d)).map(d => DAY_SHORT[d]).join(' ');
}

export interface ScheduleClockStop { terminalId: string; arrival: string; arrivalNextDay: boolean; departure: string; departureNextDay: boolean }

function clockMinutes(hhmm: string): number | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm.trim());
  if (!m) return null;
  const h = Number(m[1]); const min = Number(m[2]);
  return h > 23 || min > 59 ? null : h * 60 + min;
}

/**
 * Clock times (HH:mm, optional "+1 gun" marker per time) to minute offsets from the first departure.
 * The first stop is always 0/0. Returns null when a time is missing or invalid. A time that is earlier than the
 * previous one without a marker is not auto-corrected: the backend rejects it with INVALID_SCHEDULE.
 */
export function scheduleOffsets(departureTime: string, stops: ScheduleClockStop[]): ScheduleStopInput[] | null {
  const base = clockMinutes(departureTime);
  if (base === null) return null;
  const out: ScheduleStopInput[] = [];
  for (let i = 0; i < stops.length; i++) {
    const s = stops[i];
    if (i === 0) { out.push({ terminalId: s.terminalId, arrivalOffsetMinutes: 0, departureOffsetMinutes: 0 }); continue; }
    const a = clockMinutes(s.arrival);
    const d = clockMinutes(s.departure);
    if (a === null || d === null) return null;
    out.push({
      terminalId: s.terminalId,
      arrivalOffsetMinutes: a + (s.arrivalNextDay ? 1440 : 0) - base,
      departureOffsetMinutes: d + (s.departureNextDay ? 1440 : 0) - base,
    });
  }
  return out;
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
  COMMISSION_RATE_MISSING: SALES_CLOSED_MESSAGE,
  SCHEDULE_CODE_TAKEN: 'Bu plan kodu zaten kullaniliyor. Baska bir kod secin.',
  INVALID_SCHEDULE: 'Plan bilgileri gecersiz: gun secimi, tarih araligi, kac gun ileri ve durak saatlerini kontrol edin.',
};

/** Trip rule errors share INVALID_STOPS / INVALID_FARES; the backend text tells which rule failed (F71). */
const TRIP_RULE_TEXT: [RegExp, string][] = [
  [/Stop times must increase/i, 'Durak saatleri guzergah boyunca ileri gitmeli: bir durak bir onceki duraktan once olamaz. Gece seferlerinde ertesi gunun tarihini secin.'],
  [/Departure is before arrival/i, 'Bir durakta kalkis saati varis saatinden once olamaz.'],
  [/first departure must be in the future/i, 'Ilk kalkis gelecekte olmali.'],
  [/at least two stops/i, 'Sefer en az iki duraktan olusmali.'],
  [/terminal cannot appear twice/i, 'Ayni durak bir seferde iki kez kullanilamaz.'],
  [/fare for the full route is required/i, 'Tum guzergah (ilk duraktan son duraga) icin fiyat zorunlu.'],
  [/Duplicate fare/i, 'Ayni durak ikilisi icin birden fazla fiyat girilmis.'],
  [/published trips/i, 'Yayinda seferi olan otobusun koltuk duzeni degistirilemez.'],
  [/Bus is not active/i, 'Otobus pasif, once etkinlestirin.'],
  [/Unknown or inactive terminal/i, 'Durak bulunamadi veya pasif.'],
];

export function busErrorMessage(err: unknown, fallback: string): string {
  const code = err instanceof ApiError ? err.code : null;
  if (code && ERROR_TEXT[code]) return ERROR_TEXT[code];
  const message = err instanceof Error ? err.message : '';
  const rule = message ? TRIP_RULE_TEXT.find(([re]) => re.test(message)) : undefined;
  return rule?.[1] || message || fallback;
}
