/** Local-calendar date helpers. Never go through toISOString (that shifts the day by timezone). */
const p2 = (n: number) => String(n).padStart(2, '0');

export const isoDate = (d: Date) => `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`;
export const isoLocalDateTime = (d: Date) => `${isoDate(d)}T${p2(d.getHours())}:${p2(d.getMinutes())}:00`;

export function addDays(base: Date, n: number): Date {
  const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + n, base.getHours(), base.getMinutes());
  return d;
}

/** "2026-12-01" -> local Date at midnight, null if malformed. */
export function parseIsoDate(s: string): Date | null {
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return null;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return d.getMonth() === Number(m[2]) - 1 && d.getDate() === Number(m[3]) ? d : null;
}

export const fmtDate = (d: Date) => `${p2(d.getDate())}.${p2(d.getMonth() + 1)}.${d.getFullYear()}`;
export const fmtTime = (d: Date) => `${p2(d.getHours())}:${p2(d.getMinutes())}`;

/** Returns an error message or null. Dates are ISO yyyy-mm-dd, `today` injectable for tests. */
export function validateStayDates(checkIn: string, checkOut: string, today: Date = new Date()): string | null {
  const a = parseIsoDate(checkIn);
  const b = parseIsoDate(checkOut);
  if (!a || !b) return 'Gecerli tarih secin.';
  if (a < new Date(today.getFullYear(), today.getMonth(), today.getDate())) return 'Giris tarihi gecmiste olamaz.';
  if (b <= a) return 'Cikis tarihi giris tarihinden sonra olmali.';
  return null;
}

export function validateRentalTimes(pickupAt: Date, returnAt: Date, now: Date = new Date()): string | null {
  if (pickupAt.getTime() < now.getTime()) return 'Alis zamani gecmiste olamaz.';
  if (returnAt.getTime() <= pickupAt.getTime()) return 'Teslim zamani alistan sonra olmali.';
  return null;
}
