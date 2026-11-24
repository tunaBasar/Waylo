'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import {
  NOTIFICATION_STATUS, NOTIFICATION_TABS, NOTIFICATION_TONE_CLASS, canRetry, filterPath, nextAttemptText,
  type NotificationStatus, type NotificationTab,
} from '@/lib/notifications';
import type { Notification } from '@/lib/types';

type AdminNotification = Omit<Notification, 'status'> & {
  status: NotificationStatus;
  nextAttemptAt: string | null;
  lastError: string | null;
};

const typeIcons: Record<string, string> = { EMAIL: 'mail', SMS: 'sms', PUSH: 'notifications_active' };


function AdminOnly() {
  return (
    <div className="text-center py-12">
      <span className="material-symbols-outlined text-outline text-5xl mb-3 block">lock</span>
      <p className="text-on-surface-variant text-sm">Bu sayfa yalnizca yoneticiler icindir</p>
    </div>
  );
}

export default function NotificationsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [notifs, setNotifs] = useState<AdminNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [typeFilter, setTypeFilter] = useState<string | null>(null);
  const [tab, setTab] = useState<NotificationTab>('ALL');
  const [retrying, setRetrying] = useState<string | null>(null);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const load = useCallback(async () => {
    try {
      const r = await api.get<AdminNotification[]>(filterPath(tab));
      setNotifs(r.data);
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Bildirimler yuklenemedi');
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => {
    if (!isAdmin) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    void load();
  }, [isAdmin, load]);

  async function retry(id: string) {
    setRetrying(id);
    setMessage(null);
    try {
      await api.post(`/api/notifications/${id}/retry`, {});
      setMessage({ ok: true, text: 'Yeniden deneme baslatildi' });
      await load();
    } catch (e) {
      setMessage({ ok: false, text: e instanceof Error ? e.message : 'Yeniden deneme basarisiz' });
    } finally {
      setRetrying(null);
    }
  }

  const filtered = notifs.filter(n => !typeFilter || n.type === typeFilter);

  if (!isAdmin) return <AdminOnly />;

  return (
    <div className="space-y-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <h2 className="text-xl font-semibold text-on-surface tracking-tight">Bildirimler</h2>
          <span className="bg-primary-fixed text-primary text-xs font-semibold px-2.5 py-0.5 rounded-full">{notifs.length}</span>
        </div>
      </div>

      <p className="text-xs text-on-surface-variant">Başarısız e-posta ve push bildirimleri otomatik olarak 1 dk, 5 dk, 30 dk, 2 sa ve 12 sa sonra yeniden denenir; 5 denemeden sonra &apos;Vazgeçildi&apos; olur.</p>

      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
        {NOTIFICATION_TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-colors ${tab === t.key ? 'bg-primary text-on-primary' : 'bg-surface-container-lowest text-on-surface-variant shadow-sm'}`}>{t.label}</button>
        ))}
      </div>

      {message && <p role="status" className={`text-xs font-medium ${message.ok ? 'text-primary' : 'text-error'}`}>{message.text}</p>}
      {loadError && <p role="alert" className="text-xs font-medium text-error">{loadError}</p>}

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
            const sc = NOTIFICATION_STATUS[n.status] ?? NOTIFICATION_STATUS.PENDING;
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
                  <span className={`text-[10px] font-bold uppercase px-2.5 py-1 rounded-full shrink-0 ${NOTIFICATION_TONE_CLASS[sc.tone]}`}>{sc.label}</span>
                </div>
                <p className="text-xs text-on-surface-variant line-clamp-2 mb-2">{n.body}</p>
                <div className="flex items-center justify-between text-[10px] text-outline">
                  <span>{n.type} · Deneme: {n.retryCount}</span>
                  <span>{n.sentAt ? new Date(n.sentAt).toLocaleDateString('tr-TR') : new Date(n.createdAt).toLocaleDateString('tr-TR')}</span>
                </div>
                {n.status === 'FAILED' && <p className="text-[10px] text-on-surface-variant mt-1">Sonraki deneme: {nextAttemptText(n.nextAttemptAt)}</p>}
                {n.lastError && <p className="text-[10px] font-mono text-error mt-1 break-all">{n.lastError}</p>}
                {canRetry(n.status) && (
                  <button onClick={() => retry(n.id)} disabled={retrying !== null}
                    className="mt-2 px-3 py-1.5 rounded-full text-xs font-medium bg-primary text-on-primary disabled:opacity-50">
                    {retrying === n.id ? 'Deneniyor...' : 'Şimdi yeniden dene'}
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
