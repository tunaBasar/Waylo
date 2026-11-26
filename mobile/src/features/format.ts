export const money = (v: number, currency: string) => {
  try { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(v); }
  catch { return `${v.toFixed(2)} ${currency}`; }
};

/** Offsetless local datetime "2026-11-06T09:00:00" -> "06.11.2026 09:00" (no tz conversion). */
export function fmtLocal(iso: string | null | undefined): string {
  if (!iso) return '';
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  return m ? `${m[3]}.${m[2]}.${m[1]} ${m[4]}:${m[5]}` : iso;
}

export const ORDER_STATUS_TR: Record<string, string> = {
  PENDING: 'Odeme bekleniyor', CONFIRMED: 'Onaylandi', CANCELLED: 'Iptal edildi', EXPIRED: 'Süresi doldu',
};

export const ORDER_EXPIRED_NOTE = 'Ödeme süresi içinde tamamlanmadığı için rezervasyon tutmaları sona erdi. Ödeme yapılamaz, lütfen yeniden arama yapın.';

/** Known statuses get a Turkish label, unknown future ones show raw in a neutral badge. */
export function orderStatusInfo(status: string): { label: string; known: boolean; note: string | null } {
  const label = ORDER_STATUS_TR[status];
  return { label: label ?? status, known: label !== undefined, note: status === 'EXPIRED' ? ORDER_EXPIRED_NOTE : null };
}

const pad2 = (n: number) => String(n).padStart(2, '0');

/** "2026-12-01" -> "01.12.2026". */
function isoToDmy(iso: string): string {
  return `${iso.slice(8, 10)}.${iso.slice(5, 7)}.${iso.slice(0, 4)}`;
}

function shiftIso(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d - days)).toISOString().slice(0, 10);
}

function localTodayIso(now: Date): string {
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
}

/** Instant -> "dd.MM.yyyy HH:mm" in Europe/Istanbul (fixed UTC+3, no DST). */
function fmtIstanbul(instant: string): string {
  const t = new Date(new Date(instant).getTime() + 3 * 3_600_000);
  return `${pad2(t.getUTCDate())}.${pad2(t.getUTCMonth() + 1)}.${t.getUTCFullYear()} ${pad2(t.getUTCHours())}:${pad2(t.getUTCMinutes())}`;
}

/** "2026-12-05" -> day before as "04.12.2026". */
function prevDayDmy(iso: string): string {
  return isoToDmy(shiftIso(iso, 1));
}

/**
 * Hotel offer / quote (before booking). Free until D 00:00 Europe/Istanbul where D = checkIn - N days.
 * Never claims free cancellation without a date.
 */
export function offerPolicyText(
  policy: string | null | undefined,
  daysBefore: number | null | undefined,
  checkIn: string | null | undefined,
  now: Date = new Date(),
): string {
  if (policy === 'NON_REFUNDABLE') return 'Iade edilmez';
  if (daysBefore == null || !checkIn) return 'Ucretsiz iptal kosulu icin giris tarihi secin';
  const deadline = shiftIso(checkIn, daysBefore);
  if (deadline <= localTodayIso(now)) return 'Iade edilmez (ucretsiz iptal suresi gecti)';
  return `${prevDayDmy(deadline)} gun sonuna kadar ucretsiz iptal`;
}

/** Order card (after booking), from the stored snapshot. No Turkish suffix is ever appended to a time. */
export function orderPolicyText(
  policy: string | null | undefined,
  hours: number | null | undefined,
  freeCancellationUntil: string | null | undefined,
  now: Date = new Date(),
): string {
  if (policy === 'NON_REFUNDABLE') return 'Iade edilmez';
  if (freeCancellationUntil) {
    if (new Date(freeCancellationUntil).getTime() <= now.getTime()) return 'Iade edilmez (ucretsiz iptal suresi gecti)';
    const stamp = fmtIstanbul(freeCancellationUntil); // "dd.MM.yyyy HH:mm"
    if (stamp.endsWith(' 00:00')) {
      const [d, m, y] = stamp.slice(0, 10).split('.');
      return `${prevDayDmy(`${y}-${m}-${d}`)} gun sonuna kadar ucretsiz iptal`;
    }
    return `Ucretsiz iptal son an: ${stamp}`;
  }
  if (policy === 'FREE_UNTIL' && hours != null) return `Baslangica ${hours} saat kalaya kadar ucretsiz iptal`;
  return 'Hizmet baslangicina kadar ucretsiz iptal';
}

export function refundText(amount: number, currency: string): string {
  return amount > 0 ? `${money(amount, currency)} iade edilecek` : 'Iade yapilmayacak';
}
