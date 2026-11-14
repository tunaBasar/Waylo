import { api, type Terminal, type TripOffer } from '@/api';

/** Read-only: bus purchase is not offered in the app (sales may be closed). */
export const busApi = {
  salesStatus: () => api.get<{ enabled: boolean }>('/api/bus-sales-status'),
  terminals: () => api.get<Terminal[]>('/api/bus-terminals'),
  search: (fromCity: string, toCity: string, date: string) =>
    api.get<TripOffer[]>(`/api/bus-trips?${new URLSearchParams({ fromCity, toCity, date }).toString()}`),
};
