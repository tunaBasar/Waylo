'use client';

/** Admin review of bus company and bus documents: list, open file, approve, reject with reason. */
import { useEffect, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { adminBusApi, BUS_DOC_LABEL, type AdminBusDocument, type BusDocStatus } from '@/lib/bus';

type Filter = 'ALL' | BusDocStatus;
const FILTER_LABEL: Record<Filter, string> = { ALL: 'Tumu', PENDING: 'Beklemede', APPROVED: 'Onayli', REJECTED: 'Reddedilen' };
const STATUS_LABEL: Record<BusDocStatus, string> = { PENDING: 'Beklemede', APPROVED: 'Onayli', REJECTED: 'Reddedildi' };
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export default function AdminBusDocumentsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [items, setItems] = useState<AdminBusDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<Filter>('PENDING');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectFor, setRejectFor] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (!isAdmin) return;
    adminBusApi.documents()
      .then(res => setItems(res.data ?? []))
      .catch(() => setError('Belgeler yuklenemedi.'))
      .finally(() => setLoading(false));
  }, [isAdmin]);

  function patch(id: string, status: BusDocStatus, rejectReason: string | null) {
    setItems(list => list.map(i => (i.document.id === id ? { ...i, document: { ...i.document, status, rejectReason } } : i)));
  }

  async function approve(id: string) {
    setBusy(id); setError('');
    try { await adminBusApi.approve(id); patch(id, 'APPROVED', null); }
    catch (e) { setError(e instanceof Error ? e.message : 'Onaylanamadi.'); }
    finally { setBusy(null); }
  }

  async function reject(id: string) {
    if (!reason.trim()) { setError('Red nedeni gerekli.'); return; }
    setBusy(id); setError('');
    try { await adminBusApi.reject(id, reason.trim()); patch(id, 'REJECTED', reason.trim()); setRejectFor(null); setReason(''); }
    catch (e) { setError(e instanceof Error ? e.message : 'Reddedilemedi.'); }
    finally { setBusy(null); }
  }

  async function download(i: AdminBusDocument) {
    setBusy(i.document.id); setError('');
    try {
      const res = await fetch(`${API_BASE}/api/admin/bus-documents/${encodeURIComponent(i.document.id)}/file`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('waylo_token') || ''}` },
      });
      if (!res.ok) throw new Error('Belge indirilemedi.');
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = url; a.download = i.document.fileName; document.body.appendChild(a); a.click(); a.remove();
      URL.revokeObjectURL(url);
    } catch (e) { setError(e instanceof Error ? e.message : 'Belge indirilemedi.'); }
    finally { setBusy(null); }
  }

  if (!isAdmin) {
    return <p className="text-center py-12 text-on-surface-variant text-sm">Bu sayfa yalnizca yoneticiler icindir</p>;
  }
  const shown = items.filter(i => filter === 'ALL' || i.document.status === filter);

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Otobus Belge Yonetimi</h2>
      <div className="flex gap-2">
        {(Object.keys(FILTER_LABEL) as Filter[]).map(f => (
          <button key={f} onClick={() => setFilter(f)}
            className={`rounded-lg px-3 py-1.5 text-xs font-medium ${filter === f ? 'bg-primary text-on-primary' : 'bg-surface-container text-on-surface-variant'}`}>
            {FILTER_LABEL[f]}
          </button>
        ))}
      </div>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
      {loading ? <p className="text-sm text-on-surface-variant">Yukleniyor</p> : shown.length === 0 ? (
        <p className="text-center py-12 text-on-surface-variant text-sm">Belge bulunamadi</p>
      ) : (
        <div className="space-y-space-sm">
          {shown.map(i => {
            const d = i.document;
            return (
              <div key={d.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 space-y-2">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-sm font-bold text-on-surface">{BUS_DOC_LABEL[d.type] ?? d.type}</p>
                    <p className="text-[11px] text-on-surface-variant">
                      {i.organizationName ?? d.organizationId}{i.busLabel ? ` / ${i.busLabel}` : ''} / Belge no: {d.documentNumber}
                    </p>
                  </div>
                  <span className="text-[10px] font-bold uppercase px-2.5 py-1 rounded-full bg-surface-container">{STATUS_LABEL[d.status]}</span>
                </div>
                <p className="text-xs text-on-surface-variant">Son kullanma: {d.validUntil ?? '-'}</p>
                {d.status === 'REJECTED' && d.rejectReason && <p className="text-xs text-error">Red nedeni: {d.rejectReason}</p>}
                <div className="flex flex-wrap items-center gap-2">
                  <button disabled={busy === d.id} onClick={() => download(i)}
                    className="rounded-lg bg-surface-container px-3 py-1.5 text-xs font-medium text-on-surface disabled:opacity-50">Belgeyi indir</button>
                  {d.status === 'PENDING' && (
                    <>
                      <button disabled={busy === d.id} onClick={() => approve(d.id)}
                        className="rounded-lg bg-secondary px-3 py-1.5 text-xs font-medium text-on-secondary disabled:opacity-50">Onayla</button>
                      {rejectFor === d.id ? (
                        <>
                          <input value={reason} onChange={e => setReason(e.target.value)} placeholder="Red nedeni" maxLength={500}
                            className="rounded-lg bg-surface-container px-3 py-1.5 text-xs w-56" autoFocus />
                          <button disabled={busy === d.id} onClick={() => reject(d.id)}
                            className="rounded-lg bg-error-container px-3 py-1.5 text-xs font-medium text-on-error-container disabled:opacity-50">Reddet</button>
                        </>
                      ) : (
                        <button onClick={() => { setRejectFor(d.id); setReason(''); }}
                          className="rounded-lg bg-error-container px-3 py-1.5 text-xs font-medium text-on-error-container">Reddet</button>
                      )}
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
