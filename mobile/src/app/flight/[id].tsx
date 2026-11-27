import { useState } from 'react';
import { Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import type { FlightOffer } from '@/api';
import { flightsApi } from '@/features/flights/api';
import { useAsync } from '@/features/useAsync';
import { useCart } from '@/features/cart/CartContext';
import { AsyncView, Button, Card, Field, Muted, Screen } from '@/components/ui';
import { Price } from '@/components/Price';
import { fmtLocal } from '@/features/format';
import { color, space, type } from '@/theme/tokens';

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
      <AsyncView<FlightOffer> state={state} emptyText="Teklif bulunamadı veya süresi doldu.">
        {(o) => (
          <>
            <Card>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text style={[type.headlineMd, { color: color.onSurface }]}>{o.airline}</Text>
                <Text style={[type.monoMd, { color: color.onSurfaceVariant }]}>{o.flightNumber}</Text>
              </View>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.sm }}>
                <Text style={[type.monoMd, { color: color.onSurface }]}>{`${o.departureAirport} ${fmtLocal(o.departureTime)}`}</Text>
                <MaterialCommunityIcons name="arrow-right" size={16} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
                <Text style={[type.monoMd, { color: color.onSurface }]}>{`${o.arrivalAirport} ${fmtLocal(o.arrivalTime)}`}</Text>
              </View>
              <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{`${o.cabin} · ${o.availableSeats} boş koltuk`}</Text>
              <Price amount={o.totalAmount} currency={o.currency} />
            </Card>
            <Card>
              <Text style={[type.titleMd, { color: color.onSurface }]}>Yolcu</Text>
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
  );
}
