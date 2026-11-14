import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

export interface Tokens {
  token: string;
  refreshToken: string;
}

export interface TokenStore {
  get(): Promise<Tokens | null>;
  set(t: Tokens): Promise<void>;
  clear(): Promise<void>;
}

const ACCESS = 'waylo_token';
const REFRESH = 'waylo_refresh';

/** Tokens live in the OS keystore via expo-secure-store, never AsyncStorage. */
export const secureTokenStore: TokenStore = {
  async get() {
    const token = await SecureStore.getItemAsync(ACCESS);
    const refreshToken = await SecureStore.getItemAsync(REFRESH);
    return token && refreshToken ? { token, refreshToken } : null;
  },
  async set(t) {
    await SecureStore.setItemAsync(ACCESS, t.token);
    await SecureStore.setItemAsync(REFRESH, t.refreshToken);
  },
  async clear() {
    await SecureStore.deleteItemAsync(ACCESS);
    await SecureStore.deleteItemAsync(REFRESH);
  },
};

/** In-memory store (tests, and web dev builds where SecureStore is unavailable). Not persisted. */
export function createMemoryTokenStore(initial: Tokens | null = null): TokenStore {
  let cur = initial;
  return {
    get: async () => cur,
    set: async (t) => { cur = t; },
    clear: async () => { cur = null; },
  };
}

export const defaultTokenStore: TokenStore = Platform.OS === 'web' ? createMemoryTokenStore() : secureTokenStore;
