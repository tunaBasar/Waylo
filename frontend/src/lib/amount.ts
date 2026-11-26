export const AMOUNT_ERROR = 'Gecersiz tutar. Ornek: 2.400 veya 2400,50';

/**
 * Parses a money amount typed the Turkish way. A comma is always the decimal separator, dots followed by exactly
 * three digits are thousands separators, a single dot followed by 1-2 digits is a decimal point.
 * Returns null for anything else (mixed "2,400.50", "2.40.0", negatives, more than 2 decimals, zero, empty).
 */
export function parseTrAmount(raw: string | null | undefined): number | null {
  if (raw == null) return null;
  const t = raw.trim().replace(/\s+/g, '');
  if (!t) return null;
  let norm: string | null = null;
  if (t.includes(',')) {
    const parts = t.split(',');
    if (parts.length !== 2) return null;
    const [int, dec] = parts;
    if (!/^\d{1,2}$/.test(dec)) return null;
    if (/^\d+$/.test(int)) norm = `${int}.${dec}`;
    else if (/^\d{1,3}(\.\d{3})+$/.test(int)) norm = `${int.replace(/\./g, '')}.${dec}`;
  } else if (t.includes('.')) {
    if (/^\d{1,3}(\.\d{3})+$/.test(t)) norm = t.replace(/\./g, '');
    else if (/^\d+\.\d{1,2}$/.test(t)) norm = t;
  } else if (/^\d+$/.test(t)) {
    norm = t;
  }
  if (norm == null) return null;
  const n = Number(norm);
  return Number.isFinite(n) && n > 0 && n < 1e10 ? n : null;
}
