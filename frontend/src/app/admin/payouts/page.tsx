'use client';

import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { useSingleFlight } from '@/lib/use-single-flight';
import { ApiError } from '@/lib/api';
import {
  adminApi, payoutActionsFor, type AdminBankAccount, type AdminPayout, type AdminPayoutAction, type AdminPayoutBalance,
} from '@/lib/admin';
import {
  BANK_STATUS_LABEL, BANK_STATUS_TONE, PAYOUT_STATUS_LABEL, PAYOUT_STATUS_TONE, formatIbanGroups,
  isPayoutVersionConflict, paidAtProblem, parseAmountInput, payoutErrorMessage, toAmount,
} from '@/lib/partner';
import { formatMoney } from '@/lib/partner-car';
import { formatDate } from '@/lib/utils';
import { Button, Card, EmptyState, Field, inputClass, LoadingRows, PageHeader, StatusPill } from '@/components/partner/ui';

type Tab = 'REQUESTED' | 'APPROVED' | 'PROCESSING' | 'HISTORY' | 'BANKS';
const TABS: Array<{ key: Tab; label: string; status?: string }> = [
  { key: 'REQUESTED', label: 'Talep', status: 'REQUESTED' },
  { key: 'APPROVED', label: 'Onayli', status: 'APPROVED' },
  { key: 'PROCESSING', label: 'Isleniyor', status: 'PROCESSING' },
  { key: 'HISTORY', label: 'Gecmis', status: 'PAID,FAILED,CANCELLED' },
  { key: 'BANKS', label: 'Banka hesaplari' },
];

type Dialog =
  | { kind: 'paid' | 'failed' | 'cancel'; payout: AdminPayout }
  | { kind: 'adjust'; orgId: string }
  | { kind: 'reject-bank'; account: AdminBankAccount };

