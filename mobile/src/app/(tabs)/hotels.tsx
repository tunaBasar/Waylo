import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Image } from 'expo-image';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { API_URL } from '@/config';
import { Stack, useRouter } from 'expo-router';
import type { Hotel, StayQuery } from '@/api';
import { hotelCoverUrl, hotelsApi, stayQueryString } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import { AsyncView, Button, ErrorBanner, Field } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { ModeTabs } from '@/components/ModeTabs';
import { PhotoFallback } from '@/components/PhotoFallback';
import { DateField } from '@/components/DateField';
import { Stepper } from '@/components/Stepper';
import { addDays, fmtDate, isoDate, parseIsoDate, validateStayDates } from '@/features/dates';
import { MAX_ADULTS_PER_ROOM, MAX_CHILD_AGE, MAX_CHILDREN, MAX_ROOMS, clampInt, resizeChildren } from '@/features/hotels/guests';
import { scoreText } from '@/features/reviews/api';
import { color, MIN_TOUCH, radius, space, type } from '@/theme/tokens';

function Stars({ n }: { n: number }) {
  const count = Math.max(0, Math.min(5, Math.round(n)));
  return (
    <View accessible accessibilityLabel={`${count} yıldız`} style={{ flexDirection: 'row' }}>
      {Array.from({ length: count }, (_, i) => (
        <MaterialCommunityIcons key={i} name="star" size={14} color={color.warning} accessibilityElementsHidden importantForAccessibility="no" />
      ))}
    </View>
  );
}

function stayDate(iso: string) {
  const d = parseIsoDate(iso);
  return d ? fmtDate(d) : iso;
}

