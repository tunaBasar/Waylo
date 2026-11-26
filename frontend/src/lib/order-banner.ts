/** The "?created=<id>" banner is only true while that order still waits for payment. */
export function showCreatedBanner(createdId: string | null | undefined, orders: { id: string; status: string }[]): boolean {
  if (!createdId) return false;
  return orders.some(o => o.id === createdId && o.status === 'PENDING');
}

/**
 * Display order of the customer's orders (F76): orders waiting for payment first (the 15-minute window), then the
 * rest; newest first inside each group. Does not mutate the input.
 */
export function sortOrdersForDisplay<T extends { status: string; createdAt: string }>(orders: T[]): T[] {
  const time = (o: T) => { const t = Date.parse(o.createdAt); return Number.isNaN(t) ? 0 : t; };
  return [...orders].sort((a, b) => {
    const pa = a.status === 'PENDING' ? 0 : 1;
    const pb = b.status === 'PENDING' ? 0 : 1;
    return pa !== pb ? pa - pb : time(b) - time(a);
  });
}
