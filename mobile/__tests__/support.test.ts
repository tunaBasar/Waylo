import { BODY_MAX, canClose, canReply, capabilitiesText, ROLE_LABEL, STATUS_LABEL, SUBJECT_MAX, supportErrorMessage, validateMessage, validateNewCase } from '../src/features/support/api';

describe('support helpers', () => {
  it('labels', () => {
    expect(STATUS_LABEL).toEqual({ OPEN: 'Açık', ANSWERED: 'Yanıtlandı', CLOSED: 'Kapalı' });
    expect(ROLE_LABEL).toEqual({ CUSTOMER: 'Müşteri', PARTNER: 'İşletme', ADMIN: 'Waylo' });
  });
  it('canReply / canClose', () => {
    expect(canReply('OPEN', 'CUSTOMER')).toBe(true);
    expect(canReply('ANSWERED', 'PARTNER')).toBe(true);
    expect(canReply('CLOSED', 'ADMIN')).toBe(false);
    expect(canClose('OPEN', 'CUSTOMER')).toBe(true);
    expect(canClose('OPEN', 'PARTNER')).toBe(false);
    expect(canClose('CLOSED', 'CUSTOMER')).toBe(false);
  });
  it('validates', () => {
    expect(validateMessage('  ')).toBeTruthy();
    expect(validateMessage('a'.repeat(BODY_MAX + 1))).toBeTruthy();
    expect(validateMessage('merhaba')).toBeNull();
    expect(validateNewCase('', 'x').subject).toBeTruthy();
    expect(validateNewCase('a'.repeat(SUBJECT_MAX + 1), 'x').subject).toBeTruthy();
    expect(validateNewCase('Konu', 'Mesaj')).toEqual({ subject: null, message: null });
  });
  it('capabilitiesText is honest while pending', () => {
    const t = capabilitiesText({ liveChat: false, channels: [], hours: null, decisionPending: true });
    expect(t).toMatch(/henüz belirlenmedi/);
    expect(capabilitiesText({ liveChat: false, channels: ['E-posta'], hours: '09-18', decisionPending: false })).toMatch(/E-posta.*09-18/);
  });
  it('supportErrorMessage', () => {
    expect(supportErrorMessage({ status: 422, code: 'CASE_CLOSED' })).toMatch(/kapatılmış/);
    expect(supportErrorMessage({ status: 404 })).toMatch(/bulunamadı/);
    expect(supportErrorMessage({ status: 0 })).toMatch(/bağlanılamıyor/);
    expect(supportErrorMessage({ status: 500, message: 'boom' })).toBe('boom');
    expect(supportErrorMessage({ status: 500 })).toBe('İşlem başarısız.');
  });
});
