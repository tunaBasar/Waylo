import { useState } from 'react';
import { Pressable, Text } from 'react-native';
import { useRouter } from 'expo-router';
import type { Hotel, StayQuery } from '@/api';
import { hotelsApi, stayQueryString } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
import { DateField } from '@/components/DateField';
import { Stepper } from '@/components/Stepper';
import { addDays, isoDate, validateStayDates } from '@/features/dates';
import { MAX_ADULTS_PER_ROOM, MAX_CHILD_AGE, MAX_CHILDREN, MAX_ROOMS, clampInt, resizeChildren } from '@/features/hotels/guests';

export default function Hotels() {
  const router = useRouter();
  const [city, setCity] = useState('');
  const [checkIn, setCheckIn] = useState(() => addDays(new Date(), 7));
  const [checkOut, setCheckOut] = useState(() => addDays(new Date(), 9));
  const [rooms, setRooms] = useState(1);
  const [adults, setAdults] = useState(2);
  const [ages, setAges] = useState<number[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [query, setQuery] = useState<StayQuery | null>(null);
  const [searchCity, setSearchCity] = useState('');
  const state = useAsync(() => hotelsApi.search(query!, searchCity || undefined), [query, searchCity], query !== null);

  const search = () => {
    const err = validateStayDates(isoDate(checkIn), isoDate(checkOut));
    setFormError(err);
    if (err) return;
    setSearchCity(city.trim());
    setQuery({ checkIn: isoDate(checkIn), checkOut: isoDate(checkOut), rooms, adults, childrenAges: ages });
  };
  return (
    <Screen>
      <Title>Otel ara</Title>
      <Field label="Sehir (istege bagli)" value={city} onChangeText={setCity} />
      <DateField label="Giris" value={checkIn} minimumDate={new Date()} onChange={(d) => {
        setCheckIn(d);
        if (checkOut <= d) setCheckOut(addDays(d, 1));
      }} />
      <DateField label="Cikis" value={checkOut} minimumDate={addDays(checkIn, 1)} onChange={setCheckOut} />
      <Stepper label="Oda" value={rooms} min={1} max={MAX_ROOMS} onChange={(n) => { setRooms(n); setAdults((a) => Math.max(a, n)); }} />
      <Stepper label="Yetiskin" value={adults} min={rooms} max={rooms * MAX_ADULTS_PER_ROOM} onChange={setAdults} />
      <Stepper label="Cocuk" value={ages.length} min={0} max={MAX_CHILDREN} onChange={(n) => setAges((a) => resizeChildren(a, n))} />
      {ages.map((a, i) => (
        <Stepper key={i} label={`Cocuk ${i + 1} yasi`} value={a} min={0} max={MAX_CHILD_AGE} onChange={(n) => setAges((cur) => cur.map((x, j) => (j === i ? clampInt(n, 0, MAX_CHILD_AGE) : x)))} />
      ))}
      <ErrorBanner message={formError} />
      <Button title="Ara" onPress={search} />
      {query && (
        <AsyncView<Hotel[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bu tarihlerde uygun otel bulunamadi.">
          {(hotels) => hotels.map((h) => (
            <Pressable key={h.id} accessibilityRole="button" accessibilityLabel={`${h.name}, ${h.city}`} onPress={() => router.push(`/hotel/${h.id}?${stayQueryString(query)}`)}>
              <Card>
                <Text style={{ fontWeight: '700' }}>{h.name}</Text>
                <Muted>{`${h.city}, ${h.country} · ${h.starRating} yildiz`}</Muted>
              </Card>
            </Pressable>
          ))}
        </AsyncView>
      )}
    </Screen>
  );
}
