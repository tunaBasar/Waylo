'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { useAuth } from '@/lib/auth';

export interface NavItem {
  href: string;
  label: string;
  icon: string;
  /** Match only the exact path (surface home pages). */
  exact?: boolean;
}

interface SurfaceShellProps {
  brand: string;
  subtitle: string;
  icon: string;
  nav: NavItem[];
  /** Mobile bottom tab bar (scrolls horizontally when it does not fit). */
  bottomNav: NavItem[];
  /** Shown under the top bar, e.g. the selected organization. */
  headerExtra?: React.ReactNode;
  children: React.ReactNode;
}

const isActive = (pathname: string, item: NavItem) =>
  item.exact ? pathname === item.href : pathname === item.href || pathname.startsWith(`${item.href}/`);

export function Spinner() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
    </div>
  );
}

/** Sidebar + mobile top bar + mobile bottom tabs for one surface. Each surface passes its own navigation. */
export function SurfaceShell({ brand, subtitle, icon, nav, bottomNav, headerExtra, children }: SurfaceShellProps) {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <>
      <aside className="hidden lg:flex fixed inset-y-0 left-0 z-40 w-64 bg-surface-container-lowest border-r border-outline-variant flex-col">
        <div className="flex items-center gap-2.5 px-6 py-5 border-b border-outline-variant">
          <span className="material-symbols-outlined text-primary text-2xl" style={{ fontVariationSettings: "'FILL' 1" }}>{icon}</span>
          <div>
            <span className="text-lg font-bold text-on-surface tracking-tight">{brand}</span>
            <p className="text-[10px] text-on-surface-variant -mt-0.5">{subtitle}</p>
          </div>
        </div>

        <nav className="mt-2 px-3 space-y-0.5 flex-1 overflow-y-auto">
          {nav.map(item => {
            const active = isActive(pathname, item);
            return (
              <Link key={item.href} href={item.href}
                className={cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all',
                  active
                    ? 'bg-primary-fixed text-primary font-semibold'
                    : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface',
                )}>
                <span className={cn('material-symbols-outlined text-[20px]', active && 'text-primary')}
                  style={active ? { fontVariationSettings: "'FILL' 1" } : undefined}>
                  {item.icon}
                </span>
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-outline-variant p-4">
          {user && (
            <div className="flex items-center gap-3 mb-3">
              <div className="w-9 h-9 bg-primary-fixed rounded-full flex items-center justify-center">
                <span className="text-sm font-bold text-primary">{user.firstName[0]}{user.lastName[0]}</span>
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

      <div className="lg:hidden fixed top-0 left-0 right-0 z-40 bg-surface-container-lowest border-b border-outline-variant px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2 min-w-0">
          <span className="material-symbols-outlined text-primary text-xl" style={{ fontVariationSettings: "'FILL' 1" }}>{icon}</span>
          <span className="text-base font-bold text-on-surface">{brand}</span>
        </div>
        <button onClick={() => logout()} aria-label="Cikis Yap"
          className="w-8 h-8 bg-primary rounded-full flex items-center justify-center">
          <span className="text-xs font-bold text-on-primary">
            {user ? `${user.firstName[0]}${user.lastName[0]}` : 'U'}
          </span>
        </button>
      </div>

      <div className="lg:hidden fixed bottom-0 left-0 right-0 z-40 bg-surface-container-lowest border-t border-outline-variant flex items-center gap-1 overflow-x-auto no-scrollbar px-2 py-1.5 safe-area-bottom">
        {bottomNav.map(item => {
          const active = isActive(pathname, item);
          return (
            <Link key={item.href} href={item.href}
              className={cn(
                'flex flex-col items-center gap-0.5 py-1 px-3 rounded-xl transition-all min-w-[60px] shrink-0 flex-1',
                active ? 'text-primary' : 'text-on-surface-variant',
              )}>
              <span className={cn('material-symbols-outlined text-[22px]', active && 'text-primary')}
                style={active ? { fontVariationSettings: "'FILL' 1" } : undefined}>
                {item.icon}
              </span>
              <span className="text-[10px] font-medium">{item.label}</span>
            </Link>
          );
        })}
      </div>

      <main className="lg:ml-64 min-h-screen pt-16 pb-20 lg:pt-0 lg:pb-0">
        <div className="p-4 lg:p-8 max-w-7xl mx-auto">
          {headerExtra}
          {children}
        </div>
      </main>
    </>
  );
}
