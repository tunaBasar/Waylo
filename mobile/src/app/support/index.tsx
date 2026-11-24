import { useState } from 'react';
import { RefreshControl, Pressable, ScrollView, Text } from 'react-native';
import { useRouter, type Href } from 'expo-router';
import { useAsync } from '@/features/useAsync';
import { capabilitiesText, STATUS_LABEL, supportApi, type SupportCase } from '@/features/support/api';
import { AsyncView, Card, colors, Muted, Title } from '@/components/ui';

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
      <Title>Destek taleplerim</Title>
      {caps.data ? <Muted>{capabilitiesText(caps.data)}</Muted> : null}
      <AsyncView<SupportCase[]> state={cases} isEmpty={(d) => d.length === 0} emptyText="Henüz destek talebiniz yok. Bir siparişin detayından talep açabilirsiniz.">
        {(list) => list.map((c) => (
          <Pressable key={c.id} accessibilityRole="button"
            accessibilityLabel={`${c.subject}, ${STATUS_LABEL[c.status]}`}
            onPress={() => router.push(`/support/${c.id}` as Href)}>
            <Card>
              <Text style={{ fontWeight: '700' }}>{c.subject}</Text>
              <Text style={{ color: colors.primary, fontWeight: '600' }}>{STATUS_LABEL[c.status]}</Text>
              <Muted>{new Date(c.updatedAt).toLocaleString('tr-TR')}</Muted>
            </Card>
          </Pressable>
        ))}
      </AsyncView>
    </ScrollView>
  );
}
