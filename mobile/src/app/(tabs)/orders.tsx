import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AppHeader } from '@/components/AppHeader';
import { StatusPill, type Tone } from '@/components/StatusPill';
import { Price } from '@/components/Price';
import { color, radius, space, type } from '@/theme/tokens';
import { Stack, useFocusEffect, useRouter } from 'expo-router';
import type { Order } from '@/api';
import { ordersApi } from '@/features/orders/api';
import { loadOrdersSnapshot, saveOrdersSnapshot } from '@/features/orders/cache';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Card, Muted, Screen } from '@/components/ui';
import { money, orderStatusInfo } from '@/features/format';
import { sortOrdersForDisplay } from '@/features/orders/sort';
import { orderSummary } from '@/features/orders/summary';

const STATUS_TONE: Record<string, Tone> = { PENDING: 'warning', CONFIRMED: 'success', CANCELLED: 'neutral', EXPIRED: 'error' };

function OrderList({ orders }: { orders: Order[] }) {
  const router = useRouter();
  return sortOrdersForDisplay(orders).map((o) => {
    const status = orderStatusInfo(o.status).label;
    const sum = orderSummary(o);
    return (
      <Pressable key={o.id} accessibilityRole="button" accessibilityLabel={`Siparis ${o.id.slice(0, 8)}, ${status}, ${money(o.totalAmount, o.currency)}`}
        onPress={() => router.push(`/order/${o.id}`)}>
        <Card>
          <View style={s.top}>
            <Text style={[type.monoMd, { color: color.onSurface }]}>{`#${o.id.slice(0, 8)}`}</Text>
            <StatusPill tone={STATUS_TONE[o.status] ?? 'neutral'} text={status} />
          </View>
          {sum.title ? <Text style={[type.titleMd, { color: color.onSurface }]} numberOfLines={2}>{sum.title}</Text> : null}
          {sum.count ? <Muted>{sum.count}</Muted> : null}
          {o.status === 'EXPIRED' ? <Muted>Ödeme süresi doldu, ödeme yapılamaz.</Muted> : null}
          <Price amount={o.totalAmount} currency={o.currency} />
        </Card>
      </Pressable>
    );
  });
}

export default function Orders() {
  const state = useAsync(async () => { const d = await ordersApi.list(); void saveOrdersSnapshot(d); return d; }, []);
  // Tabs stay mounted: refetch when the tab is re-focused (first focus is covered by the initial load).
  const focused = useRef(false);
  const { reload } = state;
  useFocusEffect(useCallback(() => { if (focused.current) reload(); focused.current = true; }, [reload]));
  const [cached, setCached] = useState<{ savedAt: string; orders: Order[] } | null>(null);
  useEffect(() => {
    if (!state.offline) return;
    let alive = true;
    loadOrdersSnapshot().then((c) => { if (alive) setCached(c); });
    return () => { alive = false; };
  }, [state.offline]);

  return (
    <View style={s.root}>
      <Stack.Screen options={{ headerShown: false }} />
      <AppHeader title="Siparişlerim" />
      <Screen>
      {state.offline && cached ? (
        <>
          <Text accessibilityRole="alert" style={[type.bodyMd, { backgroundColor: color.warningContainer, color: color.onWarningContainer, padding: space.md, borderRadius: radius.md, overflow: 'hidden' }]}>
            {`Cevrimdisi: ${new Date(cached.savedAt).toLocaleString('tr-TR')} tarihli kayitli liste gosteriliyor. Durumlar guncel olmayabilir.`}
          </Text>
          <OrderList orders={cached.orders} />
        </>
      ) : (
        <AsyncView<Order[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Henuz siparisiniz yok.">
          {(orders) => <OrderList orders={orders} />}
        </AsyncView>
      )}
      </Screen>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  top: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.sm },
});
