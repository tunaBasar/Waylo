import { useAuth } from '@/features/auth/AuthContext';
import { setReturnTo } from '@/features/auth/gate';
import { useState } from 'react';
import { Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import type { Availability } from '@/api';
import { getHotelOrNotPublic, hotelsApi, type HotelResult } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { HotelGallery } from '@/features/hotels/HotelGallery';
import { AsyncView, Button, Card, ErrorBanner, Muted, Screen, Title } from '@/components/ui';
import { money, offerPolicyText } from '@/features/format';
import { reviewsApi, scoreLabel, type HotelReviewPage } from '@/features/reviews/api';
import { ApiError } from '@/api';
import { checkoutErrorMessage } from '@/features/orders/api';

export function emptyReasonText(r: Availability['emptyReason']): string {
  if (!r) return 'Bu tarihlerde musait oda yok.';
  const d = r.date ? ` (${r.date})` : '';
  if (r.code === 'NO_STOCK') return `Bu tarihlerde oda kalmadi${d}.`;
  if (r.code === 'NO_PRICE_FOR_OCCUPANCY') return `Bu kisi sayisi icin fiyat tanimli degil${d}.`;
  return 'Bu grup icin uygun oda tipi yok.';
}

export function shiftedStay(checkIn: string, checkOut: string, next: string): { checkIn: string; checkOut: string } {
  const nights = Math.round((Date.parse(checkOut) - Date.parse(checkIn)) / 86_400_000);
  return { checkIn: next, checkOut: new Date(Date.parse(next) + nights * 86_400_000).toISOString().slice(0, 10) };
}

export default function HotelDetail() {
  const router = useRouter();
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
      router.push('/(auth)/login');
      return;
    }
    setBusy(key); setError(null);
    try {
      const q = await hotelsApi.createQuote({ hotelId: p.id, roomTypeId, ratePlanId, ...stay });
      router.push(`/quote/${q.quoteId}`);
    } catch (e) {
      setError(e instanceof ApiError ? checkoutErrorMessage(e) : 'Teklif olusturulamadi');
    } finally { setBusy(null); }
  }

  return (
    <Screen>
      <AsyncView<HotelResult> state={hotel} emptyText="Otel bulunamadi.">
        {(r) => r.kind === 'notPublic' ? (<><Title>Bu otel şu anda yayında değil</Title><Muted>Otel yayından kaldırılmış veya henüz yayına alınmamış olabilir.</Muted><Button title="Geri dön" kind="secondary" onPress={() => router.back()} /></>) : ((h) => (<><Title>{h.name}</Title><Muted>{`${h.address}, ${h.city}`}</Muted>{h.description ? <Text>{h.description}</Text> : null}{h.reviewCount && h.averageScore != null ? <Text style={{ fontWeight: '700' }}>{`${scoreLabel(h.averageScore)} / 5 (${h.reviewCount} yorum)`}</Text> : null}</>))(r.hotel)}
      </AsyncView>
      {notPublic ? null : (<>
      {photos.data && photos.data.length > 0 ? <HotelGallery hotelId={p.id} photos={photos.data} /> : null}
      <ErrorBanner message={error} />
      <AsyncView<Availability> state={avail} isEmpty={() => false} emptyText="Bu tarihlerde musait oda yok.">
        {(a) => a.offers.length === 0 ? (
          <Card>
            <Text>{emptyReasonText(a.emptyReason)}</Text>
            {a.nextAvailableCheckIn ? (
              <Button title={`Su tarihleri goster: ${a.nextAvailableCheckIn}`} onPress={() => router.setParams(shiftedStay(p.checkIn, p.checkOut, a.nextAvailableCheckIn!))} />
            ) : null}
          </Card>
        ) : a.offers.map((o) => {
          const key = `${o.roomTypeId}:${o.ratePlanId}`;
          return (
            <Card key={key}>
              <Text style={{ fontWeight: '700' }}>{o.roomTypeName}</Text>
              <Muted>{`${o.ratePlanName} · ${o.mealPlan} · ${o.nights} gece`}</Muted>
              <Muted>{offerPolicyText(o.cancellationPolicy, o.freeCancellationDaysBefore, p.checkIn)}</Muted>
              <Text style={{ fontWeight: '700' }}>{money(o.totalAmount, o.currency)}</Text>
              <Button title={busy === key ? 'Teklif aliniyor...' : 'Teklif al'} disabled={busy !== null} onPress={() => pick(o.roomTypeId, o.ratePlanId, key)} />
            </Card>
          );
        })}
      </AsyncView>
      <Title>Yorumlar</Title>
      <AsyncView<HotelReviewPage> state={reviews} isEmpty={(r) => r.items.length === 0} emptyText="Henuz yorum yok.">
        {(r) => r.items.map((v) => (
          <Card key={v.id}>
            <Text style={{ fontWeight: '700' }}>{`${v.score} / 5`}</Text>
            {v.text ? <Text>{v.text}</Text> : null}
            {v.partnerResponse ? <Muted>{`Otel yaniti: ${v.partnerResponse}`}</Muted> : null}
            <Muted>{new Date(v.createdAt).toLocaleDateString('tr-TR')}</Muted>
          </Card>
        ))}
      </AsyncView>
      </>)}
    </Screen>
  );
}
