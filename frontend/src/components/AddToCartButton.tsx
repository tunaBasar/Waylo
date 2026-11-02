'use client';

import { useState } from 'react';
import Link from 'next/link';
import { addToCart, type CartItem } from '@/lib/cart';

/** "Sepete ekle" next to a direct buy button. `build` is called on click so it always sees the current form values. */
export function AddToCartButton({ build, disabled }: { build: () => Omit<CartItem, 'id'> | null; disabled?: boolean }) {
  const [state, setState] = useState<'idle' | 'added' | 'dup'>('idle');
  return (
    <span className="inline-flex items-center gap-2">
      <button type="button" disabled={disabled}
        onClick={() => {
          const item = build();
          if (!item) return;
          setState(addToCart(item) ? 'added' : 'dup');
        }}
        className="rounded-lg border border-outline-variant px-4 py-2 text-sm font-medium text-primary disabled:opacity-50">
        Sepete ekle
      </button>
      {state !== 'idle' && (
        <Link href="/cart" className="text-xs text-primary underline">
          {state === 'added' ? 'Sepete eklendi, sepeti ac' : 'Zaten sepette, sepeti ac'}
        </Link>
      )}
    </span>
  );
}
