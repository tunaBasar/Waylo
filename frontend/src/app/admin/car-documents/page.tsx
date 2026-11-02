'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { ApiError } from '@/lib/api';
import { getAdminCarDocuments, approveCarDocument, rejectCarDocument, getAdminCarDocumentFile } from '@/lib/car';

type DocFilter = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED';

type AdminCarDocument = {
  id: string;
  organizationId: string;
  organizationName?: string | null;
  legalType?: string | null;
  vehicleId: string | null;
  vehicleLabel?: string | null;
  type: string;
  documentNumber: string | null;
  validUntil: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectReason: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  fileName: string;
  createdAt: string | null;
};

const DOC_TYPE_LABEL: Record<string, string> = {
  RENTAL_AUTHORIZATION: 'Arac Kiralama Yetki Belgesi',
  TAX_CERTIFICATE: 'Vergi Levhasi',
  IDENTITY_DOCUMENT: 'Kimlik Belgesi',
  VEHICLE_REGISTRATION: 'Araç Ruhsati',
  RENTAL_INSURANCE: 'Kasko Sigortasi',
  TRAFFIC_INSURANCE: 'Trafik Sigortasi',
};

const DOC_FILTER_LABEL: Record<DocFilter, string> = {
  ALL: 'Tumu',
  PENDING: 'Beklemede',
  APPROVED: 'Onayli',
  REJECTED: 'Reddedilen',
};

const statusConfig: Record<string, { label: string; class: string; icon: string }> = {
  PENDING: { label: 'Beklemede', class: 'bg-tertiary-container text-on-tertiary-container', icon: 'schedule' },
  APPROVED: { label: 'Onayli', class: 'bg-secondary-container text-on-secondary-container', icon: 'check_circle' },
  REJECTED: { label: 'Reddedildi', class: 'bg-error-container text-on-error-container', icon: 'cancel' },
};

function AdminOnly() {
  return (
    <div className="text-center py-12">
      <span className="material-symbols-outlined text-outline text-5xl mb-3 block">lock</span>
      <p className="text-on-surface-variant text-sm">Bu sayfa yalnizca yoneticiler icindir</p>
    </div>
  );
}

