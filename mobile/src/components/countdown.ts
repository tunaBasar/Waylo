/** Remaining whole seconds until an ISO instant (rounded up so 0:00 only shows once truly expired). */
export function secondsLeft(until: string, now: number): number | null {
  const t = Date.parse(until);
  if (Number.isNaN(t)) return null;
  const ms = t - now;
  if (ms <= 0) return 0;
  return Math.ceil(ms / 1000);
}

/** mm:ss (minutes may exceed 59). Null for expired or invalid input. */
export function formatCountdown(until: string, now: number): string | null {
  const s = secondsLeft(until, now);
  if (s == null || s <= 0) return null;
  const m = Math.floor(s / 60);
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}
