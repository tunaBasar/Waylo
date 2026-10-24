/**
 * Surface routing rules. Pure functions with no imports, so they can be unit tested directly.
 *
 * One Next app, three isolated surfaces:
 *  - customer: everything outside /admin and /partner (role USER)
 *  - partner:  /partner/**  (logged in + at least one organization membership)
 *  - admin:    /admin/**    (role ADMIN only)
 * /profile and /settings are shared account pages and are exempt from the customer guard.
 */
export type Surface = 'customer' | 'partner' | 'admin';

export const ADMIN_PURCHASE_MESSAGE = 'Platform yoneticisi hesabi ile satin alma yapilamaz, musteri hesabi kullanin.';
export const PARTNER_REQUIRED_MESSAGE = 'Isletme paneline erisim icin bir isletmeye uye olmaniz gerekir.';

export interface GuardRedirect {
  to: string;
  notice?: string;
}

const under = (pathname: string, prefix: string) => pathname === prefix || pathname.startsWith(`${prefix}/`);

export function surfaceOf(pathname: string): Surface {
  if (under(pathname, '/admin')) return 'admin';
  if (under(pathname, '/partner')) return 'partner';
  return 'customer';
}

/** Account pages shared by customer and admin accounts. */
export function isAccountPath(pathname: string): boolean {
  return under(pathname, '/profile') || under(pathname, '/settings');
}

/** Customer pages that book or buy something: hotel detail/booking, quotes, vehicle rent, orders, rentals. */
export function isPurchasePath(pathname: string): boolean {
  return pathname.startsWith('/hotels/')
    || under(pathname, '/quotes')
    || under(pathname, '/vehicles')
    || under(pathname, '/orders')
    || under(pathname, '/rentals');
}

export function homeFor(role: string | null | undefined): string {
  return role === 'ADMIN' ? '/admin' : '/';
}

/**
 * Where must this visitor go instead of the requested page? null means the page is allowed.
 * `hasMembership` is null while memberships are not known yet (the partner layout decides once loaded).
 */
export function guardRedirect(
  pathname: string,
  role: string | null | undefined,
  hasMembership: boolean | null = null,
): GuardRedirect | null {
  const surface = surfaceOf(pathname);
  if (surface === 'admin') {
    return role === 'ADMIN' ? null : { to: '/' };
  }
  if (surface === 'partner') {
    if (role === 'ADMIN') return { to: '/admin' };
    if (hasMembership === false) return { to: '/', notice: PARTNER_REQUIRED_MESSAGE };
    return null;
  }
  if (role === 'ADMIN' && !isAccountPath(pathname)) {
    return isPurchasePath(pathname) ? { to: '/admin', notice: ADMIN_PURCHASE_MESSAGE } : { to: '/admin' };
  }
  return null;
}

/** Redirect target as a URL, with the notice carried in the query string. */
export function redirectUrl(redirect: GuardRedirect): string {
  return redirect.notice ? `${redirect.to}?notice=${encodeURIComponent(redirect.notice)}` : redirect.to;
}

const AUTH_PATHS = ['/login', '/register', '/forgot-password', '/reset-password', '/verify'];

/** After login: the `next` target only if it is a local path that belongs to the user's surface. */
export function postLoginTarget(role: string | null | undefined, next: string | null | undefined): string {
  const fallback = homeFor(role);
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.includes('\\')) return fallback;
  const path = next.split(/[?#]/)[0];
  if (AUTH_PATHS.some(p => under(path, p))) return fallback;
  if (guardRedirect(path, role)) return fallback;
  return next;
}
