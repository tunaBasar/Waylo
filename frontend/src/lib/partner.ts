/**
 * Partner (hotel B2B) domain types and API client.
 *
 * Kept free of React and DOM APIs on purpose: it only depends on the `api` transport, so the
 * same types and functions can be lifted into a shared package for the mobile app later.
 * Field names mirror the backend DTOs exactly (auth-service, hotel-service, package-booking-service).
 */
import { api, ApiError } from './api';

// ---------- shared enums (string unions, values identical to backend enums) ----------

export type BusinessType = 'HOTEL' | 'CAR_RENTAL' | 'BUS';
export type LegalType = 'COMPANY' | 'INDIVIDUAL';
export type OrganizationStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
export type MembershipStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED';
export type PartnerRole = 'ORGANIZATION_OWNER' | 'HOTEL_OWNER' | 'HOTEL_MANAGER' | 'HOTEL_STAFF';
export type ScopeType = 'ORGANIZATION' | 'HOTEL';
export type CatalogStatus = 'ACTIVE' | 'INACTIVE';
export type MealPlan = 'ROOM_ONLY' | 'BREAKFAST' | 'HALF_BOARD' | 'FULL_BOARD' | 'ALL_INCLUSIVE';
export type CancellationPolicy = 'FREE_CANCELLATION' | 'NON_REFUNDABLE';

/** ISO calendar date, e.g. "2027-03-01". */
export type IsoDate = string;
/** Decimal money value as returned by the backend (JSON number). */
export type Money = number;

// ---------- auth-service ----------

export interface CreateOrganizationRequest {
  name: string;
  businessType: BusinessType;
  legalType: LegalType;
}

export interface Organization {
  id: string;
  name: string;
  status: OrganizationStatus;
  businessType: BusinessType;
  legalType: LegalType;
}

export interface RoleGrant {
  role: PartnerRole;
  scopeType: ScopeType;
  scopeId: string;
}

export interface Membership {
  membershipId: string;
  organizationId: string;
  organizationName: string;
  organizationStatus: OrganizationStatus;
  /** auth-service MembershipResponse field names. */
  organizationBusinessType: BusinessType;
  organizationLegalType: LegalType;
  status: MembershipStatus;
  roles: RoleGrant[];
}

export interface RoleGrantRequest {
  email: string;
  role: PartnerRole;
  hotelId: string;
}

export interface RoleGrantResult {
  grantId: string;
  userId: string;
  role: PartnerRole;
  scopeType: ScopeType;
  scopeId: string;
}

// ---------- hotel-service ----------

