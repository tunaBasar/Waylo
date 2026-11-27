import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useAsync } from '@/features/useAsync';
import { canClose, canReply, ROLE_LABEL, STATUS_LABEL, supportApi, supportErrorMessage, validateMessage, type SupportCase } from '@/features/support/api';
import { AsyncView, Button, ErrorBanner, Field } from '@/components/ui';
import { StatusPill } from '@/components/StatusPill';
import { Hint, StackScreen, useAccountStyles } from '@/features/account/AccountUi';
import { space, type Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';

const makeStyles = ({ color, radius, card }: Theme) => StyleSheet.create({
  bubble: { maxWidth: '88%', gap: space.xs, padding: space.md, borderRadius: radius.lg },
  me: { alignSelf: 'flex-end', backgroundColor: color.primaryContainer },
  other: { alignSelf: 'flex-start', backgroundColor: card.backgroundColor, borderWidth: card.borderWidth, borderColor: card.borderColor },
});

export default function SupportThread() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const accountStyles = useAccountStyles();
  const state = useAsync(() => supportApi.myGet(id), [id]);
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  async function reply() {
    const err = validateMessage(body);
    if (err) { setMsg(err); return; }
    setBusy(true); setMsg(null);
    try { await supportApi.myReply(id, body.trim()); setBody(''); state.reload(); }
    catch (e) { setMsg(supportErrorMessage(e as Error & { status?: number; code?: string | null })); state.reload(); }
    finally { setBusy(false); }
  }
  async function close() {
    setBusy(true); setMsg(null);
    try { await supportApi.myClose(id); state.reload(); }
    catch (e) { setMsg(supportErrorMessage(e as Error & { status?: number; code?: string | null })); }
    finally { setBusy(false); }
  }

  return (
    <StackScreen title="Destek talebi">
      <AsyncView<SupportCase> state={state} emptyText="Talep bulunamadı.">
        {(c) => (
          <>
            <Text accessibilityRole="header" style={[type.headlineLg, { color: color.onSurface }]}>{c.subject}</Text>
            <StatusPill tone={c.status === 'CLOSED' ? 'neutral' : c.status === 'ANSWERED' ? 'success' : 'warning'} text={`Durum: ${STATUS_LABEL[c.status]}`} />
            {(c.messages ?? []).map((m) => {
              const mine = m.authorRole === 'CUSTOMER';
              return (
                <View key={m.id} style={[s.bubble, mine ? s.me : s.other]}>
                  <Text style={[type.labelSm, { color: mine ? color.onPrimaryContainer : color.onSurfaceVariant }]}>{ROLE_LABEL[m.authorRole]}</Text>
                  <Text style={[type.bodyLg, { color: mine ? color.onPrimaryContainer : color.onSurface }]}>{m.body}</Text>
                  <Text style={[accountStyles.meta, mine && { color: color.onPrimaryContainer }]}>{new Date(m.createdAt).toLocaleString('tr-TR')}</Text>
                </View>
              );
            })}
            <ErrorBanner message={msg} />
            {canReply(c.status, 'CUSTOMER') ? (
              <>
                <Field label="Yanıtınız" value={body} onChangeText={setBody} multiline autoCapitalize="sentences" />
                <Button title={busy ? 'Gönderiliyor...' : 'Yanıtla'} disabled={busy} onPress={reply} />
              </>
            ) : <Hint>Bu talep kapatıldı.</Hint>}
            {canClose(c.status, 'CUSTOMER') ? <Button kind="secondary" title="Talebi kapat" disabled={busy} onPress={close} /> : null}
          </>
        )}
      </AsyncView>
    </StackScreen>
  );
}
