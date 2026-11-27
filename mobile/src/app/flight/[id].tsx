import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import type { FlightOffer } from '@/api';
import { flightsApi } from '@/features/flights/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, Card, Field, Muted, Screen } from '@/components/ui';
import { Price } from '@/components/Price';
import { fmtLocal } from '@/features/format';
import { AppHeader } from '@/components/AppHeader';
import { space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

export default function FlightOfferScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const cart = useCart();
  const router = useRouter();
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  const { user } = useAuth();
  const state = useAsync(() => flightsApi.offer(id), [id]);
  const [first, setFirst] = useState('');
  const [last, setLast] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const back = () => (router.canGoBack() ? router.back() : router.replace('/hotels'));
  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      {state.data ? (
        <AppHeader title={state.data.flightNumber} subtitle={`${state.data.departureAirport} → ${state.data.arrivalAirport} · ${fmtLocal(state.data.departureTime)}`} onBack={back} />
      ) : <AppHeader title="Uçuş" onBack={back} />}
      <Screen>
      <AsyncView<FlightOffer> state={state} emptyText="Teklif bulunamadı veya süresi doldu.">
        {(o) => (
          <>
            <Card>
              <View style={s.rowBetween}>
                <Text style={s.title}>{`${o.airline} ${o.flightNumber}`}</Text>
                <Price amount={o.totalAmount} currency={o.currency} />
              </View>
              <View style={s.route}>
                <Text style={s.mono}>{`${o.departureAirport} ${fmtLocal(o.departureTime)}`}</Text>
                <MaterialCommunityIcons name="arrow-right" size={16} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
                <Text style={s.mono}>{`${o.arrivalAirport} ${fmtLocal(o.arrivalTime)}`}</Text>
              </View>
              <Text style={s.muted}>{`${o.cabin} · ${o.availableSeats} boş koltuk`}</Text>
            </Card>
            <Card>
              <Text style={s.title}>Yolcu</Text>
              <Field label="Yolcu adı" value={first} onChangeText={setFirst} autoCapitalize="words" />
              <Field label="Yolcu soyadı" value={last} onChangeText={setLast} autoCapitalize="words" />
              <Button title="Sepete ekle" disabled={!first.trim() || !last.trim()} onPress={() => {
                if (!user) { setReturnTo(`/flight/${id}`); router.navigate('/(auth)/login'); return; }
                const ok = cart.add({
                  vertical: 'FLIGHT', label: `${o.flightNumber} ${first.trim()} ${last.trim()}`, amount: o.totalAmount, currency: o.currency,
                  request: { offerId: o.offerId, passengerFirstName: first.trim(), passengerLastName: last.trim() },
                });
                setMsg(ok ? 'Sepete eklendi' : 'Zaten sepette veya sepet dolu');
              }} />
              {msg ? <Muted>{msg}</Muted> : null}
            </Card>
          </>
        )}
      </AsyncView>
      </Screen>
    </View>
  );
}

const makeStyles = ({ color, type }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: space.sm },
  route: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  title: { ...type.titleMd, color: color.onSurface, flex: 1 },
  mono: { ...type.monoMd, color: color.onSurface },
  muted: { ...type.bodyMd, color: color.onSurfaceVariant },
});
