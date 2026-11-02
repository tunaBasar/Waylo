/**
 * Partner car rental domain types and API client.
 *
 * Kept free of React and DOM APIs on purpose: it only depends on the `api` transport.
 * Field names mirror the car-rental-service DTOs exactly.
 */
import { api } from './api';

// ── shared enums (string unions, values identical to backend enums) ──────────

export type BusinessType = 'HOTEL' | 'CAR_RENTAL';
export type LegalType = 'COMPANY' | 'INDIVIDUAL';
export type RentalStatus = 'HELD' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';
export type DocumentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type Category = 'ECONOMY' | 'COMPACT' | 'MIDSIZE' | 'FULLSIZE' | 'SUV' | 'MINIVAN' | 'VAN' | 'LUXURY';
export type Transmission = 'MANUAL' | 'AUTOMATIC';
export type FuelType = 'PETROL' | 'DIESEL' | 'HYBRID' | 'ELECTRIC' | 'LPG';

/** ISO calendar date, e.g. "2027-03-01". */
export type IsoDate = string;

// ── display label maps ───────────────────────────────────────────────────────

export const CATEGORY_LABEL: Record<Category, string> = {
  ECONOMY: 'Ekonomi',
  COMPACT: 'Kompakt',
  MIDSIZE: 'Orta',
  FULLSIZE: 'Buyuk',
  SUV: 'SUV',
  MINIVAN: 'Minivan',
  VAN: 'Van',
  LUXURY: 'Luks',
};

export const TRANSMISSION_LABEL: Record<Transmission, string> = {
  MANUAL: 'Manuel',
  AUTOMATIC: 'Otomatik',
};

export const FUEL_LABEL: Record<FuelType, string> = {
  PETROL: 'Benzin',
  DIESEL: 'Dizel',
  HYBRID: 'Hibrit',
  ELECTRIC: 'Elektrikli',
  LPG: 'LPG',
};

export const RENTAL_STATUS_LABEL: Record<RentalStatus, string> = {
  HELD: 'Rezerve edildi',
  CONFIRMED: 'Onayli',
  CANCELLED: 'Iptal edildi',
  EXPIRED: 'Suresi doldu',
};

export const DOCUMENT_STATUS_LABEL: Record<DocumentStatus, string> = {
  PENDING: 'Onay bekliyor',
  APPROVED: 'Onaylandi',
  REJECTED: 'Reddedildi',
};

// ── document types allowed per legal type ────────────────────────────────────

/** Document types allowed at the organization level, keyed by legalType. */
export const ORG_DOCUMENT_TYPES: Record<LegalType, DocumentType[]> = {
  COMPANY: ['RENTAL_AUTHORIZATION', 'TAX_CERTIFICATE'],
  INDIVIDUAL: ['IDENTITY_DOCUMENT'],
};

export type OrgDocumentType = 'RENTAL_AUTHORIZATION' | 'TAX_CERTIFICATE' | 'IDENTITY_DOCUMENT';
export type VehicleDocumentType = 'VEHICLE_REGISTRATION' | 'RENTAL_INSURANCE' | 'TRAFFIC_INSURANCE';
export type DocumentType = OrgDocumentType | VehicleDocumentType;

const DOCUMENT_TYPE_LABEL: Record<DocumentType, string> = {
  RENTAL_AUTHORIZATION: 'Arac kiralama yetki belgesi',
  TAX_CERTIFICATE: 'Vergi levhasi',
  IDENTITY_DOCUMENT: 'Nufus cüzdañi / kimlik',
  VEHICLE_REGISTRATION: 'Araç ruhsati',
  RENTAL_INSURANCE: 'Araç kiralama sigortasi (kasko)',
  TRAFFIC_INSURANCE: 'Zorunlu trafik sigortasi',
};

export function documentTypeLabel(t: DocumentType): string {
  return DOCUMENT_TYPE_LABEL[t];
}

/** Returns the set of valid document types for this legal type at the org level. */
export function orgDocumentTypesFor(legalType: LegalType | undefined): OrgDocumentType[] {
  return legalType === 'COMPANY'
    ? ['RENTAL_AUTHORIZATION', 'TAX_CERTIFICATE']
    : legalType === 'INDIVIDUAL'
      ? ['IDENTITY_DOCUMENT']
      : [];
}

