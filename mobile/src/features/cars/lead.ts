export const MIN_PICKUP_LEAD_MS = 2 * 3600 * 1000;
export const PICKUP_LEAD_TEXT = 'Gidis zamani en az 2 saat sonrasi olmali. Daha ileri bir saat veya tarih secin.';

/** Null when pickup is at least now + 2 h. */
export function pickupLeadError(pickupAt: Date, nowMs: number): string | null {
  const t = pickupAt.getTime();
  if (Number.isNaN(t)) return 'Gecersiz tarih veya saat formati.';
  return t < nowMs + MIN_PICKUP_LEAD_MS ? PICKUP_LEAD_TEXT : null;
}

/** Default pickup: the usual 7 days ahead at 10:00, never earlier than now + 2 h. */
export function defaultPickup(now: Date): Date {
  const d = new Date(now.getTime()); d.setDate(d.getDate() + 7); d.setHours(10, 0, 0, 0);
  const min = new Date(now.getTime() + MIN_PICKUP_LEAD_MS);
  return d < min ? min : d;
}

export function carErrorMessage(err: unknown): string {
  const e = err as { code?: string | null; message?: string } | null;
  if (e?.code === 'INVALID_TIME') return PICKUP_LEAD_TEXT;
  return e?.message || 'Teklifler alinamadi';
}
