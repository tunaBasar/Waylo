/** Voucher QR payload helpers. Import-free on purpose so it can be unit tested with node --test. */

export const VOUCHER_PREFIX = 'WAYLO1';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const CODE_RE = /^[A-Za-z0-9_-]{22}$/;

export interface ParsedVoucherPayload {
  voucherId: string;
  code: string;
}

/** Parses `WAYLO1:<voucherId>:<code>`. Returns null for anything else (surrounding whitespace is ignored). */
export function parseVoucherPayload(raw: string | null | undefined): ParsedVoucherPayload | null {
  if (typeof raw !== 'string') return null;
  const parts = raw.trim().split(':');
  if (parts.length !== 3 || parts[0] !== VOUCHER_PREFIX) return null;
  if (!UUID_RE.test(parts[1]) || !CODE_RE.test(parts[2])) return null;
  return { voucherId: parts[1].toLowerCase(), code: parts[2] };
}

const REDEEM_ERRORS: Record<string, string> = {
  INVALID_VOUCHER: 'Gecersiz kupon.',
  WRONG_SUPPLIER: 'Bu kupon bu isletmeye ait degil.',
  NOT_YET_VALID: 'Kupon henuz gecerli degil.',
  EXPIRED: 'Kuponun suresi dolmus.',
  VOID: 'Kupon iptal edilmis.',
  ALREADY_REDEEMED: 'Bu kupon daha once kullanilmis.',
  TOO_MANY_ATTEMPTS: 'Cok fazla basarisiz deneme. Bir dakika sonra tekrar deneyin.',
  FORBIDDEN: 'Bu isletme icin kupon okutma yetkiniz yok.',
  PAYMENT_SERVICE_UNAVAILABLE: 'Odeme servisi su an kullanilamiyor, kupon kullanilmadi. Tekrar deneyin.',
};

/** "2026-11-27T21:00:00Z" -> "28.11.2026 00:00" (Europe/Istanbul, fixed UTC+3). */
export function fmtIstanbul(iso: string): string | null {
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return null;
  const d = new Date(t + 3 * 3_600_000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getUTCDate())}.${p(d.getUTCMonth() + 1)}.${d.getUTCFullYear()} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
}

export function redeemErrorMessage(code: string | null | undefined, fallback = 'Kupon okutulamadi.', body?: { validFrom?: unknown } | null): string {
  if (code === 'NOT_YET_VALID' && typeof body?.validFrom === 'string') {
    const when = fmtIstanbul(body.validFrom);
    if (when) return `Bu kupon ${when} itibariyla gecerli olur.`;
  }
  return (code && REDEEM_ERRORS[code]) || fallback;
}
