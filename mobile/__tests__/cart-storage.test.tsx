import AsyncStorage from '@react-native-async-storage/async-storage';
import { LEGACY_CART_KEY, cartKey, clearCart, loadCart, saveCart } from '@/features/cart/storage';
import type { CartItem } from '@/api';

jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));

const item: CartItem = { id: 'a', vertical: 'CAR', label: 'x', amount: 10, currency: 'TRY', request: { k: 1 } };

beforeEach(() => AsyncStorage.clear());

test('roundtrips the cart per user', async () => {
  await saveCart('u1', [item]);
  expect(await loadCart('u1')).toEqual([item]);
  expect(await loadCart('u2')).toEqual([]);
});
test('empty cart removes the key', async () => {
  await saveCart('u1', [item]); await saveCart('u1', []);
  expect(await AsyncStorage.getItem(cartKey('u1'))).toBeNull();
});
test('corrupt or malformed data yields empty/filtered cart', async () => {
  await AsyncStorage.setItem(cartKey('u1'), '{bad');
  expect(await loadCart('u1')).toEqual([]);
  await AsyncStorage.setItem(cartKey('u1'), JSON.stringify([item, { nope: true }]));
  expect(await loadCart('u1')).toEqual([item]);
});
test('never writes tokens', async () => {
  await saveCart('u1', [item]);
  expect(await AsyncStorage.getAllKeys()).toEqual([cartKey('u1')]);
});
test('no user: nothing is persisted or loaded', async () => {
  await saveCart(null, [item]);
  expect(await AsyncStorage.getAllKeys()).toEqual([]);
  expect(await loadCart(null)).toEqual([]);
});
test('logout clears the user cart only', async () => {
  await saveCart('u1', [item]); await saveCart('u2', [item]);
  await clearCart('u1');
  expect(await loadCart('u1')).toEqual([]);
  expect(await loadCart('u2')).toEqual([item]);
});
test('legacy unkeyed cart is deleted and never adopted', async () => {
  await AsyncStorage.setItem(LEGACY_CART_KEY, JSON.stringify([item]));
  expect(await loadCart('u1')).toEqual([]);
  expect(await AsyncStorage.getItem(LEGACY_CART_KEY)).toBeNull();
  expect(await AsyncStorage.getItem(cartKey('u1'))).toBeNull();
});
