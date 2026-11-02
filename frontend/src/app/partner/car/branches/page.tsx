'use client';

/**
 * Branches management page for CAR_RENTAL partner orgs.
 *
 * Implements section 3 of CAR-RENTAL-CONTRACT.md:
 * GET/POST /api/partner/organizations/{orgId}/branches
 * PUT /api/partner/organizations/{orgId}/branches/{id}
 */
import { useEffect, useRef, useState } from 'react';
import { useToast } from '@/components/Toast';
import { usePartner } from '@/lib/partner-context';
import { partnerCarApi, type Branch, type BranchCreateRequest, type BranchUpdateRequest, formatTime, isValidTime } from '@/lib/partner-car';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

function BranchForm({ branch, organizationId, onDone }: {
  branch?: Branch | null;
  organizationId: string;
  onDone: (saved: boolean) => void;
}) {
  const toast = useToast();
  const isEdit = !!branch;

  const [name, setName] = useState(branch?.name ?? '');
  const [city, setCity] = useState(branch?.city ?? '');
  const [address, setAddress] = useState(branch?.address ?? '');
  const [phone, setPhone] = useState(branch?.phone ?? '');
  const [openTime, setOpenTime] = useState(branch?.openTime ?? '09:00');
  const [closeTime, setCloseTime] = useState(branch?.closeTime ?? '18:00');
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  const nameValid = name.trim().length > 0;
  const cityValid = city.trim().length > 0;
  const openValid = isValidTime(openTime);
  const closeValid = isValidTime(closeTime);
  const valid = nameValid && cityValid && openValid && closeValid;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      if (isEdit && branch) {
        const body: BranchUpdateRequest = {
          name: name.trim(),
          city: city.trim(),
          address: address.trim() || undefined,
          phone: phone.trim() || undefined,
          openTime,
          closeTime,
        };
        await partnerCarApi.updateBranch(organizationId, branch.id, body);
        toast.success('Sube guncellendi');
      } else {
        const body: BranchCreateRequest = {
          name: name.trim(),
          city: city.trim(),
          address: address.trim(),
          phone: phone.trim(),
          openTime,
          closeTime,
        };
        await partnerCarApi.createBranch(organizationId, body);
        toast.success('Sube olusturuldu');
      }
      onDone(true);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Isletilemedi');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Sube adi" error={!nameValid ? 'Sube adi gerekli' : undefined}>
          <input className={inputClass} value={name} onChange={e => setName(e.target.value)} maxLength={255} autoFocus={!isEdit} readOnly={isEdit} />
        </Field>

        <Field label="Sehir" error={!cityValid ? 'Sehir gerekli' : undefined}>
          <input className={inputClass} value={city} onChange={e => setCity(e.target.value)} maxLength={120} />
        </Field>

        <div className="sm:col-span-2">
          <Field label="Adres">
            <input className={inputClass} value={address} onChange={e => setAddress(e.target.value)} maxLength={500} />
          </Field>
        </div>

        <Field label="Telefon">
          <input className={inputClass} value={phone} onChange={e => setPhone(e.target.value)} maxLength={40} />
        </Field>

        <Field label="Acilis" error={!openValid ? '00:00-23:59 formatinda' : undefined}>
          <input className={inputClass} type="time" value={openTime} onChange={e => setOpenTime(e.target.value)} />
        </Field>

        <Field label="Kapanis" error={!closeValid ? '00:00-23:59 formatinda' : undefined}>
          <input className={inputClass} type="time" value={closeTime} onChange={e => setCloseTime(e.target.value)} />
        </Field>

        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="check" disabled={saving || !valid}>
            {saving ? 'Kaydediliyor' : isEdit ? 'Guncelle' : 'Olustur'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

export default function BranchesPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const toast = useToast();
  const [items, setItems] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Branch | null>(null);

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    partnerCarApi.branches(organizationId)
      .then(res => {
        if (alive) {
          setItems(res.data ?? []);
          setError(null);
        }
      })
      .catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Yuklenemedi'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [organizationId]);

  const reload = () => {
    if (!organizationId) return;
    partnerCarApi.branches(organizationId)
      .then(res => { setItems(res.data ?? []); setError(null); })
      .catch(e => { setError(e instanceof Error ? e.message : 'Yuklenemedi'); });
  };

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Subeler"
        subtitle="Arac kiralama subeleri"
        actions={!showForm && !editing && (
          <Button icon="add" onClick={() => setShowForm(true)}>Yeni sube</Button>
        )}
      />

      {showForm && (
        <BranchForm organizationId={organizationId!} onDone={saved => { setShowForm(false); if (saved) void reload(); }} />
      )}

      {editing && (
        <BranchForm
          key={editing.id}
          branch={editing}
          organizationId={organizationId!}
          onDone={saved => { setEditing(null); if (saved) void reload(); }}
        />
      )}

      {items.length === 0 ? (
        !showForm && !editing && (
          <EmptyState icon="location_on" title="Henuz sube yok"
            description="Arac alip-basabilmek icin once bir sube tanimlayin."
            action={<Button icon="add" onClick={() => setShowForm(true)}>Sube ekle</Button>} />
        )
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {items.map(b => (
            <Card key={b.id} className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-on-surface truncate">{b.name}</p>
                  <p className="text-sm text-on-surface-variant mt-0.5">{b.city}</p>
                </div>
                <StatusPill tone={b.active ? 'success' : 'neutral'}>{b.active ? 'Aktif' : 'Pasif'}</StatusPill>
              </div>
              <p className="text-xs text-outline mt-2">{b.address}</p>
              {b.phone && <p className="text-xs text-outline">{b.phone}</p>}
              <p className="text-xs text-outline mt-1">
                {formatTime(b.openTime)} - {formatTime(b.closeTime)} (hergun)
              </p>
              <div className="mt-3 flex gap-2">
                <Button variant="ghost" onClick={() => { setShowForm(false); setEditing(b); }}>Duzenle</Button>
                <Button
                  variant="ghost"
                  onClick={async () => {
                    const next = !b.active;
                    const res = await partnerCarApi.updateBranch(organizationId!, b.id, { active: next });
                    setItems(prev => prev.map(x => x.id === b.id ? res.data : x));
                    toast.success(next ? 'Sube aktif edildi' : 'Sube pasif yapildi');
                  }}
                >
                  {b.active ? 'Pasif yap' : 'Aktif yap'}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
