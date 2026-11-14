'use client';

import { useEffect, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import { ESCROW_STATE_LABEL, partnerErrorMessage, partnerApi, type PartnerEarnings } from '@/lib/partner';
import { formatMoney } from '@/lib/partner-car';
import { formatDate } from '@/lib/utils';
import { Card, EmptyState, Field, inputClass, LoadingRows, PageHeader, StatusPill } from '@/components/partner/ui';

const VERTICAL_LABEL: Record<string, string> = { HOTEL: 'Otel', CAR: 'Arac', FLIGHT: 'Ucus', BUS: 'Otobus' };

export default function PartnerEarningsPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const key = `${organizationId}|${from}|${to}`;
  const [result, setResult] = useState<{ key: string; data: PartnerEarnings | null; error: string | null } | null>(null);

  useEffect(() => {
    if (!organizationId) return;
    let alive = true;
    partnerApi.earnings(organizationId, { from, to })
      .then(res => { if (alive) setResult({ key, data: res.data, error: null }); })
      .catch(e => { if (alive) setResult({ key, data: null, error: partnerErrorMessage(e, 'Kazanclar yuklenemedi') }); });
    return () => { alive = false; };
  }, [organizationId, from, to, key]);

  const loading = result?.key !== key;
  const data = result?.key === key ? result.data : null;
  const error = result?.key === key ? result.error : null;

  if (workspaceLoading) return <LoadingRows />;

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Kazanclar" subtitle="Komisyon sonrasi net kazanciniz" />

      <Card className="p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Baslangic"><input type="date" className={inputClass} value={from} onChange={e => setFrom(e.target.value)} /></Field>
          <Field label="Bitis"><input type="date" className={inputClass} value={to} onChange={e => setTo(e.target.value)} /></Field>
        </div>
      </Card>

      {loading ? <LoadingRows /> : error ? (
        <EmptyState icon="error" title="Yuklenemedi" description={error} />
      ) : !data ? null : (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <Card className="p-4"><p className="text-xs text-on-surface-variant">Bekleyen</p>
              <p className="text-xl font-semibold">{formatMoney(data.totals.held, data.currency)}</p></Card>
            <Card className="p-4"><p className="text-xs text-on-surface-variant">Serbest birakilan</p>
              <p className="text-xl font-semibold">{formatMoney(data.totals.released, data.currency)}</p></Card>
            <Card className="p-4"><p className="text-xs text-on-surface-variant">Iade edilen</p>
              <p className="text-xl font-semibold">{formatMoney(data.totals.refunded, data.currency)}</p></Card>
          </div>
          <p className="text-sm text-on-surface-variant">Odeme takvimi henuz tanimlanmadi</p>

          {data.items.length === 0 ? (
            <EmptyState icon="payments" title="Kazanc kaydi yok" description="Secili tarih araliginda odenmis siparis yok." />
          ) : (
            <div className="space-y-3">
              {data.items.map(r => (
                <Card key={r.orderItemId} className="p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="font-semibold text-on-surface">{VERTICAL_LABEL[r.vertical] ?? r.vertical}</p>
                      <p className="text-xs text-on-surface-variant">
                        {r.heldAt ? formatDate(r.heldAt) : ''}{r.releasedAt ? ` · Serbest: ${formatDate(r.releasedAt)}` : ''}
                      </p>
                    </div>
                    <StatusPill tone={r.escrowState === 'RELEASED' ? 'success' : r.escrowState === 'HELD' ? 'warning' : 'neutral'}>
                      {ESCROW_STATE_LABEL[r.escrowState]}
                    </StatusPill>
                  </div>
                  <div className="mt-2 grid gap-1 text-sm">
                    <div className="flex justify-between"><span className="text-outline">Brut</span><span>{formatMoney(r.gross, data.currency)}</span></div>
                    <div className="flex justify-between"><span className="text-outline">Komisyon</span><span>{formatMoney(r.commission, data.currency)}</span></div>
                    <div className="flex justify-between font-semibold"><span>Net</span><span>{formatMoney(r.partnerNet, data.currency)}</span></div>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
