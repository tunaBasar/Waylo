'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { PartnerProvider, usePartner } from '@/lib/partner-context';
import { cn } from '@/lib/utils';
import { inputClass } from '@/components/partner/ui';

const tabs = [
  { href: '/partner', label: 'Isletmeler', icon: 'business' },
  { href: '/partner/room-types', label: 'Oda tipleri', icon: 'bed' },
  { href: '/partner/rate-plans', label: 'Fiyat planlari', icon: 'sell' },
  { href: '/partner/calendar', label: 'Takvim', icon: 'calendar_month' },
  { href: '/partner/orders', label: 'Siparisler', icon: 'receipt_long' },
];

function WorkspaceBar() {
  const { memberships, organizationId, hotels, hotelId, selectOrganization, selectHotel, loading } = usePartner();
  if (loading || memberships.length === 0) return null;
  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="space-y-1 min-w-[200px]">
        <span className="text-[11px] font-medium text-on-surface-variant">Isletme</span>
        <select className={inputClass} value={organizationId ?? ''} onChange={e => selectOrganization(e.target.value)}>
          {memberships.map(m => (
            <option key={m.organizationId} value={m.organizationId}>{m.organizationName}</option>
          ))}
        </select>
      </label>
      <label className="space-y-1 min-w-[200px]">
        <span className="text-[11px] font-medium text-on-surface-variant">Otel</span>
        <select className={inputClass} value={hotelId ?? ''} disabled={hotels.length === 0}
          onChange={e => selectHotel(e.target.value)}>
          {hotels.length === 0 && <option value="">Otel yok</option>}
          {hotels.map(h => <option key={h.id} value={h.id}>{h.name}</option>)}
        </select>
      </label>
    </div>
  );
}

function PartnerChrome({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  return (
    <div className="space-y-space-md">
      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-primary" style={{ fontVariationSettings: "'FILL' 1" }}>storefront</span>
          <h1 className="text-sm font-semibold uppercase tracking-wider text-on-surface-variant">Partner Paneli</h1>
        </div>
        <WorkspaceBar />
        <nav className="flex gap-1 overflow-x-auto no-scrollbar border-b border-outline-variant">
          {tabs.map(t => {
            const active = t.href === '/partner' ? pathname === '/partner' : pathname.startsWith(t.href);
            return (
              <Link key={t.href} href={t.href}
                className={cn(
                  'flex items-center gap-1.5 px-3 py-2 text-sm whitespace-nowrap border-b-2 -mb-px transition-colors',
                  active ? 'border-primary text-primary font-semibold' : 'border-transparent text-on-surface-variant hover:text-on-surface',
                )}>
                <span className="material-symbols-outlined text-[18px]">{t.icon}</span>
                {t.label}
              </Link>
            );
          })}
        </nav>
      </div>
      {children}
    </div>
  );
}

export default function PartnerLayout({ children }: { children: React.ReactNode }) {
  return (
    <PartnerProvider>
      <PartnerChrome>{children}</PartnerChrome>
    </PartnerProvider>
  );
}
