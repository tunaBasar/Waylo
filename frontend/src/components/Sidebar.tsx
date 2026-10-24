'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { useAuth } from '@/lib/auth';

const nav = [
  { href: '/', label: 'Ana Sayfa', icon: 'dashboard' },
  { href: '/partner', label: 'Partner Paneli', icon: 'storefront' },
  { href: '/flights', label: 'Ucuslar', icon: 'flight' },
  { href: '/hotels', label: 'Oteller', icon: 'hotel' },
  { href: '/vehicles', label: 'Araclar', icon: 'directions_car' },
  { href: '/rentals', label: 'Kiralama', icon: 'car_rental' },
  { href: '/bookings', label: 'Rezervasyonlar', icon: 'confirmation_number' },
  { href: '/customers', label: 'Musteriler', icon: 'group' },
  { href: '/payments', label: 'Odemeler', icon: 'payments' },
  { href: '/notifications', label: 'Bildirimler', icon: 'notifications' },
  { href: '/checkins', label: 'Check-in', icon: 'check_circle' },
  { href: '/profile', label: 'Profil', icon: 'person' },
  { href: '/settings', label: 'Ayarlar', icon: 'settings' },
];

// Bottom tab bar items (mobile)
const bottomTabs = [
  { href: '/', label: 'Ana Sayfa', icon: 'dashboard' },
  { href: '/flights', label: 'Ucuslar', icon: 'flight' },
  { href: '/hotels', label: 'Oteller', icon: 'hotel' },
  { href: '/bookings', label: 'Rezervasyon', icon: 'confirmation_number' },
  { href: '/settings', label: 'Menu', icon: 'menu' },
];

export default function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <>
      {/* Desktop Sidebar */}
      <aside className="hidden lg:flex fixed inset-y-0 left-0 z-40 w-64 bg-surface-container-lowest border-r border-outline-variant flex-col">
        {/* Logo */}
        <div className="flex items-center gap-2.5 px-6 py-5 border-b border-outline-variant">
          <span className="material-symbols-outlined text-primary text-2xl" style={{ fontVariationSettings: "'FILL' 1" }}>flight</span>
          <div>
            <span className="text-lg font-bold text-on-surface tracking-tight">WAYLO</span>
            <p className="text-[10px] text-on-surface-variant -mt-0.5">Seyahat Yonetimi</p>
          </div>
        </div>

        {/* Nav */}
        <nav className="mt-2 px-3 space-y-0.5 flex-1 overflow-y-auto">
          {nav.map(({ href, label, icon }) => {
            const active = pathname === href || (href !== '/' && pathname.startsWith(href));
            return (
              <Link key={href} href={href}
                className={cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all',
                  active
                    ? 'bg-primary-fixed text-primary font-semibold'
                    : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'
                )}>
                <span className={cn('material-symbols-outlined text-[20px]', active && 'text-primary')}
                  style={active ? { fontVariationSettings: "'FILL' 1" } : undefined}>
                  {icon}
                </span>
                {label}
              </Link>
            );
          })}
        </nav>

        {/* User info + logout */}
        <div className="border-t border-outline-variant p-4">
          {user && (
            <div className="flex items-center gap-3 mb-3">
              <div className="w-9 h-9 bg-primary-fixed rounded-full flex items-center justify-center">
                <span className="text-sm font-bold text-primary">
                  {user.firstName[0]}{user.lastName[0]}
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-on-surface truncate">{user.firstName} {user.lastName}</p>
                <p className="text-xs text-on-surface-variant truncate">{user.email}</p>
              </div>
            </div>
          )}
          <button onClick={() => logout()}
            className="flex items-center gap-2 w-full px-3 py-2 text-sm text-on-surface-variant hover:text-error hover:bg-error-container/50 rounded-xl transition-colors">
            <span className="material-symbols-outlined text-[18px]">logout</span>
            Cikis Yap
          </button>
        </div>
      </aside>

      {/* Mobile Top Bar */}
      <div className="lg:hidden fixed top-0 left-0 right-0 z-40 bg-surface-container-lowest border-b border-outline-variant px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-primary text-xl" style={{ fontVariationSettings: "'FILL' 1" }}>flight</span>
          <span className="text-base font-bold text-on-surface">WAYLO</span>
        </div>
        <div className="flex items-center gap-2">
          <button className="p-2 hover:bg-surface-container rounded-full transition-colors">
            <span className="material-symbols-outlined text-on-surface-variant text-xl">search</span>
          </button>
          <button className="p-2 hover:bg-surface-container rounded-full transition-colors relative">
            <span className="material-symbols-outlined text-on-surface-variant text-xl">notifications</span>
            <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-error" />
          </button>
          <Link href="/profile" className="w-8 h-8 bg-primary rounded-full flex items-center justify-center">
            <span className="text-xs font-bold text-on-primary">
              {user ? `${user.firstName[0]}${user.lastName[0]}` : 'U'}
            </span>
          </Link>
        </div>
      </div>

      {/* Mobile Bottom Tab Bar */}
      <div className="lg:hidden fixed bottom-0 left-0 right-0 z-40 bg-surface-container-lowest border-t border-outline-variant flex items-center justify-around px-2 py-1.5 safe-area-bottom">
        {bottomTabs.map(({ href, label, icon }) => {
          const active = pathname === href || (href !== '/' && pathname.startsWith(href));
          return (
            <Link key={href} href={href}
              className={cn(
                'flex flex-col items-center gap-0.5 py-1 px-3 rounded-xl transition-all min-w-[52px]',
                active ? 'text-primary' : 'text-on-surface-variant'
              )}>
              <span className={cn('material-symbols-outlined text-[22px]', active && 'text-primary')}
                style={active ? { fontVariationSettings: "'FILL' 1" } : undefined}>
                {icon}
              </span>
              <span className="text-[10px] font-medium">{label}</span>
            </Link>
          );
        })}
      </div>
    </>
  );
}
