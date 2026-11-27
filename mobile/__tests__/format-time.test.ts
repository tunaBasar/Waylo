import { displayItemText, fmtLocal, voucherStatusText } from '@/features/format';

// Walkthrough 2026-11-26 (F37): the app showed voucher windows and flight times in UTC
// (an 11:45 departure as 08:45, a 00:00 hotel check-in as 21:00 the day before).
describe('fmtLocal', () => {
  it('converts UTC instants to Istanbul time', () => {
    expect(fmtLocal('2026-11-28T08:45:00Z')).toBe('28.11.2026 11:45');
    expect(fmtLocal('2026-11-26T21:00:00Z')).toBe('27.11.2026 00:00');
    expect(fmtLocal('2026-11-27T09:00:00.123Z')).toBe('27.11.2026 12:00');
  });

  it('converts offset instants', () => {
    expect(fmtLocal('2026-11-28T11:45:00+03:00')).toBe('28.11.2026 11:45');
    expect(fmtLocal('2026-11-28T09:45:00+01:00')).toBe('28.11.2026 11:45');
  });

  it('keeps offsetless local date-times as they are (car and bus local times)', () => {
    expect(fmtLocal('2026-11-27T13:30:00')).toBe('27.11.2026 13:30');
    expect(fmtLocal('2026-11-27T13:30')).toBe('27.11.2026 13:30');
  });

  it('handles empty and unknown input', () => {
    expect(fmtLocal(null)).toBe('');
    expect(fmtLocal(undefined)).toBe('');
    expect(fmtLocal('yarin')).toBe('yarin');
  });
});

describe('voucherStatusText', () => {
  it('shows Turkish labels and keeps unknown codes', () => {
    expect(voucherStatusText('ACTIVE')).toBe('Geçerli');
    expect(voucherStatusText('REDEEMED')).toBe('Kullanıldı');
    expect(voucherStatusText('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});

describe('displayItemText', () => {
  it('rewrites the old ISO hotel range', () => {
    expect(displayItemText('2026-11-27-2026-11-29, 2 gece, 1 oda')).toBe('27.11.2026-29.11.2026, 2 gece, 1 oda');
  });
  it('leaves other text unchanged', () => {
    expect(displayItemText('Otel · 27.11.2026-29.11.2026, 2 gece')).toBe('Otel · 27.11.2026-29.11.2026, 2 gece');
    expect(displayItemText('2026-11-27')).toBe('2026-11-27');
    expect(displayItemText(null)).toBe('');
  });
});

import { mealPlanText, offerSubtitle } from '@/features/format';
describe('meal plan text', () => {
  it('translates known codes and keeps unknown', () => {
    expect(mealPlanText('BREAKFAST')).toBe('Kahvaltı dahil');
    expect(mealPlanText('ALL_INCLUSIVE')).toBe('Her şey dahil');
    expect(mealPlanText('X')).toBe('X');
  });
  it('does not repeat the meal when the rate plan says it', () => {
    expect(offerSubtitle('Kahvalti dahil, esnek iptal', 'BREAKFAST', 2)).toBe('Kahvalti dahil, esnek iptal · 2 gece');
    expect(offerSubtitle('Esnek iptal', 'BREAKFAST', 2)).toBe('Esnek iptal · Kahvaltı dahil · 2 gece');
  });
});
