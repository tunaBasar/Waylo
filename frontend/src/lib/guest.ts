/** Hotel lead guest line: "Misafir: Ali Yilmaz · 2 yetiskin, 1 cocuk". Parts that are absent are left out. */
export function guestLine(
  firstName: string | null | undefined,
  lastName: string | null | undefined,
  adults: number | null | undefined,
  childrenCount: number | null | undefined,
): string {
  const name = [firstName, lastName].filter(Boolean).join(' ');
  const counts: string[] = [];
  if (adults != null && adults > 0) counts.push(`${adults} yetiskin`);
  if (childrenCount != null && childrenCount > 0) counts.push(`${childrenCount} cocuk`);
  const parts = [name, counts.join(', ')].filter(Boolean);
  return parts.length ? `Misafir: ${parts.join(' · ')}` : '';
}

/** Trimmed optional guest name for the order request; empty becomes undefined so the field is omitted. */
export function guestNameOrUndefined(v: string | null | undefined): string | undefined {
  const t = (v ?? '').trim();
  return t ? t : undefined;
}
