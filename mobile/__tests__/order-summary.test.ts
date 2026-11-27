import { orderSummary } from '@/features/orders/summary';

const item = (vertical: string, description: string) => ({ id: 'i', vertical, description, subtotal: 1, currency: 'TRY' });

describe('orderSummary', () => {
  it('uses the first item and counts several', () => {
    expect(orderSummary({ items: [item('HOTEL', 'Kaya Tas Konak · Standart · 28.11.2026-30.11.2026, 2 gece, 1 oda')] }))
      .toEqual({ title: 'Otel · Kaya Tas Konak · Standart · 28.11.2026-30.11.2026, 2 gece, 1 oda', count: null });
    expect(orderSummary({ items: [item('FLIGHT', 'TK100'), item('CAR', 'Fiat'), item('BUS', 'x')] }))
      .toEqual({ title: 'Uçuş · TK100', count: '3 kalem' });
  });
  it('no items: nothing extra', () => {
    expect(orderSummary({})).toEqual({ title: null, count: null });
    expect(orderSummary({ items: [] })).toEqual({ title: null, count: null });
  });
});
