import { Text } from 'react-native';
import { renderRouter, screen, waitFor } from 'expo-router/testing-library';
import { linkPath, pendingOrderPath, rememberOrderLink, peekPendingOrderLink, orderLinkOpened } from '../src/features/deeplinks';
import { api } from '../src/api';
import Verify from '../src/app/verify';
import ResetPassword from '../src/app/reset-password';

jest.mock('expo-secure-store', () => ({}));

const appJson = require('../app.json');

describe('deep link config', () => {
  it('app.json registers the waylo scheme and https placeholders', () => {
    expect(appJson.expo.scheme).toBe('waylo');
    const data = appJson.expo.android.intentFilters[0].data.map((d: { path?: string; pathPrefix?: string }) => d.path ?? d.pathPrefix);
    expect(data).toEqual(['/order/', '/verify', '/reset-password']);
    expect(appJson.expo.ios.associatedDomains[0]).toMatch(/^applinks:/);
  });

  it.each([
    ['waylo://order/abc-123', '/order/abc-123'],
    ['https://waylo.example.com/order/abc-123', '/order/abc-123'],
    ['waylo://verify?token=t1', '/verify'],
    ['https://waylo.example.com/reset-password?token=t2', '/reset-password'],
  ])('%s -> %s', (url, path) => expect(linkPath(url)).toBe(path));

  it('only order links are replayed after login', () => {
    expect(pendingOrderPath('waylo://order/abc')).toBe('/order/abc');
    expect(pendingOrderPath('waylo://verify?token=x')).toBeNull();
    expect(pendingOrderPath(null)).toBeNull();
    rememberOrderLink('waylo://verify?token=x');
    expect(peekPendingOrderLink()).toBeNull();
    rememberOrderLink('https://waylo.example.com/order/o-1');
    expect(peekPendingOrderLink()).toBe('/order/o-1');
    orderLinkOpened('other');
    expect(peekPendingOrderLink()).toBe('/order/o-1');
    orderLinkOpened('o-1'); // cleared once the order screen is shown
    expect(peekPendingOrderLink()).toBeNull();
  });
});

describe('deep links open the right screens', () => {
  afterEach(() => jest.restoreAllMocks());
  const OrderStub = () => <Text>order screen</Text>;

  it('order/<id> resolves to the order route with the id param', async () => {
    const r = await renderRouter({ index: () => <Text>home</Text>, 'order/[id]': OrderStub }, { initialUrl: '/order/abc-123' });
    expect(r.getPathname()).toBe('/order/abc-123');
    expect(r.getSearchParams()).toEqual({ id: 'abc-123' });
    expect(screen.getByText('order screen')).toBeTruthy();
  });

  it('/verify?token= calls the verify API with the token', async () => {
    const spy = jest.spyOn(api, 'publicGet').mockResolvedValue(undefined);
    const r = await renderRouter({ index: () => <Text>home</Text>, verify: Verify }, { initialUrl: '/verify?token=abc%2B1' });
    expect(r.getPathname()).toBe('/verify');
    await waitFor(() => expect(screen.getByText(/doğrulandı/)).toBeTruthy());
    expect(spy).toHaveBeenCalledWith('/api/auth/verify?token=abc%2B1');
  });

  it('/reset-password?token= shows the form; without a token shows an error', async () => {
    await renderRouter({ index: () => <Text>home</Text>, 'reset-password': ResetPassword }, { initialUrl: '/reset-password?token=xyz' });
    expect(screen.getByLabelText('Yeni şifre')).toBeTruthy();
  });

  it('/reset-password without token is rejected', async () => {
    await renderRouter({ index: () => <Text>home</Text>, 'reset-password': ResetPassword }, { initialUrl: '/reset-password' });
    expect(screen.getByText('Geçersiz sıfırlama linki')).toBeTruthy();
  });
});
