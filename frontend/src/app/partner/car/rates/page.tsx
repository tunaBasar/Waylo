'use client';

/**
 * Rates management page for CAR_RENTAL partner orgs.
 *
 * Fetches all rates from the API, displays them per-branch sorted by
 * category then transmission, allows inline editing and row insertion,
 * and saves dirty rows via PUT upsert.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  partnerCarApi,
  type CategoryRate,
  type CategoryRateItem,
  type Category,
  type Transmission,
  CATEGORY_LABEL,
  TRANSMISSION_LABEL,
  CATEGORY_ORDER,
  TRANSMISSION_ORDER,
  sortRatesByOrder,
  availableCombinations,
  validateRateItem,
  rateKey,
} from '@/lib/partner-car';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, inputClass } from '@/components/partner/ui';

/* ────────────────────────────────────────────────────────────────────────────
 * Single editable / display row
 * ──────────────────────────────────────────────────────────────────────────── */

function RateRow({
  item,
  isNew,
  onChange,
  fieldErrors,
}: {
  item: CategoryRateItem;
  isNew: boolean;
  onChange: (item: CategoryRateItem) => void;
  fieldErrors: Record<string, string | null>;
}) {
  const { category, transmission } = item;
  const catLabel = CATEGORY_LABEL[category];
  const transLabel = TRANSMISSION_LABEL[transmission];

  // Shared updater that maps input names to fields.
  const set = (field: string, value: number | null) => {
    onChange({ ...item, [field]: value });
  };

  const numberField = (
    field: keyof Pick<CategoryRateItem, 'dailyRate' | 'depositAmount' | 'kmLimitPerDay' | 'extraKmFee' | 'minDriverAge' | 'minLicenseYears'>,
    label: string,
    placeholder: string,
    min: string,
    step: string = '1',
  ) => (
    <Field label={label} error={fieldErrors[field] || null}>
      <input
        className={inputClass}
        type="number"
        min={min}
        step={step}
        placeholder={placeholder}
        value={item[field] ?? ''}
        onChange={e => {
          const raw = e.target.value;
          set(field, raw === '' ? null : Number(raw));
        }}
      />
    </Field>
  );

  if (!isNew) {
    // Existing rate: category + transmission are read-only text.
    return (
      <Card className="p-3">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="grid gap-1 sm:grid-cols-2">
            <span className="text-xs font-medium text-on-surface-variant">Kategori</span>
            <span className="text-sm text-on-surface font-medium">{catLabel}</span>
            <span className="text-xs font-medium text-on-surface-variant">Vites</span>
            <span className="text-sm text-on-surface font-medium">{transLabel}</span>
            <span className="text-xs font-medium text-on-surface-variant">Para birimi</span>
            <span className="text-sm text-on-surface font-medium">{item.currency}</span>
          </div>

          {numberField('dailyRate', 'Günlük fiyat', '', '0.01')}
          {numberField('depositAmount', 'Garanti', '', '0')}
          {numberField('kmLimitPerDay', 'Günlük KM sınırı', 'Sinirsiz', '0')}
          {numberField('extraKmFee', 'Ek KM ücreti', 'Boş', '0')}
          {numberField('minDriverAge', 'Min. sürücü yaşı', '', '18')}
          {numberField('minLicenseYears', 'Min. ehliyet yılı', '', '0')}
        </div>
      </Card>
    );
  }

  // New (unsaved) row: all fields editable.
  return (
    <Card className="p-3">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Kategori" error={fieldErrors.category || null}>
          <select
            className={inputClass}
            value={category}
            onChange={e => onChange({ ...item, category: e.target.value as Category })}
          >
            {CATEGORY_ORDER.map(c => (
              <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>
            ))}
          </select>
        </Field>

        <Field label="Vites" error={fieldErrors.transmission || null}>
          <select
            className={inputClass}
            value={transmission}
            onChange={e => onChange({ ...item, transmission: e.target.value as Transmission })}
          >
            {TRANSMISSION_ORDER.map(t => (
              <option key={t} value={t}>{TRANSMISSION_LABEL[t]}</option>
            ))}
          </select>
        </Field>

        {numberField('dailyRate', 'Günlük fiyat', '', '0.01')}
        {numberField('depositAmount', 'Garanti', '', '0')}
        {numberField('kmLimitPerDay', 'Günlük KM sınırı', 'Sinirsiz', '0')}
        {numberField('extraKmFee', 'Ek KM ücreti', 'Boş', '0')}
        {numberField('minDriverAge', 'Min. sürücü yaşı', '', '18')}
        {numberField('minLicenseYears', 'Min. ehliyet yılı', '', '0')}
      </div>
    </Card>
  );
}

/* ────────────────────────────────────────────────────────────────────────────
 * Page
 * ──────────────────────────────────────────────────────────────────────────── */

