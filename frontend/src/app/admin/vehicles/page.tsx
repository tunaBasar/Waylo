'use client';

import { useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { CATEGORY_LABEL, TRANSMISSION_LABEL, FUEL_LABEL } from '@/lib/car';

type VehicleCategory = 'ECONOMY' | 'COMPACT' | 'MIDSIZE' | 'FULLSIZE' | 'SUV' | 'MINIVAN' | 'LUXURY' | 'VAN';
type TransmissionType = 'AUTOMATIC' | 'MANUAL';
type FuelType = 'PETROL' | 'DIESEL' | 'LPG' | 'HYBRID' | 'ELECTRIC';

type AdminVehicle = {
  id: string;
  organizationId: string;
  organizationName?: string | null;
  homeBranchId: string;
  homeBranchName?: string | null;
  plateNumber: string;
  brand: string;
  model: string;
  year: number;
  category: VehicleCategory;
  transmission: TransmissionType;
  fuelType: FuelType;
  seatCount: number;
  active: boolean;
  sellable: boolean;
  missingDocuments: string[];
};

function AdminOnly() {
  return (
    <div className="text-center py-12">
      <span className="material-symbols-outlined text-outline text-5xl mb-3 block">lock</span>
      <p className="text-on-surface-variant text-sm">Bu sayfa yalnizca yoneticiler icindir</p>
    </div>
  );
}

export default function AdminVehiclesPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [vehicles, setVehicles] = useState<AdminVehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isAdmin) return;
    api.get<AdminVehicle[]>('/api/admin/car-vehicles')
      .then(res => setVehicles(res.data))
      .catch((cause) => {
        const apiError = cause as ApiError;
        setError(apiError.message || 'Araclar yuklenemedi.');
      })
      .finally(() => setLoading(false));
  }, [isAdmin]);

  if (!isAdmin) return <AdminOnly />;

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Arac Yonetimi</h2>
          <span className="bg-tertiary-container text-on-tertiary-container text-xs font-semibold px-2.5 py-0.5 rounded-full">{vehicles.length}</span>
        </div>
      </div>

      {/* Summary row */}
      <div className="grid grid-cols-3 gap-space-xs">
        <div className="bg-surface-container-lowest rounded-xl p-3 shadow-sm text-center">
          <p className="text-lg font-bold text-on-surface">{vehicles.length}</p>
          <p className="text-[10px] text-on-surface-variant">Toplam Arac</p>
        </div>
        <div className="bg-surface-container-lowest rounded-xl p-3 shadow-sm text-center">
          <p className="text-lg font-bold text-on-surface">{vehicles.filter(v => v.sellable).length}</p>
          <p className="text-[10px] text-on-surface-variant">Kiralanabilir</p>
        </div>
        <div className="bg-surface-container-lowest rounded-xl p-3 shadow-sm text-center">
          <p className="text-lg font-bold text-error">{vehicles.filter(v => !v.sellable).length}</p>
          <p className="text-[10px] text-on-surface-variant">Kiralanamaz</p>
        </div>
      </div>

      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-1/2"/></div>)}</div>
      ) : vehicles.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">directions_car</span><p className="text-on-surface-variant text-sm">Arac bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {vehicles.map(vehicle => (
            <div key={vehicle.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="text-sm font-bold text-on-surface">
                    {vehicle.brand} {vehicle.model}
                  </p>
                  <p className="text-[10px] text-on-surface-variant">Plaka: {vehicle.plateNumber}</p>
                </div>
                <div className="flex items-center gap-1.5">
                  {vehicle.sellable && (
                    <span className="text-[10px] font-bold text-on-secondary-container bg-secondary-container px-2 py-0.5 rounded-full">
                      Kiralanabilir
                    </span>
                  )}
                  {!vehicle.sellable && vehicle.missingDocuments?.length > 0 && (
                    <span className="text-[10px] font-bold text-on-error-container bg-error-container px-2 py-0.5 rounded-full">
                      Eksi: {vehicle.missingDocuments.length}
                    </span>
                  )}
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${
                    vehicle.active
                      ? 'bg-secondary-container text-on-secondary-container'
                      : 'bg-surface-container text-on-surface-variant'
                  }`}>
                    {vehicle.active ? 'Aktif' : 'Pasif'}
                  </span>
                </div>
              </div>

              {/* Org and branch context */}
              <div className="flex items-center gap-3 mb-3">
                {vehicle.organizationName && (
                  <span className="text-[10px] text-on-surface-variant bg-surface-container px-2 py-0.5 rounded-full">
                    {vehicle.organizationName}
                  </span>
                )}
                {vehicle.homeBranchName && (
                  <span className="text-[10px] text-on-surface-variant bg-surface-container px-2 py-0.5 rounded-full">
                    {vehicle.homeBranchName}
                  </span>
                )}
              </div>

              {/* Missing documents */}
              {vehicle.missingDocuments && vehicle.missingDocuments.length > 0 && (
                <div className="mb-3">
                  <p className="text-[10px] text-error mb-1">Eksik belgeler:</p>
                  <div className="flex flex-wrap gap-1">
                    {vehicle.missingDocuments.map(dt => (
                      <span key={dt} className="text-[10px] text-on-error-container bg-error-container-lowest px-1.5 py-0.5 rounded">
                        {dt}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 text-xs text-on-surface-variant">
                <p>Tip: <span className="text-on-surface">{CATEGORY_LABEL[vehicle.category] || vehicle.category}</span></p>
                <p>Vites: <span className="text-on-surface">{TRANSMISSION_LABEL[vehicle.transmission] || vehicle.transmission}</span></p>
                <p>Yakit: <span className="text-on-surface">{FUEL_LABEL[vehicle.fuelType] || vehicle.fuelType}</span></p>
                <p>Koltuk: <span className="text-on-surface">{vehicle.seatCount}</span></p>
                <p>Yil: <span className="text-on-surface">{vehicle.year}</span></p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
