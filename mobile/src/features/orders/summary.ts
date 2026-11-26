import type { Order } from '@/api';
import { verticalText } from '@/features/format';

/** Order list line: "Otel · <description>" of the first item, plus "N kalem" when there are several. */
export function orderSummary(o: Pick<Order, 'items'>): { title: string | null; count: string | null } {
  const items = o.items ?? [];
  if (items.length === 0) return { title: null, count: null };
  const first = items[0];
  const title = first.description ? `${verticalText(first.vertical)} · ${first.description}` : verticalText(first.vertical);
  return { title, count: items.length > 1 ? `${items.length} kalem` : null };
}
