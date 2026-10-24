/** Typed wrappers for the platform-admin endpoints (all ADMIN-only on the backend). */
import { api } from './api';
import type { Airport, Flight, Vehicle } from './types';

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

export const adminApi = {
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
