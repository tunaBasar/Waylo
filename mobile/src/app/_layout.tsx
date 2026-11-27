import { useEffect, useState } from 'react';
import { ActivityIndicator, Platform, View } from 'react-native';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from '@expo-google-fonts/crimson-pro/useFonts';
import { THEME_FONTS } from '@/theme/fonts';
import { StatusBar } from 'expo-status-bar';
import { ThemeProvider, useTheme } from '@/theme/ThemeContext';
import * as Linking from 'expo-linking';
import { AuthProvider, useAuth } from '@/features/auth/AuthContext';
import { CartProvider } from '@/features/cart/CartContext';
import { NetworkProvider, OfflineBanner } from '@/features/network/NetworkContext';
import { rememberOrderLink } from '@/features/deeplinks';

SplashScreen.preventAutoHideAsync().catch(() => {});

/** An order deep link opened while signed out is remembered; the tabs layout opens it after login. */
function usePendingOrderLink(signedIn: boolean) {
  const url = Linking.useLinkingURL();
  // On web the guard rewrites the address bar before the async initial URL resolves, so read it synchronously once.
  const [initialWebUrl] = useState(() => (Platform.OS === 'web' && typeof window !== 'undefined' ? window.location.href : null));
  useEffect(() => { if (!signedIn) { rememberOrderLink(initialWebUrl); rememberOrderLink(url); } }, [url, initialWebUrl, signedIn]);
}

function Routes() {
  const { color, type } = useTheme();
  const { user, loading } = useAuth();
  usePendingOrderLink(!!user);
  // The stored session is restored first: deciding earlier would send a signed-in visitor to login.
  if (loading) return <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}><ActivityIndicator accessibilityLabel="Yükleniyor" /></View>;
  return (
    <Stack screenOptions={{
      headerShown: true,
      headerStyle: { backgroundColor: color.surface },
      headerShadowVisible: false,
      headerTintColor: color.onSurface,
      headerTitleStyle: { fontFamily: type.headlineMd.fontFamily, fontSize: type.headlineMd.fontSize },
      contentStyle: { backgroundColor: color.surface },
    }}>
      {/* open to guests: the catalog is served anonymously */}
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="hotel/[id]" options={{ title: 'Otel' }} />
      <Stack.Screen name="flight/[id]" options={{ title: 'Uçuş teklifi' }} />
      <Stack.Protected guard={!!user}>
        <Stack.Screen name="quote/[id]" options={{ title: 'Teklif' }} />
        <Stack.Screen name="cart" options={{ title: 'Sepet' }} />
        <Stack.Screen name="order/[id]" options={{ title: 'Sipariş' }} />
        <Stack.Screen name="reviews" options={{ title: 'Yorumlar' }} />
        <Stack.Screen name="notifications" options={{ title: 'Bildirimler' }} />
        <Stack.Screen name="settings" options={{ title: 'Ayarlar' }} />
        <Stack.Screen name="support/index" options={{ title: 'Destek taleplerim' }} />
        <Stack.Screen name="support/new" options={{ title: 'Destek talebi' }} />
        <Stack.Screen name="support/[id]" options={{ title: 'Destek talebi' }} />
      </Stack.Protected>
      <Stack.Protected guard={!user}>
        <Stack.Screen name="(auth)/login" options={{ title: 'Giriş' }} />
        <Stack.Screen name="(auth)/register" options={{ title: 'Kayıt ol' }} />
        <Stack.Screen name="(auth)/forgot" options={{ title: 'Şifremi unuttum' }} />
      </Stack.Protected>
      {/* Email links work signed in or out. */}
      <Stack.Screen name="verify" options={{ title: 'E-posta doğrulama' }} />
      <Stack.Screen name="reset-password" options={{ title: 'Şifre sıfırlama' }} />
    </Stack>
  );
}

/** On a wide web window the app is a centered phone-width column. */
function WebFrame({ children }: { children: React.ReactNode }) {
  const { color, name } = useTheme();
  if (Platform.OS !== 'web') return <>{children}</>;
  return (
    <View style={{ flex: 1, minHeight: '100%' as unknown as number, flexDirection: 'row', justifyContent: 'center', backgroundColor: color.backdrop }}>
      <View style={{
        flex: 1, width: '100%', maxWidth: 480, backgroundColor: color.surface,
        borderLeftWidth: name === 'light' ? 1 : 0, borderRightWidth: name === 'light' ? 1 : 0, borderColor: color.outlineVariant,
      }}>{children}</View>
    </View>
  );
}

function Shell() {
  const { color, name } = useTheme();
  return (
    <WebFrame>
      <StatusBar style={name === 'dark' ? 'light' : 'dark'} />
      <NetworkProvider>
        <AuthProvider>
          <CartProvider>
            <View style={{ flex: 1, backgroundColor: color.surface }}>
              <OfflineBanner />
              <Routes />
            </View>
          </CartProvider>
        </AuthProvider>
      </NetworkProvider>
    </WebFrame>
  );
}

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts(THEME_FONTS);
  const fontsReady = fontsLoaded || !!fontError; // on error continue with system fonts
  useEffect(() => { if (fontsReady) SplashScreen.hideAsync().catch(() => {}); }, [fontsReady]);
  if (!fontsReady) return null;
  return (
    <ThemeProvider>
      <Shell />
    </ThemeProvider>
  );
}
