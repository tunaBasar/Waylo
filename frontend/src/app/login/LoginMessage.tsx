'use client';

import { useSearchParams } from 'next/navigation';

export default function LoginMessage() {
  const message = useSearchParams().get('message');
  if (!message) return null;

  return (
    <div role="status" className="mb-4 p-3 bg-secondary-container rounded-lg text-on-secondary-container text-sm flex items-center gap-2">
      <span className="material-symbols-outlined text-base text-secondary">check_circle</span>
      {message.slice(0, 200)}
    </div>
  );
}