export interface PartnerHotel {
  id: string;
  organizationId: string | null;
  name: string;
  city: string;
  country: string;
  address: string;
  starRating: number;
  description: string | null;
  latitude: number | null;
  longitude: number | null;
  active: boolean;
  /** Publication workflow state (hotel-service V10). Only PUBLISHED hotels are public. */
  publicationStatus: PublicationStatus;
  /** Optimistic version of the live content, used as expectedVersion when opening a content revision. */
  contentVersion: number;
  roomCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface HotelRequest {
  name: string;
  city: string;
  country: string;
  address: string;
  starRating: number;
  description?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  organizationId: string;
}

export type PublicationStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'SUSPENDED' | 'ARCHIVED';
export type PublicationRequestKind = 'INITIAL' | 'CONTENT_CHANGE';
export type PublicationRequestStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
export type RevisionStatus = 'DRAFT' | 'SUBMITTED' | 'APPLIED' | 'REJECTED' | 'SUPERSEDED';

export interface PublicationSubmitRequest {
  note?: string;
  /** CONTENT_CHANGE only (hotel PUBLISHED or SUSPENDED). */
  revisionId?: string;
  expectedVersion?: number;
  /** PENDING photos of this hotel attached to the request (max MAX_REQUEST_MEDIA). */
  mediaIds?: string[];
}

export interface PublicationRequestCreated {
  id: string;
  hotelId: string;
  organizationId: string;
  kind: PublicationRequestKind;
  status: PublicationRequestStatus;
  revisionId: string | null;
  submittedBy: string;
  submittedAt: string;
  note: string | null;
  version: number;
  hotelPublicationStatus: PublicationStatus;
  mediaIds?: string[];
}

export interface PublicationRequestSummary {
  id: string;
  mediaCount: number;
  kind: PublicationRequestKind;
  status: PublicationRequestStatus;
  submittedAt: string;
  decidedAt: string | null;
  decision: { decision: string; reason: string | null; actorRole: string; createdAt: string } | null;
}

/** Allowlisted editable hotel content (contract 3.2). */
export interface HotelContent {
  name: string;
  city: string;
  country: string;
  address: string;
  starRating: number;
  description: string | null;
  latitude: number | null;
  longitude: number | null;
}

export interface ContentRevision {
  id: string;
  revisionNo: number;
  status: RevisionStatus;
  version: number;
  baseContentVersion: number;
  content: HotelContent;
}

export interface HotelContentView {
  hotelId: string;
  publicationStatus: PublicationStatus;
  contentVersion: number;
  live: HotelContent;
  revision: ContentRevision | null;
}

export interface RoomType {
  id: string;
  hotelId: string;
  code: string;
  name: string;
  description: string | null;
  maxAdults: number;
  maxChildren: number;
  maxOccupancy: number;
  sizeSqm: number | null;
  status: CatalogStatus;
  version: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface RoomTypeCreateRequest {
  code: string;
  name: string;
  description?: string | null;
  maxAdults: number;
  maxChildren: number;
  maxOccupancy: number;
  sizeSqm?: number | null;
}

/**
 * PATCH semantics: omit a field to leave it unchanged. The backend ignores null, so a field cannot be
 * cleared with null: send an empty string for description to clear it. expectedVersion enables
 * optimistic locking.
 */
export interface RoomTypeUpdateRequest {
  name?: string;
  description?: string;
  maxAdults?: number;
  maxChildren?: number;
  maxOccupancy?: number;
  sizeSqm?: number;
  status?: CatalogStatus;
  expectedVersion?: number;
}

export interface RatePlan {
  id: string;
  hotelId: string;
  code: string;
  name: string;
  description: string | null;
  mealPlan: MealPlan;
  cancellationPolicy: CancellationPolicy;
  freeCancellationDaysBefore: number | null;
  status: CatalogStatus;
  version: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface RatePlanCreateRequest {
  code: string;
  name: string;
  description?: string | null;
  mealPlan: MealPlan;
  cancellationPolicy: CancellationPolicy;
  /** Required when cancellationPolicy is FREE_CANCELLATION. */
  freeCancellationDaysBefore?: number | null;
}

/** PATCH semantics as RoomTypeUpdateRequest (null/omitted = unchanged, empty string clears description). */
export interface RatePlanUpdateRequest {
  name?: string;
  description?: string;
  mealPlan?: MealPlan;
  cancellationPolicy?: CancellationPolicy;
  freeCancellationDaysBefore?: number | null;
  status?: CatalogStatus;
  expectedVersion?: number;
}

export interface DailyInventory {
  id: string | null;
  hotelId: string;
  roomTypeId: string;
  stayDate: IsoDate;
  allotment: number;
  outOfService: number;
  held: number;
  confirmed: number;
  available: number;
  stopSell: boolean;
  version: number | null;
  updatedAt: string | null;
}

export interface InventoryUpdateItem {
  roomTypeId: string;
  stayDate: IsoDate;
  allotment?: number;
  outOfService?: number;
  stopSell?: boolean;
  expectedVersion?: number | null;
}

export interface DailyRate {
  id: string | null;
  hotelId: string;
  roomTypeId: string;
  ratePlanId: string;
  stayDate: IsoDate;
  occupancy: number;
  amount: Money;
  currency: string;
  version: number | null;
  updatedAt: string | null;
}

export interface RateUpdateItem {
  roomTypeId: string;
  ratePlanId: string;
  stayDate: IsoDate;
  occupancy: number;
  amount: Money;
  /** ISO 4217, three upper-case letters. */
  currency: string;
  expectedVersion?: number | null;
}

// ---------- package-booking-service (orders) ----------

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED';
export type OrderItemVertical = 'HOTEL' | 'FLIGHT' | 'CAR';

export interface OrderItem {
  id: string;
  orderId: string;
  vertical: OrderItemVertical;
  supplierOrganizationId: string | null;
  productRef: string;
  fulfillmentRef: string | null;
  description: string | null;
  unitAmount: Money;
  quantity: number;
  subtotal: Money;
  currency: string;
  createdAt: string | null;
}

export interface Order {
  id: string;
  customerId: string;
  status: OrderStatus;
  currency: string;
  quoteId: string | null;
  totalAmount: Money;
  notes: string | null;
  version: number;
  items: OrderItem[];
  createdAt: string | null;
  updatedAt: string | null;
}

// ---------- API functions ----------

const enc = encodeURIComponent;

// ---------- team (auth-service) ----------

export interface TeamMember {
  userId: string;
  email: string;
  name: string;
  role: PartnerRole;
  scopeType: ScopeType;
  scopeId: string;
  grantId: string;
  createdAt: string;
}

// ---------- partner orders (package-booking-service) ----------

export type PartnerOrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED';
export type VoucherStatus = 'ACTIVE' | 'REDEEMED' | 'VOID' | 'NO_SHOW';

export interface PartnerOrderItem {
  orderItemId: string;
  orderId: string;
  orderStatus: PartnerOrderStatus;
  orderCreatedAt: string;
  description: string;
  amount: Money;
  currency: string;
  fulfillmentRef: string | null;
  pickupAt: string | null;
  returnAt: string | null;
  vertical: 'HOTEL' | 'FLIGHT' | 'CAR' | 'BUS' | string;
  checkIn: IsoDate | null;
  checkOut: IsoDate | null;
  passengerFirstName: string | null;
  passengerLastName: string | null;
  voucherStatus: VoucherStatus | null;
  cancelOutcome: string | null;
  refundAmount: Money | null;
  /** Recorded hotel check-out (RM-29); null until the partner checks the guest out. */
  checkedOutAt?: string | null;
  /** Optimistic version of the stay record, sent back as expectedVersion. */
  stayVersion?: number | null;
}

export interface PartnerOrderPage {
  items: PartnerOrderItem[];
  page: number;
  size: number;
  total: number;
}

// ---------- partner earnings (payment-service) ----------

export type EscrowState = 'HELD' | 'RELEASED' | 'REFUNDED';

export interface EarningsRow {
  orderItemId: string;
  vertical: string;
  gross: Money;
  commission: Money;
  partnerNet: Money;
  escrowState: EscrowState;
  /** Not in the contract's earnings spec: used when present, else the single response currency. */
  currency?: string | null;
  heldAt: string | null;
  releasedAt: string | null;
}

export interface EarningsCurrencyTotals { currency: string; held: Money; released: Money; refunded: Money }

export interface PartnerEarnings {
  /** Only present when exactly one currency exists (backward compatibility). Totals are per currency. */
  currency?: string | null;
  totalsByCurrency: EarningsCurrencyTotals[];
  /** Row currency is not in the contract, so rows are shown with the single currency when it is known. */
  items: EarningsRow[];
}

function qs(params: Record<string, string | number | undefined>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

export const partnerApi = {
  members: (organizationId: string) =>
    api.get<TeamMember[]>(`/api/organizations/${enc(organizationId)}/members`),
  orders: (organizationId: string, f: { status?: string; from?: string; to?: string; page?: number; size?: number }) =>
    api.get<PartnerOrderPage>(`/api/partner/organizations/${enc(organizationId)}/order-items${qs(f)}`),
  earnings: (organizationId: string, f: { from?: string; to?: string }) =>
    api.get<PartnerEarnings>(`/api/partner/organizations/${enc(organizationId)}/earnings${qs(f)}`),
  // partner payouts (PAYOUT-CONTRACT.md sec. 6.1)
  payoutBalance: (organizationId: string) =>
    api.get<PartnerPayoutBalance>(`/api/partner/organizations/${enc(organizationId)}/payouts/balance`),
  payouts: (organizationId: string, f: { status?: string; page?: number; size?: number } = {}) =>
    api.get<PartnerPayoutPage>(`/api/partner/organizations/${enc(organizationId)}/payouts${qs(f)}`),
  requestPayout: (organizationId: string, body: { currency: string; amount: number }, idempotencyKey: string) =>
    api.post<PartnerPayout>(`/api/partner/organizations/${enc(organizationId)}/payouts`, body, { 'Idempotency-Key': idempotencyKey }),
  cancelPayout: (organizationId: string, payoutId: string) =>
    api.post<PartnerPayout>(`/api/partner/organizations/${enc(organizationId)}/payouts/${enc(payoutId)}/cancel`, {}),
  bankAccount: (organizationId: string) =>
    api.get<PartnerBankAccount>(`/api/partner/organizations/${enc(organizationId)}/bank-account`),
  saveBankAccount: (organizationId: string, body: { accountHolderName: string; iban: string }) =>
    api.put<PartnerBankAccount>(`/api/partner/organizations/${enc(organizationId)}/bank-account`, body),
  deleteBankAccount: (organizationId: string) =>
    api.del(`/api/partner/organizations/${enc(organizationId)}/bank-account`),
  // organizations and memberships
  myMemberships: () => api.get<Membership[]>('/api/me/memberships'),
  createOrganization: (body: CreateOrganizationRequest) =>
    api.post<Organization>('/api/organizations', body),
  grantRole: (organizationId: string, body: RoleGrantRequest) =>
    api.post<RoleGrantResult>(`/api/organizations/${enc(organizationId)}/role-grants`, body),
  revokeRole: (organizationId: string, grantId: string) =>
    api.del(`/api/organizations/${enc(organizationId)}/role-grants/${enc(grantId)}`),

  // hotels
  hotelsByOrganization: (organizationId: string) =>
    api.get<PartnerHotel[]>(`/api/hotels?organizationId=${enc(organizationId)}`),
  hotel: (hotelId: string) => api.get<PartnerHotel>(`/api/hotels/${enc(hotelId)}`),
  createHotel: (body: HotelRequest) => api.post<PartnerHotel>('/api/hotels', body),
  updateHotel: (hotelId: string, body: HotelRequest) => api.put<PartnerHotel>(`/api/hotels/${enc(hotelId)}`, body),

  // publication workflow (contract 4.1, 4.2)
  submitPublication: (hotelId: string, body: PublicationSubmitRequest) =>
    api.post<PublicationRequestCreated>(`/api/hotels/${enc(hotelId)}/publication-requests`, body),
  publicationRequests: (hotelId: string) =>
    api.get<PublicationRequestSummary[]>(`/api/hotels/${enc(hotelId)}/publication-requests`),
  withdrawPublication: (hotelId: string, requestId: string) =>
    api.post<PublicationRequestCreated>(`/api/hotels/${enc(hotelId)}/publication-requests/${enc(requestId)}/withdraw`, {}),
  getContent: (hotelId: string) => api.get<HotelContentView>(`/api/hotels/${enc(hotelId)}/content`),
  patchContent: (hotelId: string, body: { expectedVersion: number; content: Partial<HotelContent> }) =>
    api.patch<ContentRevision>(`/api/hotels/${enc(hotelId)}/content`, body),
  archiveHotel: (hotelId: string, reason: string) =>
    api.post<PartnerHotel>(`/api/hotels/${enc(hotelId)}/archive`, { reason }),

  // room types (includeInactive=true lists deactivated ones too, so they can be re-activated;
  // the backend requires ROOM_MANAGE permission for it, which partner users have)
  roomTypes: (hotelId: string, includeInactive = false) =>
    api.get<RoomType[]>(`/api/hotels/${enc(hotelId)}/room-types${includeInactive ? '?includeInactive=true' : ''}`),
  createRoomType: (hotelId: string, body: RoomTypeCreateRequest) =>
    api.post<RoomType>(`/api/hotels/${enc(hotelId)}/room-types`, body),
  updateRoomType: (hotelId: string, roomTypeId: string, body: RoomTypeUpdateRequest) =>
    api.patch<RoomType>(`/api/hotels/${enc(hotelId)}/room-types/${enc(roomTypeId)}`, body),

  // rate plans
  ratePlans: (hotelId: string, includeInactive = false) =>
    api.get<RatePlan[]>(`/api/hotels/${enc(hotelId)}/rate-plans${includeInactive ? '?includeInactive=true' : ''}`),
  createRatePlan: (hotelId: string, body: RatePlanCreateRequest) =>
    api.post<RatePlan>(`/api/hotels/${enc(hotelId)}/rate-plans`, body),
  updateRatePlan: (hotelId: string, ratePlanId: string, body: RatePlanUpdateRequest) =>
    api.patch<RatePlan>(`/api/hotels/${enc(hotelId)}/rate-plans/${enc(ratePlanId)}`, body),

  // inventory calendar (inclusive from, to)
  inventory: (hotelId: string, from: IsoDate, to: IsoDate, roomTypeId?: string) =>
    api.get<DailyInventory[]>(
      `/api/hotels/${enc(hotelId)}/inventory?from=${enc(from)}&to=${enc(to)}` +
        (roomTypeId ? `&roomTypeId=${enc(roomTypeId)}` : ''),
    ),
  updateInventory: (hotelId: string, items: InventoryUpdateItem[], reason?: string) =>
    api.put<DailyInventory[]>(`/api/hotels/${enc(hotelId)}/inventory`, { reason, items }),

  // rates calendar
  rates: (hotelId: string, from: IsoDate, to: IsoDate, roomTypeId?: string, ratePlanId?: string) =>
    api.get<DailyRate[]>(
      `/api/hotels/${enc(hotelId)}/rates?from=${enc(from)}&to=${enc(to)}` +
        (roomTypeId ? `&roomTypeId=${enc(roomTypeId)}` : '') +
        (ratePlanId ? `&ratePlanId=${enc(ratePlanId)}` : ''),
    ),
  updateRates: (hotelId: string, items: RateUpdateItem[]) =>
    api.put<DailyRate[]>(`/api/hotels/${enc(hotelId)}/rates`, { items }),
  rateCurrency: (hotelId: string) =>
    api.get<{ currency: string } | null>(`/api/hotels/${enc(hotelId)}/rates/currency`),

  // orders (self-service: the caller's own orders)
  myOrders: () => api.get<Order[]>('/api/orders'),
  order: (orderId: string) => api.get<Order>(`/api/orders/${enc(orderId)}`),
};

// ---------- display helpers (pure) ----------

/** True when a partnerApi call failed because someone else saved the same record first (HTTP 409 optimistic lock). */
export function isVersionConflict(err: unknown): boolean {
  return err instanceof ApiError && err.status === 409 && err.code === 'CONCURRENT_MODIFICATION';
}

/**
 * Turns a failed partnerApi call into a Turkish message for the user. Optimistic-lock conflicts
 * (someone else saved the same record first) get an actionable explanation instead of the raw
 * backend text.
 */
export function partnerErrorMessage(err: unknown, fallback: string): string {
  if (isVersionConflict(err)) {
    return 'Bu kayit siz duzenlerken baska biri tarafindan degistirildi. Guncel bilgiler yuklendi, tekrar deneyin.';
  }
  return (err instanceof Error && err.message) || fallback;
}

export const PUBLICATION_STATUS_LABEL: Record<PublicationStatus, string> = {
  DRAFT: 'Taslak',
  SUBMITTED: 'Incelemede',
  APPROVED: 'Onaylandi, yayin bekliyor',
  PUBLISHED: 'Yayinda',
  REJECTED: 'Reddedildi',
  SUSPENDED: 'Askiya alindi',
  ARCHIVED: 'Arsivlendi',
};

export const PUBLICATION_STATUS_TONE: Record<PublicationStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  DRAFT: 'neutral',
  SUBMITTED: 'warning',
  APPROVED: 'warning',
  PUBLISHED: 'success',
  REJECTED: 'error',
  SUSPENDED: 'error',
  ARCHIVED: 'neutral',
};

export const REVISION_STATUS_LABEL: Record<RevisionStatus, string> = {
  DRAFT: 'Taslak degisiklik',
  SUBMITTED: 'Incelemede',
  APPLIED: 'Uygulandi',
  REJECTED: 'Reddedildi',
  SUPERSEDED: 'Yenisiyle degisti',
};

/** HOTEL_MANAGE holders: organization owner, or owner/manager granted on this very hotel (HOTEL_STAFF cannot). */
export function canManageHotel(roles: RoleGrant[], hotelId: string): boolean {
  return roles.some(r =>
    r.role === 'ORGANIZATION_OWNER' ||
    ((r.role === 'HOTEL_OWNER' || r.role === 'HOTEL_MANAGER') && r.scopeType === 'HOTEL' && r.scopeId === hotelId));
}

/** Why the "Yayin icin gonder" button must be disabled, or null when submitting is allowed. */
export function submitBlockReason(orgStatus: OrganizationStatus | undefined, canManage: boolean): string | null {
  if (!canManage) return 'Yayin icin gondermek otel yonetim yetkisi gerektirir.';
  if (orgStatus !== 'ACTIVE') return 'Isletmeniz platform tarafindan onaylanmadan otel yayin icin gonderilemez.';
  return null;
}

/** Only the fields that differ from the base, so PATCH carries a minimal partial content. */
export function contentDiff(base: HotelContent, next: HotelContent): Partial<HotelContent> {
  const out: Record<string, unknown> = {};
  for (const k of Object.keys(next) as Array<keyof HotelContent>) {
    if ((base[k] ?? null) !== (next[k] ?? null)) out[k] = next[k];
  }
  return out as Partial<HotelContent>;
}

export function isErrorCode(err: unknown, status: number, code: string): boolean {
  return err instanceof ApiError && err.status === status && err.code === code;
}

export const ORGANIZATION_STATUS_LABEL: Record<OrganizationStatus, string> = {
  PENDING: 'Onay bekliyor',
  ACTIVE: 'Aktif',
  SUSPENDED: 'Askida',
  ARCHIVED: 'Arsivlendi',
};

export const ROLE_LABEL: Record<PartnerRole, string> = {
  ORGANIZATION_OWNER: 'Isletme sahibi',
  HOTEL_OWNER: 'Otel sahibi',
  HOTEL_MANAGER: 'Otel yoneticisi',
  HOTEL_STAFF: 'Otel personeli',
};

export const ORDER_STATUS_LABEL: Record<PartnerOrderStatus, string> = {
  PENDING: 'Beklemede',
  CONFIRMED: 'Onaylandi',
  CANCELLED: 'Iptal',
  FAILED: 'Basarisiz',
};

export const VOUCHER_STATUS_LABEL: Record<VoucherStatus, string> = {
  ACTIVE: 'Kullanilmadi',
  REDEEMED: 'Kullanildi',
  VOID: 'Gecersiz',
  NO_SHOW: 'Gelmedi',
};

export const ESCROW_STATE_LABEL: Record<EscrowState, string> = {
  HELD: 'Bekletiliyor',
  RELEASED: 'Serbest birakildi',
  REFUNDED: 'Iade edildi',
};

export const MEAL_PLAN_LABEL: Record<MealPlan, string> = {
  ROOM_ONLY: 'Sadece oda',
  BREAKFAST: 'Kahvalti dahil',
  HALF_BOARD: 'Yarim pansiyon',
  FULL_BOARD: 'Tam pansiyon',
  ALL_INCLUSIVE: 'Her sey dahil',
};

export const CANCELLATION_POLICY_LABEL: Record<CancellationPolicy, string> = {
  FREE_CANCELLATION: 'Ucretsiz iptal',
  NON_REFUNDABLE: 'Iade edilemez',
};

// ---------- partner payouts (PAYOUT-CONTRACT.md sec. 4, 6) ----------

/** The contract shows money as strings, existing DTOs use numbers: accept both. */
export type Amount = number | string;

export type PayoutStatus = 'REQUESTED' | 'APPROVED' | 'PROCESSING' | 'PAID' | 'FAILED' | 'CANCELLED';
export type BankAccountStatus = 'PENDING_VERIFICATION' | 'VERIFIED' | 'REJECTED' | 'DISABLED';
export type PayoutBlockedReason =
  | 'NO_BANK_ACCOUNT' | 'BANK_NOT_VERIFIED' | 'BELOW_MINIMUM' | 'OPEN_PAYOUT' | 'NEGATIVE_BALANCE'
  | 'CURRENCY_NOT_SUPPORTED' | 'NOT_OWNER' | 'ORGANIZATION_NOT_ACTIVE';

export interface PayoutCurrencyBalance {
  currency: string;
  payable: Amount;
  releasedNotYetPayable: Amount;
  /** ESTIMATED: still in escrow and may be refunded. Never part of payable. */
  held: Amount;
  reserved: Amount;
  paidTotal: Amount;
  owed: Amount;
  minimumPayout: Amount;
  canRequest: boolean;
  blockedReason: PayoutBlockedReason | null;
}

export interface PartnerPayoutBalance {
  organizationId: string;
  holdbackDays: number;
  balances: PayoutCurrencyBalance[];
  bankAccount: { status: BankAccountStatus; maskedIban: string } | null;
}

export interface PartnerPayout {
  id: string;
  organizationId: string;
  currency: string;
  amount: Amount;
  status: PayoutStatus;
  maskedIban: string;
  accountHolderName: string;
  requestedAt: string;
  approvedAt: string | null;
  processingAt: string | null;
  paidAt: string | null;
  paymentReference: string | null;
  failureReason: string | null;
  cancelReason: string | null;
  cancelledByType: 'PARTNER' | 'ADMIN' | null;
  version: number;
}
export interface PartnerPayoutPage { items: PartnerPayout[]; page: number; size: number; total: number }

export interface PartnerBankAccount {
  id: string;
  accountHolderName: string;
  ibanLast4: string;
  maskedIban: string;
  status: BankAccountStatus;
  verifiedAt: string | null;
  rejectReason: string | null;
  updatedAt: string;
  version: number;
}

export const PAYOUT_STATUS_LABEL: Record<PayoutStatus, string> = {
  REQUESTED: 'Talep edildi',
  APPROVED: 'Onaylandi',
  PROCESSING: 'Isleniyor',
  PAID: 'Odendi',
  FAILED: 'Basarisiz',
  CANCELLED: 'Iptal edildi',
};

export const PAYOUT_STATUS_TONE: Record<PayoutStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  REQUESTED: 'warning',
  APPROVED: 'warning',
  PROCESSING: 'warning',
  PAID: 'success',
  FAILED: 'error',
  CANCELLED: 'neutral',
};

export const BANK_STATUS_LABEL: Record<BankAccountStatus, string> = {
  PENDING_VERIFICATION: 'Onay bekliyor',
  VERIFIED: 'Onayli',
  REJECTED: 'Reddedildi',
  DISABLED: 'Devre disi',
};

export const BANK_STATUS_TONE: Record<BankAccountStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  PENDING_VERIFICATION: 'warning',
  VERIFIED: 'success',
  REJECTED: 'error',
  DISABLED: 'neutral',
};

export const PAYOUT_BLOCKED_LABEL: Record<PayoutBlockedReason, string> = {
  NO_BANK_ACCOUNT: 'Once bir banka hesabi ekleyin.',
  BANK_NOT_VERIFIED: 'Banka hesabiniz henuz onaylanmadi.',
  BELOW_MINIMUM: 'Bakiye asgari odeme tutarinin altinda.',
  OPEN_PAYOUT: 'Bu para birimi icin devam eden bir odeme talebi var.',
  NEGATIVE_BALANCE: 'Borcunuz var. Yeni kazanclar borcu kapatana kadar odeme talep edilemez.',
  CURRENCY_NOT_SUPPORTED: 'Bu para birimi icin odeme yapilmiyor.',
  NOT_OWNER: 'Odeme talebini yalnizca isletme sahibi yapabilir.',
  ORGANIZATION_NOT_ACTIVE: 'Isletmeniz aktif olmadigi icin odeme talep edilemez.',
};

/** Turkish text per backend error code (PAYOUT-CONTRACT.md sec. 6.3). */
export const PAYOUT_ERROR_LABEL: Record<string, string> = {
  INVALID_IBAN: 'IBAN gecersiz. Gecerli bir TR IBAN girin.',
  PAYOUT_OWNER_ONLY: 'Bu islemi yalnizca isletme sahibi yapabilir.',
  INVALID_STATE_TRANSITION: 'Odeme bu durumda bu islem icin uygun degil. Sayfayi yenileyin.',
  VERSION_CONFLICT: 'Kayit baska biri tarafindan degistirildi. Sayfayi yenileyip tekrar deneyin.',
  OPEN_PAYOUT_EXISTS: 'Bu para birimi icin devam eden bir odeme var.',
  IDEMPOTENCY_KEY_REUSED: 'Bu talep anahtari farkli bir tutarla kullanilmis. Formu kapatip yeniden acin.',
  IBAN_ALREADY_IN_USE: 'Bu IBAN baska bir hesapta kullaniliyor.',
  BANK_ACCOUNT_CHANGED: 'Banka hesabi talepten sonra degisti. Talebi iptal edip yeniden olusturun.',
  ORGANIZATION_NOT_ACTIVE: 'Isletme aktif degil.',
  PAYMENT_REFERENCE_IN_USE: 'Bu banka referansi baska bir odemede kullanilmis.',
  INSUFFICIENT_PAYABLE_BALANCE: 'Tutar cekilebilir bakiyeyi asiyor.',
  PAYOUT_BELOW_MINIMUM: 'Tutar asgari odeme tutarinin altinda.',
  PAYOUT_CURRENCY_NOT_SUPPORTED: 'Bu para birimi icin odeme yapilmiyor.',
  BANK_ACCOUNT_REQUIRED: 'Once bir banka hesabi ekleyin.',
  BANK_ACCOUNT_NOT_VERIFIED: 'Banka hesabi henuz onaylanmadi.',
  DEPENDENCY_UNAVAILABLE: 'Servis gecici olarak kullanilamiyor, biraz sonra tekrar deneyin.',
  ACCESS_DENIED: 'Bu islem icin yetkiniz yok.',
};

export function isPayoutVersionConflict(err: unknown): boolean {
  return err instanceof ApiError && err.status === 409 && err.code === 'VERSION_CONFLICT';
}

/** Turkish message for a failed payout call: by error code first, then backend text, then fallback. */
export function payoutErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError && err.code && PAYOUT_ERROR_LABEL[err.code]) return PAYOUT_ERROR_LABEL[err.code];
  return partnerErrorMessage(err, fallback);
}

