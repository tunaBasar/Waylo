import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { loadCart, saveCart } from './storage';
import type { CartItem } from '@/api';

const MAX_ITEMS = 10;

interface CartState {
  items: CartItem[];
  /** false when duplicate or cart full */
  add(item: Omit<CartItem, 'id'>): boolean;
  remove(id: string): void;
  clear(): void;
}

const Ctx = createContext<CartState | null>(null);

/** Cart persisted in AsyncStorage (never tokens). Prices shown are the offers the user saw, the server re-prices at checkout. */
export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>([]);
  const hydrated = useRef(false);
  useEffect(() => {
    let alive = true;
    loadCart().then((saved) => {
      if (!alive) return;
      // merge: anything added before hydration finished wins
      setItems((cur) => (cur.length ? [...saved, ...cur].slice(0, MAX_ITEMS) : saved));
      hydrated.current = true;
    });
    return () => { alive = false; };
  }, []);
  useEffect(() => { if (hydrated.current) void saveCart(items); }, [items]);
  const add = useCallback((item: Omit<CartItem, 'id'>) => {
    let ok = false;
    setItems((cur) => {
      const key = JSON.stringify([item.vertical, item.request]);
      if (cur.length >= MAX_ITEMS || cur.some((i) => JSON.stringify([i.vertical, i.request]) === key)) return cur;
      ok = true;
      return [...cur, { ...item, id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }];
    });
    return ok;
  }, []);
  const remove = useCallback((id: string) => setItems((c) => c.filter((i) => i.id !== id)), []);
  const clear = useCallback(() => setItems([]), []);
  const value = useMemo(() => ({ items, add, remove, clear }), [items, add, remove, clear]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useCart(): CartState {
  const v = useContext(Ctx);
  if (!v) throw new Error('CartProvider missing');
  return v;
}

/** Wire shape of POST /api/orders/package. */
export function toPackageRequest(items: CartItem[], promoCode?: string) {
  return {
    items: items.map((i) => ({ vertical: i.vertical, ...i.request })),
    ...(promoCode && promoCode.trim() ? { promoCode: promoCode.trim().toUpperCase() } : {}),
  };
}
