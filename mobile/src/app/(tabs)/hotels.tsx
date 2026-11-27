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
import { GuestLoginPill } from '@/components/GuestLoginPill';
import { AppHeader } from '@/components/AppHeader';
import { ModeTabs } from '@/components/ModeTabs';
import { PhotoFallback } from '@/components/PhotoFallback';
import { DateField } from '@/components/DateField';
import { Stepper } from '@/components/Stepper';
import { addDays, fmtDate, isoDate, parseIsoDate, validateStayDates } from '@/features/dates';
import { MAX_ADULTS_PER_ROOM, MAX_CHILD_AGE, MAX_CHILDREN, MAX_ROOMS, clampInt, resizeChildren } from '@/features/hotels/guests';
import { scoreText } from '@/features/reviews/api';
import { MIN_TOUCH, space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

function stayDate(iso: string) {
  const d = parseIsoDate(iso);
  return d ? fmtDate(d) : iso;
}

export default function Hotels() {
  const router = useRouter();
  const { user } = useAuth();
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const [city, setCity] = useState('');
  const [checkIn, setCheckIn] = useState(() => addDays(new Date(), 7));
  const [checkOut, setCheckOut] = useState(() => addDays(new Date(), 9));
  const [rooms, setRooms] = useState(1);
  const [adults, setAdults] = useState(2);
  const [ages, setAges] = useState<number[]>([]);
  const [guestsOpen, setGuestsOpen] = useState(false);
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
  const guestsText = `${rooms} oda · ${adults} yetişkin · ${ages.length} çocuk${ages.length ? ` (${ages.join(', ')} yaş)` : ''}`;

  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      {showForm || !query ? (
        <AppHeader title="Waylo" right={user ? undefined : <GuestLoginPill onPress={login} />} />
      ) : (
        <AppHeader
          title={searchCity ? `${searchCity} otelleri` : 'Oteller'}
          subtitle={`${stayDate(query.checkIn)} - ${stayDate(query.checkOut)} · ${query.rooms} oda, ${query.adults} yetişkin${query.childrenAges.length ? `, ${query.childrenAges.length} çocuk` : ''}`}
          onBack={() => setCollapsed(false)}
        />
      )}
      {showForm ? <ModeTabs active="hotels" /> : null}
      <ScrollView style={s.root} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
        {showForm ? (
          <>
            <View style={{ gap: space.xs }}>
              <Text accessibilityRole="header" style={[type.headlineLg, { color: color.onSurface }]}>Yolculuğunuzu tek yerden planlayın</Text>
              <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>Üye olmadan otel, uçuş, araç ve otobüs seferlerini arayabilirsiniz.</Text>
            </View>
            <View style={s.card}>
              <Field label="Şehir (isteğe bağlı)" value={city} onChangeText={setCity} />
              <View style={s.row}>
                <View style={s.half}>
                  <DateField label="Giriş" value={checkIn} minimumDate={new Date()} onChange={(d) => {
                    setCheckIn(d);
                    if (checkOut <= d) setCheckOut(addDays(d, 1));
                  }} />
                </View>
                <View style={s.half}>
                  <DateField label="Çıkış" value={checkOut} minimumDate={addDays(checkIn, 1)} onChange={setCheckOut} />
                </View>
              </View>
              <Pressable accessibilityRole="button" accessibilityLabel={`Misafirler: ${guestsText}`} accessibilityState={{ expanded: guestsOpen }} onPress={() => setGuestsOpen((v) => !v)} style={s.guests}>
                <MaterialCommunityIcons name="account-multiple-outline" size={20} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
                <Text style={[type.bodyLg, { color: color.onSurface, flex: 1 }]}>{guestsText}</Text>
                <MaterialCommunityIcons name={guestsOpen ? 'chevron-up' : 'chevron-down'} size={22} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
              </Pressable>
              {guestsOpen ? (
                <View style={s.steppers}>
                  <Stepper label="Oda" value={rooms} min={1} max={MAX_ROOMS} onChange={(n) => { setRooms(n); setAdults((a) => Math.max(a, n)); }} />
                  <Stepper label="Yetişkin" value={adults} min={rooms} max={rooms * MAX_ADULTS_PER_ROOM} onChange={setAdults} />
                  <Stepper label="Çocuk" value={ages.length} min={0} max={MAX_CHILDREN} onChange={(n) => setAges((a) => resizeChildren(a, n))} />
                  {ages.map((a, i) => (
                    <Stepper key={i} label={`Çocuk ${i + 1} yaşı`} value={a} min={0} max={MAX_CHILD_AGE} onChange={(n) => setAges((cur) => cur.map((x, j) => (j === i ? clampInt(n, 0, MAX_CHILD_AGE) : x)))} />
                  ))}
                </View>
              ) : null}
              <ErrorBanner message={formError} />
              <Button title="Ara" onPress={search} />
            </View>
          </>
        ) : null}
        {query && !showForm && (
          <AsyncView<Hotel[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bu tarihlerde uygun otel bulunamadı.">
            {(hotels) => (
              <>
                <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>{`${hotels.length} tesis listeleniyor · Oda fiyatları otel detayında gösterilir`}</Text>
                {hotels.map((h) => {
                  const cover = hotelCoverUrl(API_URL, h);
                  const score = scoreText(h.averageScore, h.reviewCount);
                  const stars = Math.max(0, Math.min(5, Math.round(h.starRating)));
                  return (
                    <Pressable key={h.id} accessibilityRole="button" accessibilityLabel={`${h.name}, ${h.city}`} onPress={() => router.push(`/hotel/${h.id}?${stayQueryString(query)}`)} style={s.result}>
                      {cover ? (
                        <Image source={{ uri: cover }} accessibilityLabel={h.name} contentFit="cover" style={s.cover} />
                      ) : (
                        <PhotoFallback style={s.cover} />
                      )}
                      <View style={s.resultBody}>
                        <View style={s.metaRow}>
                          <View accessible accessibilityLabel={`${stars} yıldız`} style={s.starBox}>
                            <MaterialCommunityIcons name="star" size={16} color={color.primary} accessibilityElementsHidden importantForAccessibility="no" />
                            <Text style={[type.labelLg, { color: color.primary }]}>{`${stars} yıldız`}</Text>
                          </View>
                          {score ? (
                            <View style={s.chip}><Text style={[type.monoSm, { color: color.onSurface }]}>{score}</Text></View>
                          ) : null}
                        </View>
                        <Text numberOfLines={2} style={[type.headlineMd, { color: color.onSurface }]}>{h.name}</Text>
                        <Text numberOfLines={1} style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{`${h.city}, ${h.country}`}</Text>
                      </View>
                    </Pressable>
                  );
                })}
              </>
            )}
          </AsyncView>
        )}
      </ScrollView>
    </View>
  );
}

const makeStyles = ({ color, radius, card }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.xl, gap: space.lg },
  card: { backgroundColor: card.backgroundColor, borderWidth: card.borderWidth, borderColor: card.borderColor, borderRadius: radius.lg, padding: space.lg, gap: space.md },
  row: { flexDirection: 'row', gap: space.md },
  half: { flex: 1 },
  guests: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: space.sm, backgroundColor: color.surfaceLowest, borderRadius: radius.md, borderWidth: 1, borderColor: color.outlineVariant, paddingHorizontal: space.md },
  steppers: { gap: space.md },
  result: { backgroundColor: card.backgroundColor, borderWidth: card.borderWidth, borderColor: card.borderColor, borderRadius: radius.lg, overflow: 'hidden', minHeight: MIN_TOUCH },
  cover: { width: '100%', aspectRatio: 16 / 9, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg },
  resultBody: { padding: space.lg, gap: space.xs },
  metaRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  starBox: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  chip: { backgroundColor: color.surfaceContainer, borderRadius: radius.full, paddingHorizontal: space.sm, paddingVertical: space.xs },
});
