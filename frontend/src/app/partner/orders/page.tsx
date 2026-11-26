'use client';

import { guestLine } from '@/lib/guest';
import { useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  ORDER_STATUS_LABEL, VOUCHER_STATUS_LABEL, partnerErrorMessage, partnerApi, checkOutOrderItem, checkOutErrorMessage,
  type PartnerOrderItem, type PartnerOrderPage, type PartnerOrderStatus,
} from '@/lib/partner';
import { formatMoney } from '@/lib/partner-car';
import { formatDate } from '@/lib/utils';
import { Button, Card, EmptyState, Field, inputClass, LoadingRows, PageHeader, StatusPill } from '@/components/partner/ui';
import { displayItemText } from '@/lib/order-text';

const PAGE_SIZE = 20;
const VERTICAL_LABEL: Record<string, string> = { HOTEL: 'Otel', CAR: 'Arac', FLIGHT: 'Ucus', BUS: 'Otobus' };
const CANCEL_LABEL: Record<string, string> = { REFUND: 'Iade edildi', NO_REFUND: 'Iadesiz iptal' };

function statusTone(s: PartnerOrderStatus): 'success' | 'warning' | 'error' | 'neutral' {
  return s === 'CONFIRMED' ? 'success' : s === 'PENDING' ? 'warning' : 'error';
}

