import { offerPolicyText, orderPolicyText } from '@/features/format';

const NOW = new Date(2026, 10, 26, 10, 0, 0);

describe('cancellation labels', () => {
  it('offer: future, same-day, past, non refundable', () => {
    expect(offerPolicyText('FREE_CANCELLATION', 2, '2026-12-05', NOW)).toBe('02.12.2026 gun sonuna kadar ucretsiz iptal');
    expect(offerPolicyText('FREE_CANCELLATION', 2, '2026-11-26', NOW)).toBe('Iade edilmez (ucretsiz iptal suresi gecti)');
    expect(offerPolicyText('FREE_CANCELLATION', 2, '2026-11-27', NOW)).toBe('Iade edilmez (ucretsiz iptal suresi gecti)');
    expect(offerPolicyText('NON_REFUNDABLE', null, '2026-12-05', NOW)).toBe('Iade edilmez');
  });

  it('order', () => {
    const now = new Date('2026-11-26T07:00:00Z');
    expect(orderPolicyText('FREE_UNTIL', 48, '2026-11-29T21:00:00Z', now)).toBe('29.11.2026 gun sonuna kadar ucretsiz iptal');
    expect(orderPolicyText('FREE_UNTIL', 48, '2026-11-24T21:00:00Z', now)).toBe('Iade edilmez (ucretsiz iptal suresi gecti)');
    expect(orderPolicyText('FREE_CANCELLATION', null, '2026-11-25T21:00:00Z', now)).toBe('Iade edilmez (ucretsiz iptal suresi gecti)');
    expect(orderPolicyText('FREE_UNTIL', 24, '2026-11-27T10:30:00Z', now)).toBe('Ucretsiz iptal son an: 27.11.2026 13:30');
    expect(orderPolicyText('FREE_CANCELLATION', null, null, now)).toBe('Hizmet baslangicina kadar ucretsiz iptal');
  });
});
