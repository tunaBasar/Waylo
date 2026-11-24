import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, Text } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import type { Order } from '@/api';
import { ordersApi } from '@/features/orders/api';
import { loadOrdersSnapshot, saveOrdersSnapshot } from '@/features/orders/cache';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Card, Muted, Screen, Title } from '@/components/ui';
import { money, orderStatusInfo } from '@/features/format';

function OrderList({ orders }: { orders: Order[] }) {
  const router = useRouter();
  return orders.map((o) => {
    const status = orderStatusInfo(o.status).label;
    return (
      <Pressable key={o.id} accessibilityRole="button" accessibilityLabel={`Siparis ${o.id.slice(0, 8)}, ${status}, ${money(o.totalAmount, o.currency)}`}
        onPress={() => router.push(`/order/${o.id}`)}>
        <Card>
          <Text style={{ fontWeight: '700' }}>{`#${o.id.slice(0, 8)}`}</Text>
          <Muted>{status}</Muted>
          {o.status === 'EXPIRED' ? <Muted>Ödeme süresi doldu, ödeme yapılamaz.</Muted> : null}
          <Text>{money(o.totalAmount, o.currency)}</Text>
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
    <Screen>
      <Title>Siparislerim</Title>
      {state.offline && cached ? (
        <>
          <Text accessibilityRole="alert" style={{ backgroundColor: '#fff3cd', color: '#664d03', padding: 8, borderRadius: 8 }}>
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
  );
}
