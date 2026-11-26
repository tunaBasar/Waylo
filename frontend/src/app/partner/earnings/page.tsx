'use client';

import { useCallback, useEffect, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  BANK_STATUS_LABEL, BANK_STATUS_TONE, ESCROW_STATE_LABEL, PAYOUT_BLOCKED_LABEL, PAYOUT_STATUS_LABEL, PAYOUT_STATUS_TONE,
  isValidTrIban, parseAmountInput, partnerApi, partnerErrorMessage, payoutErrorMessage, toAmount, validatePayoutAmount,
  type PartnerBankAccount, type PartnerEarnings, type PartnerPayout, type PartnerPayoutBalance, type PayoutCurrencyBalance,
} from '@/lib/partner';
import { ApiError } from '@/lib/api';
import { formatMoney } from '@/lib/partner-car';
import { formatDate } from '@/lib/utils';
import { Button, Card, EmptyState, Field, inputClass, LoadingRows, PageHeader, StatusPill } from '@/components/partner/ui';

const VERTICAL_LABEL: Record<string, string> = { HOTEL: 'Otel', CAR: 'Arac', FLIGHT: 'Ucus', BUS: 'Otobus' };

function money(value: number | string | null | undefined, currency: string | null | undefined): string {
  if (value == null) return '';
  return currency ? formatMoney(toAmount(value), currency) : toAmount(value).toFixed(2);
}

interface PayoutState {
  balance: PartnerPayoutBalance | null;
  payouts: PartnerPayout[];
  bank: PartnerBankAccount | null;
  error: string | null;
}

