'use client';

import { useState } from 'react';

interface ToggleProps { label: string; desc: string; value: boolean; onChange: (v: boolean) => void }
function Toggle({ label, desc, value, onChange }: ToggleProps) {
  return (
    <div className="flex items-center justify-between py-3">
      <div>
        <p className="text-sm font-medium text-on-surface">{label}</p>
        <p className="text-xs text-on-surface-variant">{desc}</p>
      </div>
      <button onClick={() => onChange(!value)}
        className={`relative w-11 h-6 rounded-full transition-colors ${value ? 'bg-primary' : 'bg-outline/30'}`}>
        <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${value ? 'translate-x-5.5' : 'translate-x-0.5'}`} />
      </button>
    </div>
  );
}

export default function SettingsPage() {
  const [settings, setSettings] = useState({
    emailNotifs: true, pushNotifs: false, smsNotifs: true,
    darkMode: false, compactView: false,
    twoFactor: false,
  });

  const update = (key: string, val: boolean) => setSettings(s => ({ ...s, [key]: val }));

  const sections = [
    {
      title: 'Bildirimler', icon: 'notifications',
      items: [
        { key: 'emailNotifs', label: 'E-posta Bildirimleri', desc: 'Rezervasyon ve odeme bildirimlerini e-posta ile al' },
        { key: 'pushNotifs', label: 'Push Bildirimleri', desc: 'Anlik bildirimler gonder' },
        { key: 'smsNotifs', label: 'SMS Bildirimleri', desc: 'Onemli bildirimleri SMS ile gonder' },
      ],
    },
    {
      title: 'Gorunum', icon: 'palette',
      items: [
        { key: 'darkMode', label: 'Karanlik Mod', desc: 'Koyu tema kullan' },
        { key: 'compactView', label: 'Kompakt Gorunum', desc: 'Daha sikisik bir arayuz tercih et' },
      ],
    },
    {
      title: 'Guvenlik', icon: 'security',
      items: [
        { key: 'twoFactor', label: 'Iki Faktorlu Dogrulama', desc: 'Hesap guvenligini arttir' },
      ],
    },
  ];

  return (
    <div className="space-y-space-md max-w-lg mx-auto">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">Ayarlar</h2>

      {sections.map(section => (
        <div key={section.title} className="bg-surface-container-lowest rounded-2xl shadow-sm p-5">
          <div className="flex items-center gap-2 mb-3">
            <span className="material-symbols-outlined text-primary text-lg">{section.icon}</span>
            <h4 className="text-sm font-semibold text-on-surface">{section.title}</h4>
          </div>
          <div className="divide-y divide-surface-container">
            {section.items.map(item => (
              <Toggle key={item.key} label={item.label} desc={item.desc}
                value={(settings as Record<string, boolean>)[item.key]}
                onChange={v => update(item.key, v)} />
            ))}
          </div>
        </div>
      ))}

      {/* Danger zone */}
      <div className="bg-error-container/30 rounded-2xl p-5">
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-error text-lg">warning</span>
          <h4 className="text-sm font-semibold text-error">Tehlikeli Bolge</h4>
        </div>
        <p className="text-xs text-on-surface-variant mb-3">Hesabinizi silmek geri alinamaz bir islemdir.</p>
        <button className="px-4 py-2 rounded-xl bg-error text-on-error text-xs font-medium shadow-sm active:scale-95 transition-all">
          Hesabi Sil
        </button>
      </div>
    </div>
  );
}
