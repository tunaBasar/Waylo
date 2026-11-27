import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Stack, useRouter } from 'expo-router';
import { setReturnTo } from '@/features/auth/gate';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import type { FlightOffer } from '@/api';
import { flightsApi } from '@/features/flights/api';
import { useAsync } from '@/features/useAsync';
import { useAuth } from '@/features/auth/AuthContext';
import { AsyncView, Button, Card, ErrorBanner, Field, Screen } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { ModeTabs } from '@/components/ModeTabs';
import { Price } from '@/components/Price';
import { DateField } from '@/components/DateField';
import { addDays, isoDate } from '@/features/dates';
import { fmtLocal } from '@/features/format';
import { color, MIN_TOUCH, space, type } from '@/theme/tokens';

export default function Flights() {
  const router = useRouter();
  const { user } = useAuth();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState(() => addDays(new Date(), 7));
  const [formError, setFormError] = useState<string | null>(null);
  const [q, setQ] = useState<{ from: string; to: string; date: string } | null>(null);
  const state = useAsync(() => (q ? flightsApi.search(q) : Promise.resolve([] as FlightOffer[])), [q], q !== null);
  return (
    <View style={{ flex: 1, backgroundColor: color.surface }}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title="Waylo" right={user ? undefined : (
        <Pressable accessibilityRole="button" accessibilityLabel="Giriş yap" onPress={() => { setReturnTo('/flights'); router.navigate('/(auth)/login'); }} style={{ minHeight: MIN_TOUCH, justifyContent: 'center', paddingHorizontal: space.sm }}>
          <Text style={[type.labelLg, { color: color.onPrimary }]}>Giriş yap</Text>
        </Pressable>
      )} />
      <ModeTabs active="flights" />
      <Screen>
        <Card>
          <Field label="Nereden (IATA, örn. IST)" value={from} onChangeText={(v) => setFrom(v.toUpperCase())} maxLength={3} />
          <Field label="Nereye (IATA)" value={to} onChangeText={(v) => setTo(v.toUpperCase())} maxLength={3} />
          <DateField label="Tarih" value={date} minimumDate={new Date()} onChange={setDate} />
          <ErrorBanner message={formError} />
          <Button title="Ara" disabled={from.length !== 3 || to.length !== 3} onPress={() => {
            if (from === to) { setFormError('Kalkış ve varış aynı olamaz.'); return; }
            setFormError(null); setQ({ from, to, date: isoDate(date) });
          }} />
        </Card>
        {q && (
          <AsyncView<FlightOffer[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bu tarih için uçuş bulunamadı.">
            {(offers) => offers.map((o) => (
              <Pressable key={o.offerId} accessibilityRole="button" accessibilityLabel={`${o.airline} ${o.flightNumber}, ${o.departureAirport} ${fmtLocal(o.departureTime)} ${o.arrivalAirport} ${fmtLocal(o.arrivalTime)}`} onPress={() => router.push(`/flight/${o.offerId}`)}>
                <Card>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={[type.titleMd, { color: color.onSurface }]}>{o.airline}</Text>
                    <Text style={[type.monoSm, { color: color.onSurfaceVariant }]}>{o.flightNumber}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.sm }}>
                    <Text style={[type.monoMd, { color: color.onSurface }]}>{`${o.departureAirport} ${fmtLocal(o.departureTime)}`}</Text>
                    <MaterialCommunityIcons name="arrow-right" size={16} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
                    <Text style={[type.monoMd, { color: color.onSurface }]}>{`${o.arrivalAirport} ${fmtLocal(o.arrivalTime)}`}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{`${o.cabin} · ${o.availableSeats} boş koltuk`}</Text>
                    <Price amount={o.totalAmount} currency={o.currency} />
                  </View>
                </Card>
              </Pressable>
            ))}
          </AsyncView>
        )}
      </Screen>
    </View>
  );
}
