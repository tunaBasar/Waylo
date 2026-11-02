'use client';

import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { flightErrorMessage, getSupplierAccounts, getSupplierLedger, LEDGER_TYPE_LABEL, newIdempotencyKey, topUpSupplier } from '@/lib/flight';
import type { SupplierAccount, SupplierLedgerRow } from '@/lib/flight';

const money = (v: number, currency: string) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(v);

export default function SupplierBalancePage() {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState<SupplierAccount[] | null>(null);
  const [selected, setSelected] = useState('');
  const [ledger, setLedger] = useState<SupplierLedgerRow[]>([]);
  const [supplier, setSupplier] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('EUR');
  const [reference, setReference] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const keyRef = useRef(newIdempotencyKey());

  const loadAccounts = useCallback(async () => {
    try {
      const res = await getSupplierAccounts();
      setAccounts(res.data);
      setSelected(prev => prev || res.data[0]?.supplier || '');
    } catch (cause) {
      setError(flightErrorMessage((cause as ApiError).code, 'Bakiyeler yuklenemedi.'));
    }
  }, []);

  useEffect(() => { if (user?.role === 'ADMIN') void Promise.resolve().then(loadAccounts); }, [user, loadAccounts]);

  useEffect(() => {
    if (!selected) return;
    let alive = true;
    getSupplierLedger(selected).then(res => { if (alive) setLedger(res.data.items); }).catch(() => { if (alive) setLedger([]); });
    return () => { alive = false; };
  }, [selected, accounts]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = Number(amount);
    if (!supplier.trim() || !(value > 0) || !reference.trim()) { setError('Tedarikci, tutar ve referans zorunlu.'); return; }
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const res = await topUpSupplier(supplier.trim().toUpperCase(), { amount: value, currency, reference: reference.trim(), idempotencyKey: keyRef.current });
      keyRef.current = newIdempotencyKey();
      setMessage(`Bakiye yuklendi, yeni bakiye ${money(res.data.balance, res.data.currency)}.`);
      setAmount('');
      setReference('');
      setSelected(res.data.supplier);
      await loadAccounts();
    } catch (cause) {
      setError(flightErrorMessage((cause as ApiError).code, 'Bakiye yuklenemedi.'));
    } finally {
      setBusy(false);
    }
  }

  if (user?.role !== 'ADMIN') {
    return <div className="py-12 text-center"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">lock</span><p className="text-sm text-on-surface-variant">Bu sayfa yalnizca yoneticiler icindir</p></div>;
  }

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Tedarikci Bakiyesi</h2>
      <p className="text-xs text-on-surface-variant">Bakiye yalnizca burada elle yuklenir. Bilet kesiminde tedarikci bakiyesi yetersizse bilet kesilmez ve siparis iade edilir.</p>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
      {message && <p className="rounded-lg bg-secondary-container px-3 py-2 text-sm text-on-secondary-container">{message}</p>}

      {accounts && accounts.length === 0 ? (
        <p className="rounded-xl bg-surface-container-lowest p-4 text-sm text-on-surface-variant shadow-sm">Tedarikci bakiyesi yok. Asagidan ilk yuklemeyi yapin.</p>
      ) : (
        <div className="grid gap-3 md:grid-cols-3">
          {(accounts ?? []).map(a => (
            <button key={a.supplier} onClick={() => setSelected(a.supplier)}
              className={`rounded-xl p-4 text-left shadow-sm ${selected === a.supplier ? 'bg-primary-fixed' : 'bg-surface-container-lowest'}`}>
              <p className="text-xs font-medium text-on-surface-variant">{a.supplier}</p>
              <p className="text-lg font-bold text-on-surface">{money(a.balance, a.currency)}</p>
            </button>
          ))}
        </div>
      )}

      <form onSubmit={submit} className="grid gap-3 rounded-xl bg-surface-container-lowest p-4 shadow-sm md:grid-cols-5">
        <input value={supplier} onChange={e => setSupplier(e.target.value)} placeholder="Tedarikci kodu" maxLength={30} className="rounded-lg bg-surface-container px-3 py-2 text-sm uppercase" />
        <input value={amount} onChange={e => setAmount(e.target.value)} type="number" min="0.01" step="0.01" placeholder="Tutar" className="rounded-lg bg-surface-container px-3 py-2 text-sm" />
        <input value={currency} onChange={e => setCurrency(e.target.value.toUpperCase())} maxLength={3} placeholder="EUR" className="rounded-lg bg-surface-container px-3 py-2 text-sm uppercase" />
        <input value={reference} onChange={e => setReference(e.target.value)} maxLength={200} placeholder="Referans (havale no)" className="rounded-lg bg-surface-container px-3 py-2 text-sm" />
        <button disabled={busy} className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary disabled:opacity-60">{busy ? 'Yukleniyor...' : 'Bakiye Yukle'}</button>
      </form>

      {selected && (
        <div className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
          <h3 className="mb-2 text-sm font-semibold text-on-surface">{selected} hareketleri</h3>
          {ledger.length === 0 ? <p className="text-xs text-on-surface-variant">Hareket yok</p> : (
            <table className="w-full text-xs">
              <thead><tr className="text-left text-on-surface-variant"><th className="py-1">Tarih</th><th>Tur</th><th>Referans</th><th className="text-right">Tutar</th><th className="text-right">Bakiye</th></tr></thead>
              <tbody>
                {ledger.map(r => (
                  <tr key={r.id} className="border-t border-outline-variant">
                    <td className="py-1">{new Date(r.createdAt).toLocaleString('tr-TR')}</td>
                    <td>{LEDGER_TYPE_LABEL[r.entryType] ?? r.entryType}</td>
                    <td>{r.reference ?? '-'}</td>
                    <td className="text-right">{money(r.amount, r.currency)}</td>
                    <td className="text-right">{money(r.balanceAfter, r.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
