import { useState } from 'react';
import { RefreshControl, Pressable, ScrollView, Text, View } from 'react-native';
import { useRouter, type Href } from 'expo-router';
import { useAsync } from '@/features/useAsync';
import { capabilitiesText, STATUS_LABEL, supportApi, type SupportCase } from '@/features/support/api';
import { AsyncView, colors } from '@/components/ui';
import { StatusPill } from '@/components/StatusPill';
import { accountStyles, Hint, Icon, Section } from '@/features/account/AccountUi';
import { color } from '@/theme/tokens';

export default function SupportList() {
  const router = useRouter();
  const caps = useAsync(() => supportApi.capabilities(), []);
  const cases = useAsync(() => supportApi.myList(), []);
  const [refreshing, setRefreshing] = useState(false);
  const reloadCases = cases.reload;
  const reloadCaps = caps.reload;
  function onRefresh() {
    setRefreshing(true);
    reloadCases(); reloadCaps();
    setTimeout(() => setRefreshing(false), 600);
  }
  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={{ padding: 16, gap: 12 }}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} accessibilityLabel="Yenile" />}>
      {caps.data ? <Section><Hint>{capabilitiesText(caps.data)}</Hint></Section> : null}
      <AsyncView<SupportCase[]> state={cases} isEmpty={(d) => d.length === 0} emptyText="Henüz destek talebiniz yok. Bir siparişin detayından talep açabilirsiniz.">
        {(list) => list.map((c) => (
          <Pressable key={c.id} accessibilityRole="button"
            accessibilityLabel={`${c.subject}, ${STATUS_LABEL[c.status]}`}
            onPress={() => router.push(`/support/${c.id}` as Href)}>
            <Section>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                <View style={{ flex: 1, gap: 4 }}>
                  <Text style={accountStyles.strong}>{c.subject}</Text>
                  <Text style={accountStyles.meta}>{new Date(c.updatedAt).toLocaleString('tr-TR')}</Text>
                </View>
                <Icon name="chevron-right" tint={color.outline} />
              </View>
              <StatusPill tone={c.status === 'CLOSED' ? 'neutral' : c.status === 'ANSWERED' ? 'success' : 'warning'} text={STATUS_LABEL[c.status]} />
            </Section>
          </Pressable>
        ))}
      </AsyncView>
    </ScrollView>
  );
}
