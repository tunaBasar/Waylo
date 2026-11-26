import AsyncStorage from '@react-native-async-storage/async-storage';
import type { CartItem } from '@/api';

/** Pre-account-scoping key: its content belongs to an unknown user, so it is deleted and never adopted. */
export const LEGACY_CART_KEY = 'waylo.cart.v1';
/** Cart only. Auth tokens must never be stored here (they live in SecureStore). */
export const cartKey = (userId: string) => `waylo.cart.v1.${userId}`;

async function purgeLegacy(): Promise<void> {
  try { await AsyncStorage.removeItem(LEGACY_CART_KEY); } catch { /* ignore */ }
}

export async function loadCart(userId: string | null | undefined): Promise<CartItem[]> {
  await purgeLegacy();
  if (!userId) return [];
  try {
    const raw = await AsyncStorage.getItem(cartKey(userId));
    if (!raw) return [];
    const v: unknown = JSON.parse(raw);
    if (!Array.isArray(v)) return [];
    return v.filter((i): i is CartItem => !!i && typeof i.id === 'string' && typeof i.vertical === 'string' && typeof i.request === 'object' && i.request !== null);
  } catch { return []; }
}

export async function saveCart(userId: string | null | undefined, items: CartItem[]): Promise<void> {
  if (!userId) return; // guests have no persisted cart
  try {
    if (items.length === 0) await AsyncStorage.removeItem(cartKey(userId));
    else await AsyncStorage.setItem(cartKey(userId), JSON.stringify(items));
  } catch { /* storage failure: cart stays in memory */ }
}

/** Logout: drops the user's persisted cart. */
export async function clearCart(userId: string | null | undefined): Promise<void> {
  await purgeLegacy();
  if (!userId) return;
  try { await AsyncStorage.removeItem(cartKey(userId)); } catch { /* ignore */ }
}
