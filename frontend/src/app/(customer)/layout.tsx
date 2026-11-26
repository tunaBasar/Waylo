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
  { href: '/bus', label: 'Otobus', icon: 'directions_bus' },
  { href: '/cart', label: 'Sepet', icon: 'shopping_cart' },
  { href: '/orders', label: 'Siparislerim', icon: 'receipt_long' },
  { href: '/support', label: 'Destek', icon: 'support_agent' },
  { href: '/rentals', label: 'Kiralamalarim', icon: 'car_rental' },
  { href: '/profile', label: 'Profil', icon: 'person' },
  { href: '/settings', label: 'Ayarlar', icon: 'settings' },
];

const addBusinessLink: NavItem = { href: '/partner', label: 'Isletmeni ekle', icon: 'add_business' };
const partnerLink: NavItem = { href: '/partner', label: 'Isletme Paneli', icon: 'storefront' };

export default function CustomerLayout({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [hasMembership, setHasMembership] = useState(false);
  const [membershipKnown, setMembershipKnown] = useState(false);

  // Customers who belong to an organization get one link to the partner surface.
  useEffect(() => {
    if (!user || user.role === 'ADMIN') return;
    let cancelled = false;
    partnerApi.myMemberships()
      .then(res => { if (!cancelled) { setHasMembership((res.data ?? []).some(m => m.status === 'ACTIVE')); setMembershipKnown(true); } })
      .catch(() => { if (!cancelled) setHasMembership(false); });
    return () => { cancelled = true; };
  }, [user]);

  const extra = hasMembership ? partnerLink : (user && user.role !== 'ADMIN' && membershipKnown ? addBusinessLink : null);
  const nav = extra ? [...baseNav.slice(0, -2), extra, ...baseNav.slice(-2)] : baseNav;

  return (
    <SurfaceGuard>
      <SurfaceShell brand="WAYLO" subtitle="Seyahatini planla" icon="flight" nav={nav} bottomNav={nav}
        headerExtra={<Suspense fallback={null}><Notice /></Suspense>}>
        {children}
      </SurfaceShell>
    </SurfaceGuard>
  );
}
