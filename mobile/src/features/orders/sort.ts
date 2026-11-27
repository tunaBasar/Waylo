/** Unpaid (PENDING) first, newest first inside each group. Does not mutate the input. */
export function sortOrdersForDisplay<T extends { status: string; createdAt: string }>(orders: T[]): T[] {
  const time = (o: T) => { const t = Date.parse(o.createdAt); return Number.isNaN(t) ? 0 : t; };
  return [...orders].sort((a, b) => {
    const pa = a.status === 'PENDING' ? 0 : 1;
    const pb = b.status === 'PENDING' ? 0 : 1;
    return pa !== pb ? pa - pb : time(b) - time(a);
  });
}
