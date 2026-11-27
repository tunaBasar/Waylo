import { useState } from 'react';
import { Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useAsync } from '@/features/useAsync';
import { canClose, canReply, ROLE_LABEL, STATUS_LABEL, supportApi, supportErrorMessage, validateMessage, type SupportCase } from '@/features/support/api';
import { AsyncView, Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { StatusPill } from '@/components/StatusPill';
import { accountStyles, Hint } from '@/features/account/AccountUi';
import { color, radius, space, type } from '@/theme/tokens';

export default function SupportThread() {
  const { id } = useLocalSearchParams<{ id: string }>();
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
    <Screen>
      <AsyncView<SupportCase> state={state} emptyText="Talep bulunamadı.">
        {(c) => (
          <>
            <Text accessibilityRole="header" style={[type.headlineLg, { color: color.onSurface }]}>{c.subject}</Text>
            <StatusPill tone={c.status === 'CLOSED' ? 'neutral' : c.status === 'ANSWERED' ? 'success' : 'warning'} text={`Durum: ${STATUS_LABEL[c.status]}`} />
            {(c.messages ?? []).map((m) => (
              <View key={m.id} style={{ alignSelf: m.authorRole === 'CUSTOMER' ? 'flex-end' : 'flex-start', maxWidth: '88%', gap: space.xs, padding: space.md, borderRadius: radius.lg, backgroundColor: m.authorRole === 'CUSTOMER' ? color.primaryContainer : m.authorRole === 'ADMIN' ? color.secondaryContainer : color.surfaceLowest, borderWidth: m.authorRole === 'PARTNER' ? 1 : 0, borderColor: color.outlineVariant }}>
                <Text style={[type.labelSm, { color: m.authorRole === 'CUSTOMER' ? color.onPrimaryContainer : color.onSurfaceVariant }]}>{ROLE_LABEL[m.authorRole]}</Text>
                <Text style={[type.bodyLg, { color: m.authorRole === 'CUSTOMER' ? color.onPrimary : color.onSurface }]}>{m.body}</Text>
                <Text style={[accountStyles.meta, m.authorRole === 'CUSTOMER' && { color: color.onPrimaryContainer }]}>{new Date(m.createdAt).toLocaleString('tr-TR')}</Text>
              </View>
            ))}
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
    </Screen>
  );
}