/** Numeric value of a money field that may arrive as number or decimal string. */
export function toAmount(v: Amount | null | undefined): number {
  if (v == null) return 0;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}

/** Strips spaces and hyphens, uppercases. */
export function normalizeIban(raw: string): string {
  return raw.replace(/[\s-]/g, '').toUpperCase();
}

/** Turkish IBAN: TR + 24 digits and ISO 13616 mod-97 check. The server stays authoritative. */
export function isValidTrIban(raw: string): boolean {
  const iban = normalizeIban(raw);
  if (!/^TR[0-9]{24}$/.test(iban)) return false;
  const rearranged = iban.slice(4) + iban.slice(0, 4);
  let remainder = 0;
  for (const ch of rearranged) {
    const code = ch.charCodeAt(0);
    const digits = code >= 65 ? String(code - 55) : ch;
    for (const d of digits) remainder = (remainder * 10 + (d.charCodeAt(0) - 48)) % 97;
  }
  return remainder === 1;
}

/** Same format as the backend IbanMask: check digits and middle never shown, last 2 digits only. */
export function maskIbanFromLast4(last4: string): string {
  return `TR** **** **** **** **** **${last4.slice(2)}`;
}

/** Groups a normalized IBAN in blocks of four for display (only used on the admin reveal). */
export function formatIbanGroups(iban: string): string {
  return normalizeIban(iban).replace(/(.{4})/g, '$1 ').trim();
}

