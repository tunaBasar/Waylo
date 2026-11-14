import { useState } from 'react';
import { Text } from 'react-native';
import type { TripOffer } from '@/api';
import { busApi } from '@/features/bus/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, Field, Muted, Screen, Title } from '@/components/ui';
import { DateField } from '@/components/DateField';
import { addDays, isoDate } from '@/features/dates';
import { fmtLocal, money } from '@/features/format';

export default function Bus() {
  const status = useAsync(() => busApi.salesStatus(), []);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState(() => addDays(new Date(), 7));
  const [q, setQ] = useState<{ from: string; to: string; date: string } | null>(null);
  const trips = useAsync(() => busApi.search(q!.from, q!.to, q!.date), [q], q !== null && status.data?.enabled === true);
  return (
    <Screen>
      <Title>Otobus seferleri</Title>
      <Muted>Bu bolumde seferler sadece goruntulenir.</Muted>
      <AsyncView<{ enabled: boolean }> state={status} emptyText="Durum alinamadi.">
        {(s) => !s.enabled ? <Muted>Otobus satisi su an kapali.</Muted> : (
          <>
            <Field label="Nereden (sehir)" value={from} onChangeText={setFrom} autoCapitalize="words" />
            <Field label="Nereye (sehir)" value={to} onChangeText={setTo} autoCapitalize="words" />
            <DateField label="Tarih" value={date} minimumDate={new Date()} onChange={setDate} />
            <Button title="Ara" disabled={!from || !to} onPress={() => setQ({ from: from.trim(), to: to.trim(), date: isoDate(date) })} />
            {q && (
              <AsyncView<TripOffer[]> state={trips} isEmpty={(d) => d.length === 0} emptyText="Sefer bulunamadi.">
                {(list) => list.map((t) => (
                  <Card key={`${t.tripId}`}>
                    <Text style={{ fontWeight: '700' }}>{`${t.fromCity} → ${t.toCity}`}</Text>
                    <Muted>{`${t.companyName ?? ''} · ${fmtLocal(t.departureAt)} - ${fmtLocal(t.arrivalAt)} · ${t.availableSeats} bos koltuk`}</Muted>
                    <Text style={{ fontWeight: '700' }}>{money(t.price, t.currency)}</Text>
                  </Card>
                ))}
              </AsyncView>
            )}
          </>
        )}
      </AsyncView>
    </Screen>
  );
}
