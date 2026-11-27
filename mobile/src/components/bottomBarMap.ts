export type BarItem = 'search' | 'cart' | 'orders' | 'profile';
export const SEARCH_ROUTES = ['hotels', 'flights', 'cars', 'bus'] as const;
export type SearchRoute = (typeof SEARCH_ROUTES)[number];

export const isSearchRoute = (name: string): name is SearchRoute => (SEARCH_ROUTES as readonly string[]).includes(name);

/** Which of the four bar items is active for a tab route name (cart is a stack screen, never active here). */
export function barItemForRoute(name: string): BarItem | null {
  if (isSearchRoute(name)) return 'search';
  if (name === 'orders') return 'orders';
  if (name === 'profile') return 'profile';
  return null;
}
