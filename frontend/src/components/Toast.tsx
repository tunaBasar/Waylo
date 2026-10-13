'use client';

import { createContext, useContext, useState, useCallback } from 'react';

type ToastType = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: string;
  type: ToastType;
  message: string;
  duration?: number;
}

interface ToastContextType {
  toast: (type: ToastType, message: string, duration?: number) => void;
  success: (message: string) => void;
  error: (message: string) => void;
  warning: (message: string) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<ToastContextType | null>(null);

const toastConfig: Record<ToastType, { icon: string; bg: string; iconColor: string }> = {
  success: { icon: 'check_circle', bg: 'bg-secondary-container text-on-secondary-container', iconColor: 'text-secondary' },
  error: { icon: 'error', bg: 'bg-error-container text-on-error-container', iconColor: 'text-error' },
  warning: { icon: 'warning', bg: 'bg-tertiary-fixed text-on-tertiary-fixed-variant', iconColor: 'text-tertiary' },
  info: { icon: 'info', bg: 'bg-primary-fixed text-primary', iconColor: 'text-primary' },
};

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  const addToast = useCallback((type: ToastType, message: string, duration = 4000) => {
    const id = crypto.randomUUID();
    setToasts(prev => [...prev, { id, type, message, duration }]);
    if (duration > 0) {
      setTimeout(() => removeToast(id), duration);
    }
  }, [removeToast]);

  const ctx: ToastContextType = {
    toast: addToast,
    success: (msg) => addToast('success', msg),
    error: (msg) => addToast('error', msg),
    warning: (msg) => addToast('warning', msg),
    info: (msg) => addToast('info', msg),
  };

  return (
    <ToastContext.Provider value={ctx}>
      {children}

      {/* Toast container */}
      <div className="fixed bottom-4 right-4 z-[100] space-y-2 max-w-sm">
        {toasts.map(t => {
          const cfg = toastConfig[t.type];
          return (
            <div key={t.id}
              className={`flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg animate-in slide-in-from-right-5 fade-in duration-200 ${cfg.bg}`}>
              <span className={`material-symbols-outlined text-[18px] mt-0.5 shrink-0 ${cfg.iconColor}`}>{cfg.icon}</span>
              <p className="text-sm flex-1">{t.message}</p>
              <button onClick={() => removeToast(t.id)} className="shrink-0 opacity-60 hover:opacity-100">
                <span className="material-symbols-outlined text-[14px]">close</span>
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
