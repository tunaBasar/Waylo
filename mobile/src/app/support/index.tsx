import { useState } from 'react';
import { RefreshControl, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Stack } from 'expo-router';
import { useRouter, type Href } from 'expo-router';
import { useAsync } from '@/features/useAsync';
import { capabilitiesText, STATUS_LABEL, supportApi, type SupportCase } from '@/features/support/api';
import { AsyncView } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { StatusPill } from '@/components/StatusPill';
import { Hint, Icon, Section, useAccountStyles } from '@/features/account/AccountUi';
import { space, type Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

const makeStyles = ({ color }: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  content: { padding: space.xl, gap: space.md },
  rowLine: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  grow: { flex: 1, gap: space.xs },
});

export default function SupportList() {
  const router = useRouter();
  const { color } = useTheme();
  const s = useStyles(makeStyles);
  const accountStyles = useAccountStyles();
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
    <View style={s.root}>
    <Stack.Screen options={{ headerShown: false }} />
    <AppHeader title="Destek taleplerim" onBack={() => (router.canGoBack() ? router.back() : router.replace('/hotels'))} />
    <ScrollView style={s.root} contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} accessibilityLabel="Yenile" />}>
      {caps.data ? <Section><Hint>{capabilitiesText(caps.data)}</Hint></Section> : null}
      <AsyncView<SupportCase[]> state={cases} isEmpty={(d) => d.length === 0} emptyText="Henüz destek talebiniz yok. Bir siparişin detayından talep açabilirsiniz.">
        {(list) => list.map((c) => (
          <Pressable key={c.id} accessibilityRole="button"
            accessibilityLabel={`${c.subject}, ${STATUS_LABEL[c.status]}`}
            onPress={() => router.push(`/support/${c.id}` as Href)}>
            <Section>
              <View style={s.rowLine}>
                <View style={s.grow}>
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
    </View>
  );
}
