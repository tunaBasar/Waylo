import { api, type FlightOffer } from '@/api';

export const flightsApi = {
  search: (p: { from: string; to: string; date: string }) =>
    api.get<FlightOffer[]>(`/api/flight-offers?${new URLSearchParams({ ...p, adults: '1' }).toString()}`),
  offer: (id: string) => api.get<FlightOffer>(`/api/flight-offers/${encodeURIComponent(id)}`),
};
