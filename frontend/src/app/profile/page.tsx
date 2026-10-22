'use client';

import { useAuth } from '@/lib/auth';
import { api } from '@/lib/api';
import { useState, useEffect } from 'react';

type AuthData = Parameters<ReturnType<typeof useAuth>['applyAuthResponse']>[0];

interface ProfileData {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  emailVerified: boolean;
  createdAt: string;
}

export default function ProfilePage() {
  const { updateUser, applyAuthResponse } = useAuth();
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState<string | null>(null);
  const [form, setForm] = useState({ firstName: '', lastName: '' });
  const [formError, setFormError] = useState<string | null>(null);

  // Password change state
  const [pwdForm, setPwdForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [pwdSaving, setPwdSaving] = useState(false);
  const [pwdMsg, setPwdMsg] = useState<string | null>(null);
  const [pwdError, setPwdError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProfile() {
      try {
        const res = await api.get<ProfileData>('/api/me');
        if (res.success && res.data) {
          setProfile(res.data);
        } else {
          setError(res.message || 'Profil yuklenemedi');
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Profil yuklenemedi';
        setError(msg);
      } finally {
        setLoading(false);
      }
    }
    fetchProfile();
  }, []);

  const handleSave = async () => {
    setSaving(true);
    setFormError(null);
    setSaveMsg(null);
    try {
      const firstName = form.firstName.trim();
      const lastName = form.lastName.trim();
      if (!firstName || !lastName) {
        setFormError('Ad ve soyad alanlari zorunludur.');
        return;
      }
      if (firstName.length > 100 || lastName.length > 100) {
        setFormError('Ad ve soyad en fazla 100 karakter olabilir.');
        return;
      }
      const res = await api.patch<ProfileData>('/api/me', { firstName, lastName });
      if (res.success && res.data) {
        updateUser({ firstName, lastName });
        setProfile(res.data);
        setEditing(false);
        setSaveMsg('Profil guncellendi.');
      } else {
        setFormError(res.message || 'Guncellenemedi');
      }
    } catch (err: unknown) {
      setFormError(err instanceof Error ? err.message : 'Guncellenemedi');
    } finally {
      setSaving(false);
    }
  };

  const handlePasswordChange = async () => {
    setPwdSaving(true);
    setPwdError(null);
    setPwdMsg(null);
    try {
      const { currentPassword, newPassword, confirm } = pwdForm;
      if (!currentPassword || !newPassword || !confirm) {
        setPwdError('Tum alanlar zorunludur.');
        return;
      }
      if (newPassword.length < 6) {
        setPwdError('Yeni sifre en az 6 karakter olmalidir.');
        return;
      }
      if (newPassword !== confirm) {
        setPwdError('Yeni sifre ve tekrari eslesmiyor.');
        return;
      }
      if (newPassword === currentPassword) {
        setPwdError('Yeni sifre mevcut sifre ile ayni olamaz.');
        return;
      }
      const res = await api.post<AuthData>('/api/me/password', { currentPassword, newPassword });
      if (res.success && res.data) {
        // Server revoked every session; keep this one alive with the fresh pair.
        applyAuthResponse(res.data);
        setPwdForm({ currentPassword: '', newPassword: '', confirm: '' });
        setPwdMsg('Sifre guncellendi. Diger oturumlar kapatildi.');
      } else {
        setPwdError(res.message || 'Sifre guncellenemedi');
      }
    } catch (err: unknown) {
      setPwdError(err instanceof Error ? err.message : 'Sifre guncellenemedi');
    } finally {
      setPwdSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-space-md max-w-lg mx-auto">
        <h2 className="text-xl font-semibold text-on-surface tracking-tight">Profil</h2>
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 flex items-center justify-center min-h-[200px]">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-space-md max-w-lg mx-auto">
        <h2 className="text-xl font-semibold text-on-surface tracking-tight">Profil</h2>
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 text-center">
          <span className="material-symbols-outlined text-[48px] text-error">error</span>
          <p className="text-sm text-on-surface-variant mt-2">{error}</p>
        </div>
      </div>
    );
  }

  if (!profile) {
    return null;
  }

  const initials = `${profile.firstName?.[0] || ''}${profile.lastName?.[0] || ''}`.toUpperCase() || 'U';
  const fullName = `${profile.firstName} ${profile.lastName}`.trim();
  const joinDate = profile.createdAt
    ? new Date(profile.createdAt).toLocaleDateString('tr-TR', { year: 'numeric', month: 'long' })
    : '-';

  return (
    <div className="space-y-space-md max-w-lg mx-auto">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Profil</h2>

      {/* Avatar card */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-6 flex flex-col items-center">
        <div className="w-20 h-20 rounded-full bg-primary flex items-center justify-center mb-3">
          <span className="text-on-primary text-2xl font-bold">{initials}</span>
        </div>
        <h3 className="text-lg font-bold text-on-surface">{fullName}</h3>
        <p className="text-sm text-on-surface-variant">{profile.email}</p>
        <div className="mt-2 flex items-center gap-2">
          <span className="text-[10px] font-bold uppercase px-3 py-1 rounded-full bg-primary-fixed text-primary">{profile.role || '-'}</span>
          <span className={`text-[10px] font-bold uppercase px-3 py-1 rounded-full ${profile.emailVerified ? 'bg-success text-on-success' : 'bg-error text-on-error'}`}>
            {profile.emailVerified ? 'Dogrulandi' : 'Dogrulanmadi'}
          </span>
        </div>
      </div>

      {/* Info card */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-5">
        <div className="flex items-center justify-between mb-4">
          <h4 className="text-sm font-semibold text-on-surface">Hesap Bilgileri</h4>
          <button onClick={() => {
            if (!editing) setForm({ firstName: profile.firstName, lastName: profile.lastName });
            setEditing(!editing); setSaveMsg(null); setFormError(null);
          }}
            className="text-xs text-primary font-medium flex items-center gap-1">
            <span className="material-symbols-outlined text-[16px]">{editing ? 'close' : 'edit'}</span>
            {editing ? 'Iptal' : 'Duzenle'}
          </button>
        </div>

        {editing ? (
          <div className="space-y-3">
            <div className="space-y-1">
              <label className="text-xs text-on-surface-variant">Ad</label>
              <input value={form.firstName} onChange={e => setForm(f => ({...f, firstName: e.target.value}))}
                maxLength={100}
                className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-on-surface-variant">Soyad</label>
              <input value={form.lastName} onChange={e => setForm(f => ({...f, lastName: e.target.value}))}
                maxLength={100}
                className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-on-surface-variant">E-posta</label>
              <input value={profile.email} readOnly
                className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface-variant text-sm cursor-not-allowed" />
            </div>
            {formError && <p className="text-xs text-error">{formError}</p>}
            {saveMsg && <p className="text-xs text-success">{saveMsg}</p>}
            <button onClick={handleSave} disabled={saving}
              className="w-full py-2 rounded-xl bg-primary text-on-primary text-sm font-medium shadow-sm active:scale-[0.98] transition-all disabled:opacity-50">
              {saving ? 'Kaydediliyor...' : 'Kaydet'}
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            {[
              { icon: 'person', label: 'Ad Soyad', value: fullName },
              { icon: 'mail', label: 'E-posta', value: profile.email },
              { icon: 'shield', label: 'Rol', value: profile.role || '-' },
              { icon: 'calendar_today', label: 'Katilim', value: joinDate },
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

      {/* Password change card */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-5">
        <h4 className="text-sm font-semibold text-on-surface mb-4">Sifre Degistir</h4>
        <div className="space-y-3">
          <div className="space-y-1">
            <label className="text-xs text-on-surface-variant">Mevcut Sifre</label>
            <input type="password" autoComplete="current-password" value={pwdForm.currentPassword}
              onChange={e => setPwdForm(f => ({...f, currentPassword: e.target.value}))}
              className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div className="space-y-1">
            <label className="text-xs text-on-surface-variant">Yeni Sifre</label>
            <input type="password" autoComplete="new-password" value={pwdForm.newPassword}
              onChange={e => setPwdForm(f => ({...f, newPassword: e.target.value}))}
              className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div className="space-y-1">
            <label className="text-xs text-on-surface-variant">Yeni Sifre (Tekrar)</label>
            <input type="password" autoComplete="new-password" value={pwdForm.confirm}
              onChange={e => setPwdForm(f => ({...f, confirm: e.target.value}))}
              className="w-full px-3 py-2 rounded-xl bg-surface-container-low text-on-surface text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          {pwdError && <p className="text-xs text-error">{pwdError}</p>}
          {pwdMsg && <p className="text-xs text-success">{pwdMsg}</p>}
          <button onClick={handlePasswordChange} disabled={pwdSaving}
            className="w-full py-2 rounded-xl bg-primary text-on-primary text-sm font-medium shadow-sm active:scale-[0.98] transition-all disabled:opacity-50">
            {pwdSaving ? 'Guncelleniyor...' : 'Sifre Guncelle'}
          </button>
        </div>
      </div>
    </div>
  );
}
