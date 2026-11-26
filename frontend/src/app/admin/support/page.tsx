'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card, Field, inputClass } from '@/components/partner/ui';
import { SupportStatusPill, SupportThread } from '@/components/SupportThread';
import { useSingleFlight } from '@/lib/use-single-flight';
import { STATUS_LABEL, supportApi, type SupportCase, type SupportStatus } from '@/lib/support';

const fmt = (iso: string) => new Date(iso).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' });

export default function AdminSupportPage() {
  const flight = useSingleFlight();
  const [status, setStatus] = useState<SupportStatus | ''>('');
  const [cases, setCases] = useState<SupportCase[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<SupportCase | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadList = useCallback(() => {
    supportApi.adminList(status || undefined).then(r => { setCases(r.data); setError(null); })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Talepler yüklenemedi'));
  }, [status]);

  useEffect(() => { loadList(); }, [loadList]);

  useEffect(() => {
    if (!selected) return;
    let alive = true;
    supportApi.adminGet(selected).then(r => { if (alive) setDetail(r.data); })
      .catch((e: unknown) => { if (alive) setError(e instanceof Error ? e.message : 'Talep yüklenemedi'); });
    return () => { alive = false; };
  }, [selected]);

  const shown = detail && detail.id === selected ? detail : null;

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Destek talepleri</h2>
      <div className="max-w-xs">
        <Field label="Durum">
          <select className={inputClass} value={status} data-testid="support-status-filter"
            onChange={e => setStatus(e.target.value as SupportStatus | '')}>
            <option value="">Tümü</option>
            {(Object.keys(STATUS_LABEL) as SupportStatus[]).map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </Field>
      </div>
      {error && <p className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>}
      {cases && cases.length === 0 && !error && <p className="text-sm text-on-surface-variant" data-testid="support-empty">Talep bulunamadı.</p>}
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
        <SupportThread detail={shown} role="ADMIN"
          onReply={async body => { await flight(`support-reply:${shown.id}`, async () => { setDetail((await supportApi.adminReply(shown.id, body)).data); loadList(); }); }}
          onClose={async () => { await flight(`support-close:${shown.id}`, async () => { setDetail((await supportApi.adminClose(shown.id)).data); loadList(); }); }} />
      )}
    </div>
  );
}
