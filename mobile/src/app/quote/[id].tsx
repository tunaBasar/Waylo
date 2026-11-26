import { useState } from 'react';
import { Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ApiError, type Quote } from '@/api';
import { hotelsApi } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
import { money, offerPolicyText } from '@/features/format';
import { checkoutErrorMessage } from '@/features/orders/api';

export default function QuoteScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const cart = useCart();
  const state = useAsync(() => hotelsApi.getQuote(id), [id]);
  const [promo, setPromo] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [added, setAdded] = useState<string | null>(null);

  async function book() {
    setBusy(true); setError(null);
    try {
      const o = await hotelsApi.orderFromQuote(id, promo.trim() ? promo.trim().toUpperCase() : undefined);
      router.replace(`/order/${o.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? checkoutErrorMessage(e) : 'Rezervasyon olusturulamadi');
    } finally { setBusy(false); }
  }

  return (
    <Screen>
      <AsyncView<Quote> state={state} emptyText="Teklif bulunamadi.">
        {(q) => (
          <>
            <Title>{q.roomTypeName}</Title>
            <Card>
              <Muted>{`${q.ratePlanName} · ${q.checkIn} - ${q.checkOut} · ${q.nights} gece`}</Muted>
              <Muted>{offerPolicyText(q.cancellationPolicy, q.freeCancellationDaysBefore, q.checkIn)}</Muted>
              <Text style={{ fontWeight: '700', fontSize: 18 }}>{money(q.totalAmount, q.currency)}</Text>
              <Muted>{`Gecerlilik: ${new Date(q.expiresAt).toLocaleTimeString('tr-TR')}`}</Muted>
            </Card>
            {q.expired ? <ErrorBanner message="Teklifin suresi doldu. Lutfen yeniden arayin." /> : (
              <>
                <ErrorBanner message={error} />
                <Field label="Promosyon kodu (istege bagli)" value={promo} onChangeText={setPromo} autoCapitalize="characters" />
                <Button title={busy ? 'Olusturuluyor...' : 'Rezervasyon yap'} onPress={book} disabled={busy} />
                <Button kind="secondary" title="Sepete ekle" onPress={() => {
                  const ok = cart.add({ vertical: 'HOTEL', label: `${q.roomTypeName} · ${q.ratePlanName} · ${q.checkIn} - ${q.checkOut}`, amount: q.totalAmount, currency: q.currency, request: { quoteId: q.quoteId } });
                  setAdded(ok ? 'Sepete eklendi' : 'Zaten sepette veya sepet dolu');
                }} />
                {added ? <Muted>{added}</Muted> : null}
              </>
            )}
          </>
        )}
      </AsyncView>
    </Screen>
  );
}
