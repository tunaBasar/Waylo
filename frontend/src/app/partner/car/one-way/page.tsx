'use client';

/**
 * One-way routes management page for CAR_RENTAL partner orgs.
 *
 * Implements section 5 of CAR-RENTAL-CONTRACT.md:
 * GET /api/partner/organizations/{orgId}/one-way-routes
 * PUT /api/partner/organizations/{orgId}/one-way-routes { routes: [{fromBranchId, toBranchId, fee, currency}] }
 */
import { useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import { partnerCarApi, type OneWayRoute } from '@/lib/partner-car';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, inputClass } from '@/components/partner/ui';

export default function OneWayPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [routes, setRoutes] = useState<OneWayRoute[]>([]);
  const [branches, setBranches] = useState<{ id: string; name: string; city: string }[]>([]);
  const [uncommitted, setUncommitted] = useState<OneWayRoute[]>([]);

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    Promise.all([
      partnerCarApi.oneWayRoutes(organizationId),
      partnerCarApi.branches(organizationId),
    ]).then(([routesRes, branchesRes]) => {
      if (alive) {
        setRoutes(routesRes.data ?? []);
        setBranches(branchesRes.data ?? []);
        setError(null);
      }
    }).catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Yuklenemedi'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [organizationId]);

  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);
  const save = async () => {
    if (uncommitted.length === 0 || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      const res = await partnerCarApi.replaceOneWayRoutes(organizationId!, { routes: uncommitted });
      setRoutes(res.data);
      setUncommitted([]);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Isletilemedi');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  };

  // Manage routes
  const addRoute = () => {
    setUncommitted(prev => [
      ...prev,
      {
        id: `new-${Date.now()}`,
        organizationId: organizationId!,
        fromBranchId: branches[0]?.id ?? '',
        toBranchId: branches[1]?.id ?? '',
        fee: 0,
        currency: 'TRY',
      },
    ]);
  };

  const updateRoute = (routeId: string, changes: Partial<OneWayRoute>) => {
    setUncommitted(prev =>
      prev.map(r => r.id === routeId ? { ...r, ...changes } : r)
    );
  };

  const removeRoute = (routeId: string) => {
    setUncommitted(prev => prev.filter(r => r.id !== routeId));
  };

  const hasUnsaved = uncommitted.length > 0;

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  if (branches.length < 2) {
    return (
      <div className="space-y-space-lg">
        <PageHeader title="Tek yon" subtitle="Farkli subeler arasi tek yon kiralama yollari" />
        <EmptyState icon="swap_horiz" title="En az iki sube gerekli"
          description="Tek yon kiralama yollarini tanimlamak icin en az iki sube olusturun." />
      </div>
    );
  }

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Tek yon"
        subtitle="Farkli subeler arasi tek yon kiralama yollari"
        actions={hasUnsaved && (
          <Button icon="check" onClick={save} disabled={saving}>
            {saving ? 'Kaydediliyor' : 'Kaydet'}
          </Button>
        )}
      />

      <div className="flex gap-2 mb-2">
        <Button icon="add" onClick={addRoute}>Yeni yol</Button>
      </div>

      {/* Existing routes (committed) */}
      {routes.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm font-medium text-on-surface-variant">Kaydedilmis yollar</p>
          {routes.map(r => (
            <OneWayRow key={r.id} route={r} branches={branches} />
          ))}
        </div>
      )}

      {/* Uncommitted routes */}
      {uncommitted.map(r => (
        <OneWayEditor key={r.id} route={r} branches={branches}
          onChange={updateRoute} onRemove={() => removeRoute(r.id)} />
      ))}
    </div>
  );
}

function OneWayRow({ route, branches }: {
  route: OneWayRoute;
  branches: { id: string; name: string; city: string }[];
}) {
  return (
    <Card className="p-3 flex items-center gap-3">
      <span className="text-sm text-on-surface">{branchName(route.fromBranchId, branches)}</span>
      <span className="text-outline">→</span>
      <span className="text-sm text-on-surface">{branchName(route.toBranchId, branches)}</span>
      <span className="ml-auto text-sm font-medium text-on-surface">
        {route.currency} {route.fee.toLocaleString('tr-TR')}
      </span>
    </Card>
  );
}

function OneWayEditor({ route, branches, onChange, onRemove }: {
  route: OneWayRoute;
  branches: { id: string; name: string; city: string }[];
  onChange: (routeId: string, changes: Partial<OneWayRoute>) => void;
  onRemove: () => void;
}) {
  return (
    <Card className="p-4">
      <div className="grid gap-3 sm:grid-cols-4">
        <Field label="Kalkis subesi">
          <select className={inputClass} value={route.fromBranchId}
            onChange={e => onChange(route.id, { fromBranchId: e.target.value })}>
            {branches.map(b => (
              <option key={b.id} value={b.id}>{b.name} ({b.city})</option>
            ))}
          </select>
        </Field>

        <Field label="Varis subesi">
          <select className={inputClass} value={route.toBranchId}
            onChange={e => onChange(route.id, { toBranchId: e.target.value })}>
            {branches.filter(b => b.id !== route.fromBranchId).map(b => (
              <option key={b.id} value={b.id}>{b.name} ({b.city})</option>
            ))}
          </select>
        </Field>

        <Field label="Ücret">
          <input className={inputClass} type="number" min={0} step={1}
            value={route.fee} onChange={e => onChange(route.id, { fee: Number(e.target.value) || 0 })} />
        </Field>

        <Field label="Para birimi">
          <select className={inputClass} value={route.currency}
            onChange={e => onChange(route.id, { currency: e.target.value })}>
            <option value="TRY">TRY</option>
            <option value="USD">USD</option>
            <option value="EUR">EUR</option>
          </select>
        </Field>
      </div>
      <div className="mt-3 flex gap-2">
        <Button variant="ghost" onClick={onRemove}>Sil</Button>
      </div>
    </Card>
  );
}

function branchName(branchId: string, branches: { id: string; name: string; city: string }[]): string {
  const b = branches.find(b => b.id === branchId);
  return b ? `${b.name} (${b.city})` : branchId;
}
