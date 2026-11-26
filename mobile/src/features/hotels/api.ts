import { api, type Availability, type Hotel, type Quote, type StayQuery } from '@/api';

export function stayQueryString(q: StayQuery): string {
  const p = new URLSearchParams({ checkIn: q.checkIn, checkOut: q.checkOut, rooms: String(q.rooms), adults: String(q.adults) });
  q.childrenAges.forEach((a) => p.append('childrenAges', String(a)));
  return p.toString();
}

export type HotelResult = { kind: 'ok'; hotel: Hotel } | { kind: 'notPublic' };

/** 404 on the hotel detail means the hotel is not published (or gone): a state, not an error. */
export async function getHotelOrNotPublic(id: string): Promise<HotelResult> {
  try { return { kind: 'ok', hotel: await hotelsApi.get(id) }; }
  catch (e) {
    if (typeof e === 'object' && e !== null && (e as { status?: number }).status === 404) return { kind: 'notPublic' };
    throw e;
  }
}

export type HotelPhoto = { id: string; caption: string | null; sortOrder: number; width: number; height: number };

export function photoUrl(baseUrl: string, hotelId: string, mediaId: string): string {
  return `${baseUrl.replace(/\/+$/, '')}/api/hotels/${encodeURIComponent(hotelId)}/media/${encodeURIComponent(mediaId)}`;
}

/** Cover photo url of a search result, or null when the hotel has no approved photo. */
export function hotelCoverUrl(baseUrl: string, hotel: { id: string; coverMediaId?: string | null }): string | null {
  return hotel.coverMediaId ? photoUrl(baseUrl, hotel.id, hotel.coverMediaId) : null;
}

/** width / height, falling back to 4:3 when either is missing or not positive. */
export function photoAspect(width: number, height: number): number {
  return width > 0 && height > 0 ? width / height : 4 / 3;
}

export const hotelsApi = {
  photos: (id: string) => api.get<HotelPhoto[]>(`/api/hotels/${encodeURIComponent(id)}/media`),
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
