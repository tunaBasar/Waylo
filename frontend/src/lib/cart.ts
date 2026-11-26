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

/** Pre-account-scoping key: its content belongs to an unknown user, so it is deleted and never adopted. */
export const LEGACY_CART_KEY = 'waylo_cart';
export const CART_KEY_PREFIX = 'waylo_cart.';

/** Per-user cart key; null without a user (guests have no persisted cart). */
export function cartKey(userId: string | null | undefined): string | null {
  return userId ? `${CART_KEY_PREFIX}${userId}` : null;
}

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

/**
 * Package order body. A hotel item carries the lead guest name (F31: the hotel desk must see who is coming); the cart
 * has no guest form, so it is the logged-in customer's name. Other verticals already carry passengers.
 */
export function toPackageRequest(items: CartItem[], promoCode?: string, guest?: { firstName?: string | null; lastName?: string | null }) {
  const first = guest?.firstName?.trim();
  const last = guest?.lastName?.trim();
  return {
    items: items.map(i => ({
      vertical: i.vertical,
      ...i.request,
      ...(i.vertical === 'HOTEL' && first ? { guestFirstName: first } : {}),
      ...(i.vertical === 'HOTEL' && last ? { guestLastName: last } : {}),
    })),
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

/** Id of the signed-in user, read from the session the auth layer stores. */
export function currentUserId(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    const u = JSON.parse(window.localStorage.getItem('waylo_user') ?? 'null') as { id?: unknown } | null;
    return u && typeof u.id === 'string' ? u.id : null;
  } catch {
    return null;
  }
}

export function loadCart(userId: string | null = currentUserId()): CartItem[] {
  if (typeof window === 'undefined') return [];
  window.localStorage.removeItem(LEGACY_CART_KEY);
  const key = cartKey(userId);
  return key ? parseCart(window.localStorage.getItem(key)) : [];
}

export function saveCart(items: CartItem[], userId: string | null = currentUserId()): void {
  const key = cartKey(userId);
  if (!key) return;
  window.localStorage.setItem(key, JSON.stringify(items));
  window.dispatchEvent(new Event('waylo-cart'));
}

/** Logout: drops the user's persisted cart (call before the session is removed). */
export function clearCart(userId: string | null = currentUserId()): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(LEGACY_CART_KEY);
  const key = cartKey(userId);
  if (key) window.localStorage.removeItem(key);
}

export function addToCart(item: Omit<CartItem, 'id'>): boolean {
  const before = loadCart();
  const next = addItem(before, { ...item, id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}` });
  saveCart(next);
  return next.length > before.length;
}
