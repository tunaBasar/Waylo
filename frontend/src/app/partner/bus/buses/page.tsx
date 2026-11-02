'use client';

/** Buses of a BUS partner org: create, activate/deactivate, see missing documents. */
import { useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import { partnerBusApi, BUS_DOC_LABEL, LAYOUT_LABEL, type BusUnit, type SeatLayout } from '@/lib/bus';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

function BusForm({ organizationId, onDone }: { organizationId: string; onDone: (saved: boolean) => void }) {
  const [form, setForm] = useState({ plateNumber: '', brand: '', model: '', year: String(new Date().getFullYear()), layout: 'TWO_PLUS_TWO' as SeatLayout, rowCount: '10' });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));
  const valid = form.plateNumber.trim() && form.brand.trim() && form.model.trim();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    setError('');
    try {
      await partnerBusApi.createBus(organizationId, {
        plateNumber: form.plateNumber.trim(), brand: form.brand.trim(), model: form.model.trim(),
        year: Number(form.year), layout: form.layout, rowCount: Number(form.rowCount),
      });
      onDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Kaydedilemedi');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Plaka"><input className={inputClass} value={form.plateNumber} onChange={set('plateNumber')} maxLength={20} autoFocus /></Field>
        <Field label="Marka"><input className={inputClass} value={form.brand} onChange={set('brand')} maxLength={100} /></Field>
        <Field label="Model"><input className={inputClass} value={form.model} onChange={set('model')} maxLength={100} /></Field>
        <Field label="Yil"><input className={inputClass} type="number" min={1980} max={2100} value={form.year} onChange={set('year')} /></Field>
        <Field label="Koltuk duzeni">
          <select className={inputClass} value={form.layout} onChange={set('layout')}>
            {(Object.keys(LAYOUT_LABEL) as SeatLayout[]).map(l => <option key={l} value={l}>{LAYOUT_LABEL[l]}</option>)}
          </select>
        </Field>
        <Field label="Sira sayisi" hint="5 ile 20 arasi">
          <input className={inputClass} type="number" min={5} max={20} value={form.rowCount} onChange={set('rowCount')} />
        </Field>
        {error && <p className="sm:col-span-2 text-sm text-error">{error}</p>}
        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="check" disabled={saving || !valid}>{saving ? 'Kaydediliyor' : 'Otobus ekle'}</Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

export default function BusesPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [buses, setBuses] = useState<BusUnit[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    partnerBusApi.buses(organizationId)
      .then(res => { if (alive) { setBuses(res.data ?? []); setError(null); } })
      .catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Yuklenemedi'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [organizationId, version]);

  async function toggle(b: BusUnit) {
    if (!organizationId) return;
    try {
      await partnerBusApi.setBusActive(organizationId, b.id, !b.active);
      setVersion(v => v + 1);
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Islem basarisiz');
    }
  }

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Otobusler" subtitle="Filonuz ve belge durumu"
        actions={!showForm && <Button icon="add" onClick={() => setShowForm(true)}>Otobus ekle</Button>} />
      {showForm && organizationId && (
        <BusForm organizationId={organizationId} onDone={saved => { setShowForm(false); if (saved) setVersion(v => v + 1); }} />
      )}
      {buses.length === 0 ? (
        !showForm && <EmptyState icon="directions_bus" title="Henuz otobus yok" description="Ilk otobusunuzu ekleyin, sonra belgelerini yukleyin." />
      ) : (
        <div className="space-y-3">
          {buses.map(b => (
            <Card key={b.id} className="p-4 space-y-2">
              <div className="flex flex-wrap items-center gap-3">
                <p className="font-semibold text-on-surface flex-1 min-w-0 truncate">
                  {b.plateNumber} <span className="text-on-surface-variant font-normal">{b.brand} {b.model} ({b.year})</span>
                </p>
                <span className="text-xs text-outline">{LAYOUT_LABEL[b.layout]}, {b.seatCount} koltuk</span>
                <StatusPill tone={!b.active ? 'neutral' : b.sellable ? 'success' : 'warning'}>
                  {!b.active ? 'Pasif' : b.sellable ? 'Satisa acik' : 'Belge eksik'}
                </StatusPill>
                <Button variant="ghost" onClick={() => toggle(b)}>{b.active ? 'Pasife al' : 'Aktif et'}</Button>
              </div>
              {b.missingDocuments.length > 0 && (
                <p className="text-xs text-on-surface-variant">
                  Eksik veya suresi dolmus: {b.missingDocuments.map(t => BUS_DOC_LABEL[t]).join(', ')}
                </p>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
