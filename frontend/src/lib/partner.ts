/**
 * Partner (hotel B2B) domain types and API client.
 *
 * Kept free of React and DOM APIs on purpose: it only depends on the `api` transport, so the
 * same types and functions can be lifted into a shared package for the mobile app later.
 * Field names mirror the backend DTOs exactly (auth-service, hotel-service, package-booking-service).
 */
import { api } from './api';

// ---------- shared enums (string unions, values identical to backend enums) ----------

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

export interface Organization {
  id: string;
  name: string;
  status: OrganizationStatus;
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

/** PATCH semantics: omit a field to leave it unchanged. expectedVersion enables optimistic locking. */
export interface RoomTypeUpdateRequest {
  name?: string;
  description?: string | null;
  maxAdults?: number;
  maxChildren?: number;
  maxOccupancy?: number;
  sizeSqm?: number | null;
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

export interface RatePlanUpdateRequest {
  name?: string;
  description?: string | null;
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

export const partnerApi = {
  // organizations and memberships
  myMemberships: () => api.get<Membership[]>('/api/me/memberships'),
  createOrganization: (name: string) => api.post<Organization>('/api/organizations', { name }),
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

  // room types
  roomTypes: (hotelId: string) => api.get<RoomType[]>(`/api/hotels/${enc(hotelId)}/room-types`),
  createRoomType: (hotelId: string, body: RoomTypeCreateRequest) =>
    api.post<RoomType>(`/api/hotels/${enc(hotelId)}/room-types`, body),
  updateRoomType: (hotelId: string, roomTypeId: string, body: RoomTypeUpdateRequest) =>
    api.patch<RoomType>(`/api/hotels/${enc(hotelId)}/room-types/${enc(roomTypeId)}`, body),

  // rate plans
  ratePlans: (hotelId: string) => api.get<RatePlan[]>(`/api/hotels/${enc(hotelId)}/rate-plans`),
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

  // orders (self-service: the caller's own orders)
  myOrders: () => api.get<Order[]>('/api/orders'),
  order: (orderId: string) => api.get<Order>(`/api/orders/${enc(orderId)}`),
};

// ---------- display helpers (pure) ----------

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
