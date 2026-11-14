import { Pressable, Text } from 'react-native';
import { useRouter } from 'expo-router';
import type { Order } from '@/api';
import { ordersApi } from '@/features/orders/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Card, Muted, Screen, Title } from '@/components/ui';
import { money, ORDER_STATUS_TR } from '@/features/format';

export default function Orders() {
  const router = useRouter();
  const state = useAsync(() => ordersApi.list(), []);
  return (
    <Screen>
      <Title>Siparislerim</Title>
      <AsyncView<Order[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Henuz siparisiniz yok.">
        {(orders) => orders.map((o) => (
          <Pressable key={o.id} accessibilityRole="button" onPress={() => router.push(`/order/${o.id}`)}>
            <Card>
              <Text style={{ fontWeight: '700' }}>{`#${o.id.slice(0, 8)}`}</Text>
              <Muted>{ORDER_STATUS_TR[o.status] ?? o.status}</Muted>
              <Text>{money(o.totalAmount, o.currency)}</Text>
            </Card>
          </Pressable>
        ))}
      </AsyncView>
    </Screen>
  );
}
