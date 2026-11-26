import { gateDecision, needsLogin, peekReturnTo, setReturnTo, takeReturnTo } from '@/features/auth/gate';

describe('auth gate', () => {
  it('guests browse the catalog, account pages need login', () => {
    for (const p of ['/', '/hotels', '/flights', '/cars', '/bus', '/hotel/h1?checkIn=2026-12-01', '/flight/o1']) expect(needsLogin(p)).toBe(false);
    for (const p of ['/orders', '/profile', '/cart', '/order/o1', '/quote/q1', '/settings', '/support/new', '/reviews', '/notifications']) expect(needsLogin(p)).toBe(true);
  });

  it('waits while the stored session is restored, so a cold-start deep link never lands on login', () => {
    expect(gateDecision({ loading: true, signedIn: false }, '/order/o1')).toBe('wait');
    expect(gateDecision({ loading: true, signedIn: false }, '/flights')).toBe('wait');
    // session restored: the deep link is allowed
    expect(gateDecision({ loading: false, signedIn: true }, '/order/o1')).toBe('allow');
    expect(gateDecision({ loading: false, signedIn: true }, '/flights')).toBe('allow');
  });

  it('guests: open paths allowed, account paths go to login', () => {
    expect(gateDecision({ loading: false, signedIn: false }, '/hotels')).toBe('allow');
    expect(gateDecision({ loading: false, signedIn: false }, '/orders')).toBe('login');
  });

  it('remembers where to return after login, only local paths', () => {
    setReturnTo('/hotel/h1?x=1');
    expect(peekReturnTo()).toBe('/hotel/h1?x=1');
    expect(takeReturnTo()).toBe('/hotel/h1?x=1');
    expect(peekReturnTo()).toBeNull();
    setReturnTo('https://evil.example');
    expect(peekReturnTo()).toBeNull();
    setReturnTo('//evil.example');
    expect(peekReturnTo()).toBeNull();
  });
});