// Hotel stays are date-only (YYYY-MM-DD). formatDate would parse them as UTC midnight and print a fake local time.
const formatDay = (d: string) => new Date(`${d}T00:00:00Z`).toLocaleDateString('tr-TR', { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' });

function dates(i: PartnerOrderItem): string | null {
  if (i.checkIn && i.checkOut) return `${formatDay(i.checkIn)} - ${formatDay(i.checkOut)}`;
  if (i.pickupAt && i.returnAt) return `${formatDate(i.pickupAt)} - ${formatDate(i.returnAt)}`;
  return null;
}

export default function PartnerOrdersPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [reloadTick, setReloadTick] = useState(0);
  const key = `${organizationId}|${status}|${from}|${to}|${page}|${reloadTick}`;
  const [result, setResult] = useState<{ key: string; data: PartnerOrderPage | null; error: string | null } | null>(null);

  useEffect(() => {
    if (!organizationId) return;
    let alive = true;
    partnerApi.orders(organizationId, { status, from, to, page, size: PAGE_SIZE })
      .then(res => { if (alive) setResult({ key, data: res.data, error: null }); })
      .catch(e => { if (alive) setResult({ key, data: null, error: partnerErrorMessage(e, 'Siparisler yuklenemedi') }); });
    return () => { alive = false; };
  }, [organizationId, status, from, to, page, key]);

  const [busyItem, setBusyItem] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // Walkthrough 2026-11-26: one click sent two POSTs in the same millisecond; the first checked out, the second hit
  // the version check and showed "Check-out yapilamadi" for a successful check-out. Guard synchronously.
  const checkOutInFlight = useRef(false);

  async function checkOut(i: PartnerOrderItem) {
    if (!organizationId || checkOutInFlight.current) return;
    checkOutInFlight.current = true;
    setBusyItem(i.orderItemId);
    setActionError(null);
    try {
      await checkOutOrderItem(organizationId, i.orderItemId, i.stayVersion ?? 0);
    } catch (e) {
      setActionError(checkOutErrorMessage(e));
    } finally {
      checkOutInFlight.current = false;
      setBusyItem(null);
      setReloadTick(n => n + 1);
    }
  }

  const loading = result?.key !== key;
  const data = result?.key === key ? result.data : null;
  const error = result?.key === key ? result.error : null;

  if (workspaceLoading) return <LoadingRows />;
  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Siparisler" subtitle="Isletmenize ait siparis kalemleri" />

      <Card className="p-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Durum">
            <select className={inputClass} value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
              <option value="">Hepsi</option>
              {(Object.keys(ORDER_STATUS_LABEL) as PartnerOrderStatus[]).map(s => (
                <option key={s} value={s}>{ORDER_STATUS_LABEL[s]}</option>
              ))}
            </select>
          </Field>
          <Field label="Baslangic">
            <input type="date" className={inputClass} value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} />
          </Field>
          <Field label="Bitis">
            <input type="date" className={inputClass} value={to} onChange={e => { setTo(e.target.value); setPage(0); }} />
          </Field>
        </div>
      </Card>

      {actionError && <p role="alert" className="text-sm text-error">{actionError}</p>}

      {loading ? <LoadingRows /> : error ? (
        <EmptyState icon="error" title="Yuklenemedi" description={error} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState icon="receipt_long" title="Siparis bulunamadi" description="Secili filtrelere uyan siparis yok." />
      ) : (
        <div className="space-y-3">
          {data.items.map(i => {
            const guest = [i.passengerFirstName, i.passengerLastName].filter(Boolean).join(' ');
            const range = dates(i);
            return (
              <Card key={i.orderItemId} className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold text-on-surface">{displayItemText(i.description)}</p>
                    <p className="text-sm text-on-surface-variant mt-0.5">
                      {VERTICAL_LABEL[i.vertical] ?? i.vertical} · Siparis tarihi {formatDate(i.orderCreatedAt)}
                    </p>
                    {range && <p className="text-sm text-on-surface-variant">{range}</p>}
                    {i.vertical === 'HOTEL'
                      ? guestLine(i.passengerFirstName, i.passengerLastName, i.adults, i.childrenCount) && (
                        <p className="text-sm text-on-surface-variant">{guestLine(i.passengerFirstName, i.passengerLastName, i.adults, i.childrenCount)}</p>)
                      : guest && <p className="text-sm text-on-surface-variant">Misafir/yolcu: {guest}</p>}
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusPill tone={statusTone(i.orderStatus)}>{ORDER_STATUS_LABEL[i.orderStatus]}</StatusPill>
                    {i.voucherStatus && (
                      <StatusPill tone={i.voucherStatus === 'REDEEMED' ? 'success' : i.voucherStatus === 'ACTIVE' ? 'neutral' : 'warning'}>
                        Voucher: {VOUCHER_STATUS_LABEL[i.voucherStatus]}
                      </StatusPill>
                    )}
                    {i.cancelOutcome && (
                      <StatusPill tone="error">{CANCEL_LABEL[i.cancelOutcome] ?? i.cancelOutcome}</StatusPill>
                    )}
                  </div>
                </div>
                <div className="mt-2 flex justify-between text-sm">
                  <span className="text-outline">Tutar</span>
                  <span className="font-semibold text-on-surface">{formatMoney(i.amount, i.currency)}</span>
                </div>
                {i.vertical === 'HOTEL' && i.checkedOutAt && (
                  <div className="flex justify-between text-sm">
                    <span className="text-outline">Check-out</span>
                    <span>{formatDate(i.checkedOutAt)}</span>
                  </div>
                )}
                {i.vertical === 'HOTEL' && i.voucherStatus === 'REDEEMED' && !i.checkedOutAt && (
                  <div className="mt-2 flex justify-end">
                    <Button variant="secondary" disabled={busyItem === i.orderItemId} onClick={() => checkOut(i)}>
                      {busyItem === i.orderItemId ? 'Isleniyor...' : 'Check-out yap'}
                    </Button>
                  </div>
                )}
                {i.refundAmount != null && i.refundAmount > 0 && (
                  <div className="flex justify-between text-sm">
                    <span className="text-outline">Iade</span>
                    <span>{formatMoney(i.refundAmount, i.currency)}</span>
                  </div>
                )}
              </Card>
            );
          })}
          <div className="flex items-center justify-between">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Onceki</Button>
            <span className="text-sm text-on-surface-variant">Sayfa {page + 1} / {totalPages} ({data.total} kalem)</span>
            <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}>Sonraki</Button>
          </div>
        </div>
      )}
    </div>
  );
}
