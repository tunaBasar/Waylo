/** Cover photo url of a search result, or null when the hotel has no approved photo. */
export function hotelCoverUrl(
  apiBase: string,
  hotel: { id: string; coverMediaId?: string | null },
): string | null {
  if (!hotel.coverMediaId) return null;
  return `${apiBase.replace(/\/+$/, '')}/api/hotels/${encodeURIComponent(hotel.id)}/media/${encodeURIComponent(hotel.coverMediaId)}`;
}
