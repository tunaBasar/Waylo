import { buildBusReturn, buildCarsReturn, busTripKey, parseBusReturn, parseCarsReturn } from '@/features/returnState';
import { clearBusDrafts, saveBusDraft, takeBusDraft } from '@/features/bus/draft';

const now = new Date(2026, 10, 27, 12, 0);

test('bus return query round trips, incl. non-ascii and uuid trip ids', () => {
  const key = busTripKey({ tripId: 'a1b2-c3', fromSeq: 1, toSeq: 4 });
  const url = buildBusReturn({ from: 'İstanbul', to: 'Ankara', date: '2026-12-01', tripKey: key });
  expect(url.startsWith('/bus?') && !url.startsWith('//')).toBe(true);
  const p = Object.fromEntries(new URLSearchParams(url.split('?')[1]));
  expect(parseBusReturn(p, now)).toEqual({ from: 'İstanbul', to: 'Ankara', date: '2026-12-01', trip: { tripId: 'a1b2-c3', fromSeq: 1, toSeq: 4 } });
});
test('bus return without trip, invalid values ignored', () => {
  expect(parseBusReturn({ from: 'A', to: 'B', date: '2026-12-01', trip: 'junk' }, now)).toEqual({ from: 'A', to: 'B', date: '2026-12-01' });
  expect(parseBusReturn({ from: 'A', to: '', date: '2026-12-01' }, now)).toBeNull();
  expect(parseBusReturn({ from: 'A', to: 'B', date: '2026-02-30' }, now)).toBeNull();
  expect(parseBusReturn({}, now)).toBeNull();
});
test('bus return drops past dates, keeps today', () => {
  expect(parseBusReturn({ from: 'A', to: 'B', date: '2026-11-26' }, now)).toBeNull();
  expect(parseBusReturn({ from: 'A', to: 'B', date: '2026-11-27' }, now)).not.toBeNull();
});
test('cars return round trips', () => {
  const url = buildCarsReturn({ pickupBranchId: 'b-1', returnBranchId: 'b-2', pickupAt: new Date(2026, 11, 1, 10, 0), returnAt: new Date(2026, 11, 5, 10, 30), driverAge: 27 });
  const p = Object.fromEntries(new URLSearchParams(url.split('?')[1]));
  expect(parseCarsReturn(p, now)).toEqual({ pickupBranchId: 'b-1', returnBranchId: 'b-2', pickupAt: new Date(2026, 11, 1, 10, 0), returnAt: new Date(2026, 11, 5, 10, 30), driverAge: 27 });
});
test('cars return ignores invalid values and past times', () => {
  expect(parseCarsReturn({ pickup: 'x y' }, now)).toBeNull();
  expect(parseCarsReturn({ pickup: 'b1', pickupAt: '2026-11-01T10:00:00', returnAt: '2026-12-01T10:00:00', age: '12' }, now)).toEqual({ pickupBranchId: 'b1', returnBranchId: 'b1' });
  expect(parseCarsReturn({ pickup: 'b1', pickupAt: '2026-12-02T10:00:00', returnAt: '2026-12-01T10:00:00' }, now)?.returnAt).toBeUndefined();
  expect(parseCarsReturn({ pickup: 'b1', pickupAt: '2026-12-02T25:00:00', age: 'abc' }, now)).toEqual({ pickupBranchId: 'b1', returnBranchId: 'b1' });
});
test('bus draft is taken once and copied', () => {
  clearBusDrafts();
  const picked = [{ seatNo: 3, name: 'Ali Veli', gender: 'MALE' as const }];
  saveBusDraft('k', picked);
  picked[0].name = 'changed';
  expect(takeBusDraft('k')).toEqual([{ seatNo: 3, name: 'Ali Veli', gender: 'MALE' }]);
  expect(takeBusDraft('k')).toBeNull();
  saveBusDraft('e', []);
  expect(takeBusDraft('e')).toBeNull();
});
