'use client';

import Link from 'next/link';
import { useEffect, useRef } from 'react';
import { usePathname } from 'next/navigation';
import { PartnerProvider, usePartner } from '@/lib/partner-context';
import { cn } from '@/lib/utils';
import { inputClass } from '@/components/partner/ui';

const UNSAVED_LEAVE_MESSAGE =
  'Kaydedilmemis degisiklikler kaybolacak. Sayfadan ayrilmak istiyor musunuz?';

const tabs = [
  { href: '/partner', label: 'Isletmeler', icon: 'business' },
  { href: '/partner/room-types', label: 'Oda tipleri', icon: 'bed' },
  { href: '/partner/rate-plans', label: 'Fiyat planlari', icon: 'sell' },
  { href: '/partner/calendar', label: 'Takvim', icon: 'calendar_month' },
  { href: '/partner/orders', label: 'Siparisler', icon: 'receipt_long' },
];

function WorkspaceBar() {
  const { memberships, organizationId, hotels, hotelId, selectOrganization, selectHotel, loading, error, reload, hasUnsavedChanges } = usePartner();
  if (error) {
    return (
      <div className="flex flex-wrap items-center gap-3 rounded-xl bg-error-container text-on-error-container px-4 py-3 text-sm">
        <span className="material-symbols-outlined text-[20px]">error</span>
        <span className="flex-1 min-w-0">Isletme bilgileri yuklenemedi: {error}</span>
        <button onClick={() => void reload()} className="font-semibold underline">Tekrar dene</button>
      </div>
    );
  }
  if (loading || memberships.length === 0) return null;
  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="space-y-1 min-w-[200px]">
        <span className="text-[11px] font-medium text-on-surface-variant">Isletme</span>
        <select className={inputClass} value={organizationId ?? ''} disabled={hasUnsavedChanges}
          onChange={e => selectOrganization(e.target.value)}>
          {memberships.map(m => (
            <option key={m.organizationId} value={m.organizationId}>{m.organizationName}</option>
          ))}
        </select>
      </label>
      <label className="space-y-1 min-w-[200px]">
        <span className="text-[11px] font-medium text-on-surface-variant">Otel</span>
        <select className={inputClass} value={hotelId ?? ''} disabled={hotels.length === 0 || hasUnsavedChanges}
          title={hasUnsavedChanges ? 'Once degisiklikleri kaydedin veya geri alin' : undefined}
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
  const { hotelId, hasUnsavedChanges } = usePartner();
  const leaving = useRef(false);

  // ---- link-click guard (existing) ----
  useEffect(() => {
    if (!hasUnsavedChanges) return;
    const guard = (event: MouseEvent) => {
      if (!(event.target instanceof Element)) return;
      const link = event.target.closest('a[href]');
      if (!(link instanceof HTMLAnchorElement) || link.target === '_blank' || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      const destination = new URL(link.href, window.location.href);
      if (destination.pathname === pathname && destination.search === window.location.search) return;
      if (!window.confirm(UNSAVED_LEAVE_MESSAGE)) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    document.addEventListener('click', guard, true);
    return () => document.removeEventListener('click', guard, true);
  }, [hasUnsavedChanges, pathname]);

  // ---- back/forward guard ----
  // Strategy: when the user starts editing, push a sentinel history entry so that
  // browser Back/Forward lands on a *different* entry than the one we just pushed.
  // On popstate (Back/Forward), the sentinel entry is the one we pushed; the current
  // page is the real previous entry with the same URL. We ask the user whether they
  // really want to leave (discarding edits).
  //
  // Known limitation: the Forward history beyond the current page is dropped while
  // editing (pushState creates a new entry and truncates forward history), which is
  // acceptable because the user was actively editing this page.
  useEffect(() => {
    if (!hasUnsavedChanges) return;

    // Mark that we are leaving via user confirmation so we skip the guard during
    // programmatic navigation.
    leaving.current = false;

    // Push a sentinel so Back/Forward creates a separate history entry.
    window.history.pushState({ ...window.history.state, wayloGuard: true }, '', window.location.href);

    const onPopState = () => {
      if (leaving.current) return; // programmatic back after confirmation
      if (window.confirm(UNSAVED_LEAVE_MESSAGE)) {
        leaving.current = true;
        window.removeEventListener('popstate', onPopState);
        window.history.back();
      } else {
        // Stay: re-push the sentinel so the browser doesn't re-fire popstate immediately.
        window.history.pushState({ ...window.history.state, wayloGuard: true }, '', window.location.href);
      }
    };

    window.addEventListener('popstate', onPopState);

    return () => {
      window.removeEventListener('popstate', onPopState);
      // User saved/discarded while hasUnsavedChanges flips to false without leaving:
      // drop the duplicate sentinel entry so history stays clean. We re-push "back" to
      // the real entry, but mark leaving so the guard ignores it.
      if (!leaving.current && window.history.state?.wayloGuard === true) {
        leaving.current = true;
        window.history.back();
      }
    };
  }, [hasUnsavedChanges]);
  // Orders are per account, not per hotel: the organization/hotel selector would be misleading there.
  const showWorkspace = !pathname.startsWith('/partner/orders');
  return (
    <div className="space-y-space-md">
      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-primary" style={{ fontVariationSettings: "'FILL' 1" }}>storefront</span>
          <h1 className="text-sm font-semibold uppercase tracking-wider text-on-surface-variant">Partner Paneli</h1>
        </div>
        {showWorkspace && <WorkspaceBar />}
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
      {/* key: switching hotel remounts the page, so it starts clean (no stale data, no request with
          the previous hotel's ids). Unsaved calendar edits are protected by the calendar itself. */}
      <div key={hotelId ?? 'none'}>{children}</div>
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
