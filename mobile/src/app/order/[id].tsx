import { useState } from 'react';
import { Text } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import QRCode from 'react-native-qrcode-svg';
import { ApiError, type CancellationPreview, type Order, type Voucher } from '@/api';
import { checkoutErrorMessage, ordersApi, paySandbox } from '@/features/orders/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, ErrorBanner, Muted, Screen, TestBanner, Title } from '@/components/ui';
import { fmtLocal, money, ORDER_STATUS_TR, policyText, refundText } from '@/features/format';

function Vouchers({ orderId }: { orderId: string }) {
  const state = useAsync(() => ordersApi.vouchers(orderId), [orderId]);
  return (
    <AsyncView<Voucher[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Kupon henuz olusmadi.">
      {(list) => list.map((v) => (
        <Card key={v.voucherId}>
          <Text style={{ fontWeight: '700' }}>{v.description}</Text>
          <Muted>{`${v.status} · ${fmtLocal(v.validFrom)} - ${fmtLocal(v.validUntil)}`}</Muted>
          {v.payload ? <QRCode value={v.payload} size={200} /> : null}
          {v.payload ? <Text selectable style={{ fontSize: 11 }}>{v.payload}</Text> : null}
        </Card>
      ))}
    </AsyncView>
  );
}

export default function OrderDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const state = useAsync(() => ordersApi.get(id), [id]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [preview, setPreview] = useState<CancellationPreview | null>(null);

  async function pay(token: 'TEST_OK' | 'TEST_DECLINE') {
    setBusy(true); setMsg(null);
    try {
      const o = await paySandbox(id, token);
      // Only the backend's order status decides success.
      if (o.status !== 'CONFIRMED') setMsg(token === 'TEST_DECLINE' ? 'Test odemesi reddedildi.' : `Odeme onaylanmadi (durum: ${ORDER_STATUS_TR[o.status] ?? o.status}).`);
      state.reload();
    } catch (e) { setMsg(e instanceof ApiError ? checkoutErrorMessage(e) : 'Odeme basarisiz'); }
    finally { setBusy(false); }
  }
  async function loadPreview() {
    setBusy(true); setMsg(null);
    try { setPreview(await ordersApi.cancelPreview(id)); }
    catch (e) { setMsg(e instanceof ApiError ? e.message : 'Iptal bilgisi alinamadi'); }
    finally { setBusy(false); }
  }
  async function confirmCancel() {
    setBusy(true); setMsg(null);
    try { await ordersApi.cancel(id); setPreview(null); state.reload(); }
    catch (e) { setMsg(e instanceof ApiError ? e.message : 'Iptal edilemedi'); }
    finally { setBusy(false); }
  }

  return (
    <Screen>
      <AsyncView<Order> state={state} emptyText="Siparis bulunamadi.">
        {(o) => (
          <>
            <Title>{`Siparis #${o.id.slice(0, 8)}`}</Title>
            <Card>
              <Text style={{ fontWeight: '700' }}>{ORDER_STATUS_TR[o.status] ?? o.status}</Text>
              <Text>{money(o.totalAmount, o.currency)}</Text>
              {(o.items ?? []).map((i) => (
                <Muted key={i.id}>{`${i.description} · ${policyText(i.cancellationPolicy, i.cancelFreeHoursBefore)}`}</Muted>
              ))}
              {o.status === 'PENDING' && o.holdExpiresAt ? <Muted>{`Odeme son zamani: ${new Date(o.holdExpiresAt).toLocaleString('tr-TR')}`}</Muted> : null}
            </Card>
            <ErrorBanner message={msg} />
            {o.status === 'PENDING' && (
              <>
                <TestBanner />
                <Button title={busy ? 'Isleniyor...' : 'Ode (TEST ODEME)'} disabled={busy} onPress={() => pay('TEST_OK')} />
                <Button kind="secondary" title="Reddedilen test odemesi" disabled={busy} onPress={() => pay('TEST_DECLINE')} />
              </>
            )}
            {o.status === 'CONFIRMED' && <Vouchers orderId={o.id} />}
            {(o.status === 'PENDING' || o.status === 'CONFIRMED') && !preview && (
              <Button kind="secondary" title="Siparisi iptal et" disabled={busy} onPress={loadPreview} />
            )}
            {preview && (
              <Card>
                {preview.cancellable ? (
                  <>
                    <Text style={{ fontWeight: '700' }}>{o.status === 'PENDING' ? 'Odeme yapilmadi, tutar cekilmeyecek.' : refundText(preview.totalRefund, preview.currency)}</Text>
                    <Button kind="danger" title="Iptali onayla" disabled={busy} onPress={confirmCancel} />
                  </>
                ) : <Text>{`Bu siparis iptal edilemez (${preview.blockedReason}).`}</Text>}
                <Button kind="secondary" title="Vazgec" onPress={() => setPreview(null)} />
              </Card>
            )}
          </>
        )}
      </AsyncView>
    </Screen>
  );
}
