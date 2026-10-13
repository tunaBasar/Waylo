'use client';

import { useState } from 'react';
import { Globe, Bell, Moon, Shield, Database, Save } from 'lucide-react';

interface Settings {
  language: string;
  currency: string;
  timezone: string;
  emailNotifications: boolean;
  pushNotifications: boolean;
  smsNotifications: boolean;
  bookingAlerts: boolean;
  marketingEmails: boolean;
  darkMode: boolean;
  compactView: boolean;
  twoFactorAuth: boolean;
  sessionTimeout: string;
}

export default function SettingsPage() {
  const [settings, setSettings] = useState<Settings>({
    language: 'tr',
    currency: 'TRY',
    timezone: 'Europe/Istanbul',
    emailNotifications: true,
    pushNotifications: true,
    smsNotifications: false,
    bookingAlerts: true,
    marketingEmails: false,
    darkMode: false,
    compactView: false,
    twoFactorAuth: false,
    sessionTimeout: '30',
  });
  const [saved, setSaved] = useState(false);

  function toggle(key: keyof Settings) {
    setSettings(prev => ({ ...prev, [key]: !prev[key] }));
    setSaved(false);
  }

  function update(key: keyof Settings, value: string) {
    setSettings(prev => ({ ...prev, [key]: value }));
    setSaved(false);
  }

  function handleSave() {
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Ayarlar</h1>
        <button onClick={handleSave}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
          <Save size={16} /> Kaydet
        </button>
      </div>

      {saved && (
        <div className="mb-6 p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-emerald-700 text-sm">
          Ayarlar basariyla kaydedildi!
        </div>
      )}

      <div className="space-y-6">
        {/* General */}
        <section className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center gap-2 mb-4">
            <Globe size={20} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Genel</h2>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Dil</label>
              <select value={settings.language} onChange={e => update('language', e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="tr">Turkce</option>
                <option value="en">English</option>
                <option value="de">Deutsch</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Para Birimi</label>
              <select value={settings.currency} onChange={e => update('currency', e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="TRY">TRY (₺)</option>
                <option value="EUR">EUR (€)</option>
                <option value="USD">USD ($)</option>
                <option value="GBP">GBP (£)</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Saat Dilimi</label>
              <select value={settings.timezone} onChange={e => update('timezone', e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="Europe/Istanbul">Istanbul (UTC+3)</option>
                <option value="Europe/London">Londra (UTC+0)</option>
                <option value="Europe/Berlin">Berlin (UTC+1)</option>
                <option value="America/New_York">New York (UTC-5)</option>
              </select>
            </div>
          </div>
        </section>

        {/* Notifications */}
        <section className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center gap-2 mb-4">
            <Bell size={20} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Bildirimler</h2>
          </div>
          <div className="space-y-4">
            {[
              { key: 'emailNotifications' as const, label: 'E-posta Bildirimleri', desc: 'Onemli guncellemeleri e-posta ile alin' },
              { key: 'pushNotifications' as const, label: 'Push Bildirimleri', desc: 'Tarayici push bildirimleri' },
              { key: 'smsNotifications' as const, label: 'SMS Bildirimleri', desc: 'Telefona SMS ile bildirim' },
              { key: 'bookingAlerts' as const, label: 'Rezervasyon Uyarilari', desc: 'Rezervasyon durumu degisikliklerinde bildirim' },
              { key: 'marketingEmails' as const, label: 'Pazarlama E-postalari', desc: 'Kampanya ve firsatlardan haberdar olun' },
            ].map(({ key, label, desc }) => (
              <div key={key} className="flex items-center justify-between py-2">
                <div>
                  <p className="text-sm font-medium text-gray-900">{label}</p>
                  <p className="text-xs text-gray-500">{desc}</p>
                </div>
                <button onClick={() => toggle(key)}
                  className={`relative w-11 h-6 rounded-full transition-colors ${
                    settings[key] ? 'bg-blue-600' : 'bg-gray-200'
                  }`}>
                  <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${
                    settings[key] ? 'translate-x-5' : 'translate-x-0'
                  }`} />
                </button>
              </div>
            ))}
          </div>
        </section>

        {/* Appearance */}
        <section className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center gap-2 mb-4">
            <Moon size={20} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Gorunum</h2>
          </div>
          <div className="space-y-4">
            {[
              { key: 'darkMode' as const, label: 'Karanlik Mod', desc: 'Koyu tema kullan (yakinda)' },
              { key: 'compactView' as const, label: 'Kompakt Gorunum', desc: 'Daha yogun tablo ve liste gorunumu' },
            ].map(({ key, label, desc }) => (
              <div key={key} className="flex items-center justify-between py-2">
                <div>
                  <p className="text-sm font-medium text-gray-900">{label}</p>
                  <p className="text-xs text-gray-500">{desc}</p>
                </div>
                <button onClick={() => toggle(key)}
                  className={`relative w-11 h-6 rounded-full transition-colors ${
                    settings[key] ? 'bg-blue-600' : 'bg-gray-200'
                  }`}>
                  <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${
                    settings[key] ? 'translate-x-5' : 'translate-x-0'
                  }`} />
                </button>
              </div>
            ))}
          </div>
        </section>

        {/* Security */}
        <section className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center gap-2 mb-4">
            <Shield size={20} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Guvenlik</h2>
          </div>
          <div className="space-y-4">
            <div className="flex items-center justify-between py-2">
              <div>
                <p className="text-sm font-medium text-gray-900">Iki Faktorlu Dogrulama</p>
                <p className="text-xs text-gray-500">Hesabiniza ekstra guvenlik katmani ekleyin</p>
              </div>
              <button onClick={() => toggle('twoFactorAuth')}
                className={`relative w-11 h-6 rounded-full transition-colors ${
                  settings.twoFactorAuth ? 'bg-blue-600' : 'bg-gray-200'
                }`}>
                <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${
                  settings.twoFactorAuth ? 'translate-x-5' : 'translate-x-0'
                }`} />
              </button>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Oturum Zaman Asimi</label>
              <select value={settings.sessionTimeout} onChange={e => update('sessionTimeout', e.target.value)}
                className="w-full max-w-xs px-3 py-2.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="15">15 dakika</option>
                <option value="30">30 dakika</option>
                <option value="60">1 saat</option>
                <option value="120">2 saat</option>
                <option value="480">8 saat</option>
              </select>
            </div>
          </div>
        </section>

        {/* Data */}
        <section className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center gap-2 mb-4">
            <Database size={20} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Veri Yonetimi</h2>
          </div>
          <div className="space-y-3">
            <button className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              Verilerimi Indir
            </button>
            <p className="text-xs text-gray-500">Tum kisisel verilerinizi JSON formatinda indirin</p>
          </div>
        </section>
      </div>
    </div>
  );
}
