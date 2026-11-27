/**
 * Recurring flight schedules (INVENTORY-AUTOMATION-CONTRACT.md section 4).
 * Pure helpers sit in the first part so tests can import this file without the api dependency.
 */
import { api } from './api';

export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export const WEEKDAYS: { value: Weekday; label: string; short: string }[] = [
  { value: 'MONDAY', label: 'Pazartesi', short: 'Pzt' },
  { value: 'TUESDAY', label: 'Sali', short: 'Sal' },
  { value: 'WEDNESDAY', label: 'Carsamba', short: 'Car' },
  { value: 'THURSDAY', label: 'Persembe', short: 'Per' },
  { value: 'FRIDAY', label: 'Cuma', short: 'Cum' },
  { value: 'SATURDAY', label: 'Cumartesi', short: 'Cmt' },
  { value: 'SUNDAY', label: 'Pazar', short: 'Paz' },
];

export const SEAT_TYPES: { value: SeatType; label: string }[] = [
  { value: 'ECONOMY', label: 'Ekonomi' },
  { value: 'PREMIUM_ECONOMY', label: 'Premium Ekonomi' },
  { value: 'BUSINESS', label: 'Business' },
  { value: 'FIRST', label: 'First' },
];

export type SeatType = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';

export interface Cabin {
  seatType: SeatType;
  price: number;
  fromRow: number;
  toRow: number;
  letters: string;
}

export interface NextFlight { flightNumber: string; departureTime: string }

export interface FlightSchedule {
  id: string;
  code: string;
  airline: string;
  departureAirportCode: string;
  arrivalAirportCode: string;
  weekdays: Weekday[];
  departureTime: string;
  durationMinutes: number;
  validFrom: string;
  validTo: string | null;
  horizonDays: number;
  active: boolean;
  version: number;
  cabins: Cabin[];
  nextFlights: NextFlight[];
}

export interface GenerationSummary {
  created: number;
  updated: number;
  skippedExisting: number;
  skippedManual: number;
  skippedPast: number;
}

export interface ScheduleResult { schedule: FlightSchedule; summary: GenerationSummary | null }

export interface ScheduleRequest {
  code: string;
  airline: string;
  departureAirportCode: string;
  arrivalAirportCode: string;
  weekdays: Weekday[];
  departureTime: string;
  durationMinutes: number;
  validFrom: string;
  validTo?: string | null;
  horizonDays: number;
  cabins: Cabin[];
  expectedVersion?: number;
}

export const SCHEDULE_CODE_PATTERN = /^[A-Z]{2}[0-9]{1,3}$/;

export const DEFAULT_CABINS: { seatType: SeatType; fromRow: string; toRow: string; letters: string; price: string }[] = [
  { seatType: 'BUSINESS', fromRow: '1', toRow: '2', letters: 'AC', price: '' },
  { seatType: 'ECONOMY', fromRow: '3', toRow: '7', letters: 'ABCD', price: '' },
];

export function weekdayLabel(day: Weekday): string {
  return WEEKDAYS.find(w => w.value === day)?.label ?? day;
}

/** "Cum, Paz" for a list of weekdays, in week order. */
export function weekdaysSummary(days: Weekday[]): string {
  return WEEKDAYS.filter(w => days.includes(w.value)).map(w => w.short).join(', ');
}

/** Flight number for a departure date: code + yy + day of year (3 digits). WD1 + 2026-11-27 -> WD126331. */
export function flightNumberPreview(code: string, isoDate: string): string | null {
  const c = code.trim().toUpperCase();
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!SCHEDULE_CODE_PATTERN.test(c) || !m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  const start = Date.UTC(year, 0, 1);
  const date = Date.UTC(year, month - 1, day);
  const check = new Date(date);
  if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day) return null;
  const dayOfYear = Math.round((date - start) / 86_400_000) + 1;
  return `${c}${String(year % 100).padStart(2, '0')}${String(dayOfYear).padStart(3, '0')}`;
}

const ERROR_TEXTS: Record<string, string> = {
  SCHEDULE_CODE_TAKEN: 'Bu plan kodu zaten kullaniliyor.',
  INVALID_SCHEDULE: 'Plan bilgileri gecersiz. Kod, gunler, saat, sure ve tarihleri kontrol edin.',
  INVALID_CABINS: 'Kabin bilgileri gecersiz. Satir araliklari cakismamali, harfler A-K arasinda ve tekrarsiz olmali.',
  SAME_AIRPORT: 'Kalkis ve varis havalimani ayni olamaz.',
  NOT_FOUND: 'Kayit veya havalimani bulunamadi.',
  CONCURRENT_MODIFICATION: 'Plan baska biri tarafindan degistirildi. Sayfayi yenileyip tekrar deneyin.',
  FORBIDDEN: 'Bu islem icin yonetici yetkisi gerekir.',
  UNAUTHORIZED: 'Oturum gerekli. Lutfen tekrar giris yapin.',
};

/** Turkish text for a backend error code; falls back to the given message or a generic text. */
export function scheduleErrorText(code: string | null | undefined, fallback?: string): string {
  if (code && ERROR_TEXTS[code]) return ERROR_TEXTS[code];
  return fallback && fallback.trim() ? fallback : 'Islem tamamlanamadi. Lutfen tekrar deneyin.';
}

export function summaryText(s: GenerationSummary | null | undefined): string {
  if (!s) return '';
  return `${s.created} ucus olusturuldu, ${s.skippedExisting} mevcut, ${s.skippedPast} gecmis atlandi`;
}

// ── API ─────────────────────────────────────────────────────────────────────

const enc = encodeURIComponent;

export const flightSchedulesApi = {
  list: () => api.get<FlightSchedule[]>('/api/flights/schedules'),
  create: (b: ScheduleRequest) => api.post<ScheduleResult>('/api/flights/schedules', b),
  update: (id: string, b: ScheduleRequest) => api.put<ScheduleResult>(`/api/flights/schedules/${enc(id)}`, b),
  activate: (id: string) => api.post<ScheduleResult>(`/api/flights/schedules/${enc(id)}/activate`, {}),
  deactivate: (id: string) => api.post<ScheduleResult>(`/api/flights/schedules/${enc(id)}/deactivate`, {}),
  applyAll: () => api.post<GenerationSummary>('/api/flights/schedules/apply', {}),
};
