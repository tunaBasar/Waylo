'use client';

import { useSearchParams } from 'next/navigation';

/** One-line message passed through `?notice=` by a guard redirect. */
export function Notice() {
  const notice = useSearchParams().get('notice');
  if (!notice) return null;
  return (
    <div role="status" className="mb-4 p-3 bg-tertiary-fixed rounded-lg text-on-tertiary-fixed-variant text-sm flex items-center gap-2">
      <span className="material-symbols-outlined text-base">info</span>
      {notice.slice(0, 200)}
    </div>
  );
}
