'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth';
import { guardRedirect, redirectUrl } from '@/lib/surface';
import { Spinner } from './SurfaceShell';

/**
 * Route guard shared by the surface layouts. Renders children only when the current user may see
 * `pathname` (see lib/surface.ts); otherwise redirects. `hasMembership` is only passed by /partner.
 */
export function SurfaceGuard({ hasMembership = null, children }: { hasMembership?: boolean | null; children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const redirect = user ? guardRedirect(pathname, user.role, hasMembership) : null;
  const target = redirect ? redirectUrl(redirect) : null;

  useEffect(() => {
    if (target) router.replace(target);
  }, [target, router]);

  // No user: AuthProvider sends the visitor to /login.
  if (loading || !user || target) return <Spinner />;
  return <>{children}</>;
}
