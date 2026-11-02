'use client';

/**
 * Vehicles management page for CAR_RENTAL partner orgs.
 *
 * Implements section 4 of CAR-RENTAL-CONTRACT.md:
 * GET/POST /api/partner/organizations/{orgId}/vehicles
 * PUT /api/partner/organizations/{orgId}/vehicles/{id}
 */
import { useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  partnerCarApi,
  type Vehicle,
  type VehicleCreateRequest,
  type VehicleUpdateRequest,
  type Category,
  type Transmission,
  type FuelType,
  CATEGORY_LABEL,
  TRANSMISSION_LABEL,
  FUEL_LABEL,
} from '@/lib/partner-car';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

// Options come from the shared label maps, so every backend enum value is selectable.
const toOptions = <K extends string>(labels: Record<K, string>) =>
  (Object.keys(labels) as K[]).map(value => ({ value, label: labels[value] }));
const CATEGORY_OPTIONS: { value: Category; label: string }[] = toOptions(CATEGORY_LABEL);
const TRANSMISSION_OPTIONS: { value: Transmission; label: string }[] = toOptions(TRANSMISSION_LABEL);
const FUEL_OPTIONS: { value: FuelType; label: string }[] = toOptions(FUEL_LABEL);

function VehicleForm({ vehicle, organizationId, branches, onDone }: {
  vehicle?: Vehicle | null;
  organizationId: string;
  branches: { id: string; name: string }[];
  onDone: (saved: boolean) => void;
}) {
  const isEdit = !!vehicle;
  const [brand, setBrand] = useState(vehicle?.brand ?? '');
  const [model, setModel] = useState(vehicle?.model ?? '');
  const [plateNumber, setPlateNumber] = useState(vehicle?.plateNumber ?? '');
  const [year, setYear] = useState(vehicle?.year ?? new Date().getFullYear());
  const [seatCount, setSeatCount] = useState(vehicle?.seatCount ?? 5);
  const [category, setCategory] = useState<Category>(vehicle?.category ?? 'ECONOMY');
  const [transmission, setTransmission] = useState<Transmission>(vehicle?.transmission ?? 'MANUAL');
  const [fuel, setFuel] = useState<FuelType>(vehicle?.fuelType ?? 'PETROL');
  const [branchIdState, setBranchIdState] = useState(vehicle?.homeBranchId ?? (branches[0]?.id ?? ''));
  const [missingDocuments] = useState<string[]>(vehicle?.missingDocuments ?? []);
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      if (isEdit && vehicle) {
        const body: VehicleUpdateRequest = {
          brand: brand.trim() || undefined,
          model: model.trim() || undefined,
          plateNumber: plateNumber.trim() || undefined,
          year,
          seatCount,
          category,
          transmission,
          fuelType: fuel,
          homeBranchId: branchIdState || undefined,
        };
        await partnerCarApi.updateVehicle(organizationId, vehicle.id, body);
      } else {
        const body: VehicleCreateRequest = {
          homeBranchId: branchIdState,
          plateNumber: plateNumber.trim(),
          brand: brand.trim(),
          model: model.trim(),
          year,
          category,
          transmission,
          fuelType: fuel,
          seatCount,
        };
        await partnerCarApi.createVehicle(organizationId, body);
      }
      onDone(true);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Isletilemedi');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Marka">
          <input className={inputClass} value={brand} onChange={e => setBrand(e.target.value)} maxLength={60} />
        </Field>

        <Field label="Model">
          <input className={inputClass} value={model} onChange={e => setModel(e.target.value)} maxLength={60} />
        </Field>

        <Field label="Plaka">
          <input className={inputClass} value={plateNumber} onChange={e => setPlateNumber(e.target.value)} maxLength={20} />
        </Field>

        <Field label="Yil">
          <input className={inputClass} type="number" min={1900} max={2030}
            value={year} onChange={e => setYear(Number(e.target.value))} />
        </Field>

        <Field label="Koltuk">
          <input className={inputClass} type="number" min={1} max={50}
            value={seatCount} onChange={e => setSeatCount(Number(e.target.value))} />
        </Field>

        <Field label="Kategori">
          <select className={inputClass} value={category} onChange={e => setCategory(e.target.value as Category)}>
            {CATEGORY_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </Field>

        <Field label="Vites">
          <select className={inputClass} value={transmission} onChange={e => setTransmission(e.target.value as Transmission)}>
            {TRANSMISSION_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </Field>

        <Field label="Yakit">
          <select className={inputClass} value={fuel} onChange={e => setFuel(e.target.value as FuelType)}>
            {FUEL_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </Field>

        <Field label="Sube">
          <select className={inputClass} value={branchIdState} onChange={e => setBranchIdState(e.target.value)}>
            <option value="">Sube atanmadi</option>
            {branches.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </Field>

        {isEdit && missingDocuments.length > 0 && (
          <div className="sm:col-span-2 rounded-lg border border-error-container p-3">
            <p className="text-sm text-error">Eksik belgeler:</p>
            <ul className="text-sm text-on-surface-variant list-disc ml-5 mt-1">
              {missingDocuments.map(d => <li key={d}>{d}</li>)}
            </ul>
          </div>
        )}

        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="check" disabled={saving}>
            {saving ? 'Kaydediliyor' : isEdit ? 'Guncelle' : 'Olustur'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

function VehicleRow({ vehicle, organizationId, branches }: {
  vehicle: Vehicle;
  organizationId: string;
  branches: { id: string; name: string }[];
}) {
  const [editing, setEditing] = useState(false);
  const [active, setActive] = useState(vehicle.active);
  const { setHasUnsavedChanges } = usePartner();
  const inFlight = useRef(false);

  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold text-on-surface truncate">{vehicle.brand} {vehicle.model}</p>
          <p className="text-sm text-on-surface-variant mt-0.5">{vehicle.plateNumber}</p>
          <p className="text-xs text-outline mt-0.5">
            {CATEGORY_LABEL[vehicle.category]} · {TRANSMISSION_LABEL[vehicle.transmission]} · {FUEL_LABEL[vehicle.fuelType]}
          </p>
          {vehicle.homeBranchId && (
            <p className="text-xs text-outline mt-1">
              Sube: {branches.find(b => b.id === vehicle.homeBranchId)?.name ?? vehicle.homeBranchId}
            </p>
          )}
        </div>
        <StatusPill tone={active ? 'success' : 'neutral'}>{active ? 'Aktif' : 'Durduruldu'}</StatusPill>
      </div>

      {vehicle.missingDocuments.length > 0 && (
        <div className="mt-2 rounded-lg bg-error-container/10 p-2 text-xs text-error">
          {vehicle.missingDocuments.join(', ')}
        </div>
      )}

      {!editing && (
        <div className="mt-3 flex gap-2">
          <Button variant="ghost" onClick={() => { setEditing(true); setHasUnsavedChanges(true); }}>Duzenle</Button>
          <Button
            variant="ghost"
            onClick={async () => {
              if (inFlight.current) return;
              inFlight.current = true;
              try {
                if (active) {
                  await partnerCarApi.deactivateVehicle(organizationId, vehicle.id);
                } else {
                  await partnerCarApi.activateVehicle(organizationId, vehicle.id);
                }
                setActive(!active);
              } catch (err) {
                alert(err instanceof Error ? err.message : 'Isletilemedi');
              } finally {
                inFlight.current = false;
              }
            }}
          >
            {active ? 'Durdur' : 'Baslat'}
          </Button>
        </div>
      )}

      {editing && (
        <VehicleForm
          key={vehicle.id}
          vehicle={vehicle}
          organizationId={organizationId}
          branches={branches}
          onDone={() => { setEditing(false); setHasUnsavedChanges(false); }}
        />
      )}
    </Card>
  );
}

export default function VehiclesPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [items, setItems] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [branches, setBranches] = useState<{ id: string; name: string }[]>([]);

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    partnerCarApi.vehicles(organizationId)
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

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    partnerCarApi.branches(organizationId)
      .then(res => { if (alive) setBranches(res.data ?? []); })
      .catch(() => { /* branches load optional */ });
    return () => { alive = false; };
  }, [organizationId]);

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Filo"
        subtitle="Arac yonetimi"
        actions={!showForm && (
          <Button icon="add" onClick={() => setShowForm(true)}>Yeni arac</Button>
        )}
      />

      {showForm && (
        <VehicleForm
          organizationId={organizationId!}
          branches={branches}
          onDone={() => { setShowForm(false); }}
        />
      )}

      {items.length === 0 ? (
        !showForm && (
          <EmptyState icon="directions_car" title="Henuz arac yok"
            description="Arac kiralama icin arac tanimlayin."
            action={<Button icon="add" onClick={() => setShowForm(true)}>Arac ekle</Button>} />
        )
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {items.map(v => (
            <VehicleRow key={v.id} vehicle={v} organizationId={organizationId!} branches={branches} />
          ))}
        </div>
      )}
    </div>
  );
}
