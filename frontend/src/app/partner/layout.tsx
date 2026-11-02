'use client';

import { useEffect, useRef, useState } from 'react';
import { usePathname } from 'next/navigation';
import { PartnerProvider, usePartner } from '@/lib/partner-context';
import { SurfaceShell, type NavItem } from '@/components/SurfaceShell';
import { SurfaceGuard } from '@/components/SurfaceGuard';
import { inputClass } from '@/components/partner/ui';
import { partnerCarApi, type PartnerCarStatus } from '@/lib/partner-car';

const UNSAVED_LEAVE_MESSAGE =
  'Kaydedilmemis degisiklikler kaybolacak. Sayfadan ayrilmak istiyor musunuz?';

// ── Car status banner ──────────────────────────────────────────────────────────

function CarStatusBanner() {
  const { organizationId, membership } = usePartner();
  // Status is stored with the org it belongs to, so switching orgs never shows a stale banner.
  const [loaded, setLoaded] = useState<{ orgId: string; status: PartnerCarStatus } | null>(null);

  useEffect(() => {
    if (!organizationId) return;
    let cancelled = false;
    partnerCarApi.status(organizationId)
      .then(res => { if (!cancelled) setLoaded({ orgId: organizationId, status: res.data }); })
      .catch(() => { /* banner is advisory; page itself reports API errors */ });
    return () => { cancelled = true; };
  }, [organizationId]);

  const status = loaded && loaded.orgId === organizationId ? loaded.status : null;
  if (!status) return null;

  const isIndividual = membership?.organizationLegalType === 'INDIVIDUAL';
  const gateClosed = isIndividual && !status.individualGateOpen;
  if (status.sellable && !gateClosed) return null;

  return (
    <div className="space-y-2 rounded-xl bg-warning-container px-4 py-3 text-sm text-on-warning-container">
      {/* Individual gate message (shown alongside other messages) */}
      {gateClosed && (
        <div className="flex items-start gap-2">
          <span className="material-symbols-outlined text-[20px]">lock</span>
          <span>Sahis isletmelerin ilanlari yasal duzenleme tamamlanana kadar satisa kapali. Sube, filo ve belgelerinizi simdiden hazirlayabilirsiniz.</span>
        </div>
      )}
      {/* Missing org documents */}
      {status.missingOrgDocuments.length > 0 && (
        <div className="flex items-start gap-2">
          <span className="material-symbols-outlined text-[20px]">description</span>
          <span>Eksik organizasyon belgeleri: {status.missingOrgDocuments.join(', ')}. Tamamlayin.</span>
        </div>
      )}
      {/* No sellable vehicles (missingOrgDocuments empty) */}
      {!status.sellable && status.missingOrgDocuments.length === 0 && (
        <div className="flex items-start gap-2">
          <span className="material-symbols-outlined text-[20px]">directions_car</span>
          <span>Satisa acik arac yok: Filo sayfasinda eksik arac belgelerini tamamlayin.</span>
        </div>
      )}
    </div>
  );
}

// ── Hotel nav (unchanged) ─────────────────────────────────────────────────────

const hotelNav: NavItem[] = [
  { href: '/partner', label: 'Isletmeler', icon: 'business', exact: true },
  { href: '/partner/room-types', label: 'Oda tipleri', icon: 'bed' },
  { href: '/partner/rate-plans', label: 'Fiyat planlari', icon: 'sell' },
  { href: '/partner/calendar', label: 'Takvim', icon: 'calendar_month' },
  { href: '/partner/redeem', label: 'QR Okut', icon: 'qr_code_scanner' },
];

// ── Car rental nav ─────────────────────────────────────────────────────────────

