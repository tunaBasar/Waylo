import { render, waitFor } from '@testing-library/react-native';
import type { ComponentType } from 'react';
import type { ReactTestRendererJSON } from 'react-test-renderer';

jest.mock('expo-secure-store', () => ({}));
jest.mock('expo-router', () => {
  const { Text } = require('react-native');
  return {
    useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn() }),
    useFocusEffect: jest.fn(),
    useLocalSearchParams: () => ({ id: 'o1', token: 't1' }),
    // Pass the caller's props through so a missing label on a real <Link> is caught.
    Link: ({ href: _href, ...props }: { href: string }) => <Text onPress={() => {}} {...props} />,
  };
});
jest.mock('../src/features/auth/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'u1', email: 'a@b.c', firstName: 'A', lastName: 'B', roles: ['CUSTOMER'] },
    login: jest.fn(), register: jest.fn(), logout: jest.fn(), forgotPassword: jest.fn(),
    refreshProfile: jest.fn(), applyAuth: jest.fn(), clearSession: jest.fn(),
  }),
}));
jest.mock('../src/features/cart/CartContext', () => ({
  useCart: () => ({ items: [{ id: 'c1', vertical: 'HOTEL', label: 'Otel X', amount: 100, currency: 'TRY' }], add: jest.fn(), remove: jest.fn(), clear: jest.fn() }),
  toPackageRequest: jest.fn(),
}));
jest.mock('../src/api', () => {
  const actual = jest.requireActual('../src/api/client');
  // Every API call fails as "offline" so screens render their error state (with retry button).
  const fail = () => Promise.reject(new actual.ApiError('offline', 0, 'NETWORK'));
  return { ApiError: actual.ApiError, api: { get: fail, post: fail, patch: fail, del: fail, publicPost: fail, publicGet: fail, tokens: {} } };
});

type Node = ReactTestRendererJSON;
/** Every host node that reacts to presses must expose an accessibility label. */
function pressables(tree: Node | Node[] | null): { total: number; unlabeled: string[] } {
  const out: string[] = [];
  let total = 0;
  const walk = (n: Node | string) => {
    if (typeof n === 'string') return;
    const p = n.props ?? {};
    const pressable = typeof p.onClick === 'function' || typeof p.onPress === 'function';
    if (pressable) total++;
    if (pressable && !(p.accessibilityLabel || p['aria-label'])) out.push(`${n.type}: ${JSON.stringify(n.children?.[0] ?? '')}`.slice(0, 80));
    (n.children ?? []).forEach(walk);
  };
  (Array.isArray(tree) ? tree : tree ? [tree] : []).forEach(walk);
  return { total, unlabeled: out };
}

const screens: [string, () => ComponentType][] = [
  ['login', () => require('../src/app/(auth)/login').default],
  ['register', () => require('../src/app/(auth)/register').default],
  ['forgot', () => require('../src/app/(auth)/forgot').default],
  ['hotels', () => require('../src/app/(tabs)/hotels').default],
  ['flights', () => require('../src/app/(tabs)/flights').default],
  ['cars', () => require('../src/app/(tabs)/cars').default],
  ['bus', () => require('../src/app/(tabs)/bus').default],
  ['orders', () => require('../src/app/(tabs)/orders').default],
  ['profile', () => require('../src/app/(tabs)/profile').default],
  ['cart', () => require('../src/app/cart').default],
  ['order detail', () => require('../src/app/order/[id]').default],
  ['hotel detail', () => require('../src/app/hotel/[id]').default],
  ['settings', () => require('../src/app/settings').default],
  ['notifications', () => require('../src/app/notifications').default],
  ['reset-password', () => require('../src/app/reset-password').default],
  ['verify', () => require('../src/app/verify').default],
];

describe('accessibility: every pressable has a label', () => {
  // Loading (transforming) the screen modules is the slow part on a cold cache or a loaded machine.
  // Do it once here with a generous hook timeout so the per-test 5s budget only covers rendering.
  beforeAll(() => {
    screens.forEach(([, load]) => load());
  }, 120000);

  it('the walk detects an unlabeled pressable', async () => {
    const { Pressable, Text } = require('react-native');
    const r = await render(<Pressable onPress={() => {}}><Text>x</Text></Pressable>);
    expect(pressables(r.toJSON()).unlabeled).toHaveLength(1);
  });

  it.each(screens)('%s', async (_name, load) => {
    const Screen = load();
    const r = await render(<Screen />);
    await waitFor(() => expect(r.toJSON()).toBeTruthy());
    await new Promise((res) => setTimeout(res, 0)); // let failed API calls settle into error state
    const tree = r.toJSON();
    const { total, unlabeled } = pressables(tree);
    expect(total).toBeGreaterThan(0); // the walk must see the screen's buttons, otherwise the check is vacuous
    expect(unlabeled).toEqual([]);
  });
});