export default function Hotels() {
  const router = useRouter();
  const { user } = useAuth();
  const [city, setCity] = useState('');
  const [checkIn, setCheckIn] = useState(() => addDays(new Date(), 7));
  const [checkOut, setCheckOut] = useState(() => addDays(new Date(), 9));
  const [rooms, setRooms] = useState(1);
  const [adults, setAdults] = useState(2);
  const [ages, setAges] = useState<number[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [query, setQuery] = useState<StayQuery | null>(null);
  const [searchCity, setSearchCity] = useState('');
  const [collapsed, setCollapsed] = useState(false);
  const state = useAsync(() => (query ? hotelsApi.search(query, searchCity || undefined) : Promise.resolve([] as Hotel[])), [query, searchCity], query !== null);

  const search = () => {
    const err = validateStayDates(isoDate(checkIn), isoDate(checkOut));
    setFormError(err);
    if (err) return;
    setSearchCity(city.trim());
    setQuery({ checkIn: isoDate(checkIn), checkOut: isoDate(checkOut), rooms, adults, childrenAges: ages });
    setCollapsed(true);
  };
  const login = () => { setReturnTo('/hotels'); router.navigate('/(auth)/login'); };
  const showForm = !(query && collapsed);

  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader
        title="Waylo"
        right={user ? undefined : (
          <Pressable accessibilityRole="button" accessibilityLabel="Giriş yap" onPress={login} style={s.login}>
            <Text style={[type.labelLg, { color: color.onPrimary }]}>Giriş yap</Text>
          </Pressable>
        )}
      />
      <ModeTabs active="hotels" />
      <ScrollView style={s.root} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
        {showForm ? (
          <View style={s.card}>
            <Text accessibilityRole="header" style={[type.headlineMd, { color: color.onSurface }]}>Otel ara</Text>
            <Field label="Şehir (isteğe bağlı)" value={city} onChangeText={setCity} />
            <DateField label="Giriş" value={checkIn} minimumDate={new Date()} onChange={(d) => {
              setCheckIn(d);
              if (checkOut <= d) setCheckOut(addDays(d, 1));
            }} />
            <DateField label="Çıkış" value={checkOut} minimumDate={addDays(checkIn, 1)} onChange={setCheckOut} />
            <Stepper label="Oda" value={rooms} min={1} max={MAX_ROOMS} onChange={(n) => { setRooms(n); setAdults((a) => Math.max(a, n)); }} />
            <Stepper label="Yetişkin" value={adults} min={rooms} max={rooms * MAX_ADULTS_PER_ROOM} onChange={setAdults} />
            <Stepper label="Çocuk" value={ages.length} min={0} max={MAX_CHILDREN} onChange={(n) => setAges((a) => resizeChildren(a, n))} />
            {ages.map((a, i) => (
              <Stepper key={i} label={`Çocuk ${i + 1} yaşı`} value={a} min={0} max={MAX_CHILD_AGE} onChange={(n) => setAges((cur) => cur.map((x, j) => (j === i ? clampInt(n, 0, MAX_CHILD_AGE) : x)))} />
            ))}
            <ErrorBanner message={formError} />
            <Button title="Ara" onPress={search} />
          </View>
        ) : query ? (
          <View style={s.summary}>
            <View style={{ flex: 1 }}>
              <Text numberOfLines={1} style={[type.titleMd, { color: color.onSurface }]}>{searchCity || 'Tüm şehirler'}</Text>
              <Text style={[type.monoSm, { color: color.onSurfaceVariant }]}>{`${stayDate(query.checkIn)} - ${stayDate(query.checkOut)}`}</Text>
              <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{`${query.rooms} oda · ${query.adults} yetişkin${query.childrenAges.length ? ` · ${query.childrenAges.length} çocuk` : ''}`}</Text>
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel="Değiştir" onPress={() => setCollapsed(false)} style={s.change}>
              <Text style={[type.labelLg, { color: color.primary }]}>Değiştir</Text>
            </Pressable>
          </View>
        ) : null}
        {query && (
          <AsyncView<Hotel[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bu tarihlerde uygun otel bulunamadı.">
            {(hotels) => hotels.map((h) => {
              const cover = hotelCoverUrl(API_URL, h);
              const hasScore = !!h.reviewCount && h.averageScore != null;
              return (
                <Pressable key={h.id} accessibilityRole="button" accessibilityLabel={`${h.name}, ${h.city}`} onPress={() => router.push(`/hotel/${h.id}?${stayQueryString(query)}`)} style={s.result}>
                  {cover ? (
                    <Image source={{ uri: cover }} accessibilityLabel={h.name} contentFit="cover" style={s.cover} />
                  ) : (
                    <PhotoFallback style={s.cover} />
                  )}
                  <View style={s.resultBody}>
                    <Stars n={h.starRating} />
                    <Text numberOfLines={2} style={[type.titleMd, { color: color.onSurface }]}>{h.name}</Text>
                    <Text numberOfLines={1} style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{`${h.city}, ${h.country}`}</Text>
                    {hasScore ? (
                      <Text style={[type.monoSm, { color: color.onSurface }]}>{scoreText(h.averageScore, h.reviewCount)}</Text>
                    ) : null}
                  </View>
                </Pressable>
              );
            })}
          </AsyncView>
        )}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.lg, gap: space.md },
  login: { minHeight: MIN_TOUCH, minWidth: MIN_TOUCH, paddingHorizontal: space.md, justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.onPrimary },
  card: { backgroundColor: color.surfaceLowest, borderRadius: radius.lg, padding: space.lg, gap: space.md, borderWidth: 1, borderColor: color.outlineVariant },
  summary: { flexDirection: 'row', alignItems: 'center', gap: space.md, backgroundColor: color.surfaceLowest, borderRadius: radius.lg, padding: space.md, borderWidth: 1, borderColor: color.outlineVariant },
  change: { minHeight: MIN_TOUCH, minWidth: MIN_TOUCH, paddingHorizontal: space.md, justifyContent: 'center', alignItems: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.primary },
  result: { flexDirection: 'row', backgroundColor: color.surfaceLowest, borderRadius: radius.lg, overflow: 'hidden', borderWidth: 1, borderColor: color.outlineVariant, minHeight: 112 },
  cover: { width: 112, alignSelf: 'stretch', minHeight: 112 },
  resultBody: { flex: 1, padding: space.md, gap: space.xs, justifyContent: 'center' },
});
