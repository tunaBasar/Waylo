import { addDays, isoDate, isoLocalDateTime, parseIsoDate, validateRentalTimes, validateStayDates } from '@/features/dates';
import { resizeChildren } from '@/features/hotels/guests';
import { checkinWindow } from '@/features/checkin/api';

const today = new Date(2026, 10, 14, 12, 0);
test('isoDate uses local calendar', () => expect(isoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05'));
test('isoLocalDateTime has no offset', () => expect(isoLocalDateTime(new Date(2026, 11, 1, 9, 5))).toBe('2026-12-01T09:05:00'));
test('addDays crosses month', () => expect(isoDate(addDays(new Date(2026, 0, 31), 1))).toBe('2026-02-01'));
test('parseIsoDate rejects invalid', () => { expect(parseIsoDate('2026-02-30')).toBeNull(); expect(parseIsoDate('x')).toBeNull(); });
test('stay validation', () => {
  expect(validateStayDates('2026-11-20', '2026-11-22', today)).toBeNull();
  expect(validateStayDates('2026-11-20', '2026-11-20', today)).not.toBeNull();
  expect(validateStayDates('2026-11-01', '2026-11-22', today)).not.toBeNull();
  expect(validateStayDates('2026-11-14', '2026-11-15', today)).toBeNull();
});
test('rental validation', () => {
  expect(validateRentalTimes(new Date(2026, 11, 1, 10), new Date(2026, 11, 2, 10), today)).toBeNull();
  expect(validateRentalTimes(new Date(2026, 11, 2, 10), new Date(2026, 11, 1, 10), today)).not.toBeNull();
});
test('children ages resize', () => {
  expect(resizeChildren([3], 3)).toEqual([3, 5, 5]);
  expect(resizeChildren([3, 4, 5], 1)).toEqual([3]);
  expect(resizeChildren([], 99).length).toBe(6);
});
test('checkin window', () => {
  const dep = '2026-12-01T12:00:00Z';
  const t = Date.parse(dep);
  expect(checkinWindow(dep, t - 25 * 3600_000).open).toBe(false);
  expect(checkinWindow(dep, t - 24 * 3600_000).open).toBe(true);
  expect(checkinWindow(dep, t - 3600_000).open).toBe(true);
  expect(checkinWindow(dep, t - 3000_000).open).toBe(false);
  expect(checkinWindow(dep, t - 5 * 3600_000, 'AMADEUS').open).toBe(false);
});