export default function AdminPayoutsPage() {
  const toast = useToast();
  const flight = useSingleFlight();
  const [tab, setTab] = useState<Tab>('REQUESTED');
  const [payouts, setPayouts] = useState<AdminPayout[]>([]);
  const [banks, setBanks] = useState<AdminBankAccount[]>([]);
  const [orgNames, setOrgNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [balance, setBalance] = useState<{ orgId: string; data: AdminPayoutBalance } | null>(null);

  const load = useCallback(async (t: Tab) => {
    setLoading(true);
    try {
      if (t === 'BANKS') {
        const res = (await adminApi.bankAccounts('PENDING_VERIFICATION')).data;
        setBanks(Array.isArray(res) ? res : res.items ?? []);
      } else {
        const status = TABS.find(x => x.key === t)?.status;
        setPayouts((await adminApi.payouts({ status, size: 50 })).data.items ?? []);
      }
      setError(null);
    } catch (e) {
      setError(payoutErrorMessage(e, 'Odemeler yuklenemedi'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(tab); });
    return () => { cancelled = true; };
  }, [tab, load]);

  // Organization names are best effort (existing admin API lists by status). Fallback is the id.
  useEffect(() => {
    let cancelled = false;
    void Promise.allSettled((['ACTIVE', 'SUSPENDED', 'PENDING'] as const).map(s => adminApi.organizations(s))).then(rs => {
      if (cancelled) return;
      const map: Record<string, string> = {};
      for (const r of rs) if (r.status === 'fulfilled') for (const o of r.value.data ?? []) map[o.id] = o.name;
      setOrgNames(map);
    });
    return () => { cancelled = true; };
  }, []);

  function handleError(e: unknown, fallback: string) {
    if (isPayoutVersionConflict(e)) setConflict(true);
    const msg = payoutErrorMessage(e, fallback);
    setError(msg);
    toast.error(msg);
  }

  async function act(p: AdminPayout, action: AdminPayoutAction, body: { reason?: string; reference?: string; paidAt?: string } = {}) {
    await flight(`payout-${action}:${p.id}`, async () => {
      setBusy(p.id);
      try {
        await adminApi.payoutAction(p.id, action, { ...body, expectedVersion: p.version });
        setConflict(false);
        setError(null);
        toast.success('Islem tamamlandi');
        setDialog(null);
        await load(tab);
      } catch (e) {
        handleError(e, 'Islem yapilamadi');
        throw e;
      } finally { setBusy(null); }
    });
  }

  async function bankAction(a: AdminBankAccount, reject?: string) {
    await flight(`bank-${reject !== undefined ? 'reject' : 'verify'}:${a.id}`, async () => {
      setBusy(a.id);
      try {
        if (reject !== undefined) await adminApi.rejectBankAccount(a.id, reject); else await adminApi.verifyBankAccount(a.id);
        toast.success('Islem tamamlandi');
        setDialog(null);
        await load('BANKS');
      } catch (e) { handleError(e, 'Islem yapilamadi'); throw e; } finally { setBusy(null); }
    });
  }

  async function showBalance(orgId: string) {
    try { setBalance({ orgId, data: (await adminApi.payoutBalance(orgId)).data }); } catch (e) { handleError(e, 'Bakiye yuklenemedi'); }
  }

  function runPayoutAction(p: AdminPayout, action: AdminPayoutAction) {
    if (action === 'approve' || action === 'processing') { void act(p, action).catch(() => undefined); return; }
    setDialog({ kind: action, payout: p });
  }

  const orgName = (id: string) => orgNames[id] ?? id;

  return (
    <div className="space-y-space-md">
      <PageHeader title="Partner Odemeleri" subtitle="Odeme banka havalesiyle manuel yapilir. Sistem para gondermez, yalnizca kaydi tutar." />

      <div className="flex flex-wrap gap-2">
        {TABS.map(t => (
          <button key={t.key} onClick={() => { setTab(t.key); setBalance(null); }}
            className={`rounded-full px-3 py-1.5 text-sm font-medium ${tab === t.key ? 'bg-primary text-on-primary' : 'bg-surface-container text-on-surface-variant'}`}>{t.label}</button>
        ))}
      </div>

      {conflict && (
        <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">
          Kayit baska biri tarafindan degistirildi. <button className="underline" onClick={() => { setConflict(false); void load(tab); }}>Listeyi yenile</button> ve tekrar deneyin.
        </p>
      )}
      {error && !conflict && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}

      {loading ? <LoadingRows /> : tab === 'BANKS' ? (
        banks.length === 0 ? <EmptyState icon="account_balance" title="Onay bekleyen hesap yok" /> : (
          <div className="space-y-2">
            {banks.map(a => (
              <Card key={a.id} className="flex flex-wrap items-center justify-between gap-2 p-4 text-sm">
                <div>
                  <p className="font-semibold text-on-surface">{orgName(a.organizationId)}</p>
                  <BankIban account={a} />
                  <p className="text-xs text-on-surface-variant">{a.accountHolderName} · {formatDate(a.updatedAt)}</p>
                </div>
                <div className="flex items-center gap-2">
                  <StatusPill tone={BANK_STATUS_TONE[a.status]}>{BANK_STATUS_LABEL[a.status]}</StatusPill>
                  <Button disabled={busy === a.id} onClick={() => { void bankAction(a).catch(() => undefined); }}>Onayla</Button>
                  <Button variant="danger" onClick={() => setDialog({ kind: 'reject-bank', account: a })}>Reddet</Button>
                </div>
              </Card>
            ))}
          </div>
        )
      ) : payouts.length === 0 ? <EmptyState icon="payments" title="Kayit yok" /> : (
        <div className="space-y-2">
          {payouts.map(p => (
            <Card key={p.id} className="p-4 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-semibold text-on-surface">{orgName(p.organizationId)}</p>
                  <p className="text-lg font-semibold">{formatMoney(toAmount(p.amount), p.currency)}</p>
                  <p className="text-xs text-on-surface-variant">{formatDate(p.requestedAt)} · <span className="font-mono">{p.maskedIban}</span> · {p.accountHolderName}</p>
                  {p.bankAccountStatus && <p className="text-xs text-on-surface-variant">Hesap: {BANK_STATUS_LABEL[p.bankAccountStatus]}</p>}
                  {p.status === 'PAID' && p.paymentReference && <p className="text-xs text-on-surface-variant">Referans: {p.paymentReference}</p>}
                  {p.failureReason && <p className="text-xs text-error">Neden: {p.failureReason}</p>}
                  {p.cancelReason && <p className="text-xs text-on-surface-variant">Iptal nedeni: {p.cancelReason}</p>}
                </div>
                <StatusPill tone={PAYOUT_STATUS_TONE[p.status]}>{PAYOUT_STATUS_LABEL[p.status]}</StatusPill>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {payoutActionsFor(p.status).actions.map(spec => (
                  <Button key={spec.action} variant={spec.variant === 'primary' ? undefined : spec.variant} disabled={busy === p.id}
                    onClick={() => runPayoutAction(p, spec.action)}>{spec.label}</Button>
                ))}
                <Button variant="ghost" onClick={() => { void showBalance(p.organizationId); }}>Bakiye</Button>
              </div>
              {payoutActionsFor(p.status).hint && <p className="mt-2 text-xs text-on-surface-variant">{payoutActionsFor(p.status).hint}</p>}
            </Card>
          ))}
        </div>
      )}

      {balance && (
        <Card className="p-4 text-sm">
          <div className="mb-2 flex items-center justify-between">
            <p className="font-semibold">{orgName(balance.orgId)} bakiye</p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setDialog({ kind: 'adjust', orgId: balance.orgId })}>Duzeltme</Button>
              <Button variant="ghost" onClick={() => setBalance(null)}>Kapat</Button>
            </div>
          </div>
          <p className={`mb-2 text-xs ${balance.data.reconciles ? 'text-on-surface-variant' : 'text-error'}`}>
            {balance.data.reconciles ? 'Mutabakat tamam.' : 'Mutabakat tutmuyor, kayitlari kontrol edin.'}
          </p>
          {balance.data.balances.map(b => (
            <div key={b.currency} className="mb-1 grid gap-1 sm:grid-cols-5">
              <span>{b.currency}</span>
              <span>Cekilebilir: {formatMoney(toAmount(b.payable), b.currency)}</span>
              <span>Surecte: {formatMoney(toAmount(b.reserved), b.currency)}</span>
              <span>Odenen: {formatMoney(toAmount(b.paidTotal), b.currency)}</span>
              <span>Borc: {formatMoney(toAmount(b.owed), b.currency)}</span>
            </div>
          ))}
          <p className="text-xs text-on-surface-variant">Duzeltme toplami: {toAmount(balance.data.adjustmentsNet).toFixed(2)}</p>
        </Card>
      )}

      {dialog && (dialog.kind === 'paid' || dialog.kind === 'failed' || dialog.kind === 'cancel') && (
        <PayoutDialog kind={dialog.kind} payout={dialog.payout} busy={busy === dialog.payout.id}
          onClose={() => setDialog(null)}
          onSubmit={(body) => act(dialog.payout, dialog.kind === 'failed' ? 'failed' : dialog.kind === 'paid' ? 'paid' : 'cancel', body)} />
      )}
      {dialog?.kind === 'reject-bank' && (
        <ReasonModal title="Banka hesabini reddet" label="Neden" onClose={() => setDialog(null)} busy={busy === dialog.account.id}
          onSubmit={reason => bankAction(dialog.account, reason)} />
      )}
      {dialog?.kind === 'adjust' && (
        <AdjustModal orgId={dialog.orgId} onClose={() => setDialog(null)}
          onDone={async () => { toast.success('Duzeltme kaydedildi'); setDialog(null); await showBalance(dialog.orgId); }}
          onError={e => handleError(e, 'Duzeltme kaydedilemedi')} />
      )}
    </div>
  );
}

// The revealed IBAN lives only in this component's state and is gone when it unmounts.
function BankIban({ account }: { account: AdminBankAccount }) {
  const [iban, setIban] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  async function reveal() {
    setErr(null);
    try { setIban((await adminApi.bankAccountIban(account.id)).data.iban); } catch (e) { setErr(payoutErrorMessage(e, 'IBAN alinamadi')); }
  }
  return (
    <>
      <p className="font-mono text-xs text-on-surface-variant">{iban ? formatIbanGroups(iban) : account.maskedIban}</p>
      {!iban && <Button variant="ghost" icon="visibility" onClick={() => { void reveal(); }}>IBAN&apos;i goster</Button>}
      {err && <p className="text-xs text-error">{err}</p>}
    </>
  );
}

function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
      <div className="w-full max-w-md space-y-3 rounded-xl bg-surface-container-lowest p-5 shadow-lg">
        <div className="flex items-center justify-between"><h3 className="font-semibold text-on-surface">{title}</h3>
          <button aria-label="Kapat" onClick={onClose} className="material-symbols-outlined text-outline">close</button></div>
        {children}
      </div>
    </div>
  );
}

