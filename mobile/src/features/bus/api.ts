import { api, type SeatMap, type Terminal, type TripOffer } from '@/api';

export const busApi = {
  salesStatus: () => api.get<{ enabled: boolean }>('/api/bus-sales-status'),
  terminals: () => api.get<Terminal[]>('/api/bus-terminals'),
  search: (fromCity: string, toCity: string, date: string) =>
    api.get<TripOffer[]>(`/api/bus-trips?${new URLSearchParams({ fromCity, toCity, date }).toString()}`),
  seats: (tripId: string, fromSeq: number, toSeq: number) =>
    api.get<SeatMap>(`/api/bus-trips/${encodeURIComponent(tripId)}/seats?fromSeq=${fromSeq}&toSeq=${toSeq}`),
};
