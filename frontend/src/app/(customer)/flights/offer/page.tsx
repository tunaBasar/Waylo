'use client';

import { FormEvent, Suspense, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import { ApiError } from '@/lib/api';
import {
  CABIN_LABEL, flightErrorMessage, formatCountdown, getAvailableSeats, getFlightOffer, isValidPassengerName,
  mustSearchAgain, postFlightOrder, secondsLeft, seatsForCabin,
} from '@/lib/flight';
import type { AvailableSeat, FlightOffer } from '@/lib/flight';
import { PromoCodeField } from '@/components/PromoCodeField';
import { normalizePromoCode, promoErrorMessage } from '@/lib/pricing';

const dateTime = (iso: string) => new Date(iso).toLocaleString('tr-TR', { dateStyle: 'medium', timeStyle: 'short' });
const money = (v: number, currency: string) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(v);

function OfferContent() {
  const params = useSearchParams();
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const offerId = params.get('id') ?? '';
  const [offer, setOffer] = useState<FlightOffer | null>(null);
  const [seats, setSeats] = useState<AvailableSeat[]>([]);
  const [seat, setSeat] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [error, setError] = useState('');
  const [promoCode, setPromoCode] = useState('');
  const [expired, setExpired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!offerId) return;
    let alive = true;
    getFlightOffer(offerId)
      .then(async res => {
        if (!alive) return;
        setOffer(res.data);
        try {
          const s = await getAvailableSeats(res.data.flightNumber);
          if (alive) setSeats(seatsForCabin(s.data, res.data.cabin));
        } catch { /* seat choice is optional */ }
      })
      .catch(cause => {
        if (!alive) return;
        const code = (cause as ApiError).code;
        setExpired(mustSearchAgain(code));
        setError(flightErrorMessage(code, 'Teklif yuklenemedi.'));
      });
    return () => { alive = false; };
  }, [offerId]);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const left = useMemo(() => (offer ? secondsLeft(offer.expiresAt, now) : 0), [offer, now]);
  const timedOut = offer != null && left === 0;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!offer) return;
    if (authLoading || !user) { router.push('/login'); return; }
    if (!isValidPassengerName(firstName) || !isValidPassengerName(lastName)) {
      setError('Ad ve soyad yalnizca harf icermeli, kimlikteki gibi yazin.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const res = await postFlightOrder({
        offerId: offer.offerId,
        seatNumber: seat || undefined,
        passengerFirstName: firstName.trim(),
        passengerLastName: lastName.trim(),
        ...(promoCode.trim() ? { promoCode: normalizePromoCode(promoCode) } : {}),
      });
      router.push(`/orders?created=${encodeURIComponent(res.data.id)}`);
    } catch (cause) {
      const code = (cause as ApiError).code;
      setExpired(mustSearchAgain(code));
      setError(promoErrorMessage(code) ?? flightErrorMessage(code, 'Siparis olusturulamadi.'));
    } finally {
      setBusy(false);
    }
  }

  if (!offerId) return <p className="py-12 text-center text-sm text-on-surface-variant">Teklif secilmedi. <Link href="/flights" className="text-primary underline">Ucus ara</Link></p>;

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Yolcu Bilgileri</h2>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
      {expired && <Link href="/flights" className="inline-block rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary">Yeniden ara</Link>}
      {offer && (
        <>
          <div className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <p className="text-lg font-bold text-on-surface">{offer.flightNumber} · {offer.departureAirport} - {offer.arrivalAirport}</p>
              <p className="text-lg font-bold text-primary">{money(offer.totalAmount, offer.currency)}</p>
            </div>
            <p className="mt-1 text-xs text-on-surface-variant">{offer.airline} · {CABIN_LABEL[offer.cabin] ?? offer.cabin}</p>
            <p className="mt-1 text-xs text-on-surface-variant">Kalkis {dateTime(offer.departureTime)} · Varis {dateTime(offer.arrivalTime)}</p>
            <p className={`mt-2 text-xs font-medium ${timedOut ? 'text-error' : 'text-on-surface-variant'}`}>
              {timedOut ? 'Teklifin suresi doldu' : `Teklif ${formatCountdown(left)} daha gecerli`}
            </p>
          </div>
          {timedOut ? (
            <Link href="/flights" className="inline-block rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary">Yeniden ara</Link>
          ) : (
            <form onSubmit={submit} className="grid gap-3 rounded-xl bg-surface-container-lowest p-4 shadow-sm md:grid-cols-2">
              <div>
                <label className="mb-1 block text-xs font-medium text-on-surface-variant">Ad (kimlikteki gibi)</label>
                <input value={firstName} onChange={e => setFirstName(e.target.value)} maxLength={80} required className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-on-surface-variant">Soyad (kimlikteki gibi)</label>
                <input value={lastName} onChange={e => setLastName(e.target.value)} maxLength={80} required className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm" />
              </div>
              {seats.length > 0 && (
                <div className="md:col-span-2">
                  <label className="mb-1 block text-xs font-medium text-on-surface-variant">Koltuk (istege bagli)</label>
                  <select value={seat} onChange={e => setSeat(e.target.value)} className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
                    <option value="">Otomatik ata</option>
                    {seats.map(s => <option key={s.seatNumber} value={s.seatNumber}>{s.seatNumber} · {money(s.price, offer.currency)}</option>)}
                  </select>
                </div>
              )}
              <div className="md:col-span-2">
                <PromoCodeField vertical="UÇUŞ" grossAmount={offer.totalAmount} currency={offer.currency} code={promoCode} onCodeChange={setPromoCode} />
              </div>
              <div className="md:col-span-2">
                <button disabled={busy} className="w-full rounded-lg bg-tertiary px-4 py-2 text-sm font-medium text-on-tertiary disabled:opacity-60">
                  {busy ? 'Siparis olusturuluyor...' : 'Siparis Olustur ve Ode'}
                </button>
                <p className="mt-2 text-xs text-on-surface-variant">Siparisten sonra odemeyi Siparislerim sayfasindan yaparsiniz.</p>
              </div>
            </form>
          )}
        </>
      )}
    </div>
  );
}

export default function FlightOfferPage() {
  return <Suspense fallback={null}><OfferContent /></Suspense>;
}
