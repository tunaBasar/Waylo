'use client';

import { useAuth } from '@/lib/auth';
import { useState } from 'react';

export default function ProfilePage() {
  const { user } = useAuth();
  const [editing, setEditing] = useState(false);
  const fullName = `${user?.firstName || ''} ${user?.lastName || ''}`.trim();
  const [form, setForm] = useState({ name: fullName, email: user?.email || '' });

  const initials = `${user?.firstName?.[0] || ''}${user?.lastName?.[0] || ''}`.toUpperCase() || 'U';

  return (
    <div className="space-y-space-md max-w-lg mx-auto">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Profil</h2>

      {/* Avatar card */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 flex flex-col items-center">
        <div className="w-20 h-20 rounded-full bg-primary flex items-center justify-center mb-3">
          <span className="text-on-primary text-2xl font-bold">{initials}</span>
        </div>
        <h3 className="text-lg font-bold text-on-surface">{fullName}</h3>
        <p className="text-sm text-on-surface-variant">{user?.email}</p>
        <span className="mt-2 text-[10px] font-bold uppercase px-3 py-1 rounded-full bg-primary-fixed text-primary">{user?.role || 'Admin'}</span>
      </div>

      {/* Info card */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-5">
        <div className="flex items-center justify-between mb-4">
          <h4 className="text-sm font-semibold text-on-surface">Hesap Bilgileri</h4>
          <button onClick={() => setEditing(!editing)} className="text-xs text-primary font-medium flex items-center gap-1">
            <span className="material-symbols-outlined text-[16px]">{editing ? 'close' : 'edit'}</span>
            {editing ? 'Iptal' : 'Duzenle'}
          </button>
        </div>

        {editing ? (
          <div className="space-y-3">
            <div className="space-y-1">
              <label className="text-xs text-on-surface-variant">Ad Soyad</label>
              <input value={form.name} onChange={e => setForm(f => ({...f, name: e.target.value}))}
                className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-on-surface-variant">E-posta</label>
              <input value={form.email} onChange={e => setForm(f => ({...f, email: e.target.value}))}
                className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <button onClick={() => setEditing(false)}
              className="w-full py-2 rounded-xl bg-primary text-on-primary text-sm font-medium shadow-sm active:scale-[0.98] transition-all">
              Kaydet
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            {[
              { icon: 'person', label: 'Ad Soyad', value: fullName },
              { icon: 'mail', label: 'E-posta', value: user?.email },
              { icon: 'shield', label: 'Rol', value: user?.role || 'Admin' },
              { icon: 'calendar_today', label: 'Katilim', value: 'Ekim 2024' },
            ].map(item => (
              <div key={item.label} className="flex items-center gap-3 py-2">
                <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center">
                  <span className="material-symbols-outlined text-on-surface-variant text-[16px]">{item.icon}</span>
                </div>
                <div>
                  <p className="text-[10px] text-on-surface-variant">{item.label}</p>
                  <p className="text-sm text-on-surface font-medium">{item.value}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
