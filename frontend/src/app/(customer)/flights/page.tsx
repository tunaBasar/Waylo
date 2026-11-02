'use client';

import { FormEvent, Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { api, ApiError } from '@/lib/api';
import type { Airport } from '@/lib/types';
import { CABIN_LABEL, flightErrorMessage, searchFlightOffers } from '@/lib/flight';
import type { FlightOffer } from '@/lib/flight';

const time = (iso: string) => new Date(iso).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
const money = (v: number, currency: string) => new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(v);

function FlightsContent() {
  const params = useSearchParams();
  const router = useRouter();
  const [airports, setAirports] = useState<Airport[]>([]);
  const [from, setFrom] = useState(params.get('from') ?? '');
  const [to, setTo] = useState(params.get('to') ?? '');
  const [date, setDate] = useState(params.get('date') ?? '');
  const [offers, setOffers] = useState<FlightOffer[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get<Airport[]>('/api/airports').then(res => setAirports(res.data)).catch(() => setError('Havalimanlari yuklenemedi.'));
  }, []);

  async function run(f: string, t: string, d: string) {
    setLoading(true);
    setError('');
    try {
      const res = await searchFlightOffers({ from: f, to: t, date: d });
      setOffers(res.data);
    } catch (cause) {
      setOffers(null);
      setError(flightErrorMessage((cause as ApiError).code, 'Ucus aramasi yapilamadi.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const f = params.get('from'), t = params.get('to'), d = params.get('date');
    if (f && t && d) void Promise.resolve().then(() => run(f, t, d));
  }, [params]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!from || !to || !date) { setError('Kalkis, varis ve tarih secin.'); return; }
    if (from === to) { setError('Kalkis ve varis ayni olamaz.'); return; }
    router.push(`/flights?${new URLSearchParams({ from, to, date })}`);
  }

  const options = airports.map(a => <option key={a.iataCode} value={a.iataCode}>{a.city} ({a.iataCode}) {a.name}</option>);

  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Ucus Ara</h2>
      <form onSubmit={submit} className="grid gap-3 rounded-xl bg-surface-container-lowest p-4 shadow-sm md:grid-cols-4">
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Nereden</label>
          <select value={from} onChange={e => setFrom(e.target.value)} className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
            <option value="">Secin</option>{options}
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Nereye</label>
          <select value={to} onChange={e => setTo(e.target.value)} className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm">
            <option value="">Secin</option>{options}
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-on-surface-variant">Tarih</label>
          <input type="date" value={date} onChange={e => setDate(e.target.value)} min={new Date().toISOString().slice(0, 10)}
            className="w-full rounded-lg bg-surface-container px-3 py-2 text-sm" />
        </div>
        <div className="flex items-end">
          <button className="w-full rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-container">Ucus Ara</button>
        </div>
      </form>
      <p className="text-xs text-on-surface-variant">1 yolcu icin arama yapilir.</p>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
      {loading ? (
        <p className="py-12 text-center text-sm text-on-surface-variant">Teklifler araniyor...</p>
      ) : offers && offers.length === 0 ? (
        <div className="py-12 text-center">
          <span className="material-symbols-outlined text-outline text-5xl mb-3 block">flight_takeoff</span>
          <p className="text-sm text-on-surface-variant">Bu tarihte uygun ucus bulunamadi</p>
        </div>
      ) : (
        <div className="space-y-space-sm">
          {(offers ?? []).map(o => (
            <div key={o.offerId} className="rounded-xl bg-surface-container-lowest p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-lg font-bold text-on-surface">{o.flightNumber}</span>
                  <span className="ml-1.5 text-xs text-on-surface-variant">{o.airline} · {CABIN_LABEL[o.cabin] ?? o.cabin}</span>
                </div>
                <p className="text-lg font-bold text-primary">{money(o.totalAmount, o.currency)}</p>
              </div>
              <div className="mt-3 flex items-center justify-between">
                <div><p className="text-xl font-bold text-on-surface">{time(o.departureTime)}</p><p className="text-sm font-bold text-on-surface">{o.departureAirport}</p></div>
                <div className="mx-4 h-px flex-1 bg-primary-fixed" />
                <div className="text-right"><p className="text-xl font-bold text-on-surface">{time(o.arrivalTime)}</p><p className="text-sm font-bold text-on-surface">{o.arrivalAirport}</p></div>
              </div>
              <div className="mt-3 flex items-center justify-between">
                <p className="text-xs text-on-surface-variant">{o.availableSeats} koltuk kaldi</p>
                <Link href={`/flights/offer?id=${encodeURIComponent(o.offerId)}`} className="rounded-lg bg-tertiary px-4 py-2 text-sm font-medium text-on-tertiary">Sec</Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function FlightsPage() {
  return <Suspense fallback={null}><FlightsContent /></Suspense>;
}
