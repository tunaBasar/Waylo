'use client';

/**
 * Rentals list page for CAR_RENTAL partner orgs.
 *
 * Implements section 6 of CAR-RENTAL-CONTRACT.md:
 * GET /api/partner/organizations/{orgId}/rentals
 */
import { useEffect, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import { partnerCarApi, type Rental, RENTAL_STATUS_LABEL, formatLocalDateTime, formatMoney } from '@/lib/partner-car';
import { Card, EmptyState, LoadingRows, PageHeader, StatusPill } from '@/components/partner/ui';

export default function RentalsPage() {
  const { organizationId, loading: workspaceLoading } = usePartner();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rentals, setRentals] = useState<Rental[]>([]);
  const [branches, setBranches] = useState<{ id: string; name: string }[]>([]);

  useEffect(() => {
    let alive = true;
    if (!organizationId) return;
    Promise.all([
      partnerCarApi.rentals(organizationId),
      partnerCarApi.branches(organizationId),
    ]).then(([rentalsRes, branchesRes]) => {
      if (alive) {
        setRentals(rentalsRes.data ?? []);
        setBranches(branchesRes.data ?? []);
        setError(null);
      }
    }).catch(e => {
      if (alive) setError(e instanceof Error ? e.message : 'Yuklenemedi');
    }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [organizationId]);

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Kiralamalar"
        subtitle="Musteri kiralamalari"
      />

      {rentals.length === 0 ? (
        <EmptyState icon="receipt_long" title="Henuz kiralama yok"
          description="QR kod ile baslangic yapildiginda kiralamalar burada gorunecek." />
      ) : (
        <div className="space-y-3">
          {rentals.map(r => (
            <RentalCard key={r.id} rental={r} branches={branches} />
          ))}
        </div>
      )}
    </div>
  );
}

function RentalCard({ rental, branches }: {
  rental: Rental;
  branches: { id: string; name: string }[];
}) {
  const statusTone = rental.status === 'CONFIRMED' ? 'success'
    : rental.status === 'CANCELLED' ? 'error'
    : rental.status === 'EXPIRED' ? 'neutral' : 'warning';

  const pickupBranch = branches.find(b => b.id === rental.pickupBranchId);
  const returnBranch = branches.find(b => b.id === rental.returnBranchId);

  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-2 mb-2">
        <div>
          <p className="font-semibold text-on-surface">ID: {rental.id}</p>
          <p className="text-sm text-on-surface-variant mt-0.5">
            {formatLocalDateTime(rental.pickupAt)} - {formatLocalDateTime(rental.returnAt)}
          </p>
        </div>
        <StatusPill tone={statusTone as 'success' | 'error' | 'neutral' | 'warning'}>
          {RENTAL_STATUS_LABEL[rental.status]}
        </StatusPill>
      </div>

      <div className="grid gap-1 text-sm text-outline">
        <div className="flex justify-between">
          <span>Alis:</span>
          <span>{pickupBranch?.name ?? rental.pickupBranchId}</span>
        </div>
        <div className="flex justify-between">
          <span>Donus:</span>
          <span>{returnBranch?.name ?? rental.returnBranchId}</span>
        </div>
        <div className="flex justify-between">
          <span>Kategori:</span>
          <span>{rental.category} / {rental.transmission}</span>
        </div>
        <div className="flex justify-between">
          <span>Suresi:</span>
          <span>{rental.chargeableDays} gun</span>
        </div>
        <div className="flex justify-between">
          <span>Gunluk:</span>
          <span>{formatMoney(rental.dailyRate, rental.currency)}</span>
        </div>
        {rental.oneWayFee > 0 && (
          <div className="flex justify-between">
            <span>Tek yon ucreti:</span>
            <span>{formatMoney(rental.oneWayFee, rental.currency)}</span>
          </div>
        )}
        <div className="flex justify-between font-semibold text-on-surface border-t pt-1 mt-1">
          <span>Toplam:</span>
          <span>{formatMoney(rental.totalAmount, rental.currency)}</span>
        </div>
        <div className="flex justify-between text-xs">
          <span>Garanti:</span>
          <span>{formatMoney(rental.depositAmount, rental.currency)}</span>
        </div>
      </div>
    </Card>
  );
}
