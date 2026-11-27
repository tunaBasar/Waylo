import { carErrorMessage, defaultPickup, MIN_PICKUP_LEAD_MS, PICKUP_LEAD_TEXT, pickupLeadError } from '@/features/cars/lead';

const NOW = new Date(2026, 10, 27, 10, 0, 0);

describe('car pickup lead time', () => {
  it('rejects under 2 hours, accepts exactly 2 hours', () => {
    expect(pickupLeadError(new Date(NOW.getTime() + MIN_PICKUP_LEAD_MS - 60000), NOW.getTime())).toBe(PICKUP_LEAD_TEXT);
    expect(pickupLeadError(new Date(NOW.getTime() + MIN_PICKUP_LEAD_MS), NOW.getTime())).toBeNull();
    expect(pickupLeadError(new Date('x'), NOW.getTime())).toMatch(/Geçersiz/);
  });
  it('default pickup is never in the past', () => {
    const d = defaultPickup(NOW);
    expect(d.getTime()).toBeGreaterThanOrEqual(NOW.getTime() + MIN_PICKUP_LEAD_MS);
    expect(d.getHours()).toBe(10);
  });
  it('maps INVALID_TIME', () => {
    expect(carErrorMessage({ code: 'INVALID_TIME', message: 'bad' })).toBe(PICKUP_LEAD_TEXT);
    expect(carErrorMessage({ code: 'X', message: 'other' })).toBe('other');
  });
});