/** Parses "1.000,50", "1000.5" style input to a number with max 2 decimals, or null. */
export function parseAmountInput(input: string): number | null {
  const t = input.trim().replace(/\s/g, '');
  if (!t) return null;
  let norm = t;
  if (t.includes(',')) norm = t.replace(/\./g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(norm)) return null;
  const n = Number(norm);
  return Number.isFinite(n) ? n : null;
}

/** Client-side amount check for a payout request. Returns a Turkish error or null when acceptable. */
export function validatePayoutAmount(input: string, payable: Amount, minimum: Amount): string | null {
  const value = parseAmountInput(input);
  if (value == null) return 'Gecerli bir tutar girin (en fazla 2 ondalik).';
  if (value <= 0) return 'Tutar sifirdan buyuk olmali.';
  if (value > 10000000) return 'Tutar en fazla 10.000.000 olabilir.';
  const min = toAmount(minimum);
  if (Math.round(value * 100) < Math.round(min * 100)) return `Asgari odeme tutari ${min.toFixed(2)}.`;
  if (Math.round(value * 100) > Math.round(toAmount(payable) * 100)) return 'Tutar cekilebilir bakiyeyi asiyor.';
  return null;
}

// ---------- stay operations (RM-29) ----------

export interface StayOperationResult {
  orderItemId: string;
  voucherStatus: VoucherStatus;
  checkedOutAt: string | null;
  version: number;
}

