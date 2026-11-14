import { useState } from 'react';
import { Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import QRCode from 'react-native-qrcode-svg';
import { ApiError, type CancellationPreview, type Order, type OrderItem, type Voucher } from '@/api';
import { checkoutErrorMessage, ordersApi, paySandbox } from '@/features/orders/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, colors, ErrorBanner, Muted, Screen, TestBanner, Title } from '@/components/ui';
import { checkinApi, checkinWindow, type OrderCheckin } from '@/features/checkin/api';
import { fmtLocal, money, ORDER_STATUS_TR, policyText, refundText } from '@/features/format';

function FlightCheckin({ item, checkin, onDone }: { item: OrderItem; checkin: OrderCheckin | undefined; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [now] = useState(() => Date.now());
  if (!item.flight) return null;
  if (checkin) return <Text style={{ fontWeight: '700', color: colors.ok }}>{checkin.seatNumber ?? item.flight.seatNumber ? `Check-in yapildi, koltuk ${checkin.seatNumber ?? item.flight.seatNumber}` : 'Check-in yapildi'}</Text>;
  const win = checkinWindow(item.flight.departureAt ?? '', now, item.flight.supplier, !!item.cancelOutcome);
  async function run() {
    setBusy(true); setMsg(null);
    try { await checkinApi.checkIn(item.id); onDone(); }
    catch (e) { setMsg(e instanceof ApiError ? e.message : 'Check-in yapilamadi'); }
    finally { setBusy(false); }
  }
  return (
    <>
      <Button title={busy ? 'Isleniyor...' : 'Online check-in'} disabled={!win.open || busy} onPress={run} />
      {!win.open && win.reason ? <Muted>{win.reason}</Muted> : null}
      <ErrorBanner message={msg} />
    </>
  );
}

function Vouchers({ orderId, checkedInItemIds }: { orderId: string; checkedInItemIds: Set<string> }) {
  const state = useAsync(() => ordersApi.vouchers(orderId), [orderId]);
  return (
    <AsyncView<Voucher[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Kupon henuz olusmadi.">
      {(list) => list.map((v) => (
        <Card key={v.voucherId}>
          <Text style={{ fontWeight: '700' }}>{v.description}</Text>
          <Muted>{`${v.status} · ${fmtLocal(v.validFrom)} - ${fmtLocal(v.validUntil)}`}</Muted>
          {v.orderItemId && checkedInItemIds.has(v.orderItemId) ? <Text style={{ fontWeight: '700' }}>Binis karti (QR)</Text> : null}
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
  const checkins = useAsync(() => checkinApi.mine(), [id]);
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
                <View key={i.id} style={{ gap: 4 }}>
                  <Muted>{`${i.description} · ${policyText(i.cancellationPolicy, i.cancelFreeHoursBefore)}`}</Muted>
                  {i.vertical === 'FLIGHT' && i.flight ? (
                    <>
                      <Muted>{`Yolcu: ${i.flight.passengerFirstName} ${i.flight.passengerLastName}${i.flight.pnr ? ` · PNR ${i.flight.pnr}` : ''}`}</Muted>
                      {o.status === 'CONFIRMED' ? <FlightCheckin item={i} checkin={(checkins.data ?? []).find((c) => c.orderItemId === i.id)} onDone={checkins.reload} /> : null}
                    </>
                  ) : null}
                </View>
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
            {o.status === 'CONFIRMED' && <Vouchers orderId={o.id} checkedInItemIds={new Set((checkins.data ?? []).map((c) => c.orderItemId))} />}
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
