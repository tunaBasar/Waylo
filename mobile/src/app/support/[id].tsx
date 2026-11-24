import { useState } from 'react';
import { Text } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useAsync } from '@/features/useAsync';
import { canClose, canReply, ROLE_LABEL, STATUS_LABEL, supportApi, supportErrorMessage, validateMessage, type SupportCase } from '@/features/support/api';
import { AsyncView, Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';

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
            <Title>{c.subject}</Title>
            <Muted>{`Durum: ${STATUS_LABEL[c.status]}`}</Muted>
            {(c.messages ?? []).map((m) => (
              <Card key={m.id}>
                <Text style={{ fontWeight: '700' }}>{ROLE_LABEL[m.authorRole]}</Text>
                <Text>{m.body}</Text>
                <Muted>{new Date(m.createdAt).toLocaleString('tr-TR')}</Muted>
              </Card>
            ))}
            <ErrorBanner message={msg} />
            {canReply(c.status, 'CUSTOMER') ? (
              <>
                <Field label="Yanıtınız" value={body} onChangeText={setBody} multiline autoCapitalize="sentences" />
                <Button title={busy ? 'Gönderiliyor...' : 'Yanıtla'} disabled={busy} onPress={reply} />
              </>
            ) : <Muted>Bu talep kapatıldı.</Muted>}
            {canClose(c.status, 'CUSTOMER') ? <Button kind="secondary" title="Talebi kapat" disabled={busy} onPress={close} /> : null}
          </>
        )}
      </AsyncView>
    </Screen>
  );
}