/** Records the hotel check-out of a REDEEMED order item. 409 INVALID_STATE_TRANSITION / VERSION_CONFLICT on bad state. */
export const checkOutOrderItem = (organizationId: string, orderItemId: string, expectedVersion: number) =>
  api.post<StayOperationResult>(
    `/api/partner/organizations/${encodeURIComponent(organizationId)}/order-items/${encodeURIComponent(orderItemId)}/check-out`,
    { expectedVersion });

export function checkOutErrorMessage(err: unknown): string {
  if (err instanceof ApiError && err.status === 409) {
    return 'Check-out yapilamadi: konaklama kaydi bu islem icin uygun durumda degil veya baska biri tarafindan degistirildi. Liste yenilendi.';
  }
  return partnerErrorMessage(err, 'Check-out yapilamadi');
}

// ---------- hotel photos (hotel-service V11, contract sec. 5) ----------

export const HOTEL_PHOTO_MAX_BYTES = 5 * 1024 * 1024;
export const HOTEL_PHOTO_MAX_COUNT = 20;
export const HOTEL_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
export const HOTEL_PHOTO_MIN_WIDTH = 800;
export const HOTEL_PHOTO_MIN_HEIGHT = 600;

export type MediaReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface HotelMediaItem {
  id: string;
  hotelId: string;
  caption: string | null;
  sortOrder: number;
  rightsSource: string;
  rightsNote: string | null;
  contentType: string;
  sizeBytes: number;
  width: number;
  height: number;
  scanStatus: 'PENDING' | 'CLEAN' | 'REJECTED';
  reviewStatus: MediaReviewStatus;
  reviewReason: string | null;
  createdAt: string;
}

