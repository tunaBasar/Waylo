import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { ApiError } from '@/api';
import { useAuth } from '@/features/auth/AuthContext';
import { useCart } from '@/features/cart/CartContext';
import { checkoutErrorMessage, ordersApi } from '@/features/orders/api';
import { Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
import { Price } from '@/components/Price';
import { StickyActionBar } from '@/components/StickyActionBar';
import { useOnline } from '@/features/network/NetworkContext';
import { verticalText } from '@/features/format';
import { color, radius, space, type, MIN_TOUCH } from '@/theme/tokens';

type IconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];
const VERTICAL_ICON: Record<string, IconName> = { HOTEL: 'bed-outline', FLIGHT: 'airplane', CAR: 'car-outline', BUS: 'bus' };

export default function CartScreen() {
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
      setError(e instanceof ApiError ? checkoutErrorMessage(e) : 'Sepet satin alinamadi');
    } finally { setBusy(false); }
  }

  if (cart.items.length === 0) return <Screen><Title>Sepet</Title><Muted>Sepetiniz bos. Otel, ucus veya arac ekleyin.</Muted></Screen>;

  // A total is shown only when every item has a price in one currency.
  const first = cart.items[0];
  const currency = first.currency;
  const sum = cart.items.every((i) => i.amount != null && i.currency === currency) ? cart.items.reduce((t, i) => t + (i.amount ?? 0), 0) : null;
  const disabled = busy || !online;
  const actionLabel = busy ? 'İşleniyor...' : 'Siparişi oluştur';

  return (
    <View style={s.root}>
      <Screen>
        <Title>Sepet</Title>
        {cart.items.map((i) => (
          <Card key={i.id}>
            <View style={s.row}>
              <MaterialCommunityIcons name={VERTICAL_ICON[i.vertical] ?? 'tag-outline'} size={24} color={color.primary} accessibilityElementsHidden importantForAccessibility="no" />
              <View style={s.rowText}>
                <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>{verticalText(i.vertical)}</Text>
                <Text style={[type.titleMd, { color: color.onSurface }]}>{i.label}</Text>
                {i.amount != null && i.currency ? <Price amount={i.amount} currency={i.currency} /> : null}
              </View>
              <Pressable accessibilityRole="button" accessibilityLabel={`Kaldır: ${i.label}`} onPress={() => cart.remove(i.id)} style={s.remove}>
                <MaterialCommunityIcons name="trash-can-outline" size={18} color={color.error} accessibilityElementsHidden importantForAccessibility="no" />
                <Text style={[type.labelLg, { color: color.error }]}>Kaldır</Text>
              </Pressable>
            </View>
          </Card>
        ))}
        <Field label="Promosyon kodu (isteğe bağlı)" value={promo} onChangeText={setPromo} autoCapitalize="characters" />
        <Muted>Sipariş oluşturulurken uygulanır</Muted>
        <Muted>Nihai fiyat sipariş oluşturulduğunda kesinleşir.</Muted>
        <ErrorBanner message={error} />
        {!online ? <Muted>Siparis olusturmak icin internet baglantisi gerekir.</Muted> : null}
        {sum == null ? <Button title={actionLabel} onPress={buy} disabled={disabled} /> : null}
      </Screen>
      {sum != null && currency ? <StickyActionBar amount={sum} currency={currency} actionLabel={actionLabel} onAction={buy} disabled={disabled} /> : null}
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md },
  rowText: { flex: 1, gap: space.xs },
  remove: { minHeight: MIN_TOUCH, minWidth: MIN_TOUCH, flexDirection: 'row', alignItems: 'center', gap: space.xs, paddingHorizontal: space.sm, borderRadius: radius.md },
});
