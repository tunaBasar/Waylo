'use client';

import { useEffect, useState } from 'react';
import { useToast } from '@/components/Toast';
import { SEAT_TYPES, adminApi, type SeatType } from '@/lib/admin';
import type { Flight } from '@/lib/types';
import { Button, Card, Field, PageHeader, inputClass } from '@/components/partner/ui';

export default function AdminSeatsPage() {
  const toast = useToast();
  const [flights, setFlights] = useState<Flight[]>([]);
  const [flightNumber, setFlightNumber] = useState('');
  const [seatType, setSeatType] = useState<SeatType>('ECONOMY');
  const [price, setPrice] = useState('');
  const [fromRow, setFromRow] = useState('1');
  const [toRow, setToRow] = useState('10');
  const [letters, setLetters] = useState('ABCDEF');
  const [saving, setSaving] = useState(false);
  const [available, setAvailable] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    adminApi.flights().then(r => { if (!cancelled) setFlights(r.data ?? []); }).catch(() => { if (!cancelled) toast.error('Ucuslar yuklenemedi'); });
    return () => { cancelled = true; };
  }, [toast]);

  const rows = Number(toRow) - Number(fromRow) + 1;
  const cleanLetters = letters.toUpperCase().replace(/[^A-Z]/g, '');
  const valid = !!flightNumber && Number(price) >= 0.01 && Number(fromRow) >= 1 && Number(toRow) <= 999 && rows >= 1 && cleanLetters.length > 0;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || saving) return;
    setSaving(true);
    try {
      const res = await adminApi.createSeatsBulk({ flightNumber, seatType, price: Number(price), fromRow: Number(fromRow), toRow: Number(toRow), letters: cleanLetters });
      toast.success(`${res.data.created} koltuk olusturuldu`);
      setAvailable((await adminApi.availableSeatCount(flightNumber)).data);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Koltuklar olusturulamadi');
    } finally { setSaving(false); }
  }

  return (
    <div className="space-y-space-md">
      <PageHeader title="Koltuklar" subtitle="Bir ucus icin toplu koltuk olusturun." />
      <Card className="p-4">
        <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Ucus">
            <select className={inputClass} value={flightNumber} onChange={e => { setFlightNumber(e.target.value); setAvailable(null); }}>
              <option value="">Ucus secin</option>
              {flights.map(f => <option key={f.id} value={f.flightNumber}>{f.flightNumber} {f.departureAirportCode}-{f.arrivalAirportCode}</option>)}
            </select>
          </Field>
          <Field label="Koltuk tipi">
            <select className={inputClass} value={seatType} onChange={e => setSeatType(e.target.value as SeatType)}>
              {SEAT_TYPES.map(t => <option key={t}>{t}</option>)}
            </select>
          </Field>
          <Field label="Fiyat"><input type="number" min={0.01} step="0.01" className={inputClass} value={price} onChange={e => setPrice(e.target.value)} /></Field>
          <Field label="Baslangic sirasi"><input type="number" min={1} max={999} className={inputClass} value={fromRow} onChange={e => setFromRow(e.target.value)} /></Field>
          <Field label="Bitis sirasi" error={rows < 1 ? 'Bitis, baslangictan kucuk olamaz' : null}>
            <input type="number" min={1} max={999} className={inputClass} value={toRow} onChange={e => setToRow(e.target.value)} />
          </Field>
          <Field label="Harfler" hint={valid ? `${rows * cleanLetters.length} koltuk olusturulacak` : undefined}>
            <input className={inputClass} value={letters} onChange={e => setLetters(e.target.value)} />
          </Field>
          <div className="sm:col-span-2 lg:col-span-3">
            <Button type="submit" icon="add" disabled={!valid || saving}>{saving ? 'Olusturuluyor' : 'Koltuklari olustur'}</Button>
          </div>
        </form>
      </Card>
      {available !== null && <p className="text-sm text-on-surface-variant">{flightNumber} icin musait koltuk sayisi: {available}</p>}
    </div>
  );
}
