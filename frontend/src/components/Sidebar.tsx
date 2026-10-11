'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import {
  Plane, Building2, Car, Package, Users, CreditCard,
  Bell, TicketCheck, LayoutDashboard, Menu, X,
} from 'lucide-react';
import { useState } from 'react';

const nav = [
  { href: '/', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/flights', label: 'Ucuslar', icon: Plane },
  { href: '/hotels', label: 'Oteller', icon: Building2 },
  { href: '/vehicles', label: 'Araclar', icon: Car },
  { href: '/bookings', label: 'Rezervasyonlar', icon: Package },
  { href: '/customers', label: 'Musteriler', icon: Users },
  { href: '/payments', label: 'Odemeler', icon: CreditCard },
  { href: '/notifications', label: 'Bildirimler', icon: Bell },
  { href: '/checkins', label: 'Check-in', icon: TicketCheck },
];

export default function Sidebar() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  return (
    <>
      {/* Mobile toggle */}
      <button
        onClick={() => setOpen(!open)}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 bg-white rounded-lg shadow-md"
      >
        {open ? <X size={20} /> : <Menu size={20} />}
      </button>

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-64 bg-white border-r border-gray-200 transform transition-transform duration-200 lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="flex items-center gap-2 px-6 py-5 border-b border-gray-200">
          <Plane className="text-blue-600" size={28} />
          <span className="text-xl font-bold text-gray-900">Waylo</span>
        </div>

        <nav className="mt-4 px-3 space-y-1">
          {nav.map(({ href, label, icon: Icon }) => {
            const active = pathname === href || (href !== '/' && pathname.startsWith(href));
            return (
              <Link
                key={href}
                href={href}
                onClick={() => setOpen(false)}
                className={cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                  active
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                )}
              >
                <Icon size={18} />
                {label}
              </Link>
            );
          })}
        </nav>
      </aside>

      {/* Mobile overlay */}
      {open && (
        <div
          className="lg:hidden fixed inset-0 z-30 bg-black/20"
          onClick={() => setOpen(false)}
        />
      )}
    </>
  );
}
