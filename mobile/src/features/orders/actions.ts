export interface CancelOrderLike { status: string; items?: { id: string; cancelOutcome?: string | null }[] }
export interface CancelVoucherLike { orderItemId?: string; status: string }

const USED = ['REDEEMED', 'NO_SHOW'];

/** False for a cancelled order or when every item is cancelled or used. */
export function canOfferCancel(order: CancelOrderLike, vouchers: CancelVoucherLike[] | null | undefined): boolean {
  if (order.status === 'CANCELLED') return false;
  const items = order.items ?? [];
  if (items.length === 0) return true;
  return !items.every((i) => !!i.cancelOutcome || (vouchers ?? []).some((v) => v.orderItemId === i.id && USED.includes(v.status)));
}
