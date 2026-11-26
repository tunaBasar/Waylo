/** Which order actions to offer. Import-free so it can be unit tested with node --test. */

export interface CancelOrderLike {
  status: string;
  items?: { id: string; cancelOutcome?: string | null }[];
}

export interface CancelVoucherLike {
  orderItemId: string;
  status: string;
}

const USED_STATUSES = ['REDEEMED', 'NO_SHOW'];

/** False for a cancelled order or when every item is cancelled or used (voucher REDEEMED/NO_SHOW). Partly used orders keep the button, the backend decides per item. */
export function canOfferCancel(order: CancelOrderLike, vouchers: CancelVoucherLike[] | null | undefined): boolean {
  if (order.status === 'CANCELLED') return false;
  const items = order.items ?? [];
  if (items.length === 0) return true;
  const allDone = items.every(i => !!i.cancelOutcome || (vouchers ?? []).some(v => v.orderItemId === i.id && USED_STATUSES.includes(v.status)));
  return !allDone;
}
