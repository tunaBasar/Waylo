import { Text } from 'react-native';
import { notificationsApi, type AppNotification } from '@/features/notifications/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Card, Muted, Screen, Title } from '@/components/ui';

export default function Notifications() {
  const state = useAsync(() => notificationsApi.mine(), []);
  return (
    <Screen>
      <Title>Bildirimler</Title>
      <AsyncView<AppNotification[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bildiriminiz yok.">
        {(list) => list.map((n) => (
          <Card key={n.id}>
            <Text style={{ fontWeight: '700' }}>{n.subject || n.type}</Text>
            <Text>{n.body}</Text>
            <Muted>{new Date(n.createdAt).toLocaleString('tr-TR')}</Muted>
          </Card>
        ))}
      </AsyncView>
    </Screen>
  );
}