export const MEDIA_STATUS_LABEL: Record<MediaReviewStatus, string> = {
  PENDING: 'Incelemede',
  APPROVED: 'Onaylandi',
  REJECTED: 'Reddedildi',
};
export const MEDIA_STATUS_TONE: Record<MediaReviewStatus, 'success' | 'warning' | 'error'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
};

/** Client side pre-check (the server re-validates by magic bytes). Returns an error text or null. */
export function validatePhotoFile(file: { type: string; size: number }, existingCount: number): string | null {
  if (existingCount >= HOTEL_PHOTO_MAX_COUNT) return `En fazla ${HOTEL_PHOTO_MAX_COUNT} fotograf yuklenebilir`;
  if (!(HOTEL_PHOTO_TYPES as readonly string[]).includes(file.type)) return 'Yalnizca JPEG, PNG veya WebP yuklenebilir';
  if (file.size <= 0) return 'Dosya bos';
  if (file.size > HOTEL_PHOTO_MAX_BYTES) return 'Dosya 5 MB sinirini asiyor';
  return null;
}

/** Returns the ids after moving one item by delta positions, clamped. Same array when nothing changes. */
export function moveId(ids: string[], id: string, delta: number): string[] {
  const from = ids.indexOf(id);
  if (from < 0) return ids;
  const to = Math.max(0, Math.min(ids.length - 1, from + delta));
  if (to === from) return ids;
  const next = ids.slice();
  next.splice(from, 1);
  next.splice(to, 0, id);
  return next;
}

