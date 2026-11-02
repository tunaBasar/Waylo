'use client';

import { useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { getMyRentals, RENTAL_STATUS_LABEL, formatLocalDateTime, CATEGORY_LABEL, TRANSMISSION_LABEL } from '@/lib/car';
import type { Category, RentalStatus, Transmission } from '@/lib/partner-car';
import { formatCurrency } from '@/lib/utils';

export default function RentalsPage() {
  const [rentals, setRentals] = useState<Array<{ id: string; pickupBranchId: string; returnBranchId: string; chargeableDays: number; dailyRate: number; oneWayFee: number; totalAmount: number; currency: string; depositAmount: number; kmLimitPerDay: number | null; category: Category; transmission: Transmission; status: string; pickupAt: string; returnAt: string }>>([]);
  const [branchMap, setBranchMap] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Load branches for names
  useEffect(() => {
    let alive = true;
    api.get<{ id: string; name: string }[]>('/api/car-branches')
      .then(res => {
        const map: Record<string, string> = {};
        res.data.forEach(b => { map[b.id] = b.name; });
        if (alive) setBranchMap(map);
      })
      .catch(() => { /* Branch names are optional; continue without */ });
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    let alive = true;
    getMyRentals().then(res => {
      if (alive) setRentals(res.data);
    }).catch(cause => {
      if (alive) {
        const apiError = cause as ApiError;
        setError(apiError.message || 'Kiralama kayitlari yuklenemedi.');
      }
    }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Arac Kiralamalarim</h2>

      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}

      {loading ? (
        <p className="py-12 text-center text-sm text-on-surface-variant">Kiralama kayitlari yukleniyor...</p>
      ) : rentals.length === 0 ? (
        <p className="py-12 text-center text-sm text-on-surface-variant">Arac kiralama rezervasyonunuz bulunmuyor.</p>
      ) : (
        <div className="space-y-space-sm">
          {rentals.map(rental => (
            <div key={rental.id} className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-semibold text-on-surface">
                    {branchMap[rental.pickupBranchId] || rental.pickupBranchId} - {branchMap[rental.returnBranchId] || rental.returnBranchId}
                  </p>
                  <p className="text-xs text-on-surface-variant mt-1">
                    {formatLocalDateTime(rental.pickupAt)} - {formatLocalDateTime(rental.returnAt)}
                  </p>
                  <p className="text-xs text-on-surface-variant">
                    {rental.chargeableDays} gun · Gunluk {formatCurrency(rental.dailyRate, rental.currency)}
                    {rental.oneWayFee > 0 && ` · Tek yon ${formatCurrency(rental.oneWayFee, rental.currency)}`}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-primary">{formatCurrency(rental.totalAmount, rental.currency)}</p>
                  <span className={`mt-1 inline-block rounded-full px-2.5 py-1 text-[10px] font-bold ${
                    rental.status === 'CONFIRMED'
                      ? 'bg-primary-fixed text-on-primary-fixed-variant'
                      : rental.status === 'CANCELLED'
                        ? 'bg-surface-container text-on-surface-variant'
                        : 'bg-tertiary text-on-tertiary'
                  }`}>
                    {RENTAL_STATUS_LABEL[rental.status as RentalStatus]}
                  </span>
                </div>
              </div>

              {/* Info only: deposit, km limit */}
              <div className="mt-2 grid grid-cols-3 gap-2 text-xs text-on-surface-variant">
                <p>Depozito: <span className="text-on-surface">{formatCurrency(rental.depositAmount, rental.currency)}</span></p>
                <p>Kilometre: <span className="text-on-surface">{rental.kmLimitPerDay == null ? 'Sinirsiz' : `${rental.kmLimitPerDay} km/gun`}</span></p>
                <p>Sinif: <span className="text-on-surface">{CATEGORY_LABEL[rental.category] ?? rental.category}, {TRANSMISSION_LABEL[rental.transmission] ?? rental.transmission}</span></p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
