const ORDER_RE = /^\/order\/([A-Za-z0-9-]+)\/?$/;

/** Path part of a waylo:// or https:// link, e.g. waylo://order/abc -> /order/abc (host counts as first segment for custom schemes). */
export function linkPath(url: string | null | undefined): string | null {
  if (!url) return null;
  const m = /^([a-z][a-z0-9+.-]*):\/\/([^/?#]*)([^?#]*)/i.exec(url);
  if (!m) return null;
  const [, scheme, host, rest] = m;
  if (scheme.toLowerCase() === 'waylo') return `/${host}${rest}`.replace(/\/+$/, '') || '/';
  return rest || '/';
}

/** "/order/<id>" when the url is an order deep link, else null. */
export function pendingOrderPath(url: string | null | undefined): string | null {
  const p = linkPath(url);
  const m = p ? ORDER_RE.exec(p) : null;
  return m ? `/order/${m[1]}` : null;
}

/**
 * Order link opened while signed out. After login the router's guard may reset the stack more than once,
 * so the link is only peeked by the landing screens and cleared when the order screen actually mounts.
 */
let pendingOrder: string | null = null;
export function rememberOrderLink(url: string | null | undefined) {
  const p = pendingOrderPath(url);
  if (p) pendingOrder = p;
}
export const peekPendingOrderLink = (): string | null => pendingOrder;
export function orderLinkOpened(id: string) {
  if (pendingOrder === `/order/${id}`) pendingOrder = null;
}
