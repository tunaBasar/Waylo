import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { ApiError, type Quote } from '@/api';
import { hotelsApi } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, ErrorBanner, Field } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { CountdownBanner } from '@/components/CountdownBanner';
import { Price } from '@/components/Price';
import { fmtDate, parseIsoDate } from '@/features/dates';
import { offerPolicyText } from '@/features/format';
import { checkoutErrorMessage } from '@/features/orders/api';
import { space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

const day = (iso: string) => { const d = parseIsoDate(iso); return d ? fmtDate(d) : iso; };

export default function QuoteScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
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
      setError(e instanceof ApiError ? checkoutErrorMessage(e) : 'Rezervasyon oluşturulamadı');
    } finally { setBusy(false); }
  }

  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader
        title={state.data?.roomTypeName ?? 'Teklif'}
        subtitle={state.data ? `${day(state.data.checkIn)} - ${day(state.data.checkOut)} · ${state.data.nights} gece` : undefined}
        onBack={() => (router.canGoBack() ? router.back() : router.replace('/hotels'))}
      />
      <ScrollView style={s.root} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
      <AsyncView<Quote> state={state} emptyText="Teklif bulunamadı.">
        {(q) => (
          <>
            {q.expired ? null : <CountdownBanner until={q.expiresAt} label="Fiyat kilitlendi" />}
            <View style={s.card}>
              <Text style={[type.titleMd, { color: color.onSurface }]}>{q.roomTypeName}</Text>
              <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{q.ratePlanName}</Text>
              <Text style={[type.monoMd, { color: color.onSurface }]}>{`${day(q.checkIn)} - ${day(q.checkOut)} · ${q.nights} gece`}</Text>
              <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{offerPolicyText(q.cancellationPolicy, q.freeCancellationDaysBefore, q.checkIn)}</Text>
              <Price amount={q.totalAmount} currency={q.currency} />
            </View>
            {q.expired ? <ErrorBanner message="Teklifin süresi doldu. Lütfen yeniden arayın." /> : (
              <>
                <ErrorBanner message={error} />
                <Field label="Promosyon kodu (isteğe bağlı)" value={promo} onChangeText={setPromo} autoCapitalize="characters" />
                <View style={s.actions}>
                  <View style={s.half}>
                    <Button kind="secondary" title="Sepete ekle" onPress={() => {
                  const ok = cart.add({ vertical: 'HOTEL', label: `${q.roomTypeName} · ${q.ratePlanName} · ${q.checkIn} - ${q.checkOut}`, amount: q.totalAmount, currency: q.currency, request: { quoteId: q.quoteId } });
                  setAdded(ok ? 'Sepete eklendi' : 'Zaten sepette veya sepet dolu');
                }} />
                  </View>
                  <View style={s.half}>
                  <Button title={busy ? 'Oluşturuluyor...' : 'Rezervasyon yap'} onPress={book} disabled={busy} />
                  </View>
                </View>
                {added ? <Text accessibilityRole="alert" style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{added}</Text> : null}
              </>
            )}
          </>
        )}
      </AsyncView>
      </ScrollView>
    </View>
  );
}

const makeStyles = ({ color, radius, card }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.xl, gap: space.md },
  card: { backgroundColor: card.backgroundColor, borderWidth: card.borderWidth, borderColor: card.borderColor, borderRadius: radius.lg, padding: space.lg, gap: space.sm },
  actions: { flexDirection: 'row', gap: space.md },
  half: { flex: 1 },
});
