/** One-line readiness summary above "Yayin icin gonder": counts only, from data the page already loads. */
export function readinessLines(
  roomTypes: number | null,
  ratePlans: number | null,
  photos: Array<{ reviewStatus: string; deleted?: boolean; deletedAt?: string | null }>,
): string[] {
  const live = photos.filter(p => !p.deleted && !p.deletedAt);
  const pending = live.filter(p => p.reviewStatus === 'PENDING').length;
  const lines: string[] = [];
  if (roomTypes != null) lines.push(`Oda tipi: ${roomTypes}`);
  if (ratePlans != null) lines.push(`Fiyat plani: ${ratePlans}`);
  lines.push(`Fotograf: ${live.length} (incelemede ${pending})`);
  return lines;
}
