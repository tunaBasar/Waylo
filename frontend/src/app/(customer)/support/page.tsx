'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card } from '@/components/partner/ui';
import { SupportStatusPill, SupportThread } from '@/components/SupportThread';
import { ApiError } from '@/lib/api';
import { capabilitiesText, supportApi, type SupportCapabilities, type SupportCase } from '@/lib/support';

const fmt = (iso: string) => new Date(iso).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' });

export default function CustomerSupportPage() {
  const [cap, setCap] = useState<SupportCapabilities | null>(null);
  const [capFailed, setCapFailed] = useState(false);
  const [cases, setCases] = useState<SupportCase[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<SupportCase | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadList = useCallback(() => {
    supportApi.myList().then(r => { setCases(r.data); setError(null); })
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Talepler yüklenemedi'));
  }, []);

  useEffect(() => {
    supportApi.capabilities().then(r => setCap(r.data)).catch(() => setCapFailed(true));
    loadList();
  }, [loadList]);

  useEffect(() => {
    if (!selected) return;
    let alive = true;
    supportApi.myGet(selected).then(r => { if (alive) setDetail(r.data); })
      .catch((e: unknown) => { if (alive) setError(e instanceof ApiError ? e.message : 'Talep yüklenemedi'); });
    return () => { alive = false; };
  }, [selected]);

  const shown = detail && detail.id === selected ? detail : null;

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Destek taleplerim</h2>
      {capFailed && <p className="text-xs text-on-surface-variant" data-testid="side-data-note">Destek kapsami bilgisi su an yuklenemedi.</p>}
      {cap && <p className="rounded-xl bg-tertiary-container p-3 text-sm text-on-tertiary-container" data-testid="support-capabilities">{capabilitiesText(cap)}</p>}
      {error && <p className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>}
      {cases && cases.length === 0 && !error && (
        <p className="text-sm text-on-surface-variant" data-testid="support-empty">Destek talebiniz yok. Siparişlerim sayfasından talep açabilirsiniz.</p>
      )}
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
      {shown && (
        <SupportThread detail={shown} role="CUSTOMER"
          onReply={async body => { setDetail((await supportApi.myReply(shown.id, body)).data); loadList(); }}
          onClose={async () => { setDetail((await supportApi.myClose(shown.id)).data); loadList(); }} />
      )}
    </div>
  );
}
