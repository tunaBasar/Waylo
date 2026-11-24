import { useState } from 'react';
import { Pressable, Text } from 'react-native';
import { useLocalSearchParams, useRouter, type Href } from 'expo-router';
import type { Order } from '@/api';
import { ordersApi } from '@/features/orders/api';
import { useAsync } from '@/features/useAsync';
import { supportApi, supportErrorMessage, validateNewCase } from '@/features/support/api';
import { AsyncView, Button, Card, colors, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';

export default function NewSupportCase() {
  const router = useRouter();
  const { orderId } = useLocalSearchParams<{ orderId: string }>();
  const order = useAsync(() => ordersApi.get(orderId), [orderId]);
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [itemId, setItemId] = useState<string | null>(null);
  const [errs, setErrs] = useState<{ subject: string | null; message: string | null } | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    const v = validateNewCase(subject, message);
    setErrs(v);
    if (v.subject || v.message) return;
    setBusy(true); setMsg(null);
    try {
      const c = await supportApi.open({ orderId, ...(itemId ? { orderItemId: itemId } : {}), subject: subject.trim(), message: message.trim() });
      router.replace(`/support/${c.id}` as Href);
    } catch (e) { setMsg(supportErrorMessage(e as Error & { status?: number; code?: string | null })); }
    finally { setBusy(false); }
  }

  return (
    <Screen>
      <Title>Destek talebi aç</Title>
      <AsyncView<Order> state={order} emptyText="Sipariş bulunamadı.">
        {(o) => (
          <>
            <Muted>{`Sipariş #${o.id.slice(0, 8)}`}</Muted>
            {(o.items ?? []).length > 0 ? (
              <Card>
                <Text style={{ fontWeight: '700' }}>Kalem (isteğe bağlı)</Text>
                {(o.items ?? []).map((i) => {
                  const selected = itemId === i.id;
                  return (
                    <Pressable key={i.id} accessibilityRole="radio" accessibilityState={{ selected }}
                      accessibilityLabel={`Kalem: ${i.description}`} onPress={() => setItemId(selected ? null : i.id)}>
                      <Text style={{ color: selected ? colors.primary : colors.text, fontWeight: selected ? '700' : '400' }}>
                        {`${selected ? '● ' : '○ '}${i.description}`}
                      </Text>
                    </Pressable>
                  );
                })}
              </Card>
            ) : null}
            <Field label="Konu" value={subject} onChangeText={setSubject} autoCapitalize="sentences" maxLength={150} />
            <ErrorBanner message={errs?.subject ?? null} />
            <Field label="Mesaj" value={message} onChangeText={setMessage} multiline autoCapitalize="sentences" />
            <ErrorBanner message={errs?.message ?? null} />
            <ErrorBanner message={msg} />
            <Button title={busy ? 'Gönderiliyor...' : 'Talebi gönder'} disabled={busy} onPress={submit} />
          </>
        )}
      </AsyncView>
    </Screen>
  );
}
