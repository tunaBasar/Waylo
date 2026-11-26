import { Redirect, Tabs, usePathname, useRouter } from 'expo-router';
import { useEffect } from 'react';
import { Pressable, Text } from 'react-native';
import { useAuth } from '@/features/auth/AuthContext';
import { needsLogin, peekReturnTo, setReturnTo, takeReturnTo } from '@/features/auth/gate';
import { useCart } from '@/features/cart/CartContext';
import { peekPendingOrderLink } from '@/features/deeplinks';

function TabGlyph({ glyph }: { glyph: string }) {
  return <Text accessibilityElementsHidden importantForAccessibility="no">{glyph}</Text>;
}
const icon = (glyph: string) => function TabIcon() { return <TabGlyph glyph={glyph} />; };

export default function TabsLayout() {
  const router = useRouter();
  const { user } = useAuth();
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
  // A guest sent to login by an action returns to that screen once signed in.
  useEffect(() => {
    if (!user || !peekReturnTo()) return;
    const t = setTimeout(() => { const back = takeReturnTo(); if (back) router.push(back as never); }, 300);
    return () => clearTimeout(t);
  }, [user, router]);

  const goLogin = (back: string) => { setReturnTo(back); router.navigate('/(auth)/login'); };
  const cartButton = () => (
    <Pressable accessibilityRole="button" accessibilityLabel="Sepet" onPress={() => (user ? router.push('/cart') : goLogin('/cart'))} style={{ paddingHorizontal: 14 }}>
      <Text>{`Sepet${items.length ? ` (${items.length})` : ''}`}</Text>
    </Pressable>
  );
  const guestTab = (back: string) => ({ tabPress: (e: { preventDefault: () => void }) => { if (!user) { e.preventDefault(); goLogin(back); } } });
  // Opening an account tab directly (deep link) as a guest: go to login first.
  if (!user && needsLogin(pathname)) { setReturnTo(pathname); return <Redirect href="/(auth)/login" />; }
  return (
    <Tabs screenOptions={{ headerRight: cartButton }}>
      <Tabs.Screen name="hotels" options={{ title: 'Oteller', tabBarIcon: icon('🏨') }} />
      <Tabs.Screen name="flights" options={{ title: 'Ucuslar', tabBarIcon: icon('✈️') }} />
      <Tabs.Screen name="cars" options={{ title: 'Arac', tabBarIcon: icon('🚗') }} />
      <Tabs.Screen name="bus" options={{ title: 'Otobus', tabBarIcon: icon('🚌') }} />
      <Tabs.Screen name="orders" options={{ title: 'Siparisler', tabBarIcon: icon('🧾') }} listeners={guestTab('/orders')} />
      <Tabs.Screen name="profile" options={{ title: 'Profil', tabBarIcon: icon('👤') }} listeners={guestTab('/profile')} />
    </Tabs>
  );
}
