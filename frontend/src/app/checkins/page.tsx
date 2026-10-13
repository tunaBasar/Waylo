'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Checkin } from '@/lib/types';

const statusConfig: Record<string, { label: string; class: string; icon: string }> = {
  CHECKED_IN: { label: 'Check-in', class: 'bg-secondary-container text-on-secondary-container', icon: 'check_circle' },
  BOARDING_PASS_ISSUED: { label: 'Binis Karti', class: 'bg-primary-fixed text-primary', icon: 'confirmation_number' },
  BOARDED: { label: 'Binildi', class: 'bg-secondary text-on-secondary', icon: 'flight_takeoff' },
  NOT_CHECKED_IN: { label: 'Bekleniyor', class: 'bg-surface-container text-on-surface-variant', icon: 'schedule' },
  CANCELLED: { label: 'Iptal', class: 'bg-error-container text-on-error-container', icon: 'cancel' },
};

export default function CheckinsPage() {
  const [checkins, setCheckins] = useState<Checkin[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<Checkin[]>('/api/checkins').then(r => { setCheckins(r.data); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Check-in Islemleri</h2>
          <span className="bg-secondary-container text-on-secondary-container text-xs font-semibold px-2.5 py-0.5 rounded-full">{checkins.length}</span>
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-3 gap-space-xs">
        {[
          { label: 'Check-in', value: checkins.filter(c => c.status === 'CHECKED_IN').length, icon: 'how_to_reg', color: 'text-secondary' },
          { label: 'Binildi', value: checkins.filter(c => c.status === 'BOARDED').length, icon: 'flight_takeoff', color: 'text-primary' },
          { label: 'Bekleyen', value: checkins.filter(c => c.status === 'NOT_CHECKED_IN').length, icon: 'schedule', color: 'text-outline' },
        ].map(s => (
          <div key={s.label} className="bg-surface-container-lowest rounded-xl p-3 shadow-sm text-center">
            <span className={`material-symbols-outlined ${s.color} text-xl mb-1 block`}>{s.icon}</span>
            <p className="text-lg font-bold text-on-surface">{s.value}</p>
            <p className="text-[10px] text-on-surface-variant">{s.label}</p>
          </div>
        ))}
      </div>

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-1/2"/></div>)}</div>
      ) : checkins.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">airplane_ticket</span><p className="text-on-surface-variant text-sm">Check-in bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {checkins.map(c => {
            const sc = statusConfig[c.status] || statusConfig.NOT_CHECKED_IN;
            return (
              <div key={c.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className="w-9 h-9 rounded-lg bg-secondary-container flex items-center justify-center">
                      <span className="material-symbols-outlined text-secondary text-lg">{sc.icon}</span>
                    </div>
                    <div>
                      <p className="text-sm font-bold text-on-surface">Ucus: #{c.flightId.slice(0, 8)}</p>
                      <p className="text-[10px] text-on-surface-variant">Booking: #{c.bookingId.slice(0, 8)}</p>
                    </div>
                  </div>
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full ${sc.class}`}>{sc.label}</span>
                </div>
                <div className="flex items-center gap-3 text-xs text-on-surface-variant">
                  {c.seatNumber && (
                    <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[14px]">airline_seat_recline_normal</span> Koltuk: {c.seatNumber}</span>
                  )}
                  {c.checkedInAt && (
                    <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[14px]">schedule</span> {new Date(c.checkedInAt).toLocaleString('tr-TR')}</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
