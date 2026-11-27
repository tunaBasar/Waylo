import { Text, View } from 'react-native';
import { notificationsApi, type AppNotification } from '@/features/notifications/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView } from '@/components/ui';
import { StackScreen, useAccountStyles, Icon, Section } from '@/features/account/AccountUi';

export default function Notifications() {
  const state = useAsync(() => notificationsApi.mine(), []);
  const accountStyles = useAccountStyles();
  return (
    <StackScreen title="Bildirimler">
      <AsyncView<AppNotification[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Bildiriminiz yok.">
        {(list) => list.map((n) => (
          <Section key={n.id}>
            <View style={{ flexDirection: 'row', gap: 12 }}>
              <Icon name="bell-outline" />
              <View style={{ flex: 1, gap: 4 }}>
                <Text style={accountStyles.strong}>{n.subject || n.type}</Text>
                <Text style={accountStyles.body}>{n.body}</Text>
                <Text style={accountStyles.meta}>{new Date(n.createdAt).toLocaleString('tr-TR')}</Text>
              </View>
            </View>
          </Section>
        ))}
      </AsyncView>
    </StackScreen>
  );
}
