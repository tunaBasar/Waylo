/**
 * Car rental types and API client for customer and admin pages.
 *
 * Reuses type names from partner-car.ts where they overlap (Category, Transmission, FuelType, etc.)
 * and adds customer/admin-specific types. All pure helpers live at the bottom so tests can import
 * them without the api dependency.
 */
import { api, ApiError, ADMIN_CANNOT_PURCHASE_MESSAGE } from './api';
import type { Category, Transmission, FuelType, DocumentStatus, RentalStatus } from './partner-car';
import type { Rental as PartnerRental } from './partner-car';

// ── API response types ──────────────────────────────────────────────────────

export interface Branch {
  id: string;
  organizationId: string;
  name: string;
  city: string;
  address: string;
  phone: string;
  openTime: string;
  closeTime: string;
  active: boolean;
}

export interface CarOffer {
  pickupBranchId: string;
  returnBranchId: string;
  organizationId: string;
  category: Category;
  transmission: Transmission;
  exampleModel: string;
  seatCount: number;
  fuelType: FuelType;
  chargeableDays: number;
  dailyRate: number;
  oneWayFee: number;
  totalAmount: number;
  currency: string;
  depositAmount: number;
  kmLimitPerDay: number | null;
  extraKmFee: number | null;
  minDriverAge: number;
  minLicenseYears: number;
  authorizationDocumentNumber: string | null;
}

export interface CarOrderRequest {
  pickupBranchId: string;
  returnBranchId: string;
  category: Category;
  transmission: Transmission;
  pickupAt: string;
  returnAt: string;
  driverAge: number;
}

/** Subset of package-booking-service OrderResponse returned by POST /api/orders/car. */
export interface CarOrderResponse {
  id: string;
  status: string;
  totalAmount: number;
  currency: string;
  holdExpiresAt: string | null;
}

export type CarRental = PartnerRental;

export interface AdminCarVehicle {
  id: string;
  organizationId: string;
  homeBranchId: string;
  plateNumber: string;
  brand: string;
  model: string;
  year: number;
  category: Category;
  transmission: Transmission;
  fuelType: FuelType;
  seatCount: number;
  active: boolean;
}

