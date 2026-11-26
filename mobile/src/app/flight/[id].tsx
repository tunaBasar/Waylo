import { useState } from 'react';
import { Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import type { FlightOffer } from '@/api';
import { flightsApi } from '@/features/flights/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, Card, Field, Muted, Screen, Title } from '@/components/ui';
import { fmtLocal, money } from '@/features/format';

export default function FlightOfferScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const cart = useCart();
  const router = useRouter();
  const { user } = useAuth();
  const state = useAsync(() => flightsApi.offer(id), [id]);
  const [first, setFirst] = useState('');
  const [last, setLast] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  return (
    <Screen>
      <AsyncView<FlightOffer> state={state} emptyText="Teklif bulunamadi veya suresi doldu.">
        {(o) => (
          <>
            <Title>{`${o.airline} ${o.flightNumber}`}</Title>
            <Card>
              <Muted>{`${o.departureAirport} ${fmtLocal(o.departureTime)} → ${o.arrivalAirport} ${fmtLocal(o.arrivalTime)} · ${o.cabin}`}</Muted>
              <Text style={{ fontWeight: '700', fontSize: 18 }}>{money(o.totalAmount, o.currency)}</Text>
              <Muted>{`Bos koltuk: ${o.availableSeats}`}</Muted>
            </Card>
            <Field label="Yolcu adi" value={first} onChangeText={setFirst} autoCapitalize="words" />
            <Field label="Yolcu soyadi" value={last} onChangeText={setLast} autoCapitalize="words" />
            <Button title="Sepete ekle" disabled={!first.trim() || !last.trim()} onPress={() => {
              if (!user) { setReturnTo(`/flight/${id}`); router.navigate('/(auth)/login'); return; }
              const ok = cart.add({
                vertical: 'FLIGHT', label: `${o.flightNumber} ${first.trim()} ${last.trim()}`, amount: o.totalAmount, currency: o.currency,
                request: { offerId: o.offerId, passengerFirstName: first.trim(), passengerLastName: last.trim() },
              });
              setMsg(ok ? 'Sepete eklendi' : 'Zaten sepette veya sepet dolu');
            }} />
            {msg ? <Muted>{msg}</Muted> : null}
          </>
        )}
      </AsyncView>
    </Screen>
  );
}
