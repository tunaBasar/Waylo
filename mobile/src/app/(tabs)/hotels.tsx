import { useState } from 'react';
import { Pressable, Text } from 'react-native';
import { useRouter } from 'expo-router';
import type { Hotel, StayQuery } from '@/api';
import { hotelsApi, stayQueryString } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, Field, Muted, Screen, Title } from '@/components/ui';

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const addDays = (n: number) => { const d = new Date(); d.setDate(d.getDate() + n); return iso(d); };

export default function Hotels() {
  const router = useRouter();
  const [city, setCity] = useState('');
  const [checkIn, setCheckIn] = useState(addDays(7));
  const [checkOut, setCheckOut] = useState(addDays(9));
  const [adults, setAdults] = useState('2');
  const [query, setQuery] = useState<StayQuery | null>(null);
  const [searchCity, setSearchCity] = useState('');
  const state = useAsync(() => hotelsApi.search(query!, searchCity || undefined), [query, searchCity], query !== null);

  const search = () => {
    setSearchCity(city.trim());
    setQuery({ checkIn, checkOut, rooms: 1, adults: Math.max(1, parseInt(adults, 10) || 1), childrenAges: [] });
  };
  return (
    <Screen>
      <Title>Otel ara</Title>
      <Field label="Sehir (istege bagli)" value={city} onChangeText={setCity} />
      <Field label="Giris (YYYY-AA-GG)" value={checkIn} onChangeText={setCheckIn} />
      <Field label="Cikis (YYYY-AA-GG)" value={checkOut} onChangeText={setCheckOut} />
      <Field label="Yetiskin" value={adults} onChangeText={setAdults} keyboardType="number-pad" />
      <Button title="Ara" onPress={search} />
      {query && (
        <AsyncView<Hotel[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bu tarihlerde uygun otel bulunamadi.">
          {(hotels) => hotels.map((h) => (
            <Pressable key={h.id} accessibilityRole="button" onPress={() => router.push(`/hotel/${h.id}?${stayQueryString(query)}`)}>
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
