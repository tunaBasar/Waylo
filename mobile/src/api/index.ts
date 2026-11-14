import { API_URL } from '@/config';
import { createApiClient } from './client';
import { defaultTokenStore } from './tokenStore';

let expiredListener: (() => void) | null = null;
export function setSessionExpiredListener(fn: (() => void) | null) { expiredListener = fn; }

export const api = createApiClient({
  baseUrl: API_URL,
  tokens: defaultTokenStore,
  onSessionExpired: () => expiredListener?.(),
});

export { ApiError } from './client';
export * from './types';