// ── Branch ───────────────────────────────────────────────────────────────────

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
  createdAt: string | null;
  updatedAt: string | null;
}

export interface BranchCreateRequest {
  name: string;
  city: string;
  address: string;
  phone: string;
  openTime: string;
  closeTime: string;
}

export interface BranchUpdateRequest {
  name?: string;
  city?: string;
  address?: string;
  phone?: string;
  openTime?: string;
  closeTime?: string;
  active?: boolean;
}

// ── Vehicle ──────────────────────────────────────────────────────────────────

export interface Vehicle {
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
  sellable: boolean;
  missingDocuments: string[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface VehicleCreateRequest {
  homeBranchId: string;
  plateNumber: string;
  brand: string;
  model: string;
  year: number;
  category: Category;
  transmission: Transmission;
  fuelType: FuelType;
  seatCount: number;
}

export interface VehicleUpdateRequest {
  homeBranchId?: string;
  plateNumber?: string;
  brand?: string;
  model?: string;
  year?: number;
  category?: Category;
  transmission?: Transmission;
  fuelType?: FuelType;
  seatCount?: number;
  active?: boolean;
}

// ── Category Rate ────────────────────────────────────────────────────────────

export interface CategoryRate {
  id: string;
  organizationId: string;
  branchId: string;
  category: Category;
  transmission: Transmission;
  currency: string;
  dailyRate: number;
  depositAmount: number;
  kmLimitPerDay: number | null;
  extraKmFee: number | null;
  minDriverAge: number;
  minLicenseYears: number;
}

export interface CategoryRateItem {
  branchId: string;
  category: Category;
  transmission: Transmission;
  currency: string;
  dailyRate: number;
  depositAmount: number;
  kmLimitPerDay: number | null;
  extraKmFee: number | null;
  minDriverAge: number;
  minLicenseYears: number;
}

export interface CategoryRatesUpsertRequest {
  items: CategoryRateItem[];
}

// ── One-way Route ────────────────────────────────────────────────────────────

export interface OneWayRoute {
  id: string;
  organizationId: string;
  fromBranchId: string;
  toBranchId: string;
  fee: number;
  currency: string;
}

export interface OneWayRouteItem {
  fromBranchId: string;
  toBranchId: string;
  fee: number;
  currency: string;
}

export interface OneWayRoutesReplaceRequest {
  routes: OneWayRouteItem[];
}

// ── Document ─────────────────────────────────────────────────────────────────

export interface Document {
  id: string;
  organizationId: string;
  vehicleId: string | null;
  type: DocumentType;
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

export interface DocumentUploadRequest {
  file: File;
  type: DocumentType;
  documentNumber: string;
  validUntil: string | null;
  vehicleId: string | null;
}

// ── Rental ───────────────────────────────────────────────────────────────────

export interface Rental {
  id: string;
  organizationId: string;
  customerId: string;
  vehicleId: string;
  pickupBranchId: string;
  returnBranchId: string;
  category: Category;
  transmission: Transmission;
  pickupAt: string;
  returnAt: string;
  chargeableDays: number;
  dailyRate: number;
  oneWayFee: number;
  totalAmount: number;
  currency: string;
  depositAmount: number;
  kmLimitPerDay: number | null;
  driverAge: number;
  status: RentalStatus;
  holdExpiresAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

// ── Status ───────────────────────────────────────────────────────────────────

export interface PartnerCarStatus {
  sellable: boolean;
  missingOrgDocuments: string[];
  individualGateOpen: boolean;
}

// ── API functions ────────────────────────────────────────────────────────────

const enc = encodeURIComponent;

export const partnerCarApi = {
  // status
  status: (orgId: string) =>
    api.get<PartnerCarStatus>(`/api/partner/organizations/${enc(orgId)}/status`),

  // branches
  branches: (orgId: string) =>
    api.get<Branch[]>(`/api/partner/organizations/${enc(orgId)}/branches`),
  createBranch: (orgId: string, body: BranchCreateRequest) =>
    api.post<Branch>(`/api/partner/organizations/${enc(orgId)}/branches`, body),
  updateBranch: (orgId: string, branchId: string, body: BranchUpdateRequest) =>
    api.put<Branch>(`/api/partner/organizations/${enc(orgId)}/branches/${enc(branchId)}`, body),

  // vehicles
  vehicles: (orgId: string) =>
    api.get<Vehicle[]>(`/api/partner/organizations/${enc(orgId)}/vehicles`),
  createVehicle: (orgId: string, body: VehicleCreateRequest) =>
    api.post<Vehicle>(`/api/partner/organizations/${enc(orgId)}/vehicles`, body),
  updateVehicle: (orgId: string, vehicleId: string, body: VehicleUpdateRequest) =>
    api.put<Vehicle>(`/api/partner/organizations/${enc(orgId)}/vehicles/${enc(vehicleId)}`, body),
  deactivateVehicle: (orgId: string, vehicleId: string) =>
    api.post<Vehicle>(`/api/partner/organizations/${enc(orgId)}/vehicles/${enc(vehicleId)}/deactivate`, {}),
  activateVehicle: (orgId: string, vehicleId: string) =>
    api.post<Vehicle>(`/api/partner/organizations/${enc(orgId)}/vehicles/${enc(vehicleId)}/activate`, {}),

  // rates
  rates: (orgId: string) =>
    api.get<CategoryRate[]>(`/api/partner/organizations/${enc(orgId)}/rates`),
  upsertRates: (orgId: string, body: CategoryRatesUpsertRequest) =>
    api.put<CategoryRate[]>(`/api/partner/organizations/${enc(orgId)}/rates`, body),

  // one-way routes
  oneWayRoutes: (orgId: string) =>
    api.get<OneWayRoute[]>(`/api/partner/organizations/${enc(orgId)}/one-way-routes`),
  replaceOneWayRoutes: (orgId: string, body: OneWayRoutesReplaceRequest) =>
    api.put<OneWayRoute[]>(`/api/partner/organizations/${enc(orgId)}/one-way-routes`, body),

  // documents
  documents: (orgId: string) =>
    api.get<Document[]>(`/api/partner/organizations/${enc(orgId)}/documents`),
  uploadDocument: (orgId: string, body: DocumentUploadRequest) => {
    const fd = new FormData();
    fd.append('file', body.file);
    fd.append('type', body.type);
    fd.append('documentNumber', body.documentNumber);
    if (body.validUntil) fd.append('validUntil', body.validUntil);
    if (body.vehicleId) fd.append('vehicleId', body.vehicleId);
    return api.post<Document>(`/api/partner/organizations/${enc(orgId)}/documents`, fd as unknown as Document);
  },
  downloadDocument: (orgId: string, documentId: string) =>
    api.get<Blob>(`/api/partner/organizations/${enc(orgId)}/documents/${enc(documentId)}/file`),

  // rentals
  rentals: (orgId: string) =>
    api.get<Rental[]>(`/api/partner/organizations/${enc(orgId)}/rentals`),
};

// ── pure helpers ─────────────────────────────────────────────────────────────

/** Sort order for vehicle categories (ECONOMY first, LUXURY last). */
export const CATEGORY_ORDER: Category[] = [
  'ECONOMY', 'COMPACT', 'MIDSIZE', 'FULLSIZE', 'SUV', 'MINIVAN', 'VAN', 'LUXURY',
];

/** Sort order for transmissions (MANUAL first, AUTOMATIC last). */
export const TRANSMISSION_ORDER: Transmission[] = ['MANUAL', 'AUTOMATIC'];

/**
 * Returns a stable key identifying a rate row.
 * Uses `id` for existing rates, or `category~transmission` for new (id-less) items.
 */
export function rateKey(item: { id?: string } & Pick<CategoryRateItem, 'category' | 'transmission'>): string {
  return item.id ?? `${item.category}~${item.transmission}`;
}

/**
 * Returns category+transmission pairs available (not yet defined) for a branch.
 * Excludes rows that already exist in the branch's rate list.
 */
export function availableCombinations(branchRates: CategoryRateItem[]): { category: Category; transmission: Transmission }[] {
  const existing = new Set(branchRates.map(r => `${r.category}~${r.transmission}`));
  return CATEGORY_ORDER.flatMap(cat =>
    TRANSMISSION_ORDER.map(trans => ({ category: cat, transmission: trans }))
      .filter(({ category, transmission }) => !existing.has(`${category}~${transmission}`))
  );
}

/**
 * Validation errors for a single rate item, keyed by field name.
 * Returns an object with field -> error-message entries (empty object when valid).
 */
export function validateRateItem(item: CategoryRateItem): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!item.category) errors.category = 'Kategori secilmeli';
  if (!item.transmission) errors.transmission = 'Vites secilmeli';
  if (item.dailyRate <= 0) errors.dailyRate = 'Gunluk fiyat 0\'dan buyuk olmalidir';
  if (item.depositAmount < 0) errors.depositAmount = 'Garanti miktar negatif olamaz';
  if (item.kmLimitPerDay !== null && item.kmLimitPerDay < 0) errors.kmLimitPerDay = 'Gunluk km siniri negatif olamaz';
  if (item.extraKmFee !== null && item.extraKmFee < 0) errors.extraKmFee = 'Ek km ucreti negatif olamaz';
  if (typeof item.minDriverAge !== 'number' || item.minDriverAge < 18 || item.minDriverAge > 99)
    errors.minDriverAge = 'Min surucu yasi 18-99 araliginda olmalidir';
  if (typeof item.minLicenseYears !== 'number' || item.minLicenseYears < 0 || item.minLicenseYears > 50)
    errors.minLicenseYears = 'Min ehliyet yili 0-50 araliginda olmalidir';
  return errors;
}

/**
 * Sort rate items by CATEGORY_ORDER, then by TRANSMISSION_ORDER within each category.
 */
export function sortRatesByOrder(items: CategoryRateItem[]): CategoryRateItem[] {
  return [...items].sort((a, b) => {
    const catDiff = CATEGORY_ORDER.indexOf(a.category) - CATEGORY_ORDER.indexOf(b.category);
    if (catDiff !== 0) return catDiff;
    return TRANSMISSION_ORDER.indexOf(a.transmission) - TRANSMISSION_ORDER.indexOf(b.transmission);
  });
}

/**
 * Format a time string (HH:mm) for display. If the string is invalid, return it raw.
 */
export function formatTime(t: string): string {
  if (!t || typeof t !== 'string') return t || '';
  const match = t.match(/^([01]\d|2[0-3]):([0-5]\d)$/);
  if (!match) return t;
  return `${match[1].replace(/^0/, '')}:${match[2]}`;
}

/**
 * Validate a time string is in HH:mm format within valid ranges.
 */
export function isValidTime(t: string): boolean {
  if (!t || typeof t !== 'string') return false;
  const match = t.match(/^([01]\d|2[0-3]):([0-5]\d)$/);
  return match !== null;
}

/**
 * Compute chargeable days from two ISO date strings.
 * Returns days = ceil(ms between dates / 1440 * 60 * 60 * 1000).
 * Note: the real formula uses minutes and is ceil(minutes/1440), min 1, max 30.
 */
export function computeChargeableDays(pickupAt: string, returnAt: string): number {
  const pickup = new Date(pickupAt).getTime();
  const return_ = new Date(returnAt).getTime();
  const minutes = (return_ - pickup) / 60000;
  const days = Math.ceil(minutes / 1440);
  return Math.max(1, Math.min(30, days));
}

/**
 * Returns the list of all valid vehicle document types (vehicle level).
 */
export function vehicleDocumentTypes(): VehicleDocumentType[] {
  return ['VEHICLE_REGISTRATION', 'RENTAL_INSURANCE', 'TRAFFIC_INSURANCE'];
}

/**
 * Format an offsetless local datetime string as Turkish locale date+time.
 */
export function formatLocalDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  try {
    const match = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):\d{2}$/);
    if (!match) return iso;
    const [, year, month, day, hour, minute] = match;
    return `${year}.${month}.${day} ${hour}:${minute}`;
  } catch {
    return iso;
  }
}

/**
 * Format an offsetless local date string as Turkish locale date.
 */
export function formatLocalDate(iso: string | null | undefined): string {
  if (!iso) return '';
  try {
    const parts = iso.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!parts) return iso;
    const [, year, month, day] = parts;
    return `${year}.${month}.${day}`;
  } catch {
    return iso;
  }
}

/**
 * Format money with Turkish locale.
 */
export function formatMoney(value: number | null | undefined, currency: string): string {
  if (value == null) return '';
  return new Intl.NumberFormat('tr-TR', {
    style: 'currency',
    currency: currency || 'TRY',
  }).format(value);
}