export default function PartnerEarningsPage() {
  const { organizationId, membership, loading: workspaceLoading } = usePartner();
  const isOwner = !!membership?.roles.some(r => r.role === 'ORGANIZATION_OWNER');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const key = `${organizationId}|${from}|${to}`;
  const [result, setResult] = useState<{ key: string; data: PartnerEarnings | null; error: string | null } | null>(null);
  const [payout, setPayout] = useState<{ org: string; state: PayoutState } | null>(null);
  const [notice, setNotice] = useState<{ tone: 'ok' | 'err'; text: string } | null>(null);

  useEffect(() => {
    if (!organizationId) return;
    let alive = true;
    partnerApi.earnings(organizationId, { from, to })
      .then(res => { if (alive) setResult({ key, data: res.data, error: null }); })
      .catch(e => { if (alive) setResult({ key, data: null, error: partnerErrorMessage(e, 'Kazanclar yuklenemedi') }); });
    return () => { alive = false; };
  }, [organizationId, from, to, key]);

  const loadPayouts = useCallback(async (org: string): Promise<PayoutState> => {
    const [bal, list, bank] = await Promise.allSettled([
      partnerApi.payoutBalance(org),
      partnerApi.payouts(org, { page: 0, size: 20 }),
      partnerApi.bankAccount(org),
    ]);
    const bankMissing = bank.status === 'rejected' && bank.reason instanceof ApiError && bank.reason.status === 404;
    const firstError = [bal, list, bank].find(r => r.status === 'rejected' && !(r === bank && bankMissing)) as PromiseRejectedResult | undefined;
    return {
      balance: bal.status === 'fulfilled' ? bal.value.data : null,
      payouts: list.status === 'fulfilled' ? list.value.data.items : [],
      bank: bank.status === 'fulfilled' ? bank.value.data : null,
      error: firstError ? payoutErrorMessage(firstError.reason, 'Odeme bilgileri yuklenemedi') : null,
    };
  }, []);

  const refreshPayouts = useCallback(async (org: string) => {
    const state = await loadPayouts(org);
    setPayout({ org, state });
  }, [loadPayouts]);

  useEffect(() => {
    if (!organizationId) return;
    let alive = true;
    void loadPayouts(organizationId).then(state => { if (alive) setPayout({ org: organizationId, state }); });
    return () => { alive = false; };
  }, [organizationId, loadPayouts]);

  const loading = result?.key !== key;
  const data = result?.key === key ? result.data : null;
  const error = result?.key === key ? result.error : null;
  const ps = payout && payout.org === organizationId ? payout.state : null;

  if (workspaceLoading) return <LoadingRows />;

  const rowCurrency = (r: { currency?: string | null }) => r.currency ?? data?.currency ?? (data?.totalsByCurrency.length === 1 ? data.totalsByCurrency[0].currency : null);

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Kazanclar" subtitle="Komisyon sonrasi net kazanciniz" />

      {notice && (
        <p className={`rounded-lg px-3 py-2 text-sm ${notice.tone === 'ok' ? 'bg-secondary-container text-on-secondary-container' : 'bg-error-container text-on-error-container'}`}>{notice.text}</p>
      )}

      <p className="text-xs text-on-surface-variant">Odemeler platform yoneticisi tarafindan onaylanir. Ödeme banka havalesiyle manuel yapilir, sistem uzerinden otomatik para hareketi olmaz.</p>

      {!ps ? <LoadingRows /> : (
        <>
          {ps.error && <EmptyState icon="error" title="Odeme bilgileri yuklenemedi" description={ps.error} />}
          {ps.balance && ps.balance.balances.length > 0 && (
            <div className="space-y-3">
              {ps.balance.balances.map(b => (
                <BalanceBlock key={b.currency} b={b} orgId={organizationId!} owner={isOwner}
                  onDone={async msg => { setNotice({ tone: 'ok', text: msg }); await refreshPayouts(organizationId!); }}
                  onError={text => setNotice({ tone: 'err', text })} />
              ))}
              {ps.balance.holdbackDays > 0 && (
                <p className="text-xs text-on-surface-variant">Serbest birakilan tutarlar {ps.balance.holdbackDays} gun sonra cekilebilir olur.</p>
              )}
            </div>
          )}

          <BankSection bank={ps.bank} orgId={organizationId!} owner={isOwner}
            onDone={async msg => { setNotice({ tone: 'ok', text: msg }); await refreshPayouts(organizationId!); }}
            onError={text => setNotice({ tone: 'err', text })} />

          <Card className="p-4">
            <h3 className="mb-3 font-semibold text-on-surface">Odeme gecmisi</h3>
            {ps.payouts.length === 0 ? <p className="text-sm text-on-surface-variant">Henuz odeme talebi yok.</p> : (
              <div className="space-y-2">
                {ps.payouts.map(p => (
                  <PayoutRow key={p.id} p={p} owner={isOwner} orgId={organizationId!}
                    onDone={async msg => { setNotice({ tone: 'ok', text: msg }); await refreshPayouts(organizationId!); }}
                    onError={text => setNotice({ tone: 'err', text })} />
                ))}
              </div>
            )}
          </Card>
        </>
      )}

      <Card className="p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Baslangic"><input type="date" className={inputClass} value={from} onChange={e => setFrom(e.target.value)} /></Field>
          <Field label="Bitis"><input type="date" className={inputClass} value={to} onChange={e => setTo(e.target.value)} /></Field>
        </div>
      </Card>

      {loading ? <LoadingRows /> : error ? (
        <EmptyState icon="error" title="Yuklenemedi" description={error} />
      ) : !data ? null : (
        <>
          {data.totalsByCurrency.map(t => (
            <div key={t.currency} className="space-y-1">
              <p className="text-xs font-medium text-on-surface-variant">{t.currency}</p>
              <div className="grid gap-3 sm:grid-cols-3">
                <Card className="p-4"><p className="text-xs text-on-surface-variant">Bekleyen (tahmini)</p>
                  <p className="text-xl font-semibold">{money(t.held, t.currency)}</p></Card>
                <Card className="p-4"><p className="text-xs text-on-surface-variant">Serbest birakilan</p>
                  <p className="text-xl font-semibold">{money(t.released, t.currency)}</p></Card>
                <Card className="p-4"><p className="text-xs text-on-surface-variant">Iade edilen</p>
                  <p className="text-xl font-semibold">{money(t.refunded, t.currency)}</p></Card>
              </div>
            </div>
          ))}

          {data.items.length === 0 ? (
            <EmptyState icon="payments" title="Kazanc kaydi yok" description="Secili tarih araliginda odenmis siparis yok." />
          ) : (
            <div className="space-y-3">
              {data.items.map(r => (
                <Card key={r.orderItemId} className="p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="font-semibold text-on-surface">{VERTICAL_LABEL[r.vertical] ?? r.vertical}</p>
                      <p className="text-xs text-on-surface-variant">
                        {r.heldAt ? formatDate(r.heldAt) : ''}{r.releasedAt ? ` · Serbest: ${formatDate(r.releasedAt)}` : ''}
                      </p>
                    </div>
                    <StatusPill tone={r.escrowState === 'RELEASED' ? 'success' : r.escrowState === 'HELD' ? 'warning' : 'neutral'}>
                      {ESCROW_STATE_LABEL[r.escrowState]}
                    </StatusPill>
                  </div>
                  <div className="mt-2 grid gap-1 text-sm">
                    <div className="flex justify-between"><span className="text-outline">Brut</span><span>{money(r.gross, rowCurrency(r))}</span></div>
                    <div className="flex justify-between"><span className="text-outline">Komisyon</span><span>{money(r.commission, rowCurrency(r))}</span></div>
                    <div className="flex justify-between font-semibold"><span>Net</span><span>{money(r.partnerNet, rowCurrency(r))}</span></div>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

interface Callbacks { orgId: string; owner: boolean; onDone: (msg: string) => Promise<void>; onError: (msg: string) => void }

function BalanceBlock({ b, orgId, owner, onDone, onError }: { b: PayoutCurrencyBalance } & Callbacks) {
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState('');
  const [idemKey, setIdemKey] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const cur = b.currency;

  function openForm() {
    setIdemKey(crypto.randomUUID());
    setAmount(toAmount(b.payable).toFixed(2));
    setFieldError(null);
    setOpen(true);
  }

  async function submit() {
    const err = validatePayoutAmount(amount, b.payable, b.minimumPayout);
    if (err) { setFieldError(err); return; }
    setBusy(true);
    try {
      await partnerApi.requestPayout(orgId, { currency: cur, amount: parseAmountInput(amount) ?? 0 }, idemKey);
      setOpen(false);
      await onDone('Odeme talebiniz alindi.');
    } catch (e) {
      setFieldError(payoutErrorMessage(e, 'Odeme talebi olusturulamadi'));
      onError(payoutErrorMessage(e, 'Odeme talebi olusturulamadi'));
    } finally { setBusy(false); }
  }

  return (
    <Card className="p-4">
      <p className="mb-2 text-xs font-medium text-on-surface-variant">{cur}</p>
      <div className="grid gap-3 sm:grid-cols-4" data-testid={`payout-balance-${cur}`}>
        <div><p className="text-xs text-on-surface-variant">Cekilebilir bakiye</p><p className="text-xl font-semibold" data-testid="payout-payable">{money(b.payable, cur)}</p></div>
        <div><p className="text-xs text-on-surface-variant">Beklemede (tahmini)</p><p className="text-xl font-semibold" data-testid="payout-held">{money(b.held, cur)}</p></div>
        <div><p className="text-xs text-on-surface-variant">Odeme surecinde</p><p className="text-xl font-semibold" data-testid="payout-reserved">{money(b.reserved, cur)}</p></div>
        <div><p className="text-xs text-on-surface-variant">Odenen toplam</p><p className="text-xl font-semibold" data-testid="payout-paid-total">{money(b.paidTotal, cur)}</p></div>
      </div>
      {toAmount(b.releasedNotYetPayable) > 0 && (
        <p className="mt-2 text-xs text-on-surface-variant">Bekleme suresinde: {money(b.releasedNotYetPayable, cur)}</p>
      )}
      {toAmount(b.owed) > 0 && (
        <p className="mt-2 rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">Platforma borcunuz: {money(b.owed, cur)}. Yeni kazanclardan mahsup edilir.</p>
      )}
      <div className="mt-3">
        {b.blockedReason === 'NOT_OWNER' || !owner ? (
          b.blockedReason && b.blockedReason !== 'NOT_OWNER' ? <p className="text-sm text-on-surface-variant">{PAYOUT_BLOCKED_LABEL[b.blockedReason]}</p> : null
        ) : open ? (
          <div className="space-y-2">
            <Field label={`Tutar (${cur})`} hint={`Asgari ${money(b.minimumPayout, cur)}`} error={fieldError}>
              <input className={inputClass} inputMode="decimal" value={amount} onChange={e => { setAmount(e.target.value); setFieldError(null); }} />
            </Field>
            <div className="flex flex-wrap gap-2">
              <Button variant="ghost" type="button" onClick={() => setAmount(toAmount(b.payable).toFixed(2))}>Tumunu cek</Button>
              <Button type="button" disabled={busy} onClick={submit}>Talep gonder</Button>
              <Button variant="ghost" type="button" onClick={() => setOpen(false)}>Vazgec</Button>
            </div>
          </div>
        ) : b.canRequest ? (
          <Button icon="payments" onClick={openForm}>Ödeme talep et</Button>
        ) : b.blockedReason ? (
          <p className="text-sm text-on-surface-variant">{PAYOUT_BLOCKED_LABEL[b.blockedReason]}</p>
        ) : null}
      </div>
    </Card>
  );
}

function PayoutRow({ p, orgId, owner, onDone, onError }: { p: PartnerPayout } & Callbacks) {
  const [busy, setBusy] = useState(false);
  async function cancel() {
    setBusy(true);
    try {
      await partnerApi.cancelPayout(orgId, p.id);
      await onDone('Odeme talebi iptal edildi.');
    } catch (e) { onError(payoutErrorMessage(e, 'Talep iptal edilemedi')); } finally { setBusy(false); }
  }
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-outline-variant/40 p-3 text-sm">
      <div>
        <p className="font-semibold text-on-surface">{money(p.amount, p.currency)}</p>
        <p className="text-xs text-on-surface-variant">{formatDate(p.requestedAt)} · {p.maskedIban}</p>
        {p.status === 'PAID' && p.paymentReference && <p className="text-xs text-on-surface-variant">Banka referansi: {p.paymentReference}</p>}
        {p.status === 'FAILED' && p.failureReason && <p className="text-xs text-error">Neden: {p.failureReason}</p>}
        {p.status === 'CANCELLED' && p.cancelReason && <p className="text-xs text-on-surface-variant">Neden: {p.cancelReason}</p>}
      </div>
      <div className="flex items-center gap-2">
        <StatusPill tone={PAYOUT_STATUS_TONE[p.status]}>{PAYOUT_STATUS_LABEL[p.status]}</StatusPill>
        {p.status === 'REQUESTED' && owner && <Button variant="ghost" disabled={busy} onClick={cancel}>Iptal</Button>}
      </div>
    </div>
  );
}

function BankSection({ bank, orgId, owner, onDone, onError }: { bank: PartnerBankAccount | null } & Callbacks) {
  const [editing, setEditing] = useState(false);
  const [holder, setHolder] = useState('');
  const [iban, setIban] = useState('');
  const [busy, setBusy] = useState(false);
  const ibanError = iban && !isValidTrIban(iban) ? 'Gecerli bir TR IBAN girin (TR + 24 hane).' : null;
  const formOpen = editing || (!bank && owner);

  async function save() {
    if (holder.trim().length < 2 || !isValidTrIban(iban)) return;
    setBusy(true);
    try {
      await partnerApi.saveBankAccount(orgId, { accountHolderName: holder.trim(), iban });
      setIban(''); setHolder(''); setEditing(false);
      await onDone('Banka hesabi kaydedildi, yonetici onayi bekleniyor.');
    } catch (e) { onError(payoutErrorMessage(e, 'Banka hesabi kaydedilemedi')); } finally { setBusy(false); }
  }

  async function remove() {
    setBusy(true);
    try {
      await partnerApi.deleteBankAccount(orgId);
      await onDone('Banka hesabi kaldirildi.');
    } catch (e) { onError(payoutErrorMessage(e, 'Banka hesabi kaldirilamadi')); } finally { setBusy(false); }
  }

  return (
    <Card className="p-4">
      <h3 className="mb-3 font-semibold text-on-surface">Banka hesabi</h3>
      {bank && bank.status !== 'DISABLED' && (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2 text-sm" data-testid="bank-account-current">
          <div>
            <p className="font-mono text-on-surface">{bank.maskedIban}</p>
            <p className="text-xs text-on-surface-variant">{bank.accountHolderName}</p>
            {bank.status === 'REJECTED' && bank.rejectReason && <p className="text-xs text-error">Neden: {bank.rejectReason}</p>}
          </div>
          <StatusPill tone={BANK_STATUS_TONE[bank.status]}>{BANK_STATUS_LABEL[bank.status]}</StatusPill>
        </div>
      )}
      {!bank && !owner && <p className="text-sm text-on-surface-variant">Banka hesabi tanimli degil. Hesabi yalnizca isletme sahibi ekleyebilir.</p>}
      {owner && !formOpen && bank && (
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setEditing(true)}>Degistir</Button>
          <Button variant="ghost" disabled={busy} onClick={remove}>Kaldir</Button>
        </div>
      )}
      {owner && formOpen && (
        <div className="space-y-2">
          <p className="text-xs text-on-surface-variant">Hesap bilgisini degistirmek yeniden onay gerektirir, onaylanana kadar odeme talep edilemez. Mevcut IBAN guvenlik icin gosterilmez.</p>
          <Field label="Hesap sahibi"><input className={inputClass} value={holder} onChange={e => setHolder(e.target.value)} /></Field>
          <Field label="IBAN" error={ibanError} hint="TR00 0000 0000 0000 0000 0000 00"><input className={inputClass} autoComplete="off" value={iban} onChange={e => setIban(e.target.value)} /></Field>
          <div className="flex gap-2">
            <Button disabled={busy || holder.trim().length < 2 || !isValidTrIban(iban)} onClick={save}>Kaydet</Button>
            {bank && <Button variant="ghost" onClick={() => { setEditing(false); setIban(''); }}>Vazgec</Button>}
          </div>
        </div>
      )}
    </Card>
  );
}
