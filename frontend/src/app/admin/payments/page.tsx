'use client';

import { useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { VERTICAL_TR, verticalsWithoutDefault } from '@/lib/commission';
import { reconciliationLabel, reconciliationPath, reconciliationTone, shortId } from '@/lib/reconciliation';

interface CommissionRate {
  id: string;
  vertical: 'HOTEL' | 'CAR' | 'FLIGHT' | 'BUS';
  supplierOrganizationId: string | null;
  rate: number;
  validFrom: string;
  createdBy: string | null;
  createdAt: string;
}

interface LedgerEntry {
  entryId: string;
  groupId: string | null;
  occurredAt: string;
  orderItemId: string | null;
  account: string;
  debitAmount: number;
  creditAmount: number;
  currency: string;
  correlationId: string | null;
  description: string | null;
}

interface LedgerResponse {
  orderId: string;
  balance: number;
  entries: LedgerEntry[];
}

const VERTICAL_LABELS: Record<string, string> = {
  HOTEL: 'Otel',
  CAR: 'Araclar',
  FLIGHT: 'Ucuslar',
  BUS: 'Otobus',
};

const ACCOUNT_LABELS: Record<string, string> = {
  ESCROW: 'Escrow',
  REVENUE: 'Gelir',
  SUPPLIER: 'Isletme',
  COMMISSION: 'Komisyon',
  REFUND: 'Iade',
};

function AdminOnly() {
  return (
    <div className="text-center py-12">
      <span className="material-symbols-outlined text-outline text-5xl mb-3 block">lock</span>
      <p className="text-on-surface-variant text-sm">Bu sayfa yalnizca yoneticiler icindir</p>
    </div>
  );
}

interface ReconciliationItem {
  id: string;
  paymentIntentId: string;
  orderId: string;
  providerPaymentId: string | null;
  intentStatus: string;
  orderStatus: string;
  status: string;
  detail: string | null;
  createdAt: string;
}

interface ReconciliationPage {
  items: ReconciliationItem[];
  page: number;
  size: number;
  total: number;
}

const RECON_PAGE_SIZE = 20;
const RECON_FILTERS: { value: string; label: string }[] = [
  { value: '', label: 'Tümü' },
  { value: 'ESCROW_REPAIRED', label: 'Escrow onarıldı' },
  { value: 'PROVIDER_CANCELLED', label: 'Ödeme iptal edildi' },
  { value: 'MANUAL_REVIEW', label: 'Manuel inceleme' },
];
const TONE_CLASS: Record<string, string> = {
  success: 'bg-secondary-container text-on-secondary-container',
  neutral: 'bg-surface-container text-on-surface-variant',
  warning: 'bg-error-container text-on-error-container',
};

function ReconciliationSection() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ReconciliationPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.get<ReconciliationPage>(reconciliationPath(status, page, RECON_PAGE_SIZE))
      .then(r => { if (!cancelled) setData(r.data); })
      .catch(() => { if (!cancelled) { setData(null); setError('Mutabakat kayıtları yüklenemedi'); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [status, page]);

  const items = data?.items ?? [];
  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <section className="mt-6">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight flex items-center gap-2">
        <span className="material-symbols-outlined text-primary">fact_check</span>
        Mutabakat
      </h2>
      <p className="mt-1 text-xs text-on-surface-variant">
        İş her dakika çalışır ve 5 dakikadan eski, escrow kaydı olmayan ödemeleri ele alır.
      </p>
      <select
        aria-label="Mutabakat durumu"
        value={status}
        onChange={e => { setLoading(true); setError(''); setStatus(e.target.value); setPage(0); }}
        className="mt-3 px-3 py-2 bg-surface-container-lowest rounded-lg text-on-surface text-sm focus:outline-none"
      >
        {RECON_FILTERS.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
      </select>

      {error && (
        <p className="mt-3 rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>
      )}

      {loading ? (
        <p className="mt-4 text-sm text-on-surface-variant">Yükleniyor...</p>
      ) : !error && items.length === 0 ? (
        <p className="mt-4 text-on-surface-variant text-sm">Kayıt yok</p>
      ) : items.length > 0 ? (
        <div className="mt-4 bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-on-surface-variant border-b border-surface-container">
                  <th className="text-left px-3 py-2 font-medium">Zaman</th>
                  <th className="text-left px-3 py-2 font-medium">Sipariş</th>
                  <th className="text-left px-3 py-2 font-medium">Ödeme → Sipariş</th>
                  <th className="text-left px-3 py-2 font-medium">Karar</th>
                  <th className="text-left px-3 py-2 font-medium">Detay</th>
                </tr>
              </thead>
              <tbody>
                {items.map(it => (
                  <tr
                    key={it.id}
                    className={`border-b border-surface-container last:border-0 ${it.status === 'MANUAL_REVIEW' ? 'bg-error-container/30' : ''}`}
                  >
                    <td className="px-3 py-2 text-on-surface-variant text-xs">{new Date(it.createdAt).toLocaleString('tr-TR')}</td>
                    <td className="px-3 py-2 font-mono text-xs text-on-surface select-all" title={it.orderId}>{shortId(it.orderId)}</td>
                    <td className="px-3 py-2 text-xs text-on-surface">{it.intentStatus} → {it.orderStatus}</td>
                    <td className="px-3 py-2">
                      <span className={`text-[10px] font-semibold px-2 py-1 rounded-full ${TONE_CLASS[reconciliationTone(it.status)]}`}>
                        {reconciliationLabel(it.status)}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-xs text-on-surface-variant">
                      {it.detail}
                      {it.status === 'MANUAL_REVIEW' && (
                        <p className="mt-1 font-semibold text-on-error-container">
                          Otomatik karar verilemedi, ödeme sağlayıcısı ve sipariş durumu elle kontrol edilmeli.
                        </p>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {data && data.total > data.size && (
        <div className="mt-3 flex items-center justify-between text-xs text-on-surface-variant">
          <button type="button" disabled={page === 0} onClick={() => { setLoading(true); setPage(p => p - 1); }} className="px-3 py-1.5 rounded-lg bg-surface-container disabled:opacity-50">Önceki</button>
          <span>{page + 1} / {totalPages}</span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => { setLoading(true); setPage(p => p + 1); }} className="px-3 py-1.5 rounded-lg bg-surface-container disabled:opacity-50">Sonraki</button>
        </div>
      )}
    </section>
  );
}

export default function PaymentsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [rates, setRates] = useState<CommissionRate[]>([]);
  const [rateLoading, setRateLoading] = useState(true);
  const [rateError, setRateError] = useState('');

  // Form state
  const [formVertical, setFormVertical] = useState('HOTEL');
  const [formOrgId, setFormOrgId] = useState('');
  const [formRate, setFormRate] = useState('');
  const [formValidFrom, setFormValidFrom] = useState('');
  const [formError, setFormError] = useState('');
  const [formSuccess, setFormSuccess] = useState('');
  const [formSaving, setFormSaving] = useState(false);
  const savingRef = useRef(false);

  // Ledger state
  const [ledgerOrderId, setLedgerOrderId] = useState('');
  const [ledgerData, setLedgerData] = useState<LedgerResponse | null>(null);
  const [ledgerLoading, setLedgerLoading] = useState(false);
  const [ledgerError, setLedgerError] = useState('');

  useEffect(() => {
    if (!isAdmin) return;
    api.get<CommissionRate[]>('/api/admin/commission-rates')
      .then(r => { setRates(r.data); setRateLoading(false); })
      .catch(() => { setRateLoading(false); setRateError('Komisyon oranlari yuklenemedi'); });
  }, [isAdmin]);

  const handleCreateRate = async (e: React.FormEvent) => {
    e.preventDefault();
    // A ref, not state: several clicks in the same render all see formSaving === false.
    if (savingRef.current) return;
    savingRef.current = true;
    setFormError('');
    setFormSuccess('');
    setFormSaving(true);
    const rateNum = parseFloat(formRate);
    if (isNaN(rateNum) || rateNum < 0 || rateNum > 100) {
      setFormError('Komisyon oran 0-100 arasinda olmalidir.');
      setFormSaving(false);
      savingRef.current = false;
      return;
    }
    try {
      const body = {
        vertical: formVertical,
        supplierOrganizationId: formOrgId || null,
        rate: parseFloat((rateNum / 100).toFixed(4)),
        // empty = server time ("now"); the client clock is not the source of truth
        validFrom: formValidFrom ? new Date(formValidFrom).toISOString() : null,
      };
      await api.post<CommissionRate>('/api/admin/commission-rates', body);
      setFormSuccess('Komisyon oran kaydedildi');
      setFormRate('');
      setFormOrgId('');
      setFormValidFrom('');
      // Refresh rates
      const resp = await api.get<CommissionRate[]>('/api/admin/commission-rates');
      setRates(resp.data);
    } catch (err: unknown) {
      if (err instanceof Error && 'status' in err) {
        const e2 = err as { status: number; message: string };
        setFormError(e2.message || 'Komisyon oran kaydedilemedi');
      } else {
        setFormError('Komisyon oran kaydedilemedi');
      }
    } finally {
      setFormSaving(false);
      savingRef.current = false;
    }
  };

  const handleLookupLedger = async () => {
    if (!ledgerOrderId.trim() || ledgerLoading) return;
    setLedgerLoading(true);
    setLedgerError('');
    setLedgerData(null);
    try {
      const resp = await api.get<LedgerResponse>(
        `/api/admin/ledger?orderId=${encodeURIComponent(ledgerOrderId.trim())}`
      );
      setLedgerData(resp.data);
    } catch (err: unknown) {
      if (err instanceof Error && 'status' in err) {
        const e2 = err as { status: number; message: string };
        setLedgerError(e2.message || 'Hesap hareketleri yuklenemedi');
      } else {
        setLedgerError('Hesap hareketleri yuklenemedi');
      }
    } finally {
      setLedgerLoading(false);
    }
  };

  if (!isAdmin) return <AdminOnly />;

  return (
    <div className="space-y-space-md">
      {/* Commission Rates Section */}
      <section>
        <h2 className="text-xl font-semibold text-on-surface tracking-tight flex items-center gap-2">
          <span className="material-symbols-outlined text-primary">percent</span>
          Komisyon oranlari
        </h2>

        {!rateLoading && !rateError && verticalsWithoutDefault(rates).length > 0 && (
          <p className="mt-2 rounded-lg bg-warning-container px-3 py-2 text-sm text-on-warning-container" data-testid="missing-default-rates">
            Varsayilan komisyon orani olmayan dikeyler: {verticalsWithoutDefault(rates).map(v => VERTICAL_TR[v]).join(', ')}.
            Ozel orani olmayan isletmeler bu dikeylerde satis yapamaz. Isletmeyi onaylarken oran girilir.
          </p>
        )}

        {/* Error from load */}
        {rateError && (
          <p className="mt-2 rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{rateError}</p>
        )}

        {/* Rate list */}
        {rateLoading ? (
          <div className="space-y-3 mt-4">
            {[1, 2, 3].map(i => (
              <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse">
                <div className="h-5 bg-surface-container rounded w-1/3 mb-2" />
                <div className="h-4 bg-surface-container rounded w-1/2" />
              </div>
            ))}
          </div>
        ) : rates.length === 0 ? (
          <p className="mt-4 text-on-surface-variant text-sm">Henuz komisyon oran tanimlanmamis</p>
        ) : (
          <div className="mt-4 space-y-3">
            {rates.map(r => (
              <div key={r.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-bold text-on-surface">
                      {VERTICAL_LABELS[r.vertical] || r.vertical}
                      {r.supplierOrganizationId && (
                        <span className="text-xs text-on-surface-variant ml-2">
                          - Isletme: {r.supplierOrganizationId.slice(0, 8)}...
                        </span>
                      )}
                    </p>
                    <p className="text-[10px] text-on-surface-variant mt-1">
                      Gecerlilik: {new Date(r.validFrom).toLocaleDateString('tr-TR')}
                    </p>
                  </div>
                  <span className="text-lg font-bold text-primary">
                    {(r.rate * 100).toFixed(2)}%
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Add rate form */}
        <form onSubmit={handleCreateRate} className="mt-6 bg-surface-container-lowest rounded-xl shadow-sm p-4 space-y-3">
          <h3 className="text-sm font-semibold text-on-surface">Yeni oran ekle</h3>
          <div className="space-y-3">
            <div>
              <label className="text-xs text-on-surface-variant block mb-1">Vertical</label>
              <select
                value={formVertical}
                onChange={e => setFormVertical(e.target.value)}
                className="w-full px-3 py-2 bg-surface-container rounded-lg text-on-surface text-sm focus:outline-none"
              >
                <option value="HOTEL">Otel</option>
                <option value="CAR">Araclar</option>
                <option value="FLIGHT">Ucuslar</option>
                <option value="BUS">Otobus</option>
              </select>
            </div>
            <div>
              <label className="text-xs text-on-surface-variant block mb-1">Isletme ID (opsiyonel)</label>
              <input
                type="text"
                value={formOrgId}
                onChange={e => setFormOrgId(e.target.value)}
                placeholder="UUID veya bos birakin"
                className="w-full px-3 py-2 bg-surface-container rounded-lg text-on-surface text-sm placeholder:text-outline focus:outline-none"
              />
            </div>
            <div>
              <label className="text-xs text-on-surface-variant block mb-1">Komisyon oran (%)</label>
              <input
                type="number"
                step="0.01"
                min="0"
                max="100"
                value={formRate}
                onChange={e => setFormRate(e.target.value)}
                placeholder="Orn: 15"
                className="w-full px-3 py-2 bg-surface-container rounded-lg text-on-surface text-sm placeholder:text-outline focus:outline-none"
              />
            </div>
            <div>
              <label className="text-xs text-on-surface-variant block mb-1">Gecerlilik baslangici (opsiyonel)</label>
              <input
                type="datetime-local"
                value={formValidFrom}
                onChange={e => setFormValidFrom(e.target.value)}
                className="w-full px-3 py-2 bg-surface-container rounded-lg text-on-surface text-sm focus:outline-none"
              />
            </div>
            {formError && <p className="text-xs text-on-error">{formError}</p>}
            {formSuccess && <p className="text-xs text-on-primary">{formSuccess}</p>}
            <button
              type="submit"
              disabled={formSaving}
              className="w-full py-2.5 bg-primary text-on-primary rounded-lg text-sm font-semibold hover:bg-primary-container hover:text-on-primary-container transition-colors disabled:opacity-50"
            >
              {formSaving ? 'Kaydediliyor...' : 'Oran kaydet'}
            </button>
          </div>
        </form>
      </section>

      {/* Ledger Lookup Section */}
      <section className="mt-6">
        <h2 className="text-xl font-semibold text-on-surface tracking-tight flex items-center gap-2">
          <span className="material-symbols-outlined text-primary">book_online</span>
          Hesap hareketleri
        </h2>

        <div className="mt-4 flex gap-2">
          <input
            type="text"
            value={ledgerOrderId}
            onChange={e => setLedgerOrderId(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleLookupLedger()}
            placeholder="Siparis (order) UUID..."
            className="flex-1 px-3 py-2 bg-surface-container-lowest rounded-lg text-on-surface text-sm placeholder:text-outline focus:outline-none"
          />
          <button
            type="button"
            onClick={handleLookupLedger}
            disabled={ledgerLoading}
            className="px-4 py-2 bg-secondary text-on-secondary rounded-lg text-sm font-semibold hover:bg-secondary-container hover:text-on-secondary-container transition-colors disabled:opacity-50 flex items-center gap-1"
          >
            <span className="material-symbols-outlined text-[18px]">search</span>
            Ara
          </button>
        </div>

        {ledgerError && (
          <p className="mt-3 rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{ledgerError}</p>
        )}

        {ledgerLoading && (
          <div className="mt-4 space-y-3">
            {[1, 2, 3].map(i => (
              <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse">
                <div className="h-5 bg-surface-container rounded w-1/3 mb-2" />
                <div className="h-4 bg-surface-container rounded w-1/2" />
              </div>
            ))}
          </div>
        )}

        {ledgerData && (
          <div className="mt-4 space-y-3">
            <div className="bg-primary-fixed bg-opacity-20 rounded-xl p-4">
              <p className="text-xs text-on-surface-variant">Siparis</p>
              <p className="text-sm font-mono text-on-surface">{ledgerData.orderId}</p>
              <p className="text-xs text-on-surface-variant mt-2">Bakiye: <span className="font-bold text-on-surface">{ledgerData.balance.toFixed(2)}</span> {ledgerData.entries.length > 0 ? ledgerData.entries[0].currency : ''}</p>
            </div>
            {ledgerData.entries.length === 0 ? (
              <p className="text-on-surface-variant text-sm">Bu siparis icin hesap hareketi bulunamadi</p>
            ) : (
              <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-on-surface-variant border-b border-surface-container">
                        <th className="text-left px-3 py-2 font-medium">Zaman</th>
                        <th className="text-left px-3 py-2 font-medium">Hesap</th>
                        <th className="text-right px-3 py-2 font-medium">Borc</th>
                        <th className="text-right px-3 py-2 font-medium">Alacak</th>
                        <th className="text-left px-3 py-2 font-medium">Aciklama</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ledgerData.entries.map(entry => (
                        <tr key={entry.entryId} className="border-b border-surface-container last:border-0">
                          <td className="px-3 py-2 text-on-surface-variant text-xs">
                            {new Date(entry.occurredAt).toLocaleString('tr-TR')}
                          </td>
                          <td className="px-3 py-2 text-on-surface">
                            {ACCOUNT_LABELS[entry.account] || entry.account}
                          </td>
                          <td className="px-3 py-2 text-right text-on-surface">
                            {entry.debitAmount > 0 ? entry.debitAmount.toFixed(2) : '-'}
                          </td>
                          <td className="px-3 py-2 text-right text-on-surface">
                            {entry.creditAmount > 0 ? entry.creditAmount.toFixed(2) : '-'}
                          </td>
                          <td className="px-3 py-2 text-on-surface-variant text-xs max-w-[200px] truncate">
                            {entry.description}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </section>
      <ReconciliationSection />
    </div>
  );
}
