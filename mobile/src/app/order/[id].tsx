import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Stack, useLocalSearchParams, useRouter, type Href } from 'expo-router';
import QRCode from 'react-native-qrcode-svg';
import { ApiError, type CancellationPreview, type Order, type OrderItem, type Voucher } from '@/api';
import { checkoutErrorMessage, ordersApi, paySandbox } from '@/features/orders/api';
import { useAsync } from '@/features/useAsync';
import { useOnline } from '@/features/network/NetworkContext';
import { orderLinkOpened } from '@/features/deeplinks';
import { AsyncView, Button, Card, ErrorBanner, Muted, Screen, TestBanner } from '@/components/ui';
import { AppHeader } from '@/components/AppHeader';
import { CountdownBanner } from '@/components/CountdownBanner';
import { Price } from '@/components/Price';
import { StatusPill, type Tone } from '@/components/StatusPill';
import { color, space, type } from '@/theme/tokens';
import { canOfferCancel } from '@/features/orders/actions';
import { checkinApi, checkinWindow, type OrderCheckin } from '@/features/checkin/api';
import { displayItemText, fmtLocal, ORDER_STATUS_TR, orderStatusInfo, orderPolicyText, refundText, voucherStatusText } from '@/features/format';

const VOUCHER_TONE: Record<string, Tone> = { ACTIVE: 'success', REDEEMED: 'neutral', VOID: 'error', NO_SHOW: 'warning' };
const ORDER_TONE: Record<string, Tone> = { PENDING: 'warning', CONFIRMED: 'success', CANCELLED: 'neutral', EXPIRED: 'error' };