function mediaAuthHeaders(): Record<string, string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('waylo_token') : null;
  return token ? { Authorization: `Bearer ${token}` } : {};
}
const mediaBase = () => process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const hotelMediaApi = {
  list: (hotelId: string) => api.get<HotelMediaItem[]>(`/api/hotels/${enc(hotelId)}/media/mine`),
  /** Multipart: no JSON Content-Type, the browser sets the boundary. */
  upload: async (hotelId: string, file: File, caption: string): Promise<HotelMediaItem> => {
    const fd = new FormData();
    fd.append('file', file);
    if (caption.trim()) fd.append('caption', caption.trim());
    const res = await fetch(`${mediaBase()}/api/hotels/${enc(hotelId)}/media`, { method: 'POST', body: fd, headers: mediaAuthHeaders() });
    const body = await res.json().catch(() => null) as { data?: HotelMediaItem; message?: string; error?: string } | null;
    if (!res.ok || !body?.data) throw new ApiError(body?.message || 'Fotograf yuklenemedi', res.status, body?.error ?? null);
    return body.data;
  },
  caption: (hotelId: string, mediaId: string, caption: string) =>
    api.patch<HotelMediaItem>(`/api/hotels/${enc(hotelId)}/media/${enc(mediaId)}`, { caption }),
  reorder: (hotelId: string, ids: string[]) =>
    api.put<HotelMediaItem[]>(`/api/hotels/${enc(hotelId)}/media/order`, { ids }),
  remove: (hotelId: string, mediaId: string) => api.del(`/api/hotels/${enc(hotelId)}/media/${enc(mediaId)}`),
  /** Own photo bytes (any review state) as an object URL source. */
  file: async (hotelId: string, mediaId: string): Promise<Blob> => {
    const res = await fetch(`${mediaBase()}/api/hotels/${enc(hotelId)}/media/${enc(mediaId)}/file`, { headers: mediaAuthHeaders() });
    if (!res.ok) throw new ApiError('Fotograf yuklenemedi', res.status, null);
    return res.blob();
  },
};

// ---------- photos attached to a publication request (media phase 3) ----------

export const MAX_REQUEST_MEDIA = 20;

/** Ids of the non-deleted PENDING photos, in display order. */
export function pendingPhotoIds(items: Array<{ id: string; sortOrder: number; reviewStatus: MediaReviewStatus; deleted?: boolean; deletedAt?: string | null }>): string[] {
  return items
    .filter(i => i.reviewStatus === 'PENDING' && !i.deleted && !i.deletedAt)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map(i => i.id);
}

/** Turkish text for the photo related submit errors; other errors fall through to partnerErrorMessage. */
export function publicationMediaErrorMessage(err: unknown, fallback: string): string {
  if (isErrorCode(err, 404, 'MEDIA_NOT_FOUND')) {
    return 'Secilen fotograflardan biri bulunamadi. Fotograf listesi yenilendi, tekrar deneyin.';
  }
  if (isErrorCode(err, 409, 'MEDIA_NOT_PENDING')) {
    return 'Secilen fotograflardan biri artik incelemede degil. Fotograf listesi yenilendi, tekrar deneyin.';
  }
  return partnerErrorMessage(err, fallback);
}
