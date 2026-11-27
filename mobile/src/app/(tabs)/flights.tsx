import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
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
import { GuestLoginPill } from '@/components/GuestLoginPill';
import { space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

export default function Flights() {
  const router = useRouter();
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  const { user } = useAuth();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState(() => addDays(new Date(), 7));
  const [formError, setFormError] = useState<string | null>(null);
  const [q, setQ] = useState<{ from: string; to: string; date: string } | null>(null);
  const state = useAsync(() => (q ? flightsApi.search(q) : Promise.resolve([] as FlightOffer[])), [q], q !== null);
  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title="Waylo" right={user ? undefined : <GuestLoginPill onPress={() => { setReturnTo('/flights'); router.navigate('/(auth)/login'); }} />} />
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
              </Pressable>
            ))}
          </AsyncView>
        )}
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
