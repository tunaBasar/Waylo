/**
 * Client-side cart ("Sepet"). Items are the request fields of POST /api/orders/package (PACKAGE-ORDER-CONTRACT.md).
 * Prices shown are the offer prices the customer saw, the server re-prices from the holds at checkout.
 * Pure helpers on top, storage wrappers at the bottom so tests can import the pure part.
 */
export type CartVertical = 'HOTEL' | 'CAR' | 'FLIGHT' | 'BUS';

export interface CartItem {
  /** local id, not sent to the server */
  id: string;
  vertical: CartVertical;
  label: string;
  amount: number | null;
  currency: string | null;
  /** ISO time after which the offer/quote is no longer valid, when known */
  expiresAt?: string | null;
  /** fields of the single-order endpoint of the vertical, flat */
  request: Record<string, unknown>;
}

export const CART_KEY = 'waylo_cart';
export const CART_MAX_ITEMS = 10;

export function addItem(items: CartItem[], item: CartItem): CartItem[] {
  if (items.length >= CART_MAX_ITEMS) return items;
  const key = JSON.stringify([item.vertical, item.request]);
  if (items.some(i => JSON.stringify([i.vertical, i.request]) === key)) return items;
  return [...items, item];
}

export function removeItem(items: CartItem[], id: string): CartItem[] {
  return items.filter(i => i.id !== id);
}

/** Sums per currency; the server rejects mixed currencies, the cart page warns instead. */
export function totalsByCurrency(items: CartItem[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const i of items) {
    if (i.amount == null || !i.currency) continue;
    out[i.currency] = (out[i.currency] ?? 0) + i.amount;
  }
  return out;
}

export function hasMixedCurrency(items: CartItem[]): boolean {
  return new Set(items.map(i => i.currency).filter(Boolean)).size > 1;
}

export function toPackageRequest(items: CartItem[], promoCode?: string) {
  return {
    items: items.map(i => ({ vertical: i.vertical, ...i.request })),
    ...(promoCode && promoCode.trim() ? { promoCode: promoCode.trim() } : {}),
  };
}

export function parseCart(raw: string | null): CartItem[] {
  if (!raw) return [];
  try {
    const v: unknown = JSON.parse(raw);
    if (!Array.isArray(v)) return [];
    return v.filter((i): i is CartItem => !!i && typeof i === 'object' && typeof (i as CartItem).id === 'string'
      && typeof (i as CartItem).vertical === 'string' && typeof (i as CartItem).request === 'object');
  } catch {
    return [];
  }
}

export function itemIndexMessage(index: unknown, items: CartItem[]): string | null {
  if (typeof index !== 'number' || index < 0 || index >= items.length) return null;
  return `${index + 1}. kalem (${items[index].label})`;
}

export function loadCart(): CartItem[] {
  if (typeof window === 'undefined') return [];
  return parseCart(window.localStorage.getItem(CART_KEY));
}

export function saveCart(items: CartItem[]): void {
  window.localStorage.setItem(CART_KEY, JSON.stringify(items));
  window.dispatchEvent(new Event('waylo-cart'));
}

export function addToCart(item: Omit<CartItem, 'id'>): boolean {
  const before = loadCart();
  const next = addItem(before, { ...item, id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}` });
  saveCart(next);
  return next.length > before.length;
}
