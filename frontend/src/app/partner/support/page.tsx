'use client';

import { useCallback, useEffect, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import { partnerErrorMessage } from '@/lib/partner';
import { supportApi, type SupportCase } from '@/lib/support';
import { Card, EmptyState, LoadingRows, PageHeader } from '@/components/partner/ui';
import { SupportStatusPill, SupportThread } from '@/components/SupportThread';
import { useSingleFlight } from '@/lib/use-single-flight';

const fmt = (iso: string) => new Date(iso).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' });

export default function PartnerSupportPage() {
  const flight = useSingleFlight();
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [cases, setCases] = useState<SupportCase[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<SupportCase | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadList = useCallback(() => {
    if (!organizationId) return;
    supportApi.partnerList(organizationId).then(r => { setCases(r.data); setError(null); })
      .catch(e => setError(partnerErrorMessage(e, 'Talepler yüklenemedi')));
  }, [organizationId]);

  useEffect(() => { loadList(); }, [loadList]);

  useEffect(() => {
    if (!organizationId || !selected) return;
    let alive = true;
    supportApi.partnerGet(organizationId, selected).then(r => { if (alive) setDetail(r.data); })
      .catch(e => { if (alive) setError(partnerErrorMessage(e, 'Talep yüklenemedi')); });
    return () => { alive = false; };
  }, [organizationId, selected]);

  const shown = detail && detail.id === selected ? detail : null;

  return (
    <div className="space-y-space-md">
      <PageHeader title="Destek talepleri" subtitle="Siparişlerinizle ilgili müşteri talepleri" />
      {error && <p className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>}
      {(workspaceLoading || (!cases && !error)) && <LoadingRows />}
      {cases && cases.length === 0 && <EmptyState icon="support_agent" title="Destek talebi yok" />}
      <div className="space-y-2">
        {(cases ?? []).map(c => (
          <button key={c.id} type="button" onClick={() => setSelected(c.id)} data-testid="support-case-row" className="block w-full text-left">
            <Card className="p-3 flex items-center justify-between gap-2">
              <span>
                <span className="block text-sm font-medium text-on-surface">{c.subject}</span>
                <span className="block text-xs text-on-surface-variant">Sipariş #{c.orderId} · {fmt(c.updatedAt)}</span>
              </span>
              <SupportStatusPill status={c.status} />
            </Card>
          </button>
        ))}
      </div>
      {shown && organizationId && (
        <SupportThread detail={shown} role="PARTNER"
          onReply={async body => { await flight(`support-reply:${shown.id}`, async () => { setDetail((await supportApi.partnerReply(organizationId, shown.id, body)).data); loadList(); }); }} />
      )}
    </div>
  );
}
