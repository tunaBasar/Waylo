import { api, type Branch, type CarOffer } from '@/api';

export const carsApi = {
  branches: (city?: string) => api.get<Branch[]>(`/api/car-branches${city ? `?city=${encodeURIComponent(city)}` : ''}`),
  offers: (p: { pickupBranchId: string; returnBranchId: string; pickupAt: string; returnAt: string; driverAge: number }) =>
    api.get<CarOffer[]>(`/api/car-offers?${new URLSearchParams({ ...p, driverAge: String(p.driverAge) }).toString()}`),
};