function FlightCheckin({ item, checkin, onDone }: { item: OrderItem; checkin: OrderCheckin | undefined; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [now] = useState(() => Date.now());
  if (!item.flight) return null;
  if (checkin) return <Text style={[type.labelLg, { color: color.success }]}>{checkin.seatNumber ?? item.flight.seatNumber ? `Check-in yapıldı, koltuk ${checkin.seatNumber ?? item.flight.seatNumber}` : 'Check-in yapıldı'}</Text>;
  const win = checkinWindow(item.flight.departureAt ?? '', now, item.flight.supplier, !!item.cancelOutcome);
  async function run() {
    setBusy(true); setMsg(null);
    try { await checkinApi.checkIn(item.id); onDone(); }
    catch (e) { setMsg(e instanceof ApiError ? e.message : 'Check-in yapılamadı'); }
    finally { setBusy(false); }
  }
  return (
    <>
      <Button title={busy ? 'İşleniyor...' : 'Online check-in'} disabled={!win.open || busy} onPress={run} />
      {!win.open && win.reason ? <Muted>{win.reason}</Muted> : null}
      <ErrorBanner message={msg} />
    </>
  );
}

function Vouchers({ orderId, checkedInItemIds, freeCancel }: { orderId: string; checkedInItemIds: Set<string>; freeCancel: Record<string, string> }) {
  const state = useAsync(() => ordersApi.vouchers(orderId), [orderId]);
  return (
    <AsyncView<Voucher[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Kupon henüz oluşmadı.">
      {(list) => list.map((v) => (
        <Card key={v.voucherId}>
          <View style={st.top}>
            <Text style={[type.titleMd, { color: color.onSurface, flex: 1 }]}>{displayItemText(v.description)}</Text>
            <StatusPill tone={VOUCHER_TONE[v.status] ?? 'neutral'} text={voucherStatusText(v.status)} />
          </View>
          {v.payload ? <View style={st.qr}><QRCode value={v.payload} size={200} /></View> : null}
          <Text style={[type.monoSm, { color: color.onSurfaceVariant }]}>{`Geçerlilik: ${fmtLocal(v.validFrom)} - ${fmtLocal(v.validUntil)}`}</Text>
          {v.orderItemId && freeCancel[v.orderItemId] ? <Muted>{freeCancel[v.orderItemId]}</Muted> : null}
          {v.orderItemId && checkedInItemIds.has(v.orderItemId) ? <Text style={[type.labelLg, { color: color.success }]}>Biniş kartı (QR)</Text> : null}
        </Card>
      ))}
    </AsyncView>
  );
}

export default function OrderDetail() {
  const online = useOnline();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const state = useAsync(() => ordersApi.get(id), [id]);
  // Cleared only once the order is shown: right after login the router guard can unmount this screen once.
  const loaded = state.data != null;
  useEffect(() => { if (id && loaded) orderLinkOpened(id); }, [id, loaded]);
  const checkins = useAsync(() => checkinApi.mine(), [id]);
  const vouchers = useAsync(() => ordersApi.vouchers(id), [id]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [preview, setPreview] = useState<CancellationPreview | null>(null);

  async function pay(token: 'TEST_OK' | 'TEST_DECLINE') {
    setBusy(true); setMsg(null);
    try {
      const o = await paySandbox(id, token);
      // Only the backend's order status decides success.
      if (o.status !== 'CONFIRMED') setMsg(token === 'TEST_DECLINE' ? 'Test ödemesi reddedildi.' : `Ödeme onaylanmadı (durum: ${ORDER_STATUS_TR[o.status] ?? o.status}).`);
      state.reload();
    } catch (e) { setMsg(e instanceof ApiError ? checkoutErrorMessage(e) : 'Ödeme başarısız'); }
    finally { setBusy(false); }
  }
  async function loadPreview() {
    setBusy(true); setMsg(null);
    try { setPreview(await ordersApi.cancelPreview(id)); }
    catch (e) { setMsg(e instanceof ApiError ? e.message : 'İptal bilgisi alınamadı'); }
    finally { setBusy(false); }
  }
  async function confirmCancel() {
    setBusy(true); setMsg(null);
    try { await ordersApi.cancel(id); setPreview(null); state.reload(); }
    catch (e) { setMsg(e instanceof ApiError ? e.message : 'İptal edilemedi'); }
    finally { setBusy(false); }
  }

  return (
    <View style={st.root}>
    <Stack.Screen options={{ headerShown: false }} />
    <AppHeader title={`Sipariş #${(id ?? '').slice(0, 8)}`} onBack={() => router.back()}
      right={state.data ? <StatusPill tone={ORDER_TONE[state.data.status] ?? 'neutral'} text={orderStatusInfo(state.data.status).label} /> : undefined} />
    <Screen>
      <AsyncView<Order> state={state} emptyText="Sipariş bulunamadı.">
        {(o) => (
          <>
            {o.status === 'PENDING' && o.holdExpiresAt ? <CountdownBanner until={o.holdExpiresAt} label="Ödeme için kalan süre" /> : null}
            <Card>
              {orderStatusInfo(o.status).note ? <Muted>{orderStatusInfo(o.status).note}</Muted> : null}
              <Price amount={o.totalAmount} currency={o.currency} />
              {(o.items ?? []).map((i) => (
                <View key={i.id} style={{ gap: 4 }}>
                  <Muted>{`${displayItemText(i.description)} · ${orderPolicyText(i.cancellationPolicy, i.cancelFreeHoursBefore, i.freeCancellationUntil)}`}</Muted>
                  {i.vertical === 'FLIGHT' && i.flight ? (
                    <>
                      <Muted>{`Yolcu: ${i.flight.passengerFirstName} ${i.flight.passengerLastName}${i.flight.pnr ? ` · PNR ${i.flight.pnr}` : ''}`}</Muted>
                      {o.status === 'CONFIRMED' ? <FlightCheckin item={i} checkin={(checkins.data ?? []).find((c) => c.orderItemId === i.id)} onDone={checkins.reload} /> : null}
                    </>
                  ) : null}
                </View>
              ))}
              {o.status === 'PENDING' && o.holdExpiresAt ? <Muted>{`Ödeme son zamanı: ${fmtLocal(o.holdExpiresAt)}`}</Muted> : null}
            </Card>
            <ErrorBanner message={msg} />
            {o.status === 'PENDING' && (
              <>
                <TestBanner />
                <Button title={busy ? 'İşleniyor...' : 'Öde (TEST ÖDEME)'} disabled={busy || !online} onPress={() => pay('TEST_OK')} />
                <Button kind="secondary" title="Reddedilen test ödemesi" disabled={busy || !online} onPress={() => pay('TEST_DECLINE')} />
              </>
            )}
            <Button kind="secondary" title="Destek talebi aç" onPress={() => router.push(`/support/new?orderId=${o.id}` as Href)} />
            {o.status === 'CONFIRMED' && <Vouchers orderId={o.id} checkedInItemIds={new Set((checkins.data ?? []).map((c) => c.orderItemId))} freeCancel={Object.fromEntries((o.items ?? []).map((i) => [i.id, orderPolicyText(i.cancellationPolicy, i.cancelFreeHoursBefore, i.freeCancellationUntil)]))} />}
            {(o.status === 'PENDING' || o.status === 'CONFIRMED') && !preview && canOfferCancel(o, vouchers.data) && (
              <Button kind="secondary" title="Siparişi iptal et" disabled={busy} onPress={loadPreview} />
            )}
            {preview && (
              <Card>
                {preview.cancellable ? (
                  <>
                    <Text style={[type.titleMd, { color: color.onSurface }]}>{o.status === 'PENDING' ? 'Ödeme yapılmadı, tutar çekilmeyecek.' : refundText(preview.totalRefund, preview.currency)}</Text>
                    <Button kind="danger" title="İptali onayla" disabled={busy || !online} onPress={confirmCancel} />
                  </>
                ) : <Text>{`Bu sipariş iptal edilemez (${preview.blockedReason}).`}</Text>}
                <Button kind="secondary" title="Vazgeç" onPress={() => setPreview(null)} />
              </Card>
            )}
          </>
        )}
      </AsyncView>
    </Screen>
    </View>
  );
}

const st = StyleSheet.create({
  root: { flex: 1, backgroundColor: color.surface },
  top: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: space.sm },
  qr: { alignItems: 'center', paddingVertical: space.md },
});