export default function RatesPage() {
  const { organizationId, loading: workspaceLoading, setHasUnsavedChanges } = usePartner();

  // API data
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rates, setRates] = useState<CategoryRate[]>([]);
  const [branches, setBranches] = useState<{ id: string; name: string; city: string }[]>([]);

  // Fetch rates + branches on mount (or when org changes).
  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    Promise.all([
      partnerCarApi.rates(organizationId),
      partnerCarApi.branches(organizationId),
    ]).then(([ratesRes, branchesRes]) => {
      if (alive) {
        setRates(ratesRes.data ?? []);
        setBranches(branchesRes.data ?? []);
        setError(null);
      }
    }).catch(e => {
      if (alive) setError(e instanceof Error ? e.message : 'Yüklenemedi');
    }).finally(() => {
      if (alive) setLoading(false);
    });
    return () => { alive = false; };
  }, [organizationId]);

  // Dirty rows (edited or newly added, not yet saved).
  const [dirty, setDirty] = useState<CategoryRateItem[]>([]);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Keep dirty in a ref so save() callback can read the latest value.
  const dirtyRef = useRef<CategoryRateItem[]>([]);
  useEffect(() => {
    dirtyRef.current = dirty;
  }, [dirty]);

  // Update unsaved-changes flag whenever dirty changes.
  useEffect(() => {
    setHasUnsavedChanges(dirty.length > 0);
  }, [dirty.length, setHasUnsavedChanges]);

  // ── row CRUD ────────────────────────────────────────────────────────────

  /** Update a single row inside the dirty set. */
  const updateRow = useCallback((item: CategoryRateItem) => {
    const key = rateKey(item);
    setDirty(prev => {
      const idx = prev.findIndex(r => rateKey(r) === key);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = item;
        return copy;
      }
      return [...prev, item];
    });
    setSaveError(null);
  }, []);

  // ── save ────────────────────────────────────────────────────────────────

  const save = useCallback(async () => {
    if (!organizationId || dirty.length === 0 || saving) return;
    setSaving(true);
    setSaveError(null);

    // Validate all dirty rows before sending.
    for (const item of dirtyRef.current) {
      const errs = validateRateItem(item);
      if (Object.keys(errs).length > 0) {
        setSaveError(Object.values(errs).join(' | '));
        setSaving(false);
        return;
      }
    }

    try {
      const res = await partnerCarApi.upsertRates(organizationId, { items: dirtyRef.current });
      // Replace state with server-confirmed data and clear dirty.
      setRates(res.data);
      setDirty([]);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'İşletilemedi';
      setSaveError(msg);
    } finally {
      setSaving(false);
    }
  }, [organizationId, dirty.length, saving]);

  // ── render ──────────────────────────────────────────────────────────────

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yüklenemedi" description={error} />;

  const hasUnsaved = dirty.length > 0;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Fiyatlar"
        subtitle="Kategori fiyat planları"
        actions={
          <div className="flex flex-col gap-1 items-end">
            {saveError && (
              <span className="text-xs text-error font-medium">{saveError}</span>
            )}
            {hasUnsaved && (
              <Button icon="check" onClick={save} disabled={saving || dirty.length === 0}>
                {saving ? 'Kaydediliyor' : 'Kaydet'}
              </Button>
            )}
          </div>
        }
      />

      {branches.length === 0 ? (
        <EmptyState
          icon="attach_money"
          title="Şube yok"
          description="Fiyat tanımlamak için önce bir şube oluşturun."
        />
      ) : (
        <div className="space-y-6">
          {branches.map(branch => {
            // All rates for this branch (existing + dirty).
            const existingBranchRates = rates.filter(
              r => r.branchId === branch.id
            );
            const dirtyBranchRates = dirty.filter(
              r => r.branchId === branch.id
            );

            // Build a set of dirty keys for this branch.
            const dirtyKeys = new Set(dirtyBranchRates.map(rateKey));

            // Merge: dirty overrides existing by key.
            const combined: Map<string, CategoryRateItem> = new Map();
            for (const r of existingBranchRates) {
              combined.set(rateKey(r), r);
            }
            for (const r of dirtyBranchRates) {
              combined.set(rateKey(r), r);
            }
            const merged = [...combined.values()];
            const sorted = sortRatesByOrder(merged);

            // Available add combinations for this branch.
            const available = availableCombinations(existingBranchRates);

            return (
              <div key={branch.id}>
                <h3 className="text-sm font-medium text-on-surface-variant mb-2">
                  {branch.name} ({branch.city})
                </h3>

                {sorted.length === 0 && available.length === 0 ? (
                  <EmptyState
                    icon="price_check"
                    title="Henüz fiyat yok"
                    description="Bu şube için henüz fiyat tanımlanmamış."
                  />
                ) : (
                  <>
                    {sorted.map(item => {
                      const isNew = dirtyKeys.has(rateKey(item));
                      return (
                        <RateRow
                          key={rateKey(item)}
                          item={item}
                          isNew={isNew}
                          onChange={updateRow}
                          fieldErrors={validateRateItem(item)}
                        />
                      );
                    })}

                    {/* Add rows for each available combination. */}
                    {available.map(({ category, transmission }) => {
                      const newItem: CategoryRateItem = {
                        branchId: branch.id,
                        category,
                        transmission,
                        currency: 'TRY',
                        dailyRate: 0,
                        depositAmount: 0,
                        kmLimitPerDay: null,
                        extraKmFee: null,
                        minDriverAge: 21,
                        minLicenseYears: 2,
                      };
                      return (
                        <div key={`${category}~${transmission}`}>
                          <div className="flex items-center gap-2 my-3">
                            <Button
                              variant="secondary"
                              icon="add"
                              onClick={() => updateRow(newItem)}
                            >
                              Ekle
                            </Button>
                            <span className="text-xs text-on-surface-variant">
                              {CATEGORY_LABEL[category]} · {TRANSMISSION_LABEL[transmission]}
                            </span>
                          </div>
                          {/* When the same combo is added, only show it once. */}
                          {dirtyBranchRates.some(d => rateKey(d) === rateKey(newItem)) && (
                            <RateRow
                              key={rateKey(newItem)}
                              item={newItem}
                              isNew={true}
                              onChange={updateRow}
                              fieldErrors={validateRateItem(newItem)}
                            />
                          )}
                        </div>
                      );
                    })}
                  </>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
