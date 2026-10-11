'use client';

import { cn } from '@/lib/utils';

interface StatusBadgeProps {
  status: string;
  className?: string;
}

const colors: Record<string, string> = {
  SCHEDULED: 'bg-blue-100 text-blue-700',
  CANCELLED: 'bg-red-100 text-red-700',
  AVAILABLE: 'bg-emerald-100 text-emerald-700',
  RESERVED: 'bg-amber-100 text-amber-700',
  OCCUPIED: 'bg-purple-100 text-purple-700',
  RENTED: 'bg-orange-100 text-orange-700',
  MAINTENANCE: 'bg-gray-100 text-gray-600',
  PENDING: 'bg-amber-100 text-amber-700',
  CONFIRMED: 'bg-emerald-100 text-emerald-700',
  COMPLETED: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-red-100 text-red-700',
  REFUNDED: 'bg-purple-100 text-purple-700',
  SENT: 'bg-emerald-100 text-emerald-700',
  CHECKED_IN: 'bg-blue-100 text-blue-700',
  BOARDING_PASS_ISSUED: 'bg-indigo-100 text-indigo-700',
  BRONZE: 'bg-amber-100 text-amber-700',
  SILVER: 'bg-gray-200 text-gray-700',
  GOLD: 'bg-yellow-100 text-yellow-700',
  PLATINUM: 'bg-slate-200 text-slate-700',
};

export default function StatusBadge({ status, className }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
        colors[status] || 'bg-gray-100 text-gray-700',
        className
      )}
    >
      {status.replace(/_/g, ' ')}
    </span>
  );
}
