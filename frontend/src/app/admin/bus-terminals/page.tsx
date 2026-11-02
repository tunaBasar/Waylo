'use client';

/** Admin: bus terminals (duraklar) shared by all companies. */
import { useEffect, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { adminBusApi, type Terminal } from '@/lib/bus';

export default function AdminBusTerminalsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [terminals, setTerminals] = useState<Terminal[]>([]);
  const [city, setCity] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isAdmin) return;
    adminBusApi.terminals().then(res => setTerminals(res.data ?? [])).catch(() => setError('Duraklar yuklenemedi.'));
  }, [isAdmin]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    if (!city.trim() || !name.trim() || saving) return;
    setSaving(true); setError('');
    try {
      const res = await adminBusApi.createTerminal(city.trim(), name.trim());
      setTerminals(t => [...t, res.data].sort((a, b) => (a.city + a.name).localeCompare(b.city + b.name, 'tr')));
      setCity(''); setName('');
    } catch (err) { setError(err instanceof Error ? err.message : 'Eklenemedi.'); }
    finally { setSaving(false); }
  }

  if (!isAdmin) return <p className="text-center py-12 text-on-surface-variant text-sm">Bu sayfa yalnizca yoneticiler icindir</p>;
  return (
    <div className="space-y-space-md">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Otobus Duraklari</h2>
      <form onSubmit={add} className="flex flex-wrap gap-2 items-end">
        <input value={city} onChange={e => setCity(e.target.value)} placeholder="Sehir" maxLength={100}
          className="rounded-lg bg-surface-container px-3 py-2 text-sm" />
        <input value={name} onChange={e => setName(e.target.value)} placeholder="Durak adi (orn. Esenler Otogari)" maxLength={150}
          className="rounded-lg bg-surface-container px-3 py-2 text-sm w-72" />
        <button type="submit" disabled={saving || !city.trim() || !name.trim()}
          className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-on-primary disabled:opacity-50">Ekle</button>
      </form>
      {error && <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{error}</p>}
      <ul className="space-y-1">
        {terminals.map(t => (
          <li key={t.id} className="bg-surface-container-lowest rounded-lg px-3 py-2 text-sm text-on-surface">
            {t.city} - {t.name}{!t.active && <span className="text-outline"> (pasif)</span>}
          </li>
        ))}
        {terminals.length === 0 && <li className="text-sm text-on-surface-variant">Henuz durak yok.</li>}
      </ul>
    </div>
  );
}
