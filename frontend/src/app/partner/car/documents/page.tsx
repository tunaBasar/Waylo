'use client';

/**
 * Documents management page for CAR_RENTAL partner orgs.
 *
 * Implements section 6 of CAR-RENTAL-CONTRACT.md:
 * GET /api/partner/organizations/{orgId}/documents
 * POST /api/partner/organizations/{orgId}/documents (multipart)
 * GET /api/partner/organizations/{orgId}/documents/{docId}/file (download)
 *
 * Document types limited by legalType and org/vehicle level.
 */
import { useEffect, useRef, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import {
  partnerCarApi,
  type Document,
  type DocumentType,
  type Vehicle,
  documentTypeLabel,
} from '@/lib/partner-car';
import { Button, Card, EmptyState, Field, LoadingRows, PageHeader, StatusPill, inputClass } from '@/components/partner/ui';

function UploadForm({ organizationId, legalType, vehicles, onDone }: {
  organizationId: string;
  legalType: string;
  vehicles: { id: string; name: string }[];
  onDone: (saved: boolean) => void;
}) {
  const [type, setType] = useState<DocumentType>('RENTAL_AUTHORIZATION');
  const [documentNumber, setDocumentNumber] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [vehicleId, setVehicleId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  const isOrgLevel = legalType === 'COMPANY'
    ? type === 'RENTAL_AUTHORIZATION' || type === 'TAX_CERTIFICATE'
    : type === 'IDENTITY_DOCUMENT';

  const validTypes = legalType === 'COMPANY'
    ? ['RENTAL_AUTHORIZATION', 'TAX_CERTIFICATE', 'VEHICLE_REGISTRATION', 'RENTAL_INSURANCE', 'TRAFFIC_INSURANCE'] as DocumentType[]
    : ['IDENTITY_DOCUMENT', 'VEHICLE_REGISTRATION', 'RENTAL_INSURANCE', 'TRAFFIC_INSURANCE'] as DocumentType[];

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!file || !documentNumber.trim() || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    try {
      await partnerCarApi.uploadDocument(organizationId, {
        file,
        type,
        documentNumber: documentNumber.trim(),
        validUntil: validUntil || null,
        vehicleId: isOrgLevel ? null : (vehicleId || null),
      });
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
        <Field label="Belge tipi">
          <select className={inputClass} value={type} onChange={e => setType(e.target.value as DocumentType)}>
            {validTypes.map(t => (
              <option key={t} value={t}>{documentTypeLabel(t)}</option>
            ))}
          </select>
        </Field>

        <Field label="Belge no" error={!documentNumber.trim() ? 'Gerekli' : undefined}>
          <input className={inputClass} value={documentNumber} onChange={e => setDocumentNumber(e.target.value)}
            maxLength={100} autoFocus />
        </Field>

        <Field label="Gecerlilik bitis">
          <input className={inputClass} type="date" value={validUntil}
            onChange={e => setValidUntil(e.target.value)} />
        </Field>

        <Field label="Arac">
          <select className={inputClass} value={vehicleId}
            onChange={e => setVehicleId(e.target.value)} disabled={isOrgLevel}>
            <option value="">{isOrgLevel ? 'Belge sube seviyesinde' : 'Belge yok'}</option>
            {!isOrgLevel && vehicles.map(v => (
              <option key={v.id} value={v.id}>{v.name}</option>
            ))}
          </select>
        </Field>

        <Field label="Dosya" error={!file ? 'Dosya gerekli' : undefined}>
          <input className={inputClass} type="file" onChange={e => {
            const f = e.target.files?.[0];
            if (f) setFile(f);
          }} />
          {file && <span className="text-xs text-outline mt-1">{file.name} ({(file.size / 1024).toFixed(0)} KB)</span>}
        </Field>

        <div className="sm:col-span-2 flex gap-2">
          <Button type="submit" icon="upload_file" disabled={saving || !file || !documentNumber.trim()}>
            {saving ? 'Yukleniyor' : 'Yukle'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>Vazgec</Button>
        </div>
      </form>
    </Card>
  );
}

function formatLocalDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const parts = iso.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!parts) return iso;
  return `${parts[3]}.${parts[2]}.${parts[1]}`;
}

function isExpired(validUntil: string | null): boolean {
  if (!validUntil) return false;
  const [y, m, d] = validUntil.split('-').map(Number);
  const valid = new Date(y, m - 1, d);
  const today = new Date();
  return valid.getFullYear() === today.getFullYear() &&
    valid.getMonth() === today.getMonth() &&
    valid.getDate() === today.getDate()
    ? false
    : valid < today;
}

function DocumentRow({ doc, organizationId, vehicleName }: {
  doc: Document;
  organizationId: string;
  vehicleName?: string;
}) {
  const handleDownload = async () => {
    try {
      const res = await partnerCarApi.downloadDocument(organizationId, doc.id);
      const blob = res.data;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.fileName;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Yuklenemedi');
    }
  };

  const expired = isExpired(doc.validUntil);
  const validText = doc.validUntil ? formatLocalDate(doc.validUntil) : null;

  return (
    <Card className="p-3 flex items-center gap-4">
      <span className="text-sm text-on-surface flex-1 min-w-0 truncate">
        {documentTypeLabel(doc.type)}
        {vehicleName && <span className="text-on-surface-variant"> ({vehicleName})</span>}
        <span className="text-xs text-outline block">{doc.documentNumber}</span>
      </span>
      {validText && (
        <span className="text-xs text-outline whitespace-nowrap">
          Gecerlilik: {validText}
        </span>
      )}
      <StatusPill tone={
        expired ? 'error' :
          doc.status === 'APPROVED' ? 'success' :
          doc.status === 'REJECTED' ? 'error' : 'neutral'
      }>
        {expired ? 'Suresi doldu' :
          doc.status === 'APPROVED' ? 'Onaylandi' :
          doc.status === 'REJECTED' ? 'Reddedildi' : 'Onay bekliyor'}
      </StatusPill>
      <div className="flex gap-2">
        {doc.status === 'REJECTED' && doc.rejectReason && (
          <span className="text-xs text-error whitespace-nowrap" title={doc.rejectReason}>
            Red nedeni: {doc.rejectReason}
          </span>
        )}
        <Button variant="ghost" icon="download" onClick={handleDownload}>Indir</Button>
      </div>
    </Card>
  );
}

export default function DocumentsPage() {
  const { organizationId, loading: workspaceLoading, membership } = usePartner();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);

  const reload = () => {
    if (!organizationId) return;
    partnerCarApi.documents(organizationId)
      .then(res => { setDocuments(res.data ?? []); setError(null); })
      .catch(e => { setError(e instanceof Error ? e.message : 'Yuklenemedi'); });
  };

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    partnerCarApi.documents(organizationId)
      .then(res => {
        if (alive) {
          setDocuments(res.data ?? []);
          setError(null);
        }
      })
      .catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Yuklenemedi'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [organizationId]);

  useEffect(() => {
    if (!organizationId) return;
    partnerCarApi.vehicles(organizationId).then(res => setVehicles(res.data ?? [])).catch(() => {});
  }, [organizationId]);

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Belgeler"
        subtitle="Organizasyon ve arac belgeleri"
        actions={!showForm && (
          <Button icon="upload_file" onClick={() => setShowForm(true)}>Belge yukle</Button>
        )}
      />

      {showForm && (
        <UploadForm
          organizationId={organizationId!}
          legalType={membership?.organizationLegalType ?? 'COMPANY'}
          vehicles={vehicles.map(v => ({ id: v.id, name: v.brand + ' ' + v.model }))}
          onDone={saved => { setShowForm(false); if (saved) reload(); }}
        />
      )}

      {documents.length === 0 ? (
        !showForm && (
          <EmptyState icon="description" title="Henuz belge yok"
            description="Belge yukleyerek baslayin."
            action={<Button icon="upload_file" onClick={() => setShowForm(true)}>Belge yukle</Button>} />
        )
      ) : (
        <div className="space-y-2">
          {documents.map(doc => {
            const vehicle = vehicles.find(v => v.id === doc.vehicleId);
            return (
              <DocumentRow
                key={doc.id}
                doc={doc}
                organizationId={organizationId!}
                vehicleName={vehicle ? `${vehicle.brand} ${vehicle.model}` : undefined}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
