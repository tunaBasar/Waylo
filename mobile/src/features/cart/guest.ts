export function isValidGuestName(v: string | null | undefined): boolean {
  const t = (v ?? '').trim();
  return t.length > 0 && t.length <= 80 && /^[\p{L} '\-]+$/u.test(t);
}
