'use client';

import { FormEvent, Suspense, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import { ApiError } from '@/lib/api';
import {
  getCarBranches, getCarOffers, postCarOrder, errorToMessage,
  generateTimeSlots, toLocalDateTime, formatLocalDateTime,
  CATEGORY_LABEL, TRANSMISSION_LABEL, FUEL_LABEL,
} from '@/lib/car';
import type { Branch, CarOffer } from '@/lib/car';

const TIME_SLOTS = generateTimeSlots();

function VehiclesContent() {
  const router = useRouter();
  const params = useSearchParams();
  const { user, loading: authLoading } = useAuth();
  const [branches, setBranches] = useState<Branch[]>([]);
  const [loadingBranches, setLoadingBranches] = useState(true);

  // Search form state
  const [city, setCity] = useState(params.get('city') ?? '');
  const [pickupBranchId, setPickupBranchId] = useState('');
  const [returnBranchId, setReturnBranchId] = useState('');
  const [pickupDate, setPickupDate] = useState('');
  const [pickupTime, setPickupTime] = useState('09:00');
  const [returnDate, setReturnDate] = useState('');
  const [returnTime, setReturnTime] = useState('09:00');
  const [driverAge, setDriverAge] = useState('25');

  // Results
  const [offers, setOffers] = useState<CarOffer[]>([]);
  const [loadingOffers, setLoadingOffers] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [renting, setRenting] = useState<string | null>(null);

  // Load branches
  useEffect(() => {
    let alive = true;
    getCarBranches(city || undefined)
      .then(res => { if (alive) setBranches(res.data); })
      .catch(() => { if (alive) setError('Subeler yuklenemedi.'); })
      .finally(() => { if (alive) setLoadingBranches(false); });
    return () => { alive = false; };
  }, [city]);

  // Derived cities
  const cities = useMemo(() => {
    const set = new Set(branches.map(b => b.city));
    return Array.from(set).sort();
  }, [branches]);

  // Pickup branches filtered by city
  const pickupBranches = useMemo(
    () => branches.filter(b => !city || b.city === city),
    [branches, city],
  );

  // Return branches: same as pickup unless city changed
  const returnBranches = useMemo(
    () => branches.filter(b => !city || b.city === city),
    [branches, city],
  );

  // Arrived from search hub: if params have city, run search
  useEffect(() => {
    if (params.get('city')) {
      void Promise.resolve().then(() => searchOffers());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function resetForm() {
    setPickupBranchId('');
    setReturnBranchId('');
    setPickupDate('');
    setPickupTime('09:00');
    setReturnDate('');
    setReturnTime('09:00');
    setDriverAge('25');
    setOffers([]);
    setMessage('');
    setError('');
  }

  async function searchOffers(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if (!pickupBranchId || !returnBranchId) {
      setError('Giden ve donus subelerini secin.');
      return;
    }
    if (!pickupDate || !returnDate) {
      setError('Tarihleri secin.');
      return;
    }
    const pickupAt = toLocalDateTime(pickupDate, pickupTime);
    const returnAt = toLocalDateTime(returnDate, returnTime);
    if (new Date(returnAt) <= new Date(pickupAt)) {
      setError('Donus tarihi giden tarihinden sonra olamali.');
      return;
    }
    const age = Number(driverAge);
    if (age < 18) {
      setError('Sofor yaşi en az 18 olmalidir.');
      return;
    }

    setLoadingOffers(true);
    setError('');
    setMessage('');
    try {
      const res = await getCarOffers({ pickupBranchId, returnBranchId, pickupAt, returnAt, driverAge: age });
      setOffers(res.data);
    } catch (cause) {
      const apiError = cause as ApiError;
      setError(errorToMessage(apiError.code, 'Teklifler yuklenemedi.'));
    } finally {
      setLoadingOffers(false);
    }
  }

  async function bookOffer(offer: CarOffer) {
    if (authLoading || !user) {
      router.push('/login');
      return;
    }
    const pickupAt = toLocalDateTime(pickupDate, pickupTime);
    const returnAt = toLocalDateTime(returnDate, returnTime);
    const age = Number(driverAge);

    setRenting(`${offer.category}-${offer.transmission}`);
    setError('');
    setMessage('');
    try {
      const res = await postCarOrder({
        pickupBranchId: offer.pickupBranchId,
        returnBranchId: offer.returnBranchId,
        category: offer.category,
        transmission: offer.transmission,
        pickupAt,
        returnAt,
        driverAge: age,
      });
      router.push(`/orders?created=${encodeURIComponent(res.data.id)}`);
    } catch (cause) {
      const apiError = cause as ApiError;
      setError(errorToMessage(apiError.code, 'Arac kiralanamadi.'));
    } finally {
      setRenting(null);
    }
  }

  const money = (val: number | null | undefined, currency: string) => {
    if (val == null) return '-';
    return new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(val);
  };

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Arac Kiralama</h2>

      {/* Search form */}
      <form onSubmit={searchOffers} className="grid gap-3 rounded-xl bg-surface-container-lowest p-4 shadow-sm md:grid-cols-3 lg:grid-cols-4">
        {/* City select */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Sehir</label>
          <select value={city} onChange={e => { setCity(e.target.value); resetForm(); }}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
            <option value="">Tum sehirler</option>
            {cities.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>

        {/* Pickup branch */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Gidis subesi</label>
          <select value={pickupBranchId} onChange={e => { setPickupBranchId(e.target.value); setReturnBranchId(e.target.value); }}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
            <option value="">Sube secin</option>
            {pickupBranches.map(b => <option key={b.id} value={b.id}>{b.name} ({b.city})</option>)}
          </select>
        </div>

        {/* Return branch */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Donus subesi</label>
          <select value={returnBranchId} onChange={e => setReturnBranchId(e.target.value)}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
            <option value="">Sube secin</option>
            {returnBranches.map(b => <option key={b.id} value={b.id}>{b.name} ({b.city})</option>)}
          </select>
        </div>

        {/* Pickup date */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Gidis tarihi</label>
          <input type="date" value={pickupDate} onChange={e => setPickupDate(e.target.value)}
            min={new Date(Date.now() + 2 * 3600 * 1000).toISOString().slice(0, 10)}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm" />
        </div>

        {/* Pickup time */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Gidis saati</label>
          <select value={pickupTime} onChange={e => setPickupTime(e.target.value)}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
            {TIME_SLOTS.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>

        {/* Return date */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Donus tarihi</label>
          <input type="date" value={returnDate} onChange={e => setReturnDate(e.target.value)}
            min={pickupDate || new Date(Date.now() + 2 * 3600 * 1000).toISOString().slice(0, 10)}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm" />
        </div>

        {/* Return time */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Donus saati</label>
          <select value={returnTime} onChange={e => setReturnTime(e.target.value)}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
            {TIME_SLOTS.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>

        {/* Driver age */}
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Sofor yaşi</label>
          <input type="number" min={18} max={99} value={driverAge} onChange={e => setDriverAge(e.target.value)}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm" />
        </div>

        <div className="flex items-end">
          <button className="w-full rounded-lg bg-tertiary px-4 py-2 text-sm font-medium text-on-tertiary">
            Ara
          </button>
        </div>
      </form>

      {/* Status messages */}
      {(error || message) && (
        <p className={`rounded-lg px-3 py-2 text-sm ${error ? 'bg-error-container text-on-error-container' : 'bg-secondary-container text-on-secondary-container'}`}>
          {error || message}
        </p>
      )}

      {/* Loading */}
      {loadingBranches ? (
        <p className="py-12 text-center text-sm text-on-surface-variant">Subeler yukleniyor...</p>
      ) : loadingOffers ? (
        <p className="py-12 text-center text-sm text-on-surface-variant">Teklifler aranıyor...</p>
      ) : offers.length === 0 && (pickupBranchId || returnBranchId) ? (
        <p className="py-12 text-center text-sm text-on-surface-variant">Secilen kriterlerde teklif bulunamadi.</p>
      ) : (
        /* Offer cards */
        <div className="space-y-space-sm">
          {offers.map((offer, idx) => (
            <div key={`${offer.category}-${offer.transmission}-${idx}`}
              className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-base font-bold text-on-surface">{offer.exampleModel}</p>
                  <p className="text-xs text-on-surface-variant mt-1">
                    {CATEGORY_LABEL[offer.category]} · {TRANSMISSION_LABEL[offer.transmission]} · {FUEL_LABEL[offer.fuelType]} · {offer.seatCount} koltuk
                  </p>
                </div>
                <p className="text-lg font-bold text-primary">{money(offer.totalAmount, offer.currency)}</p>
              </div>

              <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-on-surface-variant">
                <p>Gunluk: <span className="font-medium text-on-surface">{money(offer.dailyRate, offer.currency)}</span></p>
                <p>Sure: <span className="font-medium text-on-surface">{offer.chargeableDays} gun</span></p>
                {offer.oneWayFee > 0 && (
                  <p>Tek yon ucreti: <span className="font-medium text-on-surface">{money(offer.oneWayFee, offer.currency)}</span></p>
                )}
                <p>Depozito: <span className="font-medium text-on-surface">{money(offer.depositAmount, offer.currency)}</span></p>
                <p>Kilometre: <span className="font-medium text-on-surface">{offer.kmLimitPerDay == null ? 'Sinirsiz km' : `${offer.kmLimitPerDay} km/gun`}</span></p>
                {offer.extraKmFee != null && (
                  <p>Ek km ucreti: <span className="font-medium text-on-surface">{money(offer.extraKmFee, offer.currency)}</span></p>
                )}
                <p>Min yas: <span className="font-medium text-on-surface">{offer.minDriverAge}</span></p>
                <p>Min ehliyet: <span className="font-medium text-on-surface">{offer.minLicenseYears} yil</span></p>
              </div>

              {/* Yetki belgesi */}
              {offer.authorizationDocumentNumber && (
                <p className="mt-2 text-xs text-on-surface-variant">
                  Yetki belgesi no: {offer.authorizationDocumentNumber}
                </p>
              )}

              {/* Pickup/return info */}
              <div className="mt-2 flex items-center gap-4 text-xs text-on-surface-variant">
                <span>Gidis: {formatLocalDateTime(toLocalDateTime(pickupDate, pickupTime))}</span>
                <span>Donus: {formatLocalDateTime(toLocalDateTime(returnDate, returnTime))}</span>
              </div>

              <div className="mt-3 flex justify-end">
                <button disabled={renting !== null} onClick={() => bookOffer(offer)}
                  className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary disabled:opacity-50">
                  {renting === `${offer.category}-${offer.transmission}` ? 'Kiralanıyor...' : 'Kirala'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function VehiclesPage() {
  return <Suspense fallback={<p className="text-sm text-on-surface-variant">Yukleniyor...</p>}><VehiclesContent /></Suspense>;
}
