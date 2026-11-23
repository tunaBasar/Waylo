import { useState } from 'react';
import { Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { ApiError } from '@/api';
import { useCart } from '@/features/cart/CartContext';
import { checkoutErrorMessage, ordersApi } from '@/features/orders/api';
import { Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
import { useOnline } from '@/features/network/NetworkContext';
import { money } from '@/features/format';

export default function CartScreen() {
  const cart = useCart();
  const router = useRouter();
  const online = useOnline();
  const [promo, setPromo] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function buy() {
    setBusy(true); setError(null);
    try {
      const o = await ordersApi.createPackage(cart.items, promo.trim() || undefined);
      cart.clear();
      router.replace(`/order/${o.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? checkoutErrorMessage(e) : 'Sepet satin alinamadi');
    } finally { setBusy(false); }
  }

  if (cart.items.length === 0) return <Screen><Title>Sepet</Title><Muted>Sepetiniz bos. Otel, ucus veya arac ekleyin.</Muted></Screen>;
  return (
    <Screen>
      <Title>Sepet</Title>
      {cart.items.map((i) => (
        <Card key={i.id}>
          <Muted>{i.vertical}</Muted>
          <Text>{i.label}</Text>
          {i.amount != null && i.currency ? <Text style={{ fontWeight: '700' }}>{money(i.amount, i.currency)}</Text> : null}
          <View><Button kind="secondary" title="Kaldir" onPress={() => cart.remove(i.id)} /></View>
        </Card>
      ))}
      <Muted>Kesin fiyat siparis olusturulurken sunucudan alinir.</Muted>
      <Field label="Promosyon kodu (istege bagli)" value={promo} onChangeText={setPromo} autoCapitalize="characters" />
      <ErrorBanner message={error} />
      <Button title={busy ? 'Isleniyor...' : 'Siparisi olustur'} onPress={buy} disabled={busy || !online} />
      {!online ? <Muted>Siparis olusturmak icin internet baglantisi gerekir.</Muted> : null}
    </Screen>
  );
}
