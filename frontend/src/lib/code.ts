/** Room type / rate plan code as the server stores it: upper case letters, digits and underscore only. */
export function normalizeCode(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9_]/g, '');
}

/** Live hint shown under a code input when typing changes the code ("DBL-TAS" -> "DBLTAS"), else null. */
export function codeHint(raw: string): string | null {
  const n = normalizeCode(raw.trim());
  return n !== raw.trim().toUpperCase() ? `Kaydedilecek kod: ${n || '(bos)'}` : null;
}
