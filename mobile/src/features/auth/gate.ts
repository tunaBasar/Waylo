/** Auth gate rules. Pure, so they can be unit tested without the router. */

const LOGIN_REQUIRED_PREFIXES = ['/orders', '/profile', '/cart', '/order', '/quote', '/reviews', '/notifications', '/settings', '/support'];

/** Paths that need an account. Catalog browsing (hotels, flights, cars, bus, hotel and flight detail) is open to guests. */
export function needsLogin(path: string | null | undefined): boolean {
  if (!path) return false;
  const p = path.split(/[?#]/)[0].replace(/\/+$/, '') || '/';
  return LOGIN_REQUIRED_PREFIXES.some((x) => p === x || p.startsWith(`${x}/`));
}

export type GateDecision = 'wait' | 'login' | 'allow';

/**
 * While the stored session is being restored the gate must wait (never send the visitor to login).
 * Afterwards: a signed-in user is always allowed, a guest only on open paths.
 */
export function gateDecision(state: { loading: boolean; signedIn: boolean }, path: string | null | undefined): GateDecision {
  if (state.loading) return 'wait';
  if (state.signedIn) return 'allow';
  return needsLogin(path) ? 'login' : 'allow';
}

/** Where to return after login, set when a guest taps something that needs an account. */
let returnTo: string | null = null;
export const setReturnTo = (path: string | null) => { returnTo = path && path.startsWith('/') && !path.startsWith('//') ? path : null; };
export const peekReturnTo = (): string | null => returnTo;
export function takeReturnTo(): string | null { const p = returnTo; returnTo = null; return p; }
