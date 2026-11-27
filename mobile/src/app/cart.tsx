import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Stack, useRouter } from 'expo-router';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { ApiError } from '@/api';
import { useAuth } from '@/features/auth/AuthContext';
import { useCart } from '@/features/cart/CartContext';
import { checkoutErrorMessage, ordersApi } from '@/features/orders/api';
import { Button, Card, ErrorBanner, Field, Muted, Screen } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { Price } from '@/components/Price';
import { StickyActionBar, STICKY_ACTION_BAR_HEIGHT } from '@/components/StickyActionBar';
import { useOnline } from '@/features/network/NetworkContext';
import { verticalText } from '@/features/format';
import { space, MIN_TOUCH } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

type IconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];
const VERTICAL_ICON: Record<string, IconName> = { HOTEL: 'bed-outline', FLIGHT: 'airplane', CAR: 'car-outline', BUS: 'bus' };

export default function CartScreen() {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const insets = useSafeAreaInsets();
  const cart = useCart();
  const router = useRouter();
  const online = useOnline();
  const { user } = useAuth();
  const [promo, setPromo] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function buy() {
    setBusy(true); setError(null);
    try {
      const o = await ordersApi.createPackage(cart.items, promo.trim() || undefined, user);
      cart.clear();
      router.replace(`/order/${o.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? checkoutErrorMessage(e) : 'Sepet satın alınamadı');
    } finally { setBusy(false); }
  }

  const goBack = () => (router.canGoBack() ? router.back() : router.replace('/hotels'));

  if (cart.items.length === 0) {
    return (
      <View style={s.root}>
        <Stack.Screen options={{ headerShown: false }} />
        <AppHeader title="Sepetim" onBack={goBack} />
        <Screen>
          <View style={s.empty}>
            <MaterialCommunityIcons name="cart-outline" size={32} color={color.outline} accessibilityElementsHidden importantForAccessibility="no" />
            <Text style={[type.bodyMd, { color: color.onSurfaceVariant, textAlign: 'center' }]}>Sepetiniz boş.</Text>
          </View>
          <Muted>Otel, uçuş, araç veya otobüs ekleyin.</Muted>
        </Screen>
      </View>
    );
  }

  // A total is shown only when every item has a price in one currency.
  const first = cart.items[0];
  const currency = first.currency;
  const sum = cart.items.every((i) => i.amount != null && i.currency === currency) ? cart.items.reduce((t, i) => t + (i.amount ?? 0), 0) : null;
  const disabled = busy || !online;
  const actionLabel = busy ? 'İşleniyor...' : 'Siparişi oluştur';

  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title={`Sepetim (${cart.items.length} ürün)`} onBack={goBack} />
      <Screen>
        {cart.items.map((i) => (
          <Card key={i.id}>
            <View style={s.row}>
              <MaterialCommunityIcons name={VERTICAL_ICON[i.vertical] ?? 'tag-outline'} size={24} color={color.primary} accessibilityElementsHidden importantForAccessibility="no" />
              <View style={s.rowText}>
                <Text style={[type.titleMd, { color: color.onSurface }]}>{`${verticalText(i.vertical)} · ${i.label}`}</Text>
                {i.amount != null && i.currency ? <Price amount={i.amount} currency={i.currency} style={{ color: color.primary }} /> : null}
              </View>
              <Pressable accessibilityRole="button" accessibilityLabel={`Kaldır: ${i.label}`} onPress={() => cart.remove(i.id)} style={s.remove}>
                <Text style={[type.labelLg, { color: color.error }]}>Kaldır</Text>
              </Pressable>
            </View>
          </Card>
        ))}
        <Field label="Promosyon kodu (isteğe bağlı)" value={promo} onChangeText={setPromo} autoCapitalize="characters" />
        <Muted>Sipariş oluşturulurken uygulanır</Muted>
        <View style={s.info}>
          <MaterialCommunityIcons name="information-outline" size={18} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
          <Text style={[type.bodyMd, { color: color.onSurfaceVariant, flex: 1 }]}>Nihai fiyat sipariş oluşturulduğunda kesinleşir.</Text>
        </View>
        <ErrorBanner message={error} />
        {!online ? <Muted>Sipariş oluşturmak için internet bağlantısı gerekir.</Muted> : null}
        {sum == null ? <Button title={actionLabel} onPress={buy} disabled={disabled} /> : null}
        {sum != null ? <View style={{ height: STICKY_ACTION_BAR_HEIGHT + insets.bottom }} /> : null}
      </Screen>
      {sum != null && currency ? <StickyActionBar totalLabel="Toplam tutar" amount={sum} currency={currency} actionLabel={actionLabel} onAction={buy} disabled={disabled} /> : null}
    </View>
  );
}

const makeStyles = ({ color, radius }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md },
  rowText: { flex: 1, gap: space.xs },
  remove: { minHeight: MIN_TOUCH, minWidth: MIN_TOUCH, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.sm, borderRadius: radius.md },
  info: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm },
  empty: { alignItems: 'center', gap: space.sm, paddingVertical: space.xxl },
});
