import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '@/api';

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  offline: boolean;
  reload(): void;
}

/** Runs `fn` on mount and on reload. Skips when `enabled` is false. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[], enabled = true): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (!enabled) { setLoading(false); return; }
    let alive = true;
    setLoading(true);
    setError(null);
    setOffline(false);
    fn().then((d) => { if (alive) setData(d); })
      .catch((e: unknown) => {
        if (!alive) return;
        setData(null);
        if (e instanceof ApiError && e.isNetwork) setOffline(true);
        setError(e instanceof Error ? e.message : 'Bir hata oluştu');
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick, enabled]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, loading, error, offline, reload };
}
