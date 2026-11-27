import { Redirect, Tabs, usePathname, useRouter } from 'expo-router';
import { useEffect } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { needsLogin, peekReturnTo, setReturnTo, takeReturnTo } from '@/features/auth/gate';
import { useCart } from '@/features/cart/CartContext';
import { peekPendingOrderLink } from '@/features/deeplinks';
import { BottomBar } from '@/components/BottomBar';
import { useTheme } from '@/theme/ThemeContext';

export default function TabsLayout() {
  const router = useRouter();
  const { color, type } = useTheme();
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
  const openCart = () => (user ? router.push('/cart') : goLogin('/cart'));
  const guestTab = (back: string) => ({ tabPress: (e: { preventDefault: () => void }) => { if (!user) { e.preventDefault(); goLogin(back); } } });
  // Opening an account tab directly (deep link) as a guest: go to login first.
  if (!user && needsLogin(pathname)) { setReturnTo(pathname); return <Redirect href="/(auth)/login" />; }
  return (
    <Tabs
      tabBar={(p) => <BottomBar {...p} cartCount={items.length} onCart={openCart} />}
      screenOptions={{
        headerStyle: { backgroundColor: color.surface },
        headerTintColor: color.onSurface,
        headerTitleStyle: { fontFamily: type.headlineMd.fontFamily, fontSize: type.headlineMd.fontSize },
      }}>
      <Tabs.Screen name="hotels" options={{ title: 'Oteller' }} />
      <Tabs.Screen name="flights" options={{ title: 'Uçuşlar' }} />
      <Tabs.Screen name="cars" options={{ title: 'Araç' }} />
      <Tabs.Screen name="bus" options={{ title: 'Otobüs' }} />
      <Tabs.Screen name="orders" options={{ title: 'Siparişler' }} listeners={guestTab('/orders')} />
      <Tabs.Screen name="profile" options={{ title: 'Profil' }} listeners={guestTab('/profile')} />
    </Tabs>
  );
}
