import { api, type Availability, type Hotel, type Quote, type StayQuery } from '@/api';

export function stayQueryString(q: StayQuery): string {
  const p = new URLSearchParams({ checkIn: q.checkIn, checkOut: q.checkOut, rooms: String(q.rooms), adults: String(q.adults) });
  q.childrenAges.forEach((a) => p.append('childrenAges', String(a)));
  return p.toString();
}

export const hotelsApi = {
  search: (q: StayQuery, city?: string) =>
    api.get<Hotel[]>(`/api/hotels/search?${stayQueryString(q)}${city ? `&city=${encodeURIComponent(city)}` : ''}`),
  get: (id: string) => api.get<Hotel>(`/api/hotels/${encodeURIComponent(id)}`),
  availability: (id: string, q: StayQuery) =>
    api.get<Availability>(`/api/hotels/${encodeURIComponent(id)}/availability?${stayQueryString(q)}`),
  createQuote: (body: { hotelId: string; roomTypeId: string; ratePlanId: string } & StayQuery) => api.post<Quote>('/api/quotes', body),
  getQuote: (id: string) => api.get<Quote>(`/api/quotes/${encodeURIComponent(id)}`),
  orderFromQuote: (quoteId: string, promoCode?: string) =>
    api.post<{ id: string }>('/api/orders/hotel', { quoteId, notes: null, ...(promoCode ? { promoCode } : {}) }),
};