export default function CarDocumentsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [documents, setDocuments] = useState<AdminCarDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<DocFilter>('PENDING');
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [downloading, setDownloading] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin) return;
    getAdminCarDocuments()
      .then(res => setDocuments(res.data))
      .catch(() => setError('Belgeler yuklenemedi.'))
      .finally(() => setLoading(false));
  }, [isAdmin]);

  async function handleReject(id: string) {
    const reason = rejectReason[id] || '';
    setRejecting(id);
    setError('');
    try {
      await rejectCarDocument(id, { reason });
      setDocuments(docs => docs.map(d => d.id === id ? { ...d, status: 'REJECTED' as const } : d));
    } catch (cause) {
      const apiError = cause as ApiError;
      setError(apiError.message || 'Belge reddedilemedi.');
    } finally {
      setRejecting(null);
    }
  }

  async function handleApprove(id: string) {
    setRejecting(id);
    setError('');
    try {
      await approveCarDocument(id);
      setDocuments(docs => docs.map(d => d.id === id ? { ...d, status: 'APPROVED' as const } : d));
    } catch (cause) {
      const apiError = cause as ApiError;
      setError(apiError.message || 'Belge onaylanamadi.');
    } finally {
      setRejecting(null);
    }
  }

  async function handleDownload(id: string) {
    setDownloading(id);
    setError('');
    try {
      const blob = await getAdminCarDocumentFile(id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `car-doc-${id}-${documents.find(d => d.id === id)?.fileName || 'document'}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch {
      setError('Belge indirilemedi.');
    } finally {
      setDownloading(null);
    }
  }

  if (!isAdmin) return <AdminOnly />;

  const filtered = documents.filter(d => filter === 'ALL' || d.status === filter);
  const pendingCount = documents.filter(d => d.status === 'PENDING').length;

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Arac Belge Yonetimi</h2>
          <span className="bg-tertiary-container text-on-tertiary-container text-xs font-semibold px-2.5 py-0.5 rounded-full">{documents.length}</span>
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-4 gap-space-xs">
        {[
          { label: 'Tumu', value: documents.length, icon: 'description', color: 'text-on-surface' },
          { label: 'Beklemede', value: pendingCount, icon: 'schedule', color: 'text-tertiary' },
          { label: 'Onayli', value: documents.filter(d => d.status === 'APPROVED').length, icon: 'check_circle', color: 'text-secondary' },
          { label: 'Reddedilen', value: documents.filter(d => d.status === 'REJECTED').length, icon: 'cancel', color: 'text-error' },
        ].map(s => (
          <div key={s.label} className="bg-surface-container-lowest rounded-xl p-3 shadow-sm text-center">
            <span className={`material-symbols-outlined ${s.color} text-xl mb-1 block`}>{s.icon}</span>
            <p className="text-lg font-bold text-on-surface">{s.value}</p>
            <p className="text-[10px] text-on-surface-variant">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Filter tabs */}
      <div className="flex gap-2">
        {(Object.keys(DOC_FILTER_LABEL) as DocFilter[]).map(f => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
              filter === f
                ? 'bg-primary text-on-primary'
                : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-higher'
            }`}
          >
            {DOC_FILTER_LABEL[f]}
          </button>
        ))}
      </div>

      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-1/2"/></div>)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">description</span><p className="text-on-surface-variant text-sm">Belge bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {filtered.map(doc => {
            const sc = statusConfig[doc.status] || statusConfig.PENDING;
            return (
              <div key={doc.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className="w-9 h-9 rounded-lg bg-surface-container flex items-center justify-center">
                      <span className="material-symbols-outlined text-on-surface-variant text-lg">{sc.icon}</span>
                    </div>
                    <div>
                      <p className="text-sm font-bold text-on-surface">{DOC_TYPE_LABEL[doc.type] || doc.type}</p>
                      <p className="text-[10px] text-on-surface-variant">Belge no: {doc.documentNumber || '-'}</p>
                    </div>
                  </div>
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${sc.class}`}>{sc.label}</span>
                </div>

                {/* Org and vehicle context */}
                <div className="flex items-center gap-3 mb-3">
                  {doc.organizationName && (
                    <span className="text-[10px] text-on-surface-variant bg-surface-container px-2 py-0.5 rounded-full">
                      {doc.organizationName}
                    </span>
                  )}
                  {doc.legalType && (
                    <span className="text-[10px] text-on-surface-variant bg-surface-container px-2 py-0.5 rounded-full">
                      {doc.legalType}
                    </span>
                  )}
                  {doc.vehicleLabel && (
                    <span className="text-[10px] text-on-surface-variant bg-surface-container px-2 py-0.5 rounded-full">
                      {doc.vehicleLabel}
                    </span>
                  )}
                </div>

                {/* Document info */}
                <div className="grid grid-cols-2 gap-2 text-xs text-on-surface-variant mb-3">
                  <p>Son kullanma: <span className="text-on-surface">{doc.validUntil || '-'}</span></p>
                  {doc.reviewedBy && (
                    <p>Inceleme: <span className="text-on-surface">{doc.reviewedBy}</span></p>
                  )}
                  {doc.reviewedAt && (
                    <p>Inceleme tarihi: <span className="text-on-surface">{new Date(doc.reviewedAt).toLocaleString('tr-TR')}</span></p>
                  )}
                  {doc.createdAt && (
                    <p>Olusturma: <span className="text-on-surface">{new Date(doc.createdAt).toLocaleString('tr-TR')}</span></p>
                  )}
                </div>

                {/* Rejection reason */}
                {doc.status === 'REJECTED' && doc.rejectReason && (
                  <p className="mb-3 text-xs text-error bg-error-container-lowest p-2 rounded-lg">
                    Red nedeni: {doc.rejectReason}
                  </p>
                )}

                {/* Actions */}
                <div className="flex items-center gap-2">
                  <button
                    disabled={downloading !== null}
                    onClick={() => handleDownload(doc.id)}
                    className="rounded-lg bg-surface-container px-3 py-1.5 text-xs font-medium text-on-surface disabled:opacity-50"
                  >
                    {downloading === doc.id ? 'Indiriliyor...' : 'Belgeyi indir'}
                  </button>

                  {doc.status === 'PENDING' && (
                    <>
                      <button
                        disabled={rejecting === doc.id}
                        onClick={() => handleApprove(doc.id)}
                        className="rounded-lg bg-secondary px-3 py-1.5 text-xs font-medium text-on-secondary disabled:opacity-50"
                      >
                        {rejecting === doc.id ? 'Onaylaniyor...' : 'Onayla'}
                      </button>
                      <button
                        disabled={rejecting === doc.id}
                        onClick={() => handleReject(doc.id)}
                        className="rounded-lg bg-error-container px-3 py-1.5 text-xs font-medium text-on-error-container disabled:opacity-50"
                      >
                        {rejecting === doc.id ? 'Reddediliyor...' : 'Reddet'}
                      </button>
                      {rejecting === doc.id && (
                        <input
                          type="text"
                          value={rejectReason[doc.id] || ''}
                          onChange={e => setRejectReason(prev => ({ ...prev, [doc.id]: e.target.value }))}
                          placeholder="Red nedeni (opsiyonel)"
                          className="rounded-lg bg-surface-container px-3 py-1.5 text-xs text-on-surface-variant w-48"
                          autoFocus
                        />
                      )}
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
