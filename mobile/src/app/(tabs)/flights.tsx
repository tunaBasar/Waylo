import { useState } from 'react';
import { Pressable, Text } from 'react-native';
import { useRouter } from 'expo-router';
import type { FlightOffer } from '@/api';
import { flightsApi } from '@/features/flights/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, Field, Muted, Screen, Title } from '@/components/ui';
import { fmtLocal, money } from '@/features/format';

export default function Flights() {
  const router = useRouter();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState('');
  const [q, setQ] = useState<{ from: string; to: string; date: string } | null>(null);
  const state = useAsync(() => flightsApi.search(q!), [q], q !== null);
  return (
    <Screen>
      <Title>Ucus ara</Title>
      <Field label="Nereden (IATA, orn. IST)" value={from} onChangeText={(v) => setFrom(v.toUpperCase())} maxLength={3} />
      <Field label="Nereye (IATA)" value={to} onChangeText={(v) => setTo(v.toUpperCase())} maxLength={3} />
      <Field label="Tarih (YYYY-AA-GG)" value={date} onChangeText={setDate} />
      <Button title="Ara" disabled={!from || !to || !date} onPress={() => setQ({ from, to, date })} />
      {q && (
        <AsyncView<FlightOffer[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bu tarih icin ucus bulunamadi.">
          {(offers) => offers.map((o) => (
            <Pressable key={o.offerId} accessibilityRole="button" onPress={() => router.push(`/flight/${o.offerId}`)}>
              <Card>
                <Text style={{ fontWeight: '700' }}>{`${o.airline} ${o.flightNumber}`}</Text>
                <Muted>{`${o.departureAirport} ${fmtLocal(o.departureTime)} → ${o.arrivalAirport} ${fmtLocal(o.arrivalTime)}`}</Muted>
                <Text style={{ fontWeight: '700' }}>{money(o.totalAmount, o.currency)}</Text>
              </Card>
            </Pressable>
          ))}
        </AsyncView>
      )}
    </Screen>
  );
}
