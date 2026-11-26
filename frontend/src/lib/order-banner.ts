/** The "?created=<id>" banner is only true while that order still waits for payment. */
export function showCreatedBanner(createdId: string | null | undefined, orders: { id: string; status: string }[]): boolean {
  if (!createdId) return false;
  return orders.some(o => o.id === createdId && o.status === 'PENDING');
}
