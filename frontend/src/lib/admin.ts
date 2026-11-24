/** Typed wrappers for the platform-admin endpoints (all ADMIN-only on the backend). */
import { api } from './api';
import type { Airport, Flight, Vehicle } from './types';
import type { Amount, BankAccountStatus, PartnerPayout, PartnerPayoutBalance, PayoutStatus, HotelContent, PublicationRequestKind, PublicationRequestStatus, PublicationStatus } from './partner';

const enc = encodeURIComponent;

export type OrgStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
export interface AdminOrganization { id: string; name: string; status: OrgStatus }
export interface AdminUser { id: string; email: string; fullName: string; role: 'USER' | 'ADMIN'; verified: boolean }

export interface AirportRequest {
  iataCode: string; name: string; city: string; country: string; timezone: string;
  latitude: number | null; longitude: number | null;
}
export interface FlightRequest {
  flightNumber: string; departureAirportCode: string; arrivalAirportCode: string;
  airline: string; departureTime: string; arrivalTime: string;
}
export interface VehicleRequest {
  plateNumber: string; brand: string; model: string; year: number; vehicleType: Vehicle['vehicleType'];
  color: string | null; seatCount: number; dailyRate: number; currency: string; location: string; mileage: number | null;
}
export interface BulkSeatRequest {
  flightNumber: string; seatType: SeatType; price: number; fromRow: number; toRow: number; letters: string;
}
export type SeatType = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';
export const SEAT_TYPES: SeatType[] = ['ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST'];
export type VehicleStatusTarget = 'AVAILABLE' | 'MAINTENANCE';

export interface AdminOrderItem {
  id: string; vertical: 'HOTEL' | 'CAR' | 'FLIGHT' | 'BUS'; description: string; netAmount: number;
  promoCode: string | null; cancelOutcome: string | null; refundAmount: number | null;
}
export interface AdminOrder {
  id: string; customerId: string; status: string; paymentState: 'UNPAID' | 'PAID' | 'PARTIALLY_REFUNDED' | 'REFUNDED';
  currency: string; totalAmount: number; createdAt: string; items: AdminOrderItem[];
}
export interface AdminOrderPage { items: AdminOrder[]; page: number; size: number; total: number }
export interface AdminOrderFilter { status?: string; vertical?: string; from?: string; to?: string; page?: number; size?: number }

export function adminOrdersQuery(f: AdminOrderFilter): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(f)) if (v !== undefined && v !== '') q.set(k, String(v));
  const s = q.toString();
  return s ? `?${s}` : '';
}

// ---------- hotel publication review (contract 4.3) ----------

export interface HotelPublicationItem {
  requestId: string;
  hotelId: string;
  hotelName: string;
  organizationId: string;
  kind: PublicationRequestKind;
  status: PublicationRequestStatus;
  submittedBy: string;
  submittedAt: string;
  note: string | null;
  hotelPublicationStatus: PublicationStatus;
  version: number;
}
export interface HotelPublicationPage { items: HotelPublicationItem[]; page: number; size: number; total: number }
export interface PublicationDiffEntry { field: string; from: unknown; to: unknown }
export interface PublicationDecisionRow {
  decision: string; fromStatus: string | null; toStatus: string; actorRole: string; reason: string | null; createdAt: string;
}
export interface HotelPublicationDetail extends HotelPublicationItem {
  hotel: HotelContent & { id?: string; active?: boolean };
  revision: { id?: string; revisionNo?: number; content: HotelContent } | null;
  diff: PublicationDiffEntry[];
  decisions: PublicationDecisionRow[];
}
export type PublicationReviewDecision = 'APPROVE' | 'REJECT' | 'APPROVE_AND_PUBLISH';
export type HotelStatusDecision = 'PUBLISH' | 'SUSPEND' | 'REINSTATE' | 'ARCHIVE';

// ---------- partner payouts (PAYOUT-CONTRACT.md sec. 6.2) ----------

export interface PayoutEventRow {
  fromStatus: PayoutStatus | null; toStatus: PayoutStatus; actorType: 'PARTNER' | 'ADMIN' | 'SYSTEM';
  reason: string | null; reference: string | null; occurredAt: string;
}
/** Admin list rows are PartnerPayout plus whatever the backend adds for the queue (bank status). */
export interface AdminPayout extends PartnerPayout {
  bankAccountStatus?: BankAccountStatus | null;
  requestedByEmail?: string | null;
}
export interface AdminPayoutDetail extends AdminPayout { events?: PayoutEventRow[]; organizationBalance?: AdminPayoutBalance | null }
export interface AdminPayoutPage { items: AdminPayout[]; page: number; size: number; total: number }
export interface AdminBankDetails { iban: string; accountHolderName: string }
export interface AdminBankAccount {
  id: string; organizationId: string; accountHolderName: string; ibanLast4: string; maskedIban: string;
  status: BankAccountStatus; verifiedAt: string | null; rejectReason: string | null; updatedAt: string; version: number;
}
export interface AdminPayoutBalance extends PartnerPayoutBalance { adjustmentsNet: Amount; reconciles: boolean }
export interface PayoutAdjustmentRequest {
  currency: string; amount: number; direction: 'DEBIT' | 'CREDIT'; reason: string; orderItemId?: string; idempotencyKey: string;
}
export type AdminPayoutAction = 'approve' | 'processing' | 'paid' | 'failed' | 'cancel';

