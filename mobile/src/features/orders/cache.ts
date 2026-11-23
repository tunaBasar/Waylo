import AsyncStorage from '@react-native-async-storage/async-storage';
import type { Order } from '@/api';

/**
 * Read-only snapshot of the order list for offline viewing. Only the orders list is cached:
 * search results, quotes and prices are never cached, so nothing bookable is ever shown offline.
 */
const KEY = 'waylo.orders.v1';

export async function saveOrdersSnapshot(orders: Order[]): Promise<void> {
  await AsyncStorage.setItem(KEY, JSON.stringify({ savedAt: new Date().toISOString(), orders })).catch(() => {});
}

export async function loadOrdersSnapshot(): Promise<{ savedAt: string; orders: Order[] } | null> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    const v = raw ? JSON.parse(raw) : null;
    return v && Array.isArray(v.orders) && typeof v.savedAt === 'string' ? v : null;
  } catch { return null; }
}

export const clearOrdersSnapshot = () => AsyncStorage.removeItem(KEY).catch(() => {});