function ReasonModal({ title, label, onClose, onSubmit, busy }: { title: string; label: string; onClose: () => void; onSubmit: (reason: string) => Promise<void>; busy: boolean }) {
  const [reason, setReason] = useState('');
  const valid = reason.trim().length >= 5 && reason.trim().length <= 500;
  return (
    <Modal title={title} onClose={onClose}>
      <Field label={label} hint="5 ile 500 karakter. Hesap bilgisi yazmayin."><textarea className={inputClass} rows={3} value={reason} onChange={e => setReason(e.target.value)} /></Field>
      <Button disabled={!valid || busy} onClick={() => { void onSubmit(reason.trim()).catch(() => undefined); }}>Onayla</Button>
    </Modal>
  );
}

function PayoutDialog({ kind, payout, busy, onClose, onSubmit }: {
  kind: 'paid' | 'failed' | 'cancel'; payout: AdminPayout; busy: boolean; onClose: () => void;
  onSubmit: (body: { reason?: string; reference?: string; paidAt?: string }) => Promise<void>;
}) {
  const [reference, setReference] = useState('');
  const [paidAt, setPaidAt] = useState('');
  const [iban, setIban] = useState<{ iban: string; holder: string } | null>(null);
  const [revealError, setRevealError] = useState<string | null>(null);

  // The revealed IBAN lives only in this modal's state and disappears when the modal unmounts.
  async function reveal() {
    setRevealError(null);
    try {
      const d = (await adminApi.payoutBankDetails(payout.id)).data;
      setIban({ iban: d.iban, holder: d.accountHolderName });
    } catch (e) { setRevealError(payoutErrorMessage(e, 'IBAN alinamadi')); }
  }

  if (kind !== 'paid') {
    return <ReasonModal title={kind === 'failed' ? 'Odeme basarisiz' : 'Talebi iptal et'} label="Neden" busy={busy} onClose={onClose}
      onSubmit={reason => onSubmit({ reason })} />;
  }
  const refOk = reference.trim().length >= 3 && reference.trim().length <= 100;
  const paidIso = paidAt ? new Date(paidAt) : null;
  const dateOk = !paidIso || !Number.isNaN(paidIso.getTime());
  const dateProblem = paidAtProblem(paidIso, payout.requestedAt);
  return (
    <Modal title={`Odendi: ${formatMoney(toAmount(payout.amount), payout.currency)}`} onClose={onClose}>
      <p className="text-xs text-on-surface-variant">Havaleyi banka uzerinden yaptiktan sonra referansi girin.</p>
      {iban ? (
        <div className="rounded-lg bg-surface-container p-3 text-sm">
          <p className="font-mono">{formatIbanGroups(iban.iban)}</p>
          <p className="text-xs text-on-surface-variant">{iban.holder}</p>
        </div>
      ) : (
        <Button variant="secondary" icon="visibility" onClick={() => { void reveal(); }}>IBAN&apos;i goster</Button>
      )}
      {revealError && <p className="text-xs text-error">{revealError}</p>}
      <Field label="Banka referansi" hint="3 ile 100 karakter"><input className={inputClass} value={reference} onChange={e => setReference(e.target.value)} /></Field>
      <Field label="Odeme tarihi (istege bagli, talep ile simdi arasinda)" error={dateOk ? null : 'Gecersiz tarih'}>
        <input type="datetime-local" className={inputClass} value={paidAt} onChange={e => setPaidAt(e.target.value)} /></Field>
      <Button disabled={!refOk || !dateOk || busy}
        onClick={() => { if (dateProblem) { setRevealError(dateProblem); return; } void onSubmit({ reference: reference.trim(), ...(paidIso ? { paidAt: paidIso.toISOString() } : {}) }).catch(() => undefined); }}>Odendi olarak isaretle</Button>
    </Modal>
  );
}

