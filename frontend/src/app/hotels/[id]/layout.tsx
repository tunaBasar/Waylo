// Opt-out: AuthProvider in root layout reads usePathname(), which is a URL data hook
// that needs Suspense during prerender for dynamic routes. See docs: cacheComponents instant guide.
export const instant = false;

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
