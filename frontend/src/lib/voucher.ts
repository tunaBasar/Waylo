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

export function redeemErrorMessage(code: string | null | undefined, fallback = 'Kupon okutulamadi.'): string {
  return (code && REDEEM_ERRORS[code]) || fallback;
}
