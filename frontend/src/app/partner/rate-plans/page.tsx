'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '@/components/Toast';
import { usePartner } from '@/lib/partner-context';
import {
  isVersionConflict,
  partnerApi,
  partnerErrorMessage,
  type RatePlan,
  type MealPlan,
  type CancellationPolicy,
  MEAL_PLAN_LABEL,
  CANCELLATION_POLICY_LABEL,
} from '@/lib/partner';
import { Button, Card, EmptyState, Field, LoadingRows, NeedsHotel, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

type FormState = {
  code: string;
  name: string;
  mealPlan: MealPlan;
  cancellationPolicy: CancellationPolicy;
  freeCancellationDaysBefore: number | null;
  description: string;
};

const EMPTY_FORM: FormState = {
  code: '',
  name: '',
  mealPlan: 'BREAKFAST',
  cancellationPolicy: 'FREE_CANCELLATION',
  freeCancellationDaysBefore: 3,
  description: '',
};

function RatePlanForm({
  hotelId,
  initial,
  onDone,
}: {
  hotelId: string;
  initial?: RatePlan;
  onDone: (saved: boolean) => void;
}) {
  const toast = useToast();
  const isEdit = !!initial;
  const [form, setForm] = useState<FormState>(
    initial
      ? { code: initial.code, name: initial.name, mealPlan: initial.mealPlan, cancellationPolicy: initial.cancellationPolicy, freeCancellationDaysBefore: initial.freeCancellationDaysBefore, description: initial.description ?? '' }
      : { ...EMPTY_FORM }
  );
  const [saving, setSaving] = useState(false);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [daysError, setDaysError] = useState<string | null>(null);
  const inFlight = useRef(false);

  const set =
    (k: keyof FormState) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
      const v = k === 'code' ? e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '') : e.target.value;
      setForm(f => ({ ...f, [k]: k === 'freeCancellationDaysBefore' && v !== '' ? parseInt(v, 10) : k === 'freeCancellationDaysBefore' && v === '' ? null : v }));
    };

  const validate = useCallback((): boolean => {
    let ok = true;
    const codeRegex = /^[A-Z0-9_]{2,30}$/;
    if (!form.code.trim() || !codeRegex.test(form.code.trim())) {
      setCodeError('2-30 karakter, sadece A-Z 0-9 _');
      ok = false;
    } else {
      setCodeError(null);
    }
    if (!form.name.trim()) {
      setNameError('Ad gerekli');
      ok = false;
    } else {
      setNameError(null);
    }
    if (form.cancellationPolicy === 'FREE_CANCELLATION') {
      const n = form.freeCancellationDaysBefore;
      if (n == null || isNaN(n) || n < 0 || n > 365 || !Number.isInteger(n)) {
        setDaysError('0-365 tam sayi gerekli');
        ok = false;
      } else {
        setDaysError(null);
      }
    } else {
      setDaysError(null);
    }
    return ok;
  }, [form.code, form.name, form.cancellationPolicy, form.freeCancellationDaysBefore]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      if (isEdit && initial) {
        await partnerApi.updateRatePlan(hotelId, initial.id, {
          name: form.name.trim(),
          // PATCH ignores null; an empty string clears the description.
          description: form.description.trim(),
          mealPlan: form.mealPlan,
          cancellationPolicy: form.cancellationPolicy,
          freeCancellationDaysBefore: form.cancellationPolicy === 'NON_REFUNDABLE' ? null : form.freeCancellationDaysBefore,
          expectedVersion: initial.version,
        });
        toast.success('Fiyat plani guncellendi');
      } else {
        const createBody = {
          code: form.code.trim().toUpperCase(),
          name: form.name.trim(),
          description: form.description.trim() || null,
          mealPlan: form.mealPlan,
          cancellationPolicy: form.cancellationPolicy,
          freeCancellationDaysBefore: form.cancellationPolicy === 'NON_REFUNDABLE' ? null : form.freeCancellationDaysBefore,
        };
        await partnerApi.createRatePlan(hotelId, createBody);
        toast.success('Fiyat plani eklendi');
      }
      onDone(true);
    } catch (err) {
      toast.error(partnerErrorMessage(err, 'Islem basarisiz'));
      // On a version conflict the form holds stale data: close it and show the fresh list.
      if (isEdit && isVersionConflict(err)) onDone(true);
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  };

  const codeValid = /^[A-Z0-9_]{2,30}$/.test(form.code.trim());

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Kod" error={codeError}>
          <input
            className={inputClass}
            value={form.code}
            onChange={set('code')}
            autoFocus={!isEdit}
            placeholder="KAHVALTI_ESNEK"
            disabled={isEdit}
            maxLength={30}
            readOnly={isEdit}
          />
        </Field>
        <Field label="Ad" error={nameError}>
          <input className={inputClass} value={form.name} onChange={set('name')} maxLength={120} />
        </Field>
        <Field label="Pansiyon tipi">
          <select className={inputClass} value={form.mealPlan} onChange={set('mealPlan')}>
            {(Object.keys(MEAL_PLAN_LABEL) as MealPlan[]).map(k => (
              <option key={k} value={k}>{MEAL_PLAN_LABEL[k]}</option>
            ))}
          </select>
        </Field>
        <Field label="Iptal kosulu">
          <select className={inputClass} value={form.cancellationPolicy} onChange={set('cancellationPolicy')}>
            {(Object.keys(CANCELLATION_POLICY_LABEL) as CancellationPolicy[]).map(k => (
              <option key={k} value={k}>{CANCELLATION_POLICY_LABEL[k]}</option>
            ))}
          </select>
        </Field>
        {form.cancellationPolicy === 'FREE_CANCELLATION' && (
          <Field label="Ucretsiz iptal gun oncesinde" error={daysError}>
            <input
              type="number"
              className={inputClass}
              value={form.freeCancellationDaysBefore ?? ''}
              onChange={set('freeCancellationDaysBefore')}
              min={0}
              max={365}
              step={1}
            />
          </Field>
        )}
        <div className="sm:col-span-2">
          <Field label="Aciklama" hint="Istege bagli">
            <textarea className={inputClass} rows={3} value={form.description} onChange={set('description')} />
          </Field>
        </div>
        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="check" disabled={saving || !codeValid || !form.name.trim()}>
            {saving ? 'Kaydediliyor' : isEdit ? 'Guncelle' : 'Olustur'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

export default function RatePlansPage() {
  const { hotelId, loading: workspaceLoading } = usePartner();
  const [plans, setPlans] = useState<RatePlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<RatePlan | null>(null);

  const load = useCallback(async (id: string) => {
    try {
      const res = await partnerApi.ratePlans(id, true);
      setPlans(res.data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Yuklenemedi');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!hotelId) return;
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(hotelId); });
    return () => { cancelled = true; };
  }, [hotelId, load]);

  const toast = useToast();
  const toggling = useRef(false);

  const toggleStatus = async (plan: RatePlan) => {
    if (toggling.current || !hotelId) return;
    toggling.current = true;
    const newStatus = plan.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await partnerApi.updateRatePlan(hotelId, plan.id, {
        status: newStatus,
        expectedVersion: plan.version,
      });
      toast.success(newStatus === 'ACTIVE' ? 'Fiyat plani aktif' : 'Fiyat plani pasif');
    } catch (err) {
      toast.error(partnerErrorMessage(err, 'Islem basarisiz'));
    } finally {
      toggling.current = false;
      await load(hotelId);
    }
  };

  const cancellationText = (plan: RatePlan): string => {
    if (plan.cancellationPolicy === 'NON_REFUNDABLE') {
      return 'Iade edilemez';
    }
    const x = plan.freeCancellationDaysBefore ?? 0;
    if (x === 0) {
      return 'Giris gunune kadar ucretsiz iptal';
    }
    return `Girise ${x} gun kalaya kadar ucretsiz iptal`;
  };

  if (workspaceLoading) return <LoadingRows />;
  if (!hotelId) return <NeedsHotel />;
  if (loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Fiyat planlari"
        subtitle="Pansiyon tipi ve iptal kosullari"
        actions={!showForm && !editing && (
          <Button icon="add" onClick={() => setShowForm(true)}>Yeni fiyat plani</Button>
        )}
      />
      {showForm && (
        <RatePlanForm hotelId={hotelId} onDone={saved => { setShowForm(false); if (saved) void load(hotelId); }} />
      )}
      {editing && (
        <RatePlanForm key={editing.id} hotelId={hotelId} initial={editing}
          onDone={saved => { setEditing(null); if (saved) void load(hotelId); }} />
      )}
      {plans.length === 0 ? (
        !showForm && !editing && (
          <EmptyState
            icon="sell"
            title="Bu otelde fiyat plani yok"
            description="Ornek: Kahvalti dahil, ucretsiz iptal."
            action={<Button icon="add" onClick={() => setShowForm(true)}>Fiyat plani ekle</Button>}
          />
        )
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {plans.map(plan => (
            <Card key={plan.id} className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-on-surface truncate">{plan.name}</p>
                  <p className="text-xs text-outline font-mono mt-0.5">{plan.code}</p>
                </div>
                <StatusPill tone={plan.status === 'ACTIVE' ? 'success' : 'neutral'}>
                  {plan.status === 'ACTIVE' ? 'Aktif' : 'Pasif'}
                </StatusPill>
              </div>
              <div className="mt-2 flex flex-wrap gap-1.5">
                <StatusPill tone="neutral">{MEAL_PLAN_LABEL[plan.mealPlan]}</StatusPill>
              </div>
              <p className="mt-2 text-xs text-on-surface-variant">{cancellationText(plan)}</p>
              {plan.description && <p className="mt-1 text-xs text-on-surface-variant">{plan.description}</p>}
              <div className="mt-3 flex gap-1.5">
                <Button variant="ghost" onClick={() => { setShowForm(false); setEditing(plan); }}>Duzenle</Button>
                <Button
                  variant="ghost"
                  onClick={() => toggleStatus(plan)}
                >
                  {plan.status === 'ACTIVE' ? 'Pasif yap' : 'Aktif yap'}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
