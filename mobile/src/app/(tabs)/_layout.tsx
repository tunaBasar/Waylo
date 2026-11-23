import { Tabs, usePathname, useRouter } from 'expo-router';
import { useEffect } from 'react';
import { Pressable, Text } from 'react-native';
import { useCart } from '@/features/cart/CartContext';
import { peekPendingOrderLink } from '@/features/deeplinks';

export default function TabsLayout() {
  const router = useRouter();
  const { items } = useCart();
  // After login the protected stack lands here: open an order deep link that arrived while signed out.
  const pathname = usePathname();
  useEffect(() => {
    const p = peekPendingOrderLink();
    if (!p) return;
    // Wait for the auth guard's own post-login navigation to settle, otherwise it resets this push.
    const t = setTimeout(() => router.push(p as `/order/${string}`), 300);
    return () => clearTimeout(t);
  }, [router, pathname]);
  const cartButton = () => (
    <Pressable accessibilityRole="button" accessibilityLabel="Sepet" onPress={() => router.push('/cart')} style={{ paddingHorizontal: 14 }}>
      <Text>{`Sepet${items.length ? ` (${items.length})` : ''}`}</Text>
    </Pressable>
  );
  return (
    <Tabs screenOptions={{ headerRight: cartButton }}>
      <Tabs.Screen name="hotels" options={{ title: 'Oteller' }} />
      <Tabs.Screen name="flights" options={{ title: 'Ucuslar' }} />
      <Tabs.Screen name="cars" options={{ title: 'Arac' }} />
      <Tabs.Screen name="bus" options={{ title: 'Otobus' }} />
      <Tabs.Screen name="orders" options={{ title: 'Siparisler' }} />
      <Tabs.Screen name="profile" options={{ title: 'Profil' }} />
    </Tabs>
  );
}
