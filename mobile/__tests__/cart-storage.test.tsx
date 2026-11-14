import AsyncStorage from '@react-native-async-storage/async-storage';
import { CART_KEY, loadCart, saveCart } from '@/features/cart/storage';
import type { CartItem } from '@/api';

jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));

const item: CartItem = { id: 'a', vertical: 'CAR', label: 'x', amount: 10, currency: 'TRY', request: { k: 1 } };

beforeEach(() => AsyncStorage.clear());

test('roundtrips the cart', async () => {
  await saveCart([item]);
  expect(await loadCart()).toEqual([item]);
});
test('empty cart removes the key', async () => {
  await saveCart([item]); await saveCart([]);
  expect(await AsyncStorage.getItem(CART_KEY)).toBeNull();
});
test('corrupt or malformed data yields empty/filtered cart', async () => {
  await AsyncStorage.setItem(CART_KEY, '{bad');
  expect(await loadCart()).toEqual([]);
  await AsyncStorage.setItem(CART_KEY, JSON.stringify([item, { nope: true }]));
  expect(await loadCart()).toEqual([item]);
});
test('never writes tokens', async () => {
  await saveCart([item]);
  expect(await AsyncStorage.getAllKeys()).toEqual([CART_KEY]);
});
