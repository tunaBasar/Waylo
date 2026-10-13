'use client';

import { usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import Sidebar from './Sidebar';

const PUBLIC_PATHS = ['/login', '/register', '/forgot-password'];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, loading } = useAuth();

  // Public pages: no sidebar, full-screen
  if (PUBLIC_PATHS.includes(pathname)) {
    return <>{children}</>;
  }

  // Loading state
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
      </div>
    );
  }

  // Not logged in: will redirect via AuthProvider
  if (!user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
      </div>
    );
  }

  // Authenticated layout: sidebar + main content
  return (
    <>
      <Sidebar />
      <main className="lg:ml-64 min-h-screen pt-16 pb-20 lg:pt-0 lg:pb-0">
        <div className="p-4 lg:p-8 max-w-7xl mx-auto">
          {children}
        </div>
      </main>
    </>
  );
}
