import AsyncStorage from '@react-native-async-storage/async-storage';
import type { CartItem } from '@/api';

/** Cart only. Auth tokens must never be stored here (they live in SecureStore). */
export const CART_KEY = 'waylo.cart.v1';

export async function loadCart(): Promise<CartItem[]> {
  try {
    const raw = await AsyncStorage.getItem(CART_KEY);
    if (!raw) return [];
    const v: unknown = JSON.parse(raw);
    if (!Array.isArray(v)) return [];
    return v.filter((i): i is CartItem => !!i && typeof i.id === 'string' && typeof i.vertical === 'string' && typeof i.request === 'object' && i.request !== null);
  } catch { return []; }
}

export async function saveCart(items: CartItem[]): Promise<void> {
  try {
    if (items.length === 0) await AsyncStorage.removeItem(CART_KEY);
    else await AsyncStorage.setItem(CART_KEY, JSON.stringify(items));
  } catch { /* storage failure: cart stays in memory */ }
}
