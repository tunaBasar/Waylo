'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import { ApiError } from '@/lib/api';
import { PromoCodeField } from '@/components/PromoCodeField';
import { normalizePromoCode, promoErrorMessage } from '@/lib/pricing';
import {
  customerBusApi, busErrorMessage, seatRows, seatBlockedByGender, formatClock, formatMoney,
  GENDER_LABEL, LAYOUT_LABEL,
  type Gender, type SeatMap, type TripOffer,
} from '@/lib/bus';

interface Picked { seatNo: number; name: string; gender: Gender }

export default function BusPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [cities, setCities] = useState<string[]>([]);
  const [fromCity, setFromCity] = useState('');
  const [toCity, setToCity] = useState('');
  const [date, setDate] = useState('');
  const [offers, setOffers] = useState<TripOffer[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');
  const [promoCode, setPromoCode] = useState('');
  const [selected, setSelected] = useState<TripOffer | null>(null);
  const [seatMap, setSeatMap] = useState<SeatMap | null>(null);
  const [picked, setPicked] = useState<Picked[]>([]);
  const [defaultGender, setDefaultGender] = useState<Gender>('MALE');
  const [buying, setBuying] = useState(false);

  useEffect(() => {
    let alive = true;
    customerBusApi.salesStatus()
      .then(res => {
        if (!alive) return;
        setEnabled(res.data.enabled);
        if (!res.data.enabled) return;
        return customerBusApi.terminals().then(t => {
          if (alive) setCities(Array.from(new Set((t.data ?? []).map(x => x.city))).sort((a, b) => a.localeCompare(b, 'tr')));
        });
      })
      .catch(() => { if (alive) { setEnabled(false); setError('Otobus servisine ulasilamadi.'); } });
    return () => { alive = false; };
  }, []);

  const minDate = useMemo(() => new Date().toISOString().slice(0, 10), []);

  async function search(e: FormEvent) {
    e.preventDefault();
    setError(''); setSelected(null); setSeatMap(null); setPicked([]);
    if (!fromCity || !toCity || fromCity === toCity || !date) {
      setError('Kalkis, varis ve tarih secin.');
      return;
    }
    setSearching(true);
    try {
      const res = await customerBusApi.search(fromCity, toCity, date);
      setOffers(res.data ?? []);
    } catch (err) {
      setError(busErrorMessage(err, 'Seferler yuklenemedi.'));
    } finally {
      setSearching(false);
    }
  }

  async function choose(o: TripOffer) {
    setSelected(o); setPicked([]); setError('');
    try {
      const res = await customerBusApi.seats(o.tripId, o.fromSeq, o.toSeq);
      setSeatMap(res.data);
    } catch (err) {
      setError(busErrorMessage(err, 'Koltuk haritasi yuklenemedi.'));
    }
  }

  function toggleSeat(seatNo: number) {
    setError('');
    setPicked(p => {
      if (p.some(x => x.seatNo === seatNo)) return p.filter(x => x.seatNo !== seatNo);
      if (p.length >= 5) { setError('Tek seferde en fazla 5 koltuk secilebilir.'); return p; }
      return [...p, { seatNo, name: '', gender: defaultGender }];
    });
  }

  function setPassenger(seatNo: number, patch: Partial<Picked>) {
    setPicked(p => p.map(x => (x.seatNo === seatNo ? { ...x, ...patch } : x)));
  }

  const conflicts = useMemo(() => {
    if (!seatMap) return new Set<number>();
    const nos = picked.map(p => p.seatNo);
    return new Set(picked.filter(p => seatBlockedByGender(seatMap.layout, seatMap.seats, p.seatNo, p.gender, nos)).map(p => p.seatNo));
  }, [picked, seatMap]);

  const ready = picked.length > 0 && picked.every(p => p.name.trim().length >= 2) && conflicts.size === 0;

  async function buy() {
    if (!selected || !ready) return;
    if (authLoading || !user) { router.push('/login'); return; }
    setBuying(true); setError('');
    try {
      const res = await customerBusApi.order({
        tripId: selected.tripId, fromSeq: selected.fromSeq, toSeq: selected.toSeq,
        passengers: picked.map(p => ({ seatNo: p.seatNo, name: p.name.trim(), gender: p.gender })),
        ...(promoCode.trim() ? { promoCode: normalizePromoCode(promoCode) } : {}),
      });
      router.push(`/orders?created=${encodeURIComponent(res.data.id)}`);
    } catch (err) {
      setError(promoErrorMessage(err instanceof ApiError ? err.code : null) ?? busErrorMessage(err, 'Bilet alinamadi.'));
      if (selected) void choose(selected);
    } finally {
      setBuying(false);
    }
  }

  if (enabled === null) return <p className="text-sm text-on-surface-variant">Yukleniyor</p>;
  if (!enabled) {
    return (
      <div className="text-center py-16 space-y-2">
        <span className="material-symbols-outlined text-outline text-5xl block">directions_bus</span>
        <h1 className="text-lg font-semibold text-on-surface">Otobus bileti yakinda</h1>
        <p className="text-sm text-on-surface-variant">Otobus bileti satisi henuz acik degil.</p>
        {error && <p className="text-sm text-error">{error}</p>}
      </div>
    );
  }

  const total = selected ? picked.length * selected.price : 0;
  return (
    <div className="space-y-space-lg">
      <h1 className="text-xl font-semibold text-on-surface tracking-tight">Otobus bileti</h1>
      <form onSubmit={search} className="flex flex-wrap items-end gap-3">
        <label className="space-y-1"><span className="text-[11px] text-on-surface-variant">Nereden</span>
          <select value={fromCity} onChange={e => setFromCity(e.target.value)} className="block rounded-lg bg-surface-container px-3 py-2 text-sm">
            <option value="">Secin</option>{cities.map(c => <option key={c} value={c}>{c}</option>)}
          </select></label>
        <label className="space-y-1"><span className="text-[11px] text-on-surface-variant">Nereye</span>
          <select value={toCity} onChange={e => setToCity(e.target.value)} className="block rounded-lg bg-surface-container px-3 py-2 text-sm">
            <option value="">Secin</option>{cities.map(c => <option key={c} value={c}>{c}</option>)}
          </select></label>
        <label className="space-y-1"><span className="text-[11px] text-on-surface-variant">Tarih</span>
          <input type="date" min={minDate} value={date} onChange={e => setDate(e.target.value)} className="block rounded-lg bg-surface-container px-3 py-2 text-sm" /></label>
        <button type="submit" disabled={searching} className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary disabled:opacity-50">
          {searching ? 'Araniyor' : 'Sefer ara'}</button>
      </form>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}

      {offers && offers.length === 0 && <p className="text-sm text-on-surface-variant">Bu tarih icin sefer bulunamadi.</p>}
      <div className="space-y-2">
        {offers?.map(o => (
          <div key={`${o.tripId}-${o.fromSeq}-${o.toSeq}`}
            className={`bg-surface-container-lowest rounded-xl shadow-sm p-4 flex flex-wrap items-center gap-4 ${selected?.tripId === o.tripId ? 'ring-2 ring-primary' : ''}`}>
            <div className="flex-1 min-w-[180px]">
              <p className="font-semibold text-on-surface">{o.companyName ?? 'Otobus firmasi'}</p>
              <p className="text-xs text-on-surface-variant">{o.busBrand} {o.busModel}, {LAYOUT_LABEL[o.layout]}</p>
            </div>
            <p className="text-sm text-on-surface">{formatClock(o.departureAt)} - {formatClock(o.arrivalAt)}</p>
            <p className="text-xs text-on-surface-variant">{o.availableSeats} bos koltuk</p>
            <p className="font-semibold text-on-surface">{formatMoney(o.price, o.currency)}</p>
            <button onClick={() => choose(o)} className="rounded-lg bg-secondary px-3 py-2 text-sm font-medium text-on-secondary">Koltuk sec</button>
          </div>
        ))}
      </div>

      {selected && seatMap && (
        <div className="grid gap-6 lg:grid-cols-2">
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-4 space-y-3">
            <div className="flex items-center gap-3 text-xs text-on-surface-variant">
              <span>Yeni secimde cinsiyet:</span>
              {(['MALE', 'FEMALE'] as Gender[]).map(g => (
                <label key={g} className="flex items-center gap-1">
                  <input type="radio" name="g" checked={defaultGender === g} onChange={() => setDefaultGender(g)} />{GENDER_LABEL[g]}
                </label>
              ))}
            </div>
            <div className="space-y-1.5">
              {seatRows(seatMap.layout, seatMap.seats).map((row, i) => (
                <div key={i} className="flex items-center gap-6 justify-center">
                  {row.map((block, k) => (
                    <div key={k} className="flex gap-1.5">
                      {block.map(s => {
                        const isPicked = picked.some(p => p.seatNo === s.seatNo);
                        const color = !s.available
                          ? (s.occupantGender === 'FEMALE' ? 'bg-pink-200 text-pink-900' : 'bg-blue-200 text-blue-900') + ' opacity-60 cursor-not-allowed'
                          : isPicked ? 'bg-primary text-on-primary' : 'bg-surface-container hover:bg-surface-container-high text-on-surface';
                        return (
                          <button key={s.seatNo} disabled={!s.available} onClick={() => toggleSeat(s.seatNo)}
                            aria-label={`Koltuk ${s.seatNo}`} aria-pressed={isPicked}
                            className={`w-9 h-9 rounded-md text-xs font-medium ${color} ${conflicts.has(s.seatNo) ? 'ring-2 ring-error' : ''}`}>
                            {s.seatNo}
                          </button>
                        );
                      })}
                    </div>
                  ))}
                </div>
              ))}
            </div>
            <p className="text-[11px] text-on-surface-variant">Dolu koltuklar: pembe kadin, mavi erkek. Yan yana farkli cinsiyetten tanimadigi yolcu oturamaz.</p>
          </div>

          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-4 space-y-3">
            <h2 className="font-semibold text-on-surface">Yolcular</h2>
            {picked.length === 0 && <p className="text-sm text-on-surface-variant">Haritadan koltuk secin.</p>}
            {picked.map(p => (
              <div key={p.seatNo} className="flex flex-wrap items-center gap-2">
                <span className="w-16 text-sm text-on-surface">Koltuk {p.seatNo}</span>
                <input value={p.name} onChange={e => setPassenger(p.seatNo, { name: e.target.value })} placeholder="Ad soyad" maxLength={150}
                  className="flex-1 min-w-[140px] rounded-lg bg-surface-container px-3 py-2 text-sm" />
                <select value={p.gender} onChange={e => setPassenger(p.seatNo, { gender: e.target.value as Gender })}
                  className="rounded-lg bg-surface-container px-2 py-2 text-sm">
                  {(['MALE', 'FEMALE'] as Gender[]).map(g => <option key={g} value={g}>{GENDER_LABEL[g]}</option>)}
                </select>
                {conflicts.has(p.seatNo) && <span className="text-xs text-error">Yan koltukta farkli cinsiyet var</span>}
              </div>
            ))}
            <PromoCodeField vertical="OTOBÜS" supplierOrganizationId={selected.organizationId} grossAmount={total}
              currency={selected.currency} code={promoCode} onCodeChange={setPromoCode} />
            <div className="flex items-center justify-between pt-2">
              <span className="font-semibold text-on-surface">{formatMoney(total, selected.currency)}</span>
              <button onClick={buy} disabled={!ready || buying}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary disabled:opacity-50">
                {buying ? 'Isleniyor' : 'Satin al'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
