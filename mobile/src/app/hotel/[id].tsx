import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import type { Availability } from '@/api';
import { getHotelOrNotPublic, hotelsApi, type HotelResult } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { HotelGallery } from '@/features/hotels/HotelGallery';
import { AsyncView, Button, ErrorBanner, Muted, Title } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { PhotoFallback } from '@/components/PhotoFallback';
import { Price } from '@/components/Price';
import { fmtDate, parseIsoDate } from '@/features/dates';
import { MIN_TOUCH, space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';
import { money, offerPolicyText, offerSubtitle } from '@/features/format';
import { reviewsApi, scoreText, type HotelReviewPage } from '@/features/reviews/api';
import { ApiError } from '@/api';
import { checkoutErrorMessage } from '@/features/orders/api';

export function emptyReasonText(r: Availability['emptyReason']): string {
  if (!r) return 'Bu tarihlerde uygun oda yok.';
  const d = r.date ? ` (${r.date})` : '';
  if (r.code === 'NO_STOCK') return `Bu tarihlerde oda kalmadı${d}.`;
  if (r.code === 'NO_PRICE_FOR_OCCUPANCY') return `Bu kişi sayısı için fiyat tanımlı değil${d}.`;
  return 'Bu grup için uygun oda tipi yok.';
}

export function shiftedStay(checkIn: string, checkOut: string, next: string): { checkIn: string; checkOut: string } {
  const nights = Math.round((Date.parse(checkOut) - Date.parse(checkIn)) / 86_400_000);
  return { checkIn: next, checkOut: new Date(Date.parse(next) + nights * 86_400_000).toISOString().slice(0, 10) };
}

export default function HotelDetail() {
  const router = useRouter();
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const { user } = useAuth();
  const p = useLocalSearchParams<{ id: string; checkIn: string; checkOut: string; rooms: string; adults: string; childrenAges?: string | string[] }>();
  const ages = ([] as string[]).concat(p.childrenAges ?? []).map(Number).filter((n) => !Number.isNaN(n));
  const stay = { checkIn: p.checkIn, checkOut: p.checkOut, rooms: Number(p.rooms) || 1, adults: Number(p.adults) || 1, childrenAges: ages };
  const hotel = useAsync(() => getHotelOrNotPublic(p.id), [p.id]);
  const notPublic = hotel.data?.kind === 'notPublic';
  const avail = useAsync(() => hotelsApi.availability(p.id, stay), [p.id, p.checkIn, p.checkOut], !notPublic && !hotel.loading);
  const reviews = useAsync(() => reviewsApi.list(p.id), [p.id], !notPublic && !hotel.loading);
  const photos = useAsync(() => hotelsApi.photos(p.id), [p.id], hotel.data?.kind === 'ok');
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function pick(roomTypeId: string, ratePlanId: string, key: string) {
    if (!user) {
      const qs = new URLSearchParams({ checkIn: p.checkIn ?? '', checkOut: p.checkOut ?? '', rooms: String(stay.rooms), adults: String(stay.adults) });
      ages.forEach((a) => qs.append('childrenAges', String(a)));
      setReturnTo(`/hotel/${p.id}?${qs}`);
      router.navigate('/(auth)/login');
      return;
    }
    setBusy(key); setError(null);
    try {
      const q = await hotelsApi.createQuote({ hotelId: p.id, roomTypeId, ratePlanId, ...stay });
      router.push(`/quote/${q.quoteId}`);
    } catch (e) {
      setError(e instanceof ApiError ? checkoutErrorMessage(e) : 'Teklif oluşturulamadı');
    } finally { setBusy(null); }
  }

  const nightsText = (() => {
    const a = p.checkIn ? parseIsoDate(p.checkIn) : null;
    const b = p.checkOut ? parseIsoDate(p.checkOut) : null;
    if (!a || !b) return undefined;
    const n = Math.round((b.getTime() - a.getTime()) / 86_400_000);
    return `${fmtDate(a)} - ${fmtDate(b)} · ${n} gece`;
  })();
  const hotelName = hotel.data?.kind === 'ok' ? hotel.data.hotel.name : 'Otel';

  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title={hotelName} subtitle={nightsText} onBack={() => (router.canGoBack() ? router.back() : router.replace('/hotels'))} />
      <ScrollView style={s.root} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
      <AsyncView<HotelResult> state={hotel} emptyText="Otel bulunamadı.">
        {(r) => r.kind === 'notPublic' ? (<><Title>Bu otel şu anda yayında değil</Title><Muted>Otel yayından kaldırılmış veya henüz yayına alınmamış olabilir.</Muted><Button title="Geri dön" kind="secondary" onPress={() => router.back()} /></>) : ((h) => (
          <>
            {photos.data && photos.data.length > 0 ? <HotelGallery hotelId={p.id} photos={photos.data} /> : photos.loading ? null : <PhotoFallback style={s.noPhoto} />}
            <View style={{ gap: space.xs }}>
              <View accessible accessibilityLabel={`${h.starRating} yıldız`} style={{ flexDirection: 'row' }}>
                {Array.from({ length: Math.max(0, Math.min(5, Math.round(h.starRating))) }, (_, i) => (
                  <MaterialCommunityIcons key={i} name="star" size={16} color={color.primary} accessibilityElementsHidden importantForAccessibility="no" />
                ))}
              </View>
              <Title>{h.name}</Title>
              <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{`${h.address}, ${h.city}`}</Text>
              {scoreText(h.averageScore, h.reviewCount) ? <Text style={[type.monoMd, { color: color.onSurface }]}>{scoreText(h.averageScore, h.reviewCount)}</Text> : null}
            </View>
            {h.description ? <Text style={[type.bodyLg, { color: color.onSurface }]}>{h.description}</Text> : null}
          </>
        ))(r.hotel)}
      </AsyncView>
      {notPublic ? null : (<>
      <ErrorBanner message={error} />
      <Text accessibilityRole="header" style={[type.headlineMd, { color: color.onSurface }]}>Oda teklifleri</Text>
      <AsyncView<Availability> state={avail} isEmpty={() => false} emptyText="Bu tarihlerde uygun oda yok.">
        {(a) => a.offers.length === 0 ? (
          <View style={[s.card, { alignItems: 'center' }]}>
            <MaterialCommunityIcons name="calendar-remove-outline" size={32} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
            <Text style={[type.titleMd, { color: color.onSurface }]}>Bu tarihlerde uygun oda yok</Text>
            <Text style={[type.bodyMd, { color: color.onSurfaceVariant, textAlign: 'center' }]}>{emptyReasonText(a.emptyReason)}</Text>
            {a.nextAvailableCheckIn ? (
              <Button title={`Şu tarihleri göster: ${(() => { const d = parseIsoDate(a.nextAvailableCheckIn); return d ? fmtDate(d) : a.nextAvailableCheckIn; })()}`} onPress={() => { if (a.nextAvailableCheckIn) router.setParams(shiftedStay(p.checkIn, p.checkOut, a.nextAvailableCheckIn)); }} />
            ) : null}
          </View>
        ) : a.offers.map((o) => {
          const key = `${o.roomTypeId}:${o.ratePlanId}`;
          const policy = offerPolicyText(o.cancellationPolicy, o.freeCancellationDaysBefore, p.checkIn);
          const free = policy.endsWith('ücretsiz iptal');
          return (
            <Pressable key={key} accessibilityRole="button" accessibilityLabel={`${busy === key ? 'Teklif alınıyor' : 'Teklif al'}: ${o.roomTypeName}, ${money(o.totalAmount, o.currency)}`}
              accessibilityState={{ disabled: busy !== null }} disabled={busy !== null} onPress={() => pick(o.roomTypeId, o.ratePlanId, key)}
              style={[s.card, busy !== null && { opacity: 0.6 }]}>
              <View style={s.offerFoot}>
                <View style={{ flex: 1, gap: space.xs }}>
                  <Text style={[type.titleMd, { color: color.onSurface }]}>{o.roomTypeName}</Text>
                  <Text style={[type.bodyMd, { color: color.onSurfaceVariant }]}>{offerSubtitle(o.ratePlanName, o.mealPlan, o.nights)}</Text>
                </View>
                <Price amount={o.totalAmount} currency={o.currency} />
                <MaterialCommunityIcons name={busy === key ? 'loading' : 'chevron-right'} size={24} color={color.onSurfaceVariant} accessibilityElementsHidden importantForAccessibility="no" />
              </View>
              <View style={s.policyRow}>
                {free ? <MaterialCommunityIcons name="check-circle" size={16} color={color.success} accessibilityElementsHidden importantForAccessibility="no" /> : null}
                <Text style={[type.bodyMd, { color: free ? color.success : color.onSurfaceVariant, flex: 1 }]}>{policy}</Text>
              </View>
            </Pressable>
          );
        })}
      </AsyncView>
      <View style={s.offerFoot}>
        <Text accessibilityRole="header" style={[type.headlineMd, { color: color.onSurface }]}>Değerlendirmeler</Text>
        {reviews.data && reviews.data.items.length > 0 && hotel.data?.kind === 'ok' && scoreText(hotel.data.hotel.averageScore, hotel.data.hotel.reviewCount) ? (
          <Text style={[type.monoSm, { color: color.onSurfaceVariant }]}>{`${(hotel.data.hotel.averageScore ?? 0).toFixed(1).replace('.', ',')} / 5`}</Text>
        ) : null}
      </View>
      <AsyncView<HotelReviewPage> state={reviews} isEmpty={(r) => r.items.length === 0} emptyText="Henüz yorum yok.">
        {(r) => r.items.map((v) => (
          <View key={v.id} style={s.card}>
            <View style={s.offerFoot}>
              <Text style={[type.titleMd, { color: color.onSurface }]}>{`Puan: ${v.score} / 5`}</Text>
              <Text style={[type.monoSm, { color: color.onSurfaceVariant }]}>{fmtDate(new Date(v.createdAt))}</Text>
            </View>
            {v.text ? <Text style={[type.bodyLg, { color: color.onSurface }]}>{v.text}</Text> : null}
            {v.partnerResponse ? (
              <View style={s.reply}>
                <Text style={[type.labelSm, { color: color.onSurfaceVariant }]}>Otel yanıtı</Text>
                <Text style={[type.bodyMd, { color: color.onSurface }]}>{v.partnerResponse}</Text>
              </View>
            ) : null}
          </View>
        ))}
      </AsyncView>
      </>)}
      </ScrollView>
    </View>
  );
}

const makeStyles = ({ color, radius, card, name }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.xl, gap: space.md },
  noPhoto: { width: '100%', aspectRatio: 16 / 9, borderRadius: radius.lg, overflow: 'hidden' },
  card: { backgroundColor: card.backgroundColor, borderWidth: card.borderWidth, borderColor: card.borderColor, borderRadius: radius.lg, padding: space.lg, gap: space.sm, minHeight: MIN_TOUCH },
  offerFoot: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.sm },
  policyRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  reply: { backgroundColor: name === 'dark' ? color.surfaceContainer : color.surfaceLow, borderRadius: radius.md, borderLeftWidth: 2, borderLeftColor: color.primary, padding: space.md, gap: space.xs },
});
