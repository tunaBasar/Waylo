'use client';

/** Company and bus documents of a BUS partner org (upload, review status, download). */
import { useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  partnerBusApi, BUS_DOC_LABEL, ORG_BUS_DOC_TYPES, BUS_BUS_DOC_TYPES, formatTripTime,
  type BusDocType, type BusDocument, type BusUnit,
} from '@/lib/bus';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

const ALL_TYPES: BusDocType[] = [...ORG_BUS_DOC_TYPES, ...BUS_BUS_DOC_TYPES];

function UploadForm({ organizationId, buses, onDone }: {
  organizationId: string; buses: BusUnit[]; onDone: (saved: boolean) => void;
}) {
  const [type, setType] = useState<BusDocType>('TRANSPORT_AUTHORIZATION');
  const [documentNumber, setDocumentNumber] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [busId, setBusId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const busLevel = BUS_BUS_DOC_TYPES.includes(type);
  const needsDate = type !== 'TAX_CERTIFICATE';
  const ready = !!file && documentNumber.trim() && (!busLevel || busId) && (!needsDate || validUntil);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!ready || !file || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    setError('');
    try {
      await partnerBusApi.uploadDocument(organizationId, {
        file, type, documentNumber: documentNumber.trim(), validUntil: validUntil || null, busId: busLevel ? busId : null,
      });
      onDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Yuklenemedi');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Belge tipi">
          <select className={inputClass} value={type} onChange={e => setType(e.target.value as BusDocType)}>
            {ALL_TYPES.map(t => <option key={t} value={t}>{BUS_DOC_LABEL[t]}</option>)}
          </select>
        </Field>
        <Field label="Belge no"><input className={inputClass} value={documentNumber} maxLength={100}
          onChange={e => setDocumentNumber(e.target.value)} /></Field>
        <Field label="Gecerlilik bitis" hint={needsDate ? 'Gerekli' : 'Istege bagli'}>
          <input className={inputClass} type="date" value={validUntil} onChange={e => setValidUntil(e.target.value)} />
        </Field>
        <Field label="Otobus">
          <select className={inputClass} value={busId} disabled={!busLevel} onChange={e => setBusId(e.target.value)}>
            <option value="">{busLevel ? 'Otobus secin' : 'Firma belgesi'}</option>
            {busLevel && buses.map(b => <option key={b.id} value={b.id}>{b.plateNumber}</option>)}
          </select>
        </Field>
        <Field label="Dosya" hint="PDF, JPEG veya PNG, en fazla 10 MB">
          <input className={inputClass} type="file" accept="application/pdf,image/jpeg,image/png"
            onChange={e => setFile(e.target.files?.[0] ?? null)} />
        </Field>
        {error && <p className="sm:col-span-2 text-sm text-error">{error}</p>}
        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="upload_file" disabled={saving || !ready}>{saving ? 'Yukleniyor' : 'Yukle'}</Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

export default function BusDocumentsPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [docs, setDocs] = useState<BusDocument[]>([]);
  const [buses, setBuses] = useState<BusUnit[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    Promise.all([partnerBusApi.documents(organizationId), partnerBusApi.buses(organizationId)])
      .then(([d, b]) => { if (alive) { setDocs(d.data ?? []); setBuses(b.data ?? []); setError(null); } })
      .catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Yuklenemedi'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [organizationId, version]);

  async function download(d: BusDocument) {
    if (!organizationId) return;
    try {
      const res = await partnerBusApi.downloadDocument(organizationId, d.id);
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = d.fileName;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Indirilemedi');
    }
  }

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;
  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Belgeler" subtitle="Firma ve otobus belgeleri, platform onayindan sonra gecerli olur"
        actions={!showForm && <Button icon="upload_file" onClick={() => setShowForm(true)}>Belge yukle</Button>} />
      {showForm && organizationId && (
        <UploadForm organizationId={organizationId} buses={buses}
          onDone={saved => { setShowForm(false); if (saved) setVersion(v => v + 1); }} />
      )}
      {docs.length === 0 ? (
        !showForm && <EmptyState icon="description" title="Henuz belge yok" description="Belge yukleyerek baslayin." />
      ) : (
        <div className="space-y-2">
          {docs.map(d => {
            const expired = !!d.validUntil && d.validUntil < today;
            const bus = buses.find(b => b.id === d.busId);
            return (
              <Card key={d.id} className="p-3 flex flex-wrap items-center gap-4">
                <span className="text-sm text-on-surface flex-1 min-w-0 truncate">
                  {BUS_DOC_LABEL[d.type]}{bus && <span className="text-on-surface-variant"> ({bus.plateNumber})</span>}
                  <span className="text-xs text-outline block">{d.documentNumber}</span>
                </span>
                {d.validUntil && <span className="text-xs text-outline">Gecerlilik: {formatTripTime(d.validUntil + 'T00:00').slice(0, 10)}</span>}
                <StatusPill tone={expired || d.status === 'REJECTED' ? 'error' : d.status === 'APPROVED' ? 'success' : 'neutral'}>
                  {expired ? 'Suresi doldu' : d.status === 'APPROVED' ? 'Onaylandi' : d.status === 'REJECTED' ? 'Reddedildi' : 'Onay bekliyor'}
                </StatusPill>
                {d.status === 'REJECTED' && d.rejectReason && <span className="text-xs text-error">Red nedeni: {d.rejectReason}</span>}
                <Button variant="ghost" icon="download" onClick={() => download(d)}>Indir</Button>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
