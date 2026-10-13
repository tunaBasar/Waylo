'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Notification } from '@/lib/types';

const statusConfig: Record<string, { label: string; class: string }> = {
  SENT: { label: 'Gonderildi', class: 'bg-secondary-container text-on-secondary-container' },
  PENDING: { label: 'Beklemede', class: 'bg-primary-fixed text-primary' },
  FAILED: { label: 'Basarisiz', class: 'bg-error-container text-on-error-container' },
  RETRYING: { label: 'Tekrar', class: 'bg-tertiary-fixed text-on-tertiary-fixed-variant' },
};
const typeIcons: Record<string, string> = { EMAIL: 'mail', SMS: 'sms', PUSH: 'notifications_active' };

export default function NotificationsPage() {
  const [notifs, setNotifs] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState<string | null>(null);

  useEffect(() => {
    api.get<Notification[]>('/api/notifications').then(r => { setNotifs(r.data); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const filtered = notifs.filter(n => !typeFilter || n.type === typeFilter);

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Bildirimler</h2>
          <span className="bg-primary-fixed text-primary text-xs font-semibold px-2.5 py-0.5 rounded-full">{notifs.length}</span>
        </div>
      </div>

      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
        <button onClick={() => setTypeFilter(null)} className={`px-3 py-1.5 rounded-full text-xs font-medium shrink-0 transition-colors ${!typeFilter ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest text-on-surface-variant shadow-sm'}`}>Tumu</button>
        {['EMAIL', 'SMS', 'PUSH'].map(t => (
          <button key={t} onClick={() => setTypeFilter(typeFilter === t ? null : t)}
            className={`px-3 py-1.5 rounded-full text-xs font-medium shrink-0 flex items-center gap-1 transition-colors ${typeFilter === t ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest text-on-surface-variant shadow-sm'}`}>
            <span className="material-symbols-outlined text-[14px]">{typeIcons[t]}</span> {t}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="bg-surface-container-lowest rounded-xl shadow-sm p-4 animate-pulse"><div className="h-5 bg-surface-container rounded w-1/3 mb-2"/><div className="h-4 bg-surface-container rounded w-2/3"/></div>)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12"><span className="material-symbols-outlined text-outline text-5xl mb-3 block">notifications_off</span><p className="text-on-surface-variant text-sm">Bildirim bulunamadi</p></div>
      ) : (
        <div className="space-y-space-sm">
          {filtered.map(n => {
            const sc = statusConfig[n.status] || statusConfig.PENDING;
            return (
              <div key={n.id} className="bg-surface-container-lowest rounded-xl shadow-sm p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className="w-9 h-9 rounded-lg bg-primary-fixed flex items-center justify-center">
                      <span className="material-symbols-outlined text-primary text-lg">{typeIcons[n.type] || 'mail'}</span>
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-bold text-on-surface truncate">{n.subject || 'Bildirim'}</p>
                      <p className="text-[10px] text-on-surface-variant">{n.recipient}</p>
                    </div>
                  </div>
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full shrink-0 ${sc.class}`}>{sc.label}</span>
                </div>
                <p className="text-xs text-on-surface-variant line-clamp-2 mb-2">{n.body}</p>
                <div className="flex items-center justify-between text-[10px] text-outline">
                  <span>{n.type} · Deneme: {n.retryCount}</span>
                  <span>{n.sentAt ? new Date(n.sentAt).toLocaleDateString('tr-TR') : new Date(n.createdAt).toLocaleDateString('tr-TR')}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
