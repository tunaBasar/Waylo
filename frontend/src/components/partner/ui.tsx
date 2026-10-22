'use client';

/**
 * Small shared building blocks for /partner pages. Every partner page should compose these
 * instead of re-styling headers, cards, inputs, empty states and status pills by hand.
 */
import { cn } from '@/lib/utils';

export function PageHeader({ title, subtitle, actions }: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-space-sm">
      <div className="min-w-0">
        <h2 className="text-xl font-semibold text-on-surface tracking-tight">{title}</h2>
        {subtitle && <p className="text-sm text-on-surface-variant mt-0.5">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Card({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn('bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/40', className)}>
      {children}
    </div>
  );
}

export function Button({ children, variant = 'primary', icon, className, ...rest }:
  React.ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
    icon?: string;
  }) {
  const styles = {
    primary: 'bg-primary text-on-primary hover:opacity-90',
    secondary: 'bg-secondary text-on-secondary hover:opacity-90',
    ghost: 'bg-transparent text-on-surface-variant hover:bg-surface-container',
    danger: 'bg-error text-on-error hover:opacity-90',
  }[variant];
  return (
    <button
      {...rest}
      className={cn(
        'inline-flex items-center justify-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium shadow-sm transition-all active:scale-95 disabled:opacity-50 disabled:pointer-events-none',
        styles,
        className,
      )}>
      {icon && <span className="material-symbols-outlined text-[18px]">{icon}</span>}
      {children}
    </button>
  );
}

export function Field({ label, hint, error, children }: {
  label: string;
  hint?: string;
  error?: string | null;
  children: React.ReactNode;
}) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-medium text-on-surface-variant">{label}</span>
      {children}
      {error ? <span className="block text-xs text-error">{error}</span>
        : hint ? <span className="block text-xs text-outline">{hint}</span> : null}
    </label>
  );
}

export const inputClass =
  'w-full bg-surface-container-low text-on-surface placeholder:text-outline px-3 py-2 rounded-lg text-sm outline-none border border-outline-variant/60 focus:border-primary transition-colors';

export function StatusPill({ tone, children }: { tone: 'success' | 'warning' | 'error' | 'neutral'; children: React.ReactNode }) {
  const styles = {
    success: 'bg-secondary-container text-on-secondary-container',
    warning: 'bg-tertiary-fixed text-on-tertiary-fixed-variant',
    error: 'bg-error-container text-on-error-container',
    neutral: 'bg-surface-container-high text-on-surface-variant',
  }[tone];
  return <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold', styles)}>{children}</span>;
}

export function EmptyState({ icon, title, description, action }: {
  icon: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <Card className="py-12 px-6 text-center">
      <span className="material-symbols-outlined text-outline text-5xl block mb-3">{icon}</span>
      <p className="text-on-surface font-medium">{title}</p>
      {description && <p className="text-sm text-on-surface-variant mt-1 max-w-md mx-auto">{description}</p>}
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </Card>
  );
}

export function LoadingRows({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-16 rounded-xl bg-surface-container animate-pulse" />
      ))}
    </div>
  );
}

/** Shown by pages that need a selected hotel when the user has none yet. */
export function NeedsHotel() {
  return (
    <EmptyState
      icon="domain_add"
      title="Once bir otel secin"
      description="Bu sayfa secili otel icin calisir. Ust kisimdan bir otel secin veya Isletmeler sayfasindan otel ekleyin."
    />
  );
}
