import { canOfferCancel } from '@/features/orders/actions';
import { sortOrdersForDisplay } from '@/features/orders/sort';
import { isValidGuestName } from '@/features/cart/guest';
import { toPackageRequest } from '@/features/cart/CartContext';
import type { CartItem } from '@/api';

jest.mock('@/features/auth/AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('@/features/cart/storage', () => ({ loadCart: jest.fn(), saveCart: jest.fn() }));

describe('sortOrdersForDisplay', () => {
  it('PENDING first, newest first, input untouched', () => {
    const input = [
      { id: 'a', status: 'CONFIRMED', createdAt: '2026-11-03T10:00:00Z' },
      { id: 'b', status: 'PENDING', createdAt: '2026-11-01T10:00:00Z' },
      { id: 'c', status: 'CONFIRMED', createdAt: '2026-11-05T10:00:00Z' },
      { id: 'd', status: 'PENDING', createdAt: '2026-11-02T10:00:00Z' },
    ];
    const copy = JSON.stringify(input);
    expect(sortOrdersForDisplay(input).map((o) => o.id)).toEqual(['d', 'b', 'c', 'a']);
    expect(JSON.stringify(input)).toBe(copy);
  });
});

describe('hotel lead guest', () => {
  const hotel: CartItem = { id: '1', vertical: 'HOTEL', label: 'h', amount: 1, currency: 'TRY', request: { hotelId: 'x' } };
  const car: CartItem = { id: '2', vertical: 'CAR', label: 'c', amount: 1, currency: 'TRY', request: { category: 'x' } };
  it('validates names', () => {
    expect(isValidGuestName("Ayse-Nur O'Neil")).toBe(true);
    expect(isValidGuestName('Musteri E2E')).toBe(false);
    expect(isValidGuestName('  ')).toBe(false);
    expect(isValidGuestName('a'.repeat(81))).toBe(false);
    expect(isValidGuestName(null)).toBe(false);
  });
  it('sends names for hotel items only', () => {
    const r = toPackageRequest([hotel, car], undefined, { firstName: ' Ayse ', lastName: 'Yilmaz' });
    expect(r.items[0]).toMatchObject({ guestFirstName: 'Ayse', guestLastName: 'Yilmaz' });
    expect(r.items[1]).not.toHaveProperty('guestFirstName');
  });
  it('invalid name is omitted, never blocks', () => {
    const r = toPackageRequest([hotel], undefined, { firstName: 'Ayse', lastName: 'E2E 42' });
    expect(r.items[0]).toHaveProperty('guestFirstName', 'Ayse');
    expect(r.items[0]).not.toHaveProperty('guestLastName');
  });
});

describe('canOfferCancel', () => {
  const items = [{ id: 'i1' }, { id: 'i2', cancelOutcome: 'REFUNDED' }];
  it('hidden when cancelled', () => expect(canOfferCancel({ status: 'CANCELLED', items }, [])).toBe(false));
  it('shown when no items', () => expect(canOfferCancel({ status: 'CONFIRMED' }, null)).toBe(true));
  it('shown when one item still open', () => expect(canOfferCancel({ status: 'CONFIRMED', items }, [])).toBe(true));
  it('hidden when all cancelled or used', () => {
    expect(canOfferCancel({ status: 'CONFIRMED', items }, [{ orderItemId: 'i1', status: 'REDEEMED' }])).toBe(false);
    expect(canOfferCancel({ status: 'CONFIRMED', items }, [{ orderItemId: 'i1', status: 'NO_SHOW' }])).toBe(false);
    expect(canOfferCancel({ status: 'CONFIRMED', items }, [{ orderItemId: 'i1', status: 'ACTIVE' }])).toBe(true);
  });
});