export const adminApi = {
  payouts: (f: { status?: string; organizationId?: string; currency?: string; page?: number; size?: number } = {}) => {
    const q = new URLSearchParams();
    for (const [k, v] of Object.entries(f)) if (v !== undefined && v !== '') q.set(k, String(v));
    const s = q.toString();
    return api.get<AdminPayoutPage>(`/api/admin/payouts${s ? `?${s}` : ''}`);
  },
  payout: (id: string) => api.get<AdminPayoutDetail>(`/api/admin/payouts/${enc(id)}`),
  /** Audited full IBAN reveal. The caller must not keep the result beyond the view. */
  payoutBankDetails: (id: string) => api.get<AdminBankDetails>(`/api/admin/payouts/${enc(id)}/bank-details`),
  payoutAction: (id: string, action: AdminPayoutAction, body: { reason?: string; reference?: string; paidAt?: string; expectedVersion?: number }) =>
    api.post<AdminPayout>(`/api/admin/payouts/${enc(id)}/${action}`, body),
  bankAccounts: (status: BankAccountStatus = 'PENDING_VERIFICATION') =>
    api.get<AdminBankAccount[] | { items: AdminBankAccount[] }>(`/api/admin/bank-accounts?status=${status}`),
  verifyBankAccount: (id: string) => api.post<AdminBankAccount>(`/api/admin/bank-accounts/${enc(id)}/verify`, {}),
  rejectBankAccount: (id: string, reason: string) => api.post<AdminBankAccount>(`/api/admin/bank-accounts/${enc(id)}/reject`, { reason }),
  payoutBalance: (orgId: string) => api.get<AdminPayoutBalance>(`/api/admin/organizations/${enc(orgId)}/payout-balance`),
  payoutAdjustment: (orgId: string, body: PayoutAdjustmentRequest) =>
    api.post<unknown>(`/api/admin/organizations/${enc(orgId)}/payout-adjustments`, body),

  hotelPublications: (status: PublicationRequestStatus, page = 0, size = 20) =>
    api.get<HotelPublicationPage>(`/api/admin/hotel-publications?status=${status}&page=${page}&size=${size}`),
  hotelPublication: (requestId: string) =>
    api.get<HotelPublicationDetail>(`/api/admin/hotel-publications/${enc(requestId)}`),
  decideHotelPublication: (requestId: string, body: { decision: PublicationReviewDecision; reason: string | null; expectedVersion: number }) =>
    api.post<HotelPublicationItem>(`/api/admin/hotel-publications/${enc(requestId)}/decisions`, body),
  setHotelPublicationStatus: (hotelId: string, body: { decision: HotelStatusDecision; reason: string | null }) =>
    api.post<{ id: string; publicationStatus: PublicationStatus }>(`/api/admin/hotels/${enc(hotelId)}/publication-status`, body),

  orders: (f: AdminOrderFilter = {}) => api.get<AdminOrderPage>(`/api/admin/orders${adminOrdersQuery(f)}`),
  organizations: (status: OrgStatus) => api.get<AdminOrganization[]>(`/api/admin/organizations?status=${status}`),
  approveOrganization: (id: string) => api.post<null>(`/api/admin/organizations/${enc(id)}/approve`, {}),
  suspendOrganization: (id: string, reason: string) => api.post<null>(`/api/admin/organizations/${enc(id)}/suspend`, { reason }),

  userByEmail: (email: string) => api.get<AdminUser>(`/api/admin/users?email=${enc(email)}`),
  changeRole: (id: string, role: 'USER' | 'ADMIN') => api.post<AdminUser>(`/api/admin/users/${enc(id)}/role`, { role }),

  airports: () => api.get<Airport[]>('/api/airports'),
  createAirport: (b: AirportRequest) => api.post<Airport>('/api/airports', b),
  updateAirport: (id: string, b: AirportRequest) => api.put<Airport>(`/api/airports/${enc(id)}`, b),
  deleteAirport: (id: string) => api.del(`/api/airports/${enc(id)}`),

  flights: () => api.get<Flight[]>('/api/flights'),
  createFlight: (b: FlightRequest) => api.post<Flight>('/api/flights', b),
  updateFlight: (id: string, b: FlightRequest) => api.put<Flight>(`/api/flights/${enc(id)}`, b),
  cancelFlight: (id: string) => api.post<Flight>(`/api/flights/${enc(id)}/cancel`, {}),
  deleteFlight: (id: string) => api.del(`/api/flights/${enc(id)}`),

  vehicles: () => api.get<Vehicle[]>('/api/vehicles'),
  createVehicle: (b: VehicleRequest) => api.post<Vehicle>('/api/vehicles', b),
  updateVehicle: (id: string, b: VehicleRequest) => api.put<Vehicle>(`/api/vehicles/${enc(id)}`, b),
  setVehicleStatus: (id: string, status: VehicleStatusTarget) => api.post<Vehicle>(`/api/vehicles/${enc(id)}/status?status=${status}`, {}),
  activateVehicle: (id: string) => api.post<Vehicle>(`/api/vehicles/${enc(id)}/activate`, {}),
  deactivateVehicle: (id: string) => api.post<Vehicle>(`/api/vehicles/${enc(id)}/deactivate`, {}),
  deleteVehicle: (id: string) => api.del(`/api/vehicles/${enc(id)}`),

  createSeatsBulk: (b: BulkSeatRequest) => api.post<{ flightNumber: string; created: number }>('/api/seat-inventory/seats/bulk', b),
  availableSeatCount: (flightNumber: string) => api.get<number>(`/api/seat-inventory/seats/${enc(flightNumber)}/count`),
};