function AdjustModal({ orgId, onClose, onDone, onError }: { orgId: string; onClose: () => void; onDone: () => Promise<void>; onError: (e: unknown) => void }) {
  const [direction, setDirection] = useState<'DEBIT' | 'CREDIT'>('DEBIT');
  const [currency, setCurrency] = useState('TRY');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [key] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const value = parseAmountInput(amount);
  const valid = value != null && value > 0 && reason.trim().length >= 5 && /^[A-Z]{3}$/.test(currency);
  async function submit() {
    if (value == null) return;
    setBusy(true);
    try {
      await adminApi.payoutAdjustment(orgId, { currency, amount: value, direction, reason: reason.trim(), idempotencyKey: key });
      await onDone();
    } catch (e) { onError(e instanceof ApiError || e instanceof Error ? e : new Error('Duzeltme kaydedilemedi')); } finally { setBusy(false); }
  }
  return (
    <Modal title="Bakiye duzeltmesi" onClose={onClose}>
      <Field label="Yon"><select className={inputClass} value={direction} onChange={e => setDirection(e.target.value as 'DEBIT' | 'CREDIT')}>
        <option value="DEBIT">Borclandir (DEBIT)</option><option value="CREDIT">Alacaklandir (CREDIT)</option></select></Field>
      <Field label="Para birimi"><input className={inputClass} value={currency} onChange={e => setCurrency(e.target.value.toUpperCase())} /></Field>
      <Field label="Tutar"><input className={inputClass} inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} /></Field>
      <Field label="Neden" hint="En az 5 karakter"><textarea className={inputClass} rows={2} value={reason} onChange={e => setReason(e.target.value)} /></Field>
      <Button disabled={!valid || busy} onClick={() => { void submit(); }}>Kaydet</Button>
    </Modal>
  );
}
