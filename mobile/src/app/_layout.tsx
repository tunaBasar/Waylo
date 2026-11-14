import { Stack } from 'expo-router';
import { AuthProvider, useAuth } from '@/features/auth/AuthContext';
import { CartProvider } from '@/features/cart/CartContext';

function Routes() {
  const { user, loading } = useAuth();
  if (loading) return null;
  return (
    <Stack screenOptions={{ headerShown: true }}>
      <Stack.Protected guard={!!user}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="hotel/[id]" options={{ title: 'Otel' }} />
        <Stack.Screen name="quote/[id]" options={{ title: 'Teklif' }} />
        <Stack.Screen name="flight/[id]" options={{ title: 'Ucus teklifi' }} />
        <Stack.Screen name="cart" options={{ title: 'Sepet' }} />
        <Stack.Screen name="order/[id]" options={{ title: 'Siparis' }} />
        <Stack.Screen name="reviews" options={{ title: 'Yorumlar' }} />
        <Stack.Screen name="notifications" options={{ title: 'Bildirimler' }} />
        <Stack.Screen name="settings" options={{ title: 'Ayarlar' }} />
      </Stack.Protected>
      <Stack.Protected guard={!user}>
        <Stack.Screen name="(auth)/login" options={{ title: 'Giris' }} />
        <Stack.Screen name="(auth)/register" options={{ title: 'Kayit ol' }} />
        <Stack.Screen name="(auth)/forgot" options={{ title: 'Sifremi unuttum' }} />
      </Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  return (
    <AuthProvider>
      <CartProvider>
        <Routes />
      </CartProvider>
    </AuthProvider>
  );
}
