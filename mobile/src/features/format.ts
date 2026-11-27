export const VERTICAL_TR: Record<string, string> = { HOTEL: 'Otel', CAR: 'Araç', FLIGHT: 'Uçuş', BUS: 'Otobüs' };
export const verticalText = (v: string) => VERTICAL_TR[v] ?? v;

export const money = (v: number, currency: string) => {
  try { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(v); }
  catch { return `${v.toFixed(2)} ${currency}`; }
};

/**
 * Date-time for display in Europe/Istanbul as "dd.MM.yyyy HH:mm".
 * - Instants with "Z" or an offset (flights, vouchers) are converted to Istanbul time (UTC+3, no DST since 2016).
 * - Offsetless local date-times ("2026-11-06T09:00:00", car/bus local times) are shown as is.
 * Walkthrough 2026-11-26: slicing an instant showed an 11:45 departure as 08:45.
 */
export function fmtLocal(iso: string | null | undefined): string {
  if (!iso) return '';
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$/);
  if (!m) return iso;
  if (!m[6]) return `${m[3]}.${m[2]}.${m[1]} ${m[4]}:${m[5]}`;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return iso;
  const d = new Date(t + 3 * 3_600_000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getUTCDate())}.${p(d.getUTCMonth() + 1)}.${d.getUTCFullYear()} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
}

/** Same labels as the web order page. */
export const VOUCHER_STATUS_TR: Record<string, string> = {
  ACTIVE: 'Geçerli', REDEEMED: 'Kullanıldı', VOID: 'İptal edildi', NO_SHOW: 'Gelinmedi',
};
export const voucherStatusText = (s: string) => VOUCHER_STATUS_TR[s] ?? s;

export const ORDER_STATUS_TR: Record<string, string> = {
  PENDING: 'Ödeme bekleniyor', CONFIRMED: 'Onaylandı', CANCELLED: 'İptal edildi', EXPIRED: 'Süresi doldu',
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
  if (policy === 'NON_REFUNDABLE') return 'İade edilmez';
  if (daysBefore == null || !checkIn) return 'Ücretsiz iptal koşulu için giriş tarihi seçin';
  const deadline = shiftIso(checkIn, daysBefore);
  if (deadline <= localTodayIso(now)) return 'İade edilmez (ücretsiz iptal süresi geçti)';
  return `${prevDayDmy(deadline)} gün sonuna kadar ücretsiz iptal`;
}

/** Order card (after booking), from the stored snapshot. No Turkish suffix is ever appended to a time. */
export function orderPolicyText(
  policy: string | null | undefined,
  hours: number | null | undefined,
  freeCancellationUntil: string | null | undefined,
  now: Date = new Date(),
): string {
  if (policy === 'NON_REFUNDABLE') return 'İade edilmez';
  if (freeCancellationUntil) {
    if (new Date(freeCancellationUntil).getTime() <= now.getTime()) return 'İade edilmez (ücretsiz iptal süresi geçti)';
    const stamp = fmtIstanbul(freeCancellationUntil); // "dd.MM.yyyy HH:mm"
    if (stamp.endsWith(' 00:00')) {
      const [d, m, y] = stamp.slice(0, 10).split('.');
      return `${prevDayDmy(`${y}-${m}-${d}`)} gün sonuna kadar ücretsiz iptal`;
    }
    return `Ücretsiz iptal son an: ${stamp}`;
  }
  if (policy === 'FREE_UNTIL' && hours != null) return `Başlangıca ${hours} saat kalaya kadar ücretsiz iptal`;
  return 'Hizmet başlangıcına kadar ücretsiz iptal';
}

export function refundText(amount: number, currency: string): string {
  return amount > 0 ? `${money(amount, currency)} iade edilecek` : 'İade yapılmayacak';
}

/** Display only: old hotel orders stored "2026-11-27-2026-11-29, 2 gece, 1 oda". Shows the leading ISO range as dd.MM.yyyy-dd.MM.yyyy. */
export function displayItemText(text: string | null | undefined): string {
  if (!text) return '';
  return text.replace(/^(\d{4})-(\d{2})-(\d{2})-(\d{4})-(\d{2})-(\d{2})(?=\D|$)/, '$3.$2.$1-$6.$5.$4');
}
