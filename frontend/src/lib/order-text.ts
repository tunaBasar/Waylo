/** Display only: old hotel orders stored "2026-11-27-2026-11-29, 2 gece, 1 oda". Shows the leading ISO range as dd.MM.yyyy-dd.MM.yyyy. */
export function displayItemText(text: string | null | undefined): string {
  if (!text) return '';
  return text.replace(/^(\d{4})-(\d{2})-(\d{2})-(\d{4})-(\d{2})-(\d{2})(?=\D|$)/, '$3.$2.$1-$6.$5.$4');
}
