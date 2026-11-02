'use client';

import { SurfaceShell, type NavItem } from '@/components/SurfaceShell';
import { SurfaceGuard } from '@/components/SurfaceGuard';

const nav: NavItem[] = [
  { href: '/admin', label: 'Genel Bakis', icon: 'dashboard', exact: true },
  { href: '/admin/organizations', label: 'Isletmeler', icon: 'domain' },
  { href: '/admin/users', label: 'Kullanicilar', icon: 'manage_accounts' },
  { href: '/admin/flights', label: 'Ucuslar', icon: 'flight' },
  { href: '/admin/airports', label: 'Havalimanlari', icon: 'flight_takeoff' },
  { href: '/admin/seats', label: 'Koltuklar', icon: 'airline_seat_recline_normal' },
  { href: '/admin/supplier-balance', label: 'Tedarikci Bakiyesi', icon: 'account_balance_wallet' },
  { href: '/admin/vehicles', label: 'Araclar', icon: 'directions_car' },
  { href: '/admin/car-documents', label: 'Arac Belgeleri', icon: 'assignment_turned_in' },
  { href: '/admin/bus-documents', label: 'Otobus Belgeleri', icon: 'assignment_turned_in' },
  { href: '/admin/bus-terminals', label: 'Otobus Duraklari', icon: 'directions_bus' },
  { href: '/admin/bookings', label: 'Siparisler', icon: 'confirmation_number' },
  { href: '/admin/customers', label: 'Musteriler', icon: 'group' },
  { href: '/admin/campaigns', label: 'Kampanyalar', icon: 'sell' },
  { href: '/admin/payments', label: 'Odemeler', icon: 'payments' },
  { href: '/admin/notifications', label: 'Bildirimler', icon: 'notifications' },
  { href: '/admin/checkins', label: 'Check-in', icon: 'check_circle' },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <SurfaceGuard>
      <SurfaceShell brand="WAYLO" subtitle="Platform Yonetimi" icon="admin_panel_settings" nav={nav} bottomNav={nav}>
        {children}
      </SurfaceShell>
    </SurfaceGuard>
  );
}
