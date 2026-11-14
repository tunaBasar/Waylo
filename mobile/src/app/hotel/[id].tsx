import { useState } from 'react';
import { Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import type { Availability, Hotel } from '@/api';
import { hotelsApi } from '@/features/hotels/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, ErrorBanner, Muted, Screen, Title } from '@/components/ui';
import { money } from '@/features/format';
import { reviewsApi, scoreLabel, type HotelReviewPage } from '@/features/reviews/api';
import { ApiError } from '@/api';
import { checkoutErrorMessage } from '@/features/orders/api';

export default function HotelDetail() {
  const router = useRouter();
  const p = useLocalSearchParams<{ id: string; checkIn: string; checkOut: string; rooms: string; adults: string; childrenAges?: string | string[] }>();
  const ages = ([] as string[]).concat(p.childrenAges ?? []).map(Number).filter((n) => !Number.isNaN(n));
  const stay = { checkIn: p.checkIn, checkOut: p.checkOut, rooms: Number(p.rooms) || 1, adults: Number(p.adults) || 1, childrenAges: ages };
  const hotel = useAsync(() => hotelsApi.get(p.id), [p.id]);
  const avail = useAsync(() => hotelsApi.availability(p.id, stay), [p.id, p.checkIn, p.checkOut]);
  const reviews = useAsync(() => reviewsApi.list(p.id), [p.id]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function pick(roomTypeId: string, ratePlanId: string, key: string) {
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
      <AsyncView<Hotel> state={hotel} emptyText="Otel bulunamadi.">
        {(h) => (<><Title>{h.name}</Title><Muted>{`${h.address}, ${h.city}`}</Muted>{h.description ? <Text>{h.description}</Text> : null}{h.reviewCount && h.averageScore != null ? <Text style={{ fontWeight: '700' }}>{`${scoreLabel(h.averageScore)} / 5 (${h.reviewCount} yorum)`}</Text> : null}</>)}
      </AsyncView>
      <ErrorBanner message={error} />
      <AsyncView<Availability> state={avail} isEmpty={(a) => a.offers.length === 0} emptyText="Bu tarihlerde musait oda yok.">
        {(a) => a.offers.map((o) => {
          const key = `${o.roomTypeId}:${o.ratePlanId}`;
          return (
            <Card key={key}>
              <Text style={{ fontWeight: '700' }}>{o.roomTypeName}</Text>
              <Muted>{`${o.ratePlanName} · ${o.mealPlan} · ${o.nights} gece`}</Muted>
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
    </Screen>
  );
}
