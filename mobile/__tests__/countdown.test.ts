import { formatCountdown, secondsLeft } from '@/components/countdown';
import { barItemForRoute, isSearchRoute } from '@/components/bottomBarMap';

const now = Date.parse('2026-11-27T10:00:00Z');
test('formats mm:ss', () => {
  expect(formatCountdown('2026-11-27T10:14:42Z', now)).toBe('14:42');
  expect(formatCountdown('2026-11-27T10:00:09Z', now)).toBe('00:09');
});
test('rounds partial seconds up', () => {
  expect(secondsLeft('2026-11-27T10:00:00.200Z', now)).toBe(1);
  expect(formatCountdown('2026-11-27T10:00:00.200Z', now)).toBe('00:01');
});
test('expired and invalid give null', () => {
  expect(formatCountdown('2026-11-27T09:59:59Z', now)).toBeNull();
  expect(formatCountdown('2026-11-27T10:00:00Z', now)).toBeNull();
  expect(formatCountdown('nope', now)).toBeNull();
  expect(secondsLeft('2026-11-27T09:00:00Z', now)).toBe(0);
});
test('minutes beyond 59', () => expect(formatCountdown('2026-11-27T11:05:00Z', now)).toBe('65:00'));
test('bottom bar mapping', () => {
  for (const r of ['hotels', 'flights', 'cars', 'bus']) expect(barItemForRoute(r)).toBe('search');
  expect(barItemForRoute('orders')).toBe('orders');
  expect(barItemForRoute('profile')).toBe('profile');
  expect(barItemForRoute('cart')).toBeNull();
  expect(isSearchRoute('orders')).toBe(false);
});
