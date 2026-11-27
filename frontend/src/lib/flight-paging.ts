export const FLIGHT_PAGE_SIZE = 50;
/** The flight list endpoint returns at most this many rows per page (larger sizes are clamped by the backend). */
export const FLIGHT_MAX_PAGE_SIZE = 200;

/** "Toplam N ucus" plus the visible range, e.g. "Toplam 510 ucus (51-100)". */
export function flightPagingText(total: number, page: number, size: number, shown: number): string {
  if (total <= 0 || shown <= 0) return `Toplam ${total} ucus`;
  const first = page * size + 1;
  return `Toplam ${total} ucus (${first}-${first + shown - 1})`;
}

export function hasNextFlightPage(total: number, page: number, size: number): boolean {
  return (page + 1) * size < total;
}

/** Keeps only flights whose number contains the (case-insensitive) query. */
export function filterFlightsByNumber<T extends { flightNumber: string }>(flights: T[], query: string): T[] {
  const q = query.trim().toUpperCase();
  return q ? flights.filter(f => f.flightNumber.toUpperCase().includes(q)) : flights;
}

/**
 * Seat picker hint when the server found more matches than one page holds, so the admin knows to narrow the search
 * instead of assuming the flight does not exist. Null when everything is listed.
 */
export function flightPickerHint(total: number, shown: number): string | null {
  if (total <= shown) return null;
  return `${total} ucustan ilk ${shown} tanesi listelendi. Ucus no yazarak daraltin.`;
}

/** Problem with an optional departure date range (yyyy-MM-dd from date inputs), or null when usable. */
export function flightDateRangeProblem(from: string, to: string): string | null {
  if (from && to && to < from) return 'Bitis tarihi baslangictan once olamaz.';
  return null;
}