export interface AdminCarDocument {
  id: string;
  organizationId: string;
  vehicleId: string | null;
  type: string;
  documentNumber: string | null;
  validUntil: string | null;
  status: DocumentStatus;
  rejectReason: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  storageKey: string;
  uploadedBy: string;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface AdminApproveResponse {
  success: boolean;
}

export interface AdminRejectRequest {
  reason: string;
}

// ── API functions ───────────────────────────────────────────────────────────

const enc = encodeURIComponent;

/** Fetch active branches of sellable orgs. Optional city filter. */
export function getCarBranches(city?: string): Promise<{ data: Branch[] }> {
  const qs = city ? `?city=${enc(city)}` : '';
  return api.get<Branch[]>(`/api/car-branches${qs}`);
}

/** Fetch car offers for the given search parameters. */
export function getCarOffers(params: {
  pickupBranchId: string;
  returnBranchId: string;
  pickupAt: string;
  returnAt: string;
  driverAge: number;
}): Promise<{ data: CarOffer[] }> {
  const qs = new URLSearchParams({
    pickupBranchId: params.pickupBranchId,
    returnBranchId: params.returnBranchId,
    pickupAt: params.pickupAt,
    returnAt: params.returnAt,
    driverAge: String(params.driverAge),
  }).toString();
  return api.get<CarOffer[]>(`/api/car-offers?${qs}`);
}

/** Place a car order (POST /api/orders/car). */
export function postCarOrder(body: CarOrderRequest): Promise<{ data: CarOrderResponse }> {
  return api.post<CarOrderResponse>('/api/orders/car', body);
}

/** Fetch current user's car rentals. */
export function getMyRentals(): Promise<{ data: CarRental[] }> {
  return api.get<CarRental[]>('/api/rentals/me');
}

// ── Admin API functions ─────────────────────────────────────────────────────

/** Fetch car documents with optional status filter. */
export function getAdminCarDocuments(status?: string): Promise<{ data: AdminCarDocument[] }> {
  const qs = status ? `?status=${enc(status)}` : '';
  return api.get<AdminCarDocument[]>(`/api/admin/car-documents${qs}`);
}

/** Fetch the raw file bytes for a document (returns blob). */
export async function getAdminCarDocumentFile(documentId: string): Promise<Blob> {
  const res = await fetch(
    `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/api/admin/car-documents/${enc(documentId)}/file`,
    {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('waylo_token') || ''}` },
    },
  );
  if (!res.ok) {
    throw new ApiError('Belge dosyasi yuklenemedi.', res.status, null);
  }
  return res.blob();
}

/** Approve a car document. */
export function approveCarDocument(documentId: string): Promise<{ data: AdminApproveResponse }> {
  return api.post<AdminApproveResponse>(`/api/admin/car-documents/${enc(documentId)}/approve`, {});
}

/** Reject a car document with a reason. */
export function rejectCarDocument(documentId: string, body: { reason: string }): Promise<{ data: AdminApproveResponse }> {
  return api.post<AdminApproveResponse>(`/api/admin/car-documents/${enc(documentId)}/reject`, body);
}

/** Fetch all admin car vehicles (read-only). */
export function getAdminCarVehicles(): Promise<{ data: AdminCarVehicle[] }> {
  return api.get<AdminCarVehicle[]>('/api/admin/car-vehicles');
}

// ── Display label maps ──────────────────────────────────────────────────────

export const CATEGORY_LABEL: Record<Category, string> = {
  ECONOMY: 'Ekonomi',
  COMPACT: 'Kompakt',
  MIDSIZE: 'Orta',
  FULLSIZE: 'Buyuk',
  SUV: 'SUV',
  MINIVAN: 'Minivan',
  LUXURY: 'Luks',
  VAN: 'Van',
};

export const TRANSMISSION_LABEL: Record<Transmission, string> = {
  AUTOMATIC: 'Otomatik',
  MANUAL: 'Manuel',
};

export const FUEL_LABEL: Record<FuelType, string> = {
  PETROL: 'Benzin',
  DIESEL: 'Dizel',
  LPG: 'LPG',
  HYBRID: 'Hibrit',
  ELECTRIC: 'Elektrik',
};

export const BRANCH_STATUS_LABEL: Record<DocumentStatus, string> = {
  PENDING: 'Onay bekliyor',
  APPROVED: 'Onaylandi',
  REJECTED: 'Reddedildi',
};

export const RENTAL_STATUS_LABEL: Record<RentalStatus, string> = {
  HELD: 'Rezerve edildi',
  CONFIRMED: 'Onayli',
  CANCELLED: 'Iptal edildi',
  EXPIRED: 'Suresi doldu',
};

// ── Pure helpers (no api import) ─────────────────────────────────────────────

/** Generate time slot strings in 30-minute steps from HH:MM to HH:MM. */
export function generateTimeSlots(): string[] {
  const slots: string[] = [];
  for (let h = 0; h < 24; h++) {
    for (const m of [0, 30]) {
      const hh = String(h).padStart(2, '0');
      const mm = String(m).padStart(2, '0');
      slots.push(`${hh}:${mm}`);
    }
  }
  return slots;
}

/** Convert a local date+time (YYYY-MM-DD, HH:mm) into an offsetless local datetime string. */
export function toLocalDateTime(date: string, time: string): string {
  return `${date}T${time}:00`;
}

/**
 * Format an offsetless local datetime string (e.g. "2026-11-05T10:00:00")
 * as a Turkish locale date-time, without timezone conversion.
 */
export function formatLocalDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  try {
    const match = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):\d{2}$/);
    if (!match) return iso;
    const [, year, month, day, hour, minute] = match;
    return `${day}.${month}.${year} ${hour}:${minute}`;
  } catch {
    return iso;
  }
}

/** Map a backend error code to a Turkish user-facing message. */
export function errorToMessage(code: string | null | undefined, fallback: string): string {
  switch (code) {
    case 'NO_VEHICLE_AVAILABLE':
      return 'Secilen tarihlerde musait arac bulunamadi.';
    case 'DRIVER_TOO_YOUNG':
      return 'Sofor yas min gerekliligi karsilanmiyor.';
    case 'OUTSIDE_BRANCH_HOURS':
      return 'Secilen saat sube calisma saatleri disinda.';
    case 'INVALID_TIME':
      return 'Gecersiz tarih veya saat formati.';
    case 'ROUTE_NOT_OFFERED':
      return 'Giden-donus rotasi icin araç kiralama teklifi bulunmuyor.';
    case 'COMMISSION_RATE_MISSING':
      return 'Komisyon oran tanimli degil. lutfen sonra tekrar deneyin.';
    case 'ADMIN_CANNOT_PURCHASE':
      return ADMIN_CANNOT_PURCHASE_MESSAGE;
    default:
      return fallback;
  }
}

/** Format a Turkish date-time from an ISO string. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  try {
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return iso;
    return date.toLocaleString('tr-TR', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return iso;
  }
}
