import { reviewErrorMessage, REVIEW_WHEN_HINT } from '../src/features/reviews/api';
import { checkoutErrorMessage } from '../src/features/orders/api';
import { getHotelOrNotPublic, hotelsApi } from '../src/features/hotels/api';
import { orderStatusInfo } from '../src/features/format';

jest.mock('expo-secure-store', () => ({}));

describe('review reasons', () => {
  it.each([
    ['REVIEW_NOT_CHECKED_OUT', 'check-out'],
    ['REVIEW_NOT_CHECKED_IN', 'check-in'],
    ['REVIEW_CANCELLED_OR_NO_SHOW', 'İptal'],
    ['REVIEW_ORDER_NOT_CONFIRMED', 'onaylanmadığı'],
    ['ORG_MEMBER_CANNOT_REVIEW', 'işletmesinde çalışan'],
  ])('%s maps to Turkish', (code, frag) => {
    expect(reviewErrorMessage({ status: 422, code, message: 'raw' })).toContain(frag);
  });
  it('NOT_CHECKED_OUT includes the when-hint', () => {
    expect(reviewErrorMessage({ status: 422, code: 'REVIEW_NOT_CHECKED_OUT' })).toContain(REVIEW_WHEN_HINT);
  });
  it('unknown code falls back to message', () => {
    expect(reviewErrorMessage({ status: 500, code: 'X', message: 'boom' })).toBe('boom');
  });
});

describe('hotel not public', () => {
  afterEach(() => jest.restoreAllMocks());
  it('404 becomes notPublic', async () => {
    jest.spyOn(hotelsApi, 'get').mockRejectedValue({ status: 404 });
    expect(await getHotelOrNotPublic('h')).toEqual({ kind: 'notPublic' });
  });
  it('other errors rethrow', async () => {
    jest.spyOn(hotelsApi, 'get').mockRejectedValue({ status: 500 });
    await expect(getHotelOrNotPublic('h')).rejects.toEqual({ status: 500 });
  });
  it('maps HOTEL_NOT_BOOKABLE', () => {
    expect(checkoutErrorMessage({ status: 422, code: 'HOTEL_NOT_BOOKABLE' })).toContain('rezervasyona açık değil');
  });
});

describe('order status', () => {
  it('EXPIRED is Süresi doldu with a note', () => {
    const i = orderStatusInfo('EXPIRED');
    expect(i.label).toBe('Süresi doldu');
    expect(i.known).toBe(true);
    expect(i.note).toBeTruthy();
  });
  it('unknown status shows raw', () => {
    expect(orderStatusInfo('REFUNDED')).toEqual({ label: 'REFUNDED', known: false, note: null });
  });
});
