'use client';

import { Suspense, useEffect, useState } from 'react';
import { useAuth } from '@/lib/auth';
import { partnerApi } from '@/lib/partner';
import { SurfaceShell, type NavItem } from '@/components/SurfaceShell';
import { SurfaceGuard } from '@/components/SurfaceGuard';
import { Notice } from '@/components/Notice';

const baseNav: NavItem[] = [
  { href: '/', label: 'Ana Sayfa', icon: 'home', exact: true },
  { href: '/hotels', label: 'Oteller', icon: 'hotel' },
  { href: '/flights', label: 'Ucuslar', icon: 'flight' },
  { href: '/vehicles', label: 'Araclar', icon: 'directions_car' },
  { href: '/orders', label: 'Siparislerim', icon: 'receipt_long' },
  { href: '/rentals', label: 'Kiralamalarim', icon: 'car_rental' },
  { href: '/profile', label: 'Profil', icon: 'person' },
  { href: '/settings', label: 'Ayarlar', icon: 'settings' },
];

const partnerLink: NavItem = { href: '/partner', label: 'Isletme Paneli', icon: 'storefront' };

export default function CustomerLayout({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [hasMembership, setHasMembership] = useState(false);

  // Customers who belong to an organization get one link to the partner surface.
  useEffect(() => {
    if (!user || user.role === 'ADMIN') return;
    let cancelled = false;
    partnerApi.myMemberships()
      .then(res => { if (!cancelled) setHasMembership((res.data ?? []).some(m => m.status === 'ACTIVE')); })
      .catch(() => { if (!cancelled) setHasMembership(false); });
    return () => { cancelled = true; };
  }, [user]);

  const nav = hasMembership ? [...baseNav.slice(0, -2), partnerLink, ...baseNav.slice(-2)] : baseNav;

  return (
    <SurfaceGuard>
      <SurfaceShell brand="WAYLO" subtitle="Seyahatini planla" icon="flight" nav={nav} bottomNav={nav}
        headerExtra={<Suspense fallback={null}><Notice /></Suspense>}>
        {children}
      </SurfaceShell>
    </SurfaceGuard>
  );
}