const carNav: NavItem[] = [
  { href: '/partner/car/branches', label: 'Subeler', icon: 'location_on' },
  { href: '/partner/car/vehicles', label: 'Filo', icon: 'directions_car' },
  { href: '/partner/car/rates', label: 'Fiyatlar', icon: 'attach_money' },
  { href: '/partner/car/one-way', label: 'Tek yon', icon: 'swap_horiz' },
  { href: '/partner/car/documents', label: 'Belgeler', icon: 'description' },
  { href: '/partner/car/rentals', label: 'Kiralamalar', icon: 'receipt_long' },
  { href: '/partner/redeem', label: 'QR Okut', icon: 'qr_code_scanner' },
];

// ── Workspace bar (hotel selector hidden for CAR_RENTAL) ───────────────────────

function WorkspaceBar() {
  const { memberships, organizationId, hotels, hotelId, selectOrganization, selectHotel, loading, error, reload, hasUnsavedChanges, isCarRentalOrg } = usePartner();
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
      {/* Hotel selector only for HOTEL orgs */}
      {!isCarRentalOrg && (
        <label className="space-y-1 min-w-[200px]">
          <span className="text-[11px] font-medium text-on-surface-variant">Otel</span>
          <select className={inputClass} value={hotelId ?? ''} disabled={hotels.length === 0 || hasUnsavedChanges}
            title={hasUnsavedChanges ? 'Once degisiklikleri kaydedin veya geri alin' : undefined}
            onChange={e => selectHotel(e.target.value)}>
            {hotels.length === 0 && <option value="">Otel yok</option>}
            {hotels.map(h => <option key={h.id} value={h.id}>{h.name}</option>)}
          </select>
        </label>
      )}
    </div>
  );
}

// ── Chrome (unsaved changes guard, unchanged) ──────────────────────────────────

function PartnerChrome({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { hotelId, hasUnsavedChanges, isCarRentalOrg } = usePartner();
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
  useEffect(() => {
    if (!hasUnsavedChanges) return;

    leaving.current = false;
    window.history.pushState({ ...window.history.state, wayloGuard: true }, '', window.location.href);

    const onPopState = () => {
      if (leaving.current) return;
      if (window.confirm(UNSAVED_LEAVE_MESSAGE)) {
        leaving.current = true;
        window.removeEventListener('popstate', onPopState);
        window.history.back();
      } else {
        window.history.pushState({ ...window.history.state, wayloGuard: true }, '', window.location.href);
      }
    };

    window.addEventListener('popstate', onPopState);

    return () => {
      window.removeEventListener('popstate', onPopState);
      if (!leaving.current && window.history.state?.wayloGuard === true) {
        leaving.current = true;
        window.history.back();
      }
    };
  }, [hasUnsavedChanges]);

  return (
    <div className="space-y-space-md">
      <WorkspaceBar />
      {isCarRentalOrg && <CarStatusBanner />}
      {/* key: switching hotel remounts the page for HOTEL orgs; for CAR_RENTAL, switching org remounts too */}
      <div key={isCarRentalOrg ? 'car' : (hotelId ?? 'none')}>{children}</div>
    </div>
  );
}

// ── Frame (selects nav based on businessType) ─────────────────────────────────

function PartnerFrame({ children }: { children: React.ReactNode }) {
  const { memberships, loading, error, membership, isCarRentalOrg } = usePartner();
  const hasMembership = loading || error ? null : memberships.length > 0;

  // Determine nav and subtitle
  const nav = isCarRentalOrg ? carNav : hotelNav;
  const subtitle = membership?.organizationName ?? 'Isletme Paneli';

  return (
    <SurfaceGuard hasMembership={hasMembership}>
      <SurfaceShell brand="WAYLO" subtitle={subtitle} icon={isCarRentalOrg ? 'directions_car' : 'storefront'}
        nav={nav} bottomNav={nav}>
        <PartnerChrome>{children}</PartnerChrome>
      </SurfaceShell>
    </SurfaceGuard>
  );
}

export default function PartnerLayout({ children }: { children: React.ReactNode }) {
  return (
    <PartnerProvider>
      <PartnerFrame>{children}</PartnerFrame>
    </PartnerProvider>
  );
}
